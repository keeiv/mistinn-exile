package life.shizune.mistinn.terra;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.team.DownService;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Travel to a homestead, chosen on the map. From one homestead to another the way is open and
 * quick. From anywhere else it takes a homeward chip and eight seconds standing still while the
 * crystal finds the way; moving or being hurt breaks it. Not while down, while helping someone up
 * or dragging them, nor within ten seconds of being hurt; and not again for two minutes after.
 */
public final class HomesteadTravel {
	/** How long the way home takes to find from out in the world, how long before the next, and how lately hurt is too lately. */
	public static final int CHANNEL = 160;
	public static final long COOLDOWN = 2400L;
	public static final long CALM = 200L;
	/** How far from a homestead's hub counts as being there. */
	public static final double AT_HOME = Settlements.DECK + 2.0;
	/** How far one may drift while the way is found. */
	private static final double STILL = 0.6;

	/** What the way says to a hunter who asks it. */
	public enum Result {
		GONE, CHANNELING, UNKNOWN, ASLEEP, ELSEWHERE, DOWN, BUSY, HURT, COOLING, NO_CHIP, ALREADY_THERE
	}

	/** When the hunter may travel again, on the hunter (and their client, for the map). */
	public static final AttachmentType<Long> READY_AT = AttachmentRegistry.<Long>builder()
		.persistent(com.mojang.serialization.Codec.LONG)
		.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Mistinn.id("homestead_ready_at"));

	/** A way being found: to which homestead, from where, since when. */
	private record Channel(int domain, Vec3 from, long start) {
	}

	private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
	private static final Map<UUID, Long> HURT = new HashMap<>();

	private HomesteadTravel() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(TravelPayload.TYPE, TravelPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TravelPayload.TYPE, (payload, context) -> {
			Result result = ask(context.player(), payload.domain());
			if (result != Result.GONE && result != Result.CHANNELING) {
				context.player().sendSystemMessage(Component.translatable("message.mistinn.travel." + result.name().toLowerCase(java.util.Locale.ROOT))
					.withStyle(ChatFormatting.GRAY), true);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0.0F) {
				HURT.put(player.getUUID(), player.level().getGameTime());
				if (CHANNELS.remove(player.getUUID()) != null) {
					player.sendSystemMessage(Component.translatable("message.mistinn.travel.broken").withStyle(ChatFormatting.RED), true);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(HomesteadTravel::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CHANNELS.clear();
			HURT.clear();
		});
	}

	/** The hunter asks to go to the homestead of this highland: off at once, starting to find the way, or why not. */
	public static Result ask(final ServerPlayer player, final int domain) {
		MinecraftServer server = player.level().getServer();
		Settlements.Settlement target = Settlements.of(server, domain);
		if (target == null) {
			return Result.UNKNOWN;
		}
		if (!target.lit()) {
			return Result.ASLEEP;
		}
		if (player.level().dimension() != Level.OVERWORLD || player.isSpectator()) {
			return Result.ELSEWHERE;
		}
		if (DownService.isDown(player)) {
			return Result.DOWN;
		}
		if (helping(player)) {
			return Result.BUSY;
		}
		long now = player.level().getGameTime();
		if (now - HURT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2) < CALM) {
			return Result.HURT;
		}
		if (now < player.getAttachedOrElse(READY_AT, 0L)) {
			return Result.COOLING;
		}
		Settlements.Settlement here = homeHere(server, player);
		if (here != null && here.domain() == domain) {
			return Result.ALREADY_THERE;
		}
		if (here != null) {
			// from one homestead to another the way is open
			go(player, target);
			return Result.GONE;
		}
		if (!hasChip(player)) {
			return Result.NO_CHIP;
		}
		CHANNELS.put(player.getUUID(), new Channel(domain, player.position(), now));
		player.sendSystemMessage(Component.translatable("message.mistinn.travel.finding").withStyle(ChatFormatting.AQUA), true);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.4F);
		return Result.CHANNELING;
	}

	/** Whether the hunter is helping someone up or dragging them. */
	private static boolean helping(final ServerPlayer player) {
		for (ServerPlayer other : player.level().players()) {
			DownService.Down down = other.getAttached(DownService.DOWN);
			if (down != null && down.rescuer() == player.getId()) {
				return true;
			}
		}
		return false;
	}

	/** The homestead the hunter stands at, if any. */
	public static Settlements.@Nullable Settlement homeHere(final MinecraftServer server, final ServerPlayer player) {
		for (Settlements.Settlement home : Settlements.homesteads(server)) {
			if (Math.hypot(player.getX() - (home.hub().getX() + 0.5), player.getZ() - (home.hub().getZ() + 0.5)) <= AT_HOME
				&& Math.abs(player.getY() - home.hub().getY()) < 8.0) {
				return home;
			}
		}
		return null;
	}

	private static void tick(final MinecraftServer server) {
		if (CHANNELS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Channel> entry : new ArrayList<>(CHANNELS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Channel channel = entry.getValue();
			if (player == null || !player.isAlive() || DownService.isDown(player)) {
				CHANNELS.remove(entry.getKey());
				continue;
			}
			if (player.position().distanceTo(channel.from()) > STILL) {
				CHANNELS.remove(entry.getKey());
				player.sendSystemMessage(Component.translatable("message.mistinn.travel.broken").withStyle(ChatFormatting.RED), true);
				continue;
			}
			long elapsed = player.level().getGameTime() - channel.start();
			ServerLevel level = player.level();
			double turn = elapsed * 0.35;
			for (int i = 0; i < 3; i++) {
				double a = turn + i * Math.PI * 2.0 / 3.0;
				double rise = (elapsed % 40) / 40.0 * 2.0;
				level.sendParticles(ParticleTypes.END_ROD, player.getX() + Math.cos(a) * 0.9, player.getY() + rise, player.getZ() + Math.sin(a) * 0.9, 1, 0.0, 0.0, 0.0, 0.0);
			}
			if (elapsed % 20 == 0) {
				player.sendSystemMessage(Component.translatable("message.mistinn.travel.counting", (CHANNEL - elapsed + 19) / 20).withStyle(ChatFormatting.AQUA), true);
			}
			if (elapsed >= CHANNEL) {
				CHANNELS.remove(entry.getKey());
				Settlements.Settlement target = Settlements.of(server, channel.domain());
				if (target != null && target.lit() && takeChip(player)) {
					go(player, target);
				}
			}
		}
	}

	/** Off to the homestead: out beside its hub, the way closed behind for a while. */
	private static void go(final ServerPlayer player, final Settlements.Settlement target) {
		ServerLevel level = player.level().getServer().overworld();
		Vec3 from = player.position();
		level.sendParticles(ParticleTypes.END_ROD, from.x, from.y + 1.0, from.z, 40, 0.4, 0.8, 0.4, 0.05);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2F, 1.2F);
		BlockPos at = target.hub().offset(2, 0, 0);
		player.teleportTo(level, at.getX() + 0.5, target.deck() + 1.0, at.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
		level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, target.deck() + 2.0, at.getZ() + 0.5, 40, 0.4, 0.8, 0.4, 0.05);
		level.playSound(null, at, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 1.3F);
		player.setAttached(READY_AT, level.getGameTime() + COOLDOWN);
		player.sendSystemMessage(Component.translatable("message.mistinn.travel.arrived").withStyle(ChatFormatting.AQUA), true);
	}

	/** Whether the hunter has a homeward chip about them. */
	public static boolean hasChip(final ServerPlayer player) {
		if (player.getInventory().countItem(MistinnItems.LATTICE_CHIP) > 0) {
			return true;
		}
		GridInventory grid = GridService.get(player);
		for (Area area : new Area[] {Area.POCKETS, Area.RIG, Area.BACKPACK}) {
			for (Placed placed : grid.items(area)) {
				if (placed.stack().is(MistinnItems.LATTICE_CHIP)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Spends a homeward chip: from the hotbar first, then the pockets, the rig and the backpack. */
	static boolean takeChip(final ServerPlayer player) {
		if (player.isCreative()) {
			return true;
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(MistinnItems.LATTICE_CHIP)) {
				stack.shrink(1);
				return true;
			}
		}
		GridInventory grid = GridService.get(player);
		for (Area area : new Area[] {Area.POCKETS, Area.RIG, Area.BACKPACK}) {
			List<Placed> items = new ArrayList<>(grid.items(area));
			for (int i = 0; i < items.size(); i++) {
				ItemStack there = items.get(i).stack();
				if (there.is(MistinnItems.LATTICE_CHIP)) {
					if (there.getCount() <= 1) {
						items.remove(i);
					} else {
						items.set(i, items.get(i).withStack(there.copyWithCount(there.getCount() - 1)));
					}
					GridService.set(player, grid.with(area, items));
					return true;
				}
			}
		}
		return false;
	}

	/** Whether the hunter is finding the way just now, for tests. */
	public static boolean channeling(final ServerPlayer player) {
		return CHANNELS.containsKey(player.getUUID());
	}

	/** The map asks to go to a homestead: the highland it stands in. */
	public record TravelPayload(int domain) implements CustomPacketPayload {
		public static final Type<TravelPayload> TYPE = new Type<>(Mistinn.id("homestead_travel"));
		public static final StreamCodec<ByteBuf, TravelPayload> CODEC = ByteBufCodecs.VAR_INT.map(TravelPayload::new, TravelPayload::domain);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
