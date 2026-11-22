package life.shizune.mistinn.grid;

import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.tier.Tier;
import life.shizune.mistinn.tier.Tiers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 放進背包的聲音: what a thing is made of is heard as it is set down in the pack, and as it is lifted out. Blades and tools knock as metal, armour
 * plates clank, cloth gear and leather rustle, salt and sand settle in grains, bottles clink, books and papers lie flat,
 * wood taps; anything else is the plain sound of something set down.
 */
public final class GridSounds {
	private GridSounds() {
	}

	/** What a thing sounds like set down, by what it is made of. */
	public enum Material {
		METAL, PLATE, CLOTH, GRAIN, GLASS, PAPER, WOOD, PLAIN;

		public SoundEvent pickup() {
			return switch (this) {
				case METAL -> MistinnSounds.GRID_PICKUP_METAL;
				case PLATE -> MistinnSounds.GRID_PICKUP_PLATE;
				case GRAIN -> MistinnSounds.GRID_PICKUP_GRAIN;
				case GLASS -> MistinnSounds.GRID_PICKUP_GLASS;
				case PAPER -> MistinnSounds.GRID_PICKUP_PAPER;
				case WOOD -> MistinnSounds.GRID_PICKUP_WOOD;
				case CLOTH, PLAIN -> MistinnSounds.GRID_PICKUP;
			};
		}

		public SoundEvent place() {
			return switch (this) {
				case METAL -> MistinnSounds.GRID_PLACE_METAL;
				case PLATE -> MistinnSounds.GRID_PLACE_PLATE;
				case CLOTH -> MistinnSounds.GRID_PLACE_CLOTH;
				case GRAIN -> MistinnSounds.GRID_PLACE_GRAIN;
				case GLASS -> MistinnSounds.GRID_PLACE_GLASS;
				case PAPER -> MistinnSounds.GRID_PLACE_PAPER;
				case WOOD -> MistinnSounds.GRID_PLACE_WOOD;
				case PLAIN -> MistinnSounds.GRID_PLACE;
			};
		}
	}

	/** The sound of lifting this stack out of the pack. */
	public static SoundEvent pickup(final ItemStack stack) {
		return material(stack).pickup();
	}

	/** The sound of setting this stack down in the pack. */
	public static SoundEvent place(final ItemStack stack) {
		return material(stack).place();
	}

	public static Material material(final ItemStack stack) {
		if (stack.is(MistinnItems.SALT) || stack.is(MistinnItems.RUST_GOD_SALT) || stack.is(ItemTags.SAND) || stack.is(Items.GRAVEL)
			|| stack.is(Items.SUGAR) || stack.is(Items.GUNPOWDER) || stack.is(Items.REDSTONE) || stack.is(Items.GLOWSTONE_DUST)) {
			return Material.GRAIN;
		}
		if (stack.is(MistinnItems.HUNTER_GUIDE) || stack.is(ItemTags.BOOKSHELF_BOOKS) || stack.is(Items.PAPER) || stack.is(Items.MAP)
			|| stack.is(Items.FILLED_MAP)) {
			return Material.PAPER;
		}
		if (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION) || stack.is(Items.GLASS_BOTTLE)
			|| stack.is(Items.HONEY_BOTTLE) || stack.is(Items.EXPERIENCE_BOTTLE) || stack.is(Items.GLASS) || stack.is(Items.GLASS_PANE)) {
			return Material.GLASS;
		}
		if (GridInventory.backpackOf(stack) != null || GridInventory.rigOf(stack) != null || GridInventory.maskOf(stack) != null
			|| stack.is(ItemTags.WOOL) || stack.is(ItemTags.WOOL_CARPETS) || stack.is(Items.LEATHER) || stack.is(Items.STRING)) {
			return Material.CLOTH;
		}
		if (stack.has(DataComponents.EQUIPPABLE)) {
			// worn armour: padded cloth at the lowest tiers, plates from II up
			return Tiers.of(stack).ordinal() >= Tier.II.ordinal() ? Material.PLATE : Material.CLOTH;
		}
		if (stack.has(DataComponents.WEAPON) || stack.has(DataComponents.TOOL)) {
			String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			return id.startsWith("wooden_") ? Material.WOOD : Material.METAL;
		}
		if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || stack.is(Items.STICK) || stack.is(Items.BOW) || stack.is(Items.CROSSBOW)) {
			return Material.WOOD;
		}
		return Material.PLAIN;
	}
}
