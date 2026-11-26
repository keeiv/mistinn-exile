package life.shizune.mistinn.grid;

import java.util.IdentityHashMap;
import java.util.Map;
import life.shizune.mistinn.gear.Gear;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;

/**
 * How many cells an item takes in a grid, as {width, height} lying upright. Small things (gems,
 * ingots, food) take one cell, long things (sticks, tools, weapons) a column, and blocks such as
 * ores a 2x2 square. Specific items are listed; everything else follows the rules in {@link #of}.
 */
public final class ItemSizes {
	private static final Map<Item, int[]> OVERRIDES = new IdentityHashMap<>();

	static {
		put(1, 1, Items.DIAMOND, Items.EMERALD, Items.LAPIS_LAZULI, Items.AMETHYST_SHARD, Items.QUARTZ,
			Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP,
			Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER, Items.COAL, Items.CHARCOAL, Items.REDSTONE, Items.ARROW);
		put(1, 2, Items.STICK, Items.BLAZE_ROD, Items.BREEZE_ROD, Items.BONE, Items.TOTEM_OF_UNDYING, Items.END_ROD);
		put(1, 3, Items.BOW, Items.FISHING_ROD, Items.CARROT_ON_A_STICK, Items.WARPED_FUNGUS_ON_A_STICK);
		put(1, 4, Items.TRIDENT);
		put(2, 2, Items.CROSSBOW, Items.SHIELD, Items.CAKE, Items.MACE);
		put(2, 3, Items.ELYTRA);

		put(1, 1, MistinnItems.SALT);
		put(1, 2, MistinnItems.LAMP_OIL);
		put(1, 4, MistinnItems.FLINTLOCK);
		put(1, 2, MistinnItems.FLINTLOCK_PISTOL);
		put(1, 1, MistinnItems.PAPER_CARTRIDGE, MistinnItems.CANNONBALL);
		put(1, 1, MistinnItems.SALT_BOMB, MistinnItems.FLARE, MistinnItems.BELL_LURE);
		put(2, 2, MistinnItems.RUST_GOD_SALT, MistinnItems.GOLDEN_BELL, MistinnItems.ORE_BELL);
		put(1, 3, MistinnItems.ANCHOR_BLADE);
		put(1, 4, MistinnItems.BELL_GREATSWORD);
		put(1, 5, MistinnItems.MIST_SPEAR);
		put(1, 2, MistinnItems.SALT_FANG);
		put(2, 4, MistinnItems.SNUFFER_HOOK);
		put(1, 5, MistinnItems.LANTERN_STAFF);
		put(2, 4, MistinnItems.TIDE_MAUL);
		put(1, 3, MistinnItems.BELL_CLAPPER);
		put(1, 4, MistinnItems.RUST_RIM);
		// repair kits grow with their tier (III, IV, V, VI); the surgery roll is long, the injector
		// and the bottle fit a cell
		int[][] armorKits = {{1, 2}, {1, 3}, {2, 2}, {2, 2}};
		int[][] helmetKits = {{1, 2}, {1, 2}, {1, 3}, {2, 2}};
		for (int i = 0; i < MistinnItems.ARMOR_REPAIR_KITS.size(); i++) {
			put(armorKits[i][0], armorKits[i][1], MistinnItems.ARMOR_REPAIR_KITS.get(i));
			put(helmetKits[i][0], helmetKits[i][1], MistinnItems.HELMET_REPAIR_KITS.get(i));
		}
		put(1, 2, MistinnItems.SURGERY_KIT, MistinnItems.SPLINT);
		put(1, 1, MistinnItems.TOURNIQUET);
		put(1, 1, MistinnItems.POTENT_INJECTOR, MistinnItems.PAINKILLERS);
		MistinnItems.WEAPONS.forEach((style, byMaterial) -> {
			int[] size = switch (style) {
				case SLASH -> new int[] {1, 3};
				case CLEAVE, THRUST -> new int[] {1, 4};
				case FLURRY -> new int[] {1, 2};
				case SMASH -> new int[] {2, 3};
				case REAP -> new int[] {2, 4};
			};
			byMaterial.values().forEach(item -> put(size[0], size[1], item));
		});
		for (int i = 0; i < Gear.BACKPACKS.size(); i++) {
			Gear.Backpack pack = Gear.BACKPACKS.get(i);
			put(pack.itemWidth(), pack.itemHeight(), MistinnItems.BACKPACKS.get(i));
		}
		for (int i = 0; i < Gear.RIGS.size(); i++) {
			Gear.ChestRig rig = Gear.RIGS.get(i);
			put(rig.itemWidth(), rig.itemHeight(), MistinnItems.RIGS.get(i));
		}
		MistinnItems.MASKS.forEach(item -> put(2, 2, item));
		MistinnItems.LEGGINGS.forEach(item -> put(2, 3, item));
		MistinnItems.BOOTS.forEach(item -> put(2, 2, item));
	}

	private ItemSizes() {
	}

	private static void put(final int width, final int height, final Item... items) {
		for (Item item : items) {
			OVERRIDES.put(item, new int[] {width, height});
		}
	}

	/** {width, height} of the stack when it is not rotated. */
	public static int[] of(final ItemStack stack) {
		if (stack.isEmpty()) {
			return new int[] {1, 1};
		}
		int[] fixed = OVERRIDES.get(stack.getItem());
		if (fixed != null) {
			return fixed;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable != null) {
			EquipmentSlot slot = equippable.slot();
			if (slot == EquipmentSlot.CHEST) {
				return new int[] {2, 3};
			}
			if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
				return new int[] {2, 2};
			}
		}
		if (stack.has(DataComponents.KINETIC_WEAPON)) {
			return new int[] {1, 4};
		}
		if (stack.has(DataComponents.WEAPON) || stack.has(DataComponents.TOOL)) {
			return new int[] {1, 3};
		}
		if (stack.getItem() instanceof BlockItem blockItem) {
			Block block = blockItem.getBlock();
			if (block instanceof DoorBlock) {
				return new int[] {1, 2};
			}
			// things you can walk through (torches, flowers, rails) are small; solid blocks are not
			if (block.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
				return new int[] {1, 1};
			}
			return new int[] {2, 2};
		}
		return new int[] {1, 1};
	}
}
