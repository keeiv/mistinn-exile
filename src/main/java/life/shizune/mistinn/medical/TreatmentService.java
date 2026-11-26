package life.shizune.mistinn.medical;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnSounds;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 處置引導: timed uses of the medical and repair items, run by the server. A use starts on a
 * right click and runs its full time unless it is broken off: by switching the held item, by
 * rolling, by attacking, or by a hit of at least {@link #FLINCH} (vanilla
 * points, after armour). While it runs the hunter walks slower (by the treatment's share) and
 * cannot sprint. Everyone who can see the hunter is told, so the animation plays for them too.
 *
 * <p>Also keeps the lasting effects: the injector's mending over time and the painkillers'
 * numbness ({@link MistinnAttachments#PAINKILLER}).
 */
public final class TreatmentService {
	/** A hit this heavy (vanilla points after armour) breaks the use off. */
	public static final float FLINCH = 3.0F;
	/** Part health a roll or dive costs while numb with a destroyed part, split over the whole body: 1.5× the dive's landing. */
	public static final float NUMB_STRAIN = 18.0F;

	private static final Identifier SLOW_ID = Mistinn.id("treatment_slow");

	/** 引導中: one use in progress; {@code source} is the grid spot of an item used from the pouch, null for one in hand. */
	private record Channel(Item item, InteractionHand hand, int slot, ItemStack target, Treatment treatment, int ticks, int[] elapsed,
		life.shizune.mistinn.grid.QuickUse.@org.jspecify.annotations.Nullable Spot source) {
	}

	private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
	private static final Map<UUID, Integer> MENDING = new HashMap<>();
	private static final Set<UUID> NUMB = new HashSet<>();

	private TreatmentService() {
	}

	public static boolean applies(final Player player) {
		return BodyService.applies(player);
	}

	public static boolean active(final Player player) {
		return CHANNELS.containsKey(player.getUUID());
	}

	/** Begins a use; the item has already agreed it can be used. */
	public static void start(final ServerPlayer player, final InteractionHand hand, final ItemStack stack, final TreatmentItem item) {
		if (life.shizune.mistinn.team.DownService.isDown(player)) {
			return;
		}
		// intellect shortens every treatment and repair
		int ticks = life.shizune.mistinn.skill.SkillService.treatmentTicks(player, item.ticks(player, stack));
		Treatment treatment = item.treatment(player, stack);
		int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : -1;
		begin(player, new Channel(item, hand, slot, item.target(player), treatment, ticks, new int[] {0}, null));
	}

	/**
	 * Begins a use of an item where it lies in the pockets or the rig (the quick-use wheel): the item
	 * stays there until the use is done, and is spent there.
	 */
	public static void startFrom(final ServerPlayer player, final life.shizune.mistinn.grid.QuickUse.Spot spot, final ItemStack stack, final TreatmentItem item) {
		if (life.shizune.mistinn.team.DownService.isDown(player)) {
			return;
		}
		int ticks = life.shizune.mistinn.skill.SkillService.treatmentTicks(player, item.ticks(player, stack));
		begin(player, new Channel(item, InteractionHand.MAIN_HAND, -1, item.target(player), item.treatment(player, stack), ticks, new int[] {0}, spot));
	}

	private static void begin(final ServerPlayer player, final Channel channel) {
		Treatment treatment = channel.treatment();
		int ticks = channel.ticks();
		Item item = channel.item();
		CHANNELS.put(player.getUUID(), channel);
		slow(player, treatment.slow);
		player.setSprinting(false);
		life.shizune.mistinn.enemy.NoiseService.emit(player, life.shizune.mistinn.enemy.NoiseService.Noise.TREATMENT);
		broadcast(player, new TreatmentPayload(player.getId(), treatment.ordinal(), ticks, Item.getId(item), RESULT_RUNNING));
	}

	/** Breaks off the hunter's use, if one runs. */
	public static void interrupt(final ServerPlayer player) {
		if (CHANNELS.remove(player.getUUID()) != null) {
			slow(player, 0.0);
			broadcast(player, new TreatmentPayload(player.getId(), -1, 0, 0, RESULT_INTERRUPTED));
			player.sendSystemMessage(Component.translatable("message.mistinn.treatment.interrupted"), true);
		}
	}

	/** A hit landed: a heavy one breaks the use off. */
	public static void hurt(final ServerPlayer player, final float vanillaDamage) {
		if (vanillaDamage >= FLINCH) {
			interrupt(player);
			life.shizune.mistinn.grid.QuickUse.stopEating(player);
		}
	}

	/** For repairs: the piece worn now is the one the use began on. */
	public static boolean sameTarget(final ServerPlayer player, final ItemStack worn) {
		Channel channel = CHANNELS.get(player.getUUID());
		return channel == null || channel.target().isEmpty() || channel.target() == worn
			|| ItemStack.isSameItem(channel.target(), worn);
	}

	public static void tick(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			Channel channel = CHANNELS.get(id);
			if (channel != null) {
				tickChannel(player, channel);
			}
			Integer mending = MENDING.get(id);
			if (mending != null) {
				if (player.isAlive() && applies(player)) {
					float dose = life.shizune.mistinn.skill.SkillService.treatmentAmount(player, InjectorItem.HEAL / InjectorItem.HEAL_TICKS);
					BodyService.set(player, BodyService.mend(BodyService.get(player), dose, BodyService.max(player)));
				}
				if (mending <= 1) {
					MENDING.remove(id);
				} else {
					MENDING.put(id, mending - 1);
				}
			}
			boolean numb = numb(player);
			if (numb != NUMB.contains(id)) {
				if (numb) {
					NUMB.add(id);
				} else {
					NUMB.remove(id);
					if (player.isAlive()) {
						player.sendSystemMessage(Component.translatable("message.mistinn.painkillers.worn_off"), true);
						player.level().playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.PAINKILLER_WEAR_OFF, SoundSource.PLAYERS, 0.8F, 1.0F);
					}
				}
				// the destroyed parts are felt again (or no longer)
				BodyService.applyPenalties(player, BodyService.get(player));
			}
		}
		CHANNELS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		MENDING.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		NUMB.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	private static void tickChannel(final ServerPlayer player, final Channel channel) {
		if (channel.source() != null) {
			tickFromPouch(player, channel);
			return;
		}
		ItemStack held = player.getItemInHand(channel.hand());
		boolean slotMoved = channel.slot() >= 0 && player.getInventory().getSelectedSlot() != channel.slot();
		if (!player.isAlive() || slotMoved || !held.is(channel.item()) || !((TreatmentItem) channel.item()).stillValid(player, held)) {
			interrupt(player);
			return;
		}
		player.setSprinting(false);
		if (++channel.elapsed()[0] < channel.ticks()) {
			return;
		}
		CHANNELS.remove(player.getUUID());
		slow(player, 0.0);
		((TreatmentItem) channel.item()).finish(player, held, channel.hand());
		life.shizune.mistinn.training.TrainingService.treated(player, channel.treatment());
		broadcast(player, new TreatmentPayload(player.getId(), -1, 0, 0, RESULT_DONE));
	}

	/** A use from the pouch: the item must still lie where it did; done, it is spent where it lies. */
	private static void tickFromPouch(final ServerPlayer player, final Channel channel) {
		ItemStack lying = life.shizune.mistinn.grid.QuickUse.at(player, channel.source());
		if (!player.isAlive() || !lying.is(channel.item()) || !((TreatmentItem) channel.item()).stillValid(player, lying)) {
			interrupt(player);
			return;
		}
		player.setSprinting(false);
		if (++channel.elapsed()[0] < channel.ticks()) {
			return;
		}
		CHANNELS.remove(player.getUUID());
		slow(player, 0.0);
		ItemStack used = lying.copy();
		((TreatmentItem) channel.item()).finish(player, used, channel.hand());
		life.shizune.mistinn.grid.QuickUse.put(player, channel.source(), used);
		life.shizune.mistinn.training.TrainingService.treated(player, channel.treatment());
		broadcast(player, new TreatmentPayload(player.getId(), -1, 0, 0, RESULT_DONE));
	}

	private static void slow(final ServerPlayer player, final double share) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		if (share <= 0.0) {
			speed.removeModifier(SLOW_ID);
		} else {
			speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW_ID, -share, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** Death, respawn or leaving: nothing carries over but the saved numbness. */
	public static void reset(final ServerPlayer player) {
		CHANNELS.remove(player.getUUID());
		MENDING.remove(player.getUUID());
		slow(player, 0.0);
	}

	// ── Lasting effects ───────────────────────────────────────────

	/** The injector's dose starts mending. */
	public static void inject(final ServerPlayer player) {
		MENDING.put(player.getUUID(), InjectorItem.HEAL_TICKS);
	}

	/** Painkillers taken: numb for {@code ticks} from now (a second dose restarts the clock). */
	public static void numb(final ServerPlayer player, final int ticks) {
		player.setAttached(MistinnAttachments.PAINKILLER, player.level().getGameTime() + ticks);
		NUMB.add(player.getUUID());
		BodyService.applyPenalties(player, BodyService.get(player));
	}

	/** Whether the hunter feels nothing of their destroyed parts. Works on both sides. */
	public static boolean numb(final Player player) {
		return numbTicks(player) > 0;
	}

	/** Ticks of numbness left. */
	public static long numbTicks(final Player player) {
		Long until = player.getAttached(MistinnAttachments.PAINKILLER);
		return until == null ? 0L : Math.max(0L, until - player.level().getGameTime());
	}

	// ── Network ───────────────────────────────────────────────────

	public static final int RESULT_RUNNING = 0;
	public static final int RESULT_DONE = 1;
	public static final int RESULT_INTERRUPTED = 2;

	private static void broadcast(final ServerPlayer player, final TreatmentPayload payload) {
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			if (watcher != player) {
				ServerPlayNetworking.send(watcher, payload);
			}
		}
		ServerPlayNetworking.send(player, payload);
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TreatmentPayload.TYPE, TreatmentPayload.CODEC);
	}

	/**
	 * A use began ({@code treatment} 0 or more, lasting {@code ticks}, with {@code item}'s raw
	 * id) or ended ({@code treatment} -1, {@code result} done or interrupted).
	 */
	public record TreatmentPayload(int entity, int treatment, int ticks, int item, int result) implements CustomPacketPayload {
		public static final Type<TreatmentPayload> TYPE = new Type<>(Mistinn.id("treatment"));
		public static final StreamCodec<ByteBuf, TreatmentPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TreatmentPayload::entity,
			ByteBufCodecs.VAR_INT, TreatmentPayload::treatment,
			ByteBufCodecs.VAR_INT, TreatmentPayload::ticks,
			ByteBufCodecs.VAR_INT, TreatmentPayload::item,
			ByteBufCodecs.VAR_INT, TreatmentPayload::result,
			TreatmentPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
