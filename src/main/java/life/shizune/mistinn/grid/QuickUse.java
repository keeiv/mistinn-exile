package life.shizune.mistinn.grid;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.medical.TreatmentItem;
import life.shizune.mistinn.medical.TreatmentService;
import life.shizune.mistinn.registry.MistinnAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.UseRemainder;
import org.jspecify.annotations.Nullable;

/**
 * 快捷使用: food, medicine and repair kits used straight from the pockets and the rig's pouches,
 * picked on a wheel, without taking them in hand first. The item stays where it lies until the use is
 * done, and only then is it taken (or worn down) there: broken off, or the hunter gone, nothing is
 * spent and nothing is doubled.
 */
public final class QuickUse {
	/** Where an item lies in the grid: the area and its top left cell. */
	public record Spot(Area area, int section, int x, int y) {
		public static final StreamCodec<ByteBuf, Spot> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, spot -> spot.area().ordinal(),
			ByteBufCodecs.VAR_INT, Spot::section,
			ByteBufCodecs.VAR_INT, Spot::x,
			ByteBufCodecs.VAR_INT, Spot::y,
			(area, section, x, y) -> new Spot(Area.byId(area), section, x, y));

		public static Spot of(final Area area, final Placed placed) {
			return new Spot(area, placed.section(), placed.x(), placed.y());
		}
	}

	/** The areas the wheel reaches: what a hunter can get at without opening the pack. */
	public static final List<Area> REACH = List.of(Area.POCKETS, Area.RIG);
	/** How much slower a hunter walks while eating from the pouch. */
	public static final double EATING_SLOW = 0.5;
	private static final Identifier SLOW_ID = Mistinn.id("quick_eat");

	private record Eating(Spot spot, Item item, int ticks, int[] elapsed) {
	}

	private static final Map<UUID, Eating> EATING = new HashMap<>();

	private QuickUse() {
	}

	// ── What can be used ──────────────────────────────────────────

	/** Medicine and repair kits, and anything eaten. */
	public static boolean usable(final ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof TreatmentItem || stack.has(DataComponents.FOOD) && stack.has(DataComponents.CONSUMABLE));
	}

	/** Every usable item the hunter can reach, in the order they lie. */
	public static List<Spot> spots(final Player player) {
		GridInventory grid = player.getAttachedOrElse(MistinnAttachments.GRID, GridInventory.EMPTY);
		List<Spot> spots = new ArrayList<>();
		for (Area area : REACH) {
			for (Placed placed : grid.items(area)) {
				if (usable(placed.stack())) {
					spots.add(Spot.of(area, placed));
				}
			}
		}
		return spots;
	}

	/** The item at the spot, or empty. */
	public static ItemStack at(final Player player, final Spot spot) {
		GridInventory grid = player.getAttachedOrElse(MistinnAttachments.GRID, GridInventory.EMPTY);
		for (Placed placed : grid.items(spot.area())) {
			if (placed.section() == spot.section() && placed.x() == spot.x() && placed.y() == spot.y()) {
				return placed.stack();
			}
		}
		return ItemStack.EMPTY;
	}

	/** Puts what is left of a used item back where it lay; nothing left, the spot is cleared. */
	public static void put(final ServerPlayer player, final Spot spot, final ItemStack left) {
		GridInventory grid = GridService.get(player);
		List<Placed> items = new ArrayList<>(grid.items(spot.area()));
		for (int i = 0; i < items.size(); i++) {
			Placed placed = items.get(i);
			if (placed.section() == spot.section() && placed.x() == spot.x() && placed.y() == spot.y()) {
				if (left.isEmpty()) {
					items.remove(i);
				} else {
					items.set(i, placed.withStack(left));
				}
				GridService.set(player, grid.with(spot.area(), items));
				return;
			}
		}
	}

	// ── Using ─────────────────────────────────────────────────────

	/** The hunter picked {@code spot} on the wheel. */
	public static void request(final ServerPlayer player, final Spot spot) {
		if (!REACH.contains(spot.area()) || !player.isAlive() || life.shizune.mistinn.team.DownService.isDown(player)
			|| TreatmentService.active(player) || EATING.containsKey(player.getUUID()) || player.isUsingItem()) {
			return;
		}
		ItemStack stack = at(player, spot);
		if (stack.getItem() instanceof TreatmentItem treatment) {
			String refusal = treatment.refusal(player, stack);
			if (refusal != null) {
				player.sendSystemMessage(Component.translatable(refusal), true);
				return;
			}
			TreatmentService.startFrom(player, spot, stack, treatment);
		} else if (usable(stack)) {
			eat(player, spot, stack);
		}
	}

	private static void eat(final ServerPlayer player, final Spot spot, final ItemStack stack) {
		if (life.shizune.mistinn.hardship.Hardship.rustAnchor(player) && life.shizune.mistinn.food.RustAnchorFood.ordinary(stack)) {
			player.sendSystemMessage(Component.translatable("message.mistinn.quick.damp"), true);
			return;
		}
		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food != null && !player.canEat(food.canAlwaysEat())) {
			player.sendSystemMessage(Component.translatable("message.mistinn.quick.full"), true);
			return;
		}
		Consumable consumable = stack.get(DataComponents.CONSUMABLE);
		EATING.put(player.getUUID(), new Eating(spot, stack.getItem(), consumable == null ? 32 : consumable.consumeTicks(), new int[] {0}));
		slow(player, EATING_SLOW);
		player.setSprinting(false);
	}

	/** Whether the hunter is eating from the pouch now. */
	public static boolean eating(final Player player) {
		return EATING.containsKey(player.getUUID());
	}

	/** Stops eating from the pouch; nothing is spent. */
	public static void stopEating(final ServerPlayer player) {
		if (EATING.remove(player.getUUID()) != null) {
			slow(player, 0.0);
		}
	}

	private static void tick(final MinecraftServer server) {
		EATING.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			Eating eating = EATING.get(player.getUUID());
			if (eating == null) {
				continue;
			}
			ItemStack stack = at(player, eating.spot());
			if (!player.isAlive() || life.shizune.mistinn.team.DownService.isDown(player) || !stack.is(eating.item())) {
				stopEating(player);
				continue;
			}
			int elapsed = ++eating.elapsed()[0];
			if (elapsed % 4 == 0) {
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS,
					0.5F, 0.9F + player.getRandom().nextFloat() * 0.2F);
			}
			if (elapsed < eating.ticks()) {
				continue;
			}
			stopEating(player);
			ItemStack used = stack.copy();
			UseRemainder remainder = used.get(DataComponents.USE_REMAINDER);
			Consumable consumable = used.get(DataComponents.CONSUMABLE);
			ItemStack left = consumable == null ? used : consumable.onConsume(player.level(), player, used);
			put(player, eating.spot(), left);
			if (remainder != null) {
				// the bowl of a soup comes back to the hunter
				ItemStack back = remainder.convertInto().create();
				GridService.insert(player, back);
				if (!back.isEmpty() && !player.getInventory().add(back)) {
					player.drop(back, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
		}
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

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(QuickUsePayload.TYPE, QuickUsePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(QuickUsePayload.TYPE, (payload, context) -> request(context.player(), payload.spot()));
		ServerTickEvents.END_SERVER_TICK.register(QuickUse::tick);
	}

	/** The wheel's pick. */
	public record QuickUsePayload(Spot spot) implements CustomPacketPayload {
		public static final Type<QuickUsePayload> TYPE = new Type<>(Mistinn.id("quick_use"));
		public static final StreamCodec<ByteBuf, QuickUsePayload> CODEC = Spot.CODEC.map(QuickUsePayload::new, QuickUsePayload::spot);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Test hook: how far along the pouch meal is, or -1. */
	public static int eatingElapsed(final Player player) {
		Eating eating = EATING.get(player.getUUID());
		return eating == null ? -1 : eating.elapsed()[0];
	}

	static @Nullable Spot eatingSpot(final Player player) {
		Eating eating = EATING.get(player.getUUID());
		return eating == null ? null : eating.spot();
	}
}
