package life.shizune.mistinn.food;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Rust Anchor's food rule: a hunter eats only what the station feeds them. Ordinary food goes
 * damp the moment it is in a hunter's hands, and cake cannot be eaten. To make up for it, the
 * animals drop the station's scraps instead of their meat, and the crates hold its biscuits.
 */
public final class RustAnchorFood {
	/** What an animal, a catch or a crate gives in a Rust Anchor world instead of ordinary food. */
	private static final Map<Item, StationFood> SCRAPS = Map.ofEntries(
		Map.entry(Items.PORKCHOP, StationFood.PIG_FAT),
		Map.entry(Items.COOKED_PORKCHOP, StationFood.PORK_CRACKLINGS),
		Map.entry(Items.BEEF, StationFood.BEEF_TALLOW),
		Map.entry(Items.COOKED_BEEF, StationFood.TALLOW_CRACKLINGS),
		Map.entry(Items.MUTTON, StationFood.MUTTON_FAT),
		Map.entry(Items.COOKED_MUTTON, StationFood.ROASTED_MUTTON_FAT),
		Map.entry(Items.CHICKEN, StationFood.CHICKEN_SCRAPS),
		Map.entry(Items.COOKED_CHICKEN, StationFood.ROASTED_CHICKEN_SCRAPS),
		Map.entry(Items.COD, StationFood.FISH_OFFAL),
		Map.entry(Items.SALMON, StationFood.FISH_OFFAL),
		Map.entry(Items.TROPICAL_FISH, StationFood.FISH_OFFAL),
		Map.entry(Items.PUFFERFISH, StationFood.FISH_OFFAL),
		Map.entry(Items.COOKED_COD, StationFood.GRILLED_OFFAL),
		Map.entry(Items.COOKED_SALMON, StationFood.GRILLED_OFFAL));
	/** Crates only: bread and apples in a station's crates are its own biscuits. */
	private static final Map<Item, StationFood> RATIONS = Map.of(
		Items.BREAD, StationFood.HARDTACK,
		Items.APPLE, StationFood.RATION_BISCUIT);
	/** A cow gives sinew as well as tallow, this often. */
	private static final float SINEW_CHANCE = 0.5F;
	private static final ResourceKey<LootTable> COW = ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, Identifier.withDefaultNamespace("entities/cow"));

	private RustAnchorFood() {
	}

	/** Ordinary food: anything edible the base game makes. The station's own food is not. */
	public static boolean ordinary(final ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.FOOD) && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft");
	}

	/** The same amount, gone damp. */
	public static ItemStack damp(final ItemStack stack) {
		return new ItemStack(MistinnItems.DAMP_FOOD, stack.getCount());
	}

	/** Whether this hunter's food goes damp: Rust Anchor, and not a creative or watching player. */
	public static boolean applies(final Player player) {
		return Hardship.rustAnchor(player) && !player.isCreative() && !player.isSpectator();
	}

	/** Every tick: whatever ordinary food a hunter holds anywhere goes damp. */
	public static void tick(final MinecraftServer server) {
		if (Hardship.of(server) != Hardship.RUST_ANCHOR) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isAlive() && applies(player)) {
				spoilHeld(player);
			}
		}
	}

	/** Turns what this hunter holds damp; returns the first stack that turned, if any did. */
	public static Optional<ItemStack> spoilHeld(final ServerPlayer player) {
		ItemStack first = ItemStack.EMPTY;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (ordinary(stack)) {
				first = first.isEmpty() ? stack.copy() : first;
				inventory.setItem(slot, damp(stack));
			}
		}
		ItemStack carried = player.containerMenu.getCarried();
		if (ordinary(carried)) {
			first = first.isEmpty() ? carried.copy() : first;
			player.containerMenu.setCarried(damp(carried));
		}
		GridInventory grid = GridService.get(player);
		GridInventory spoiled = grid;
		for (Area area : Area.values()) {
			List<Placed> items = spoiled.items(area);
			List<Placed> changed = new ArrayList<>(items.size());
			boolean any = false;
			for (Placed placed : items) {
				if (ordinary(placed.stack())) {
					first = first.isEmpty() ? placed.stack().copy() : first;
					changed.add(placed.withStack(damp(placed.stack())));
					any = true;
				} else {
					changed.add(placed);
				}
			}
			if (any) {
				spoiled = spoiled.with(area, changed);
			}
		}
		if (spoiled != grid) {
			GridService.set(player, spoiled);
		}
		if (first.isEmpty()) {
			return Optional.empty();
		}
		player.containerMenu.broadcastChanges();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.6F, 0.6F);
		player.sendSystemMessage(Component.translatable("message.mistinn.food.damp", first.getHoverName()), true);
		return Optional.of(first);
	}

	/** In a Rust Anchor world animals, catches and crates give the station's food instead. */
	private static void swapDrops(final net.minecraft.core.Holder<LootTable> table, final net.minecraft.world.level.storage.loot.LootContext context, final List<ItemStack> drops) {
		ServerLevel level = context.getLevel();
		if (Hardship.of(level.getServer()) != Hardship.RUST_ANCHOR) {
			return;
		}
		Optional<ResourceKey<LootTable>> key = table.unwrapKey();
		boolean crate = key.isPresent() && key.get().identifier().getPath().startsWith("chests/");
		for (int i = 0; i < drops.size(); i++) {
			ItemStack stack = drops.get(i);
			StationFood food = SCRAPS.get(stack.getItem());
			if (food == null && crate) {
				food = RATIONS.get(stack.getItem());
			}
			if (food != null) {
				drops.set(i, new ItemStack(MistinnItems.FOODS.get(food), stack.getCount()));
			}
		}
		if (key.isPresent() && key.get().equals(COW) && context.getRandom().nextFloat() < SINEW_CHANCE) {
			drops.add(new ItemStack(MistinnItems.FOODS.get(StationFood.BEEF_SINEW)));
		}
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RustAnchorFood::tick);
		LootTableEvents.MODIFY_DROPS.register(RustAnchorFood::swapDrops);
		// a cake is ordinary food too: eaten from the block, it is not eaten at all
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			var state = level.getBlockState(hit.getBlockPos());
			if ((state.getBlock() instanceof CakeBlock || state.getBlock() instanceof CandleCakeBlock) && applies(player)) {
				if (!level.isClientSide()) {
					player.sendSystemMessage(Component.translatable("message.mistinn.food.cake"));
				}
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}
}
