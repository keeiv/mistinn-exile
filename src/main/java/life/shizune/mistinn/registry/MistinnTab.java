package life.shizune.mistinn.registry;

import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.world.item.ItemStack;

/** The creative tab that lists the mod's items. */
public final class MistinnTab {
	public static final CreativeModeTab TAB = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		Mistinn.id("main"),
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.mistinn"))
			.icon(() -> new ItemStack(MistinnItems.GOLDEN_BELL))
			.displayItems((parameters, output) -> {
				output.accept(MistinnItems.ANCHOR_BLADE);
				output.accept(MistinnItems.BELL_GREATSWORD);
				output.accept(MistinnItems.MIST_SPEAR);
				output.accept(MistinnItems.SALT_FANG);
				output.accept(MistinnItems.SNUFFER_HOOK);
				output.accept(MistinnItems.LANTERN_STAFF);
				output.accept(MistinnItems.TIDE_MAUL);
				output.accept(MistinnItems.BELL_CLAPPER);
				output.accept(MistinnItems.RUST_RIM);
				for (WeaponMaterial material : WeaponMaterial.values()) {
					for (WeaponStyle style : WeaponStyle.values()) {
						output.accept(MistinnItems.WEAPONS.get(style).get(material));
					}
				}
				output.accept(MistinnItems.HUNTER_GUIDE);
				output.accept(MistinnItems.GOLDEN_BELL);
				output.accept(MistinnItems.ORE_BELL);
				output.accept(MistinnItems.POTENT_INJECTOR);
				output.accept(MistinnItems.SURGERY_KIT);
				output.accept(MistinnItems.PAINKILLERS);
				output.accept(MistinnItems.TOURNIQUET);
				output.accept(MistinnItems.SPLINT);
				MistinnItems.ARMOR_REPAIR_KITS.forEach(output::accept);
				MistinnItems.HELMET_REPAIR_KITS.forEach(output::accept);
				output.accept(MistinnItems.LAMP_STAND);
				output.accept(MistinnItems.MARKER_BEACON);
				output.accept(MistinnItems.REDSTONE_SOIL);
				output.accept(MistinnItems.GRASS_REDSTONE_SOIL);
				output.accept(MistinnItems.RIFT_TURF);
				output.accept(MistinnItems.RIFT_SOIL);
				output.accept(MistinnItems.SCORCHED_STONE);
				output.accept(MistinnItems.CRIMSON_MOSS);
				output.accept(MistinnItems.CRIMSON_MOSS_CARPET);
				output.accept(MistinnItems.BLOOD_THORN);
				output.accept(MistinnItems.RIFT_CRYSTAL_BUD);
				output.accept(MistinnItems.RIFT_CRYSTAL_CLUSTER);
				output.accept(MistinnItems.RIFT_CRYSTAL_BLOCK);
				output.accept(MistinnItems.CHARRED_LOG);
				output.accept(MistinnItems.RIFT_SHARD);
				output.accept(MistinnItems.JADE_TURF);
				output.accept(MistinnItems.VEIN_SOIL);
				output.accept(MistinnItems.VEIN_STONE);
				output.accept(MistinnItems.VEIN_STONE_POLISHED);
				output.accept(MistinnItems.VEIN_STONE_BRICKS);
				output.accept(MistinnItems.VEIN_CRYSTAL_ORE);
				output.accept(MistinnItems.MIST_PINE_LOG);
				output.accept(MistinnItems.MIST_PINE_PLANKS);
				output.accept(MistinnItems.MIST_PINE_LEAVES);
				output.accept(MistinnItems.JADE_GRASS);
				output.accept(MistinnItems.JADE_FERN);
				output.accept(MistinnItems.MIST_REED);
				output.accept(MistinnItems.CRYSTAL_SPROUT);
				output.accept(MistinnItems.TERRA_CRYSTAL_SMALL_BUD);
				output.accept(MistinnItems.TERRA_CRYSTAL_MEDIUM_BUD);
				output.accept(MistinnItems.TERRA_CRYSTAL_LARGE_BUD);
				output.accept(MistinnItems.TERRA_CRYSTAL_CLUSTER);
				output.accept(MistinnItems.TERRA_CRYSTAL_BLOCK);
				output.accept(MistinnItems.LATTICE_CRYSTAL);
				output.accept(MistinnItems.LATTICE_CRYSTAL_DIM);
				output.accept(MistinnItems.CRYSTAL_DUST);
				output.accept(MistinnItems.CRYSTAL_CORE);
				output.accept(MistinnItems.LATTICE_CHIP);
				output.accept(MistinnItems.SALT);
				output.accept(MistinnItems.RUST_GOD_SALT);
				output.accept(MistinnItems.LAMP_OIL);
				output.accept(MistinnItems.FLINTLOCK);
				output.accept(MistinnItems.FLINTLOCK_PISTOL);
				output.accept(MistinnItems.PAPER_CARTRIDGE);
				output.accept(MistinnItems.CANNONBALL);
				for (life.shizune.mistinn.food.StationFood food : life.shizune.mistinn.food.StationFood.values()) {
					output.accept(MistinnItems.FOODS.get(food));
				}
				output.accept(MistinnItems.DAMP_FOOD);
				MistinnItems.HELMETS.forEach(output::accept);
				MistinnItems.MASKS.forEach(output::accept);
				MistinnItems.ARMORS.forEach(output::accept);
				MistinnItems.LEGGINGS.forEach(output::accept);
				MistinnItems.BOOTS.forEach(output::accept);
				MistinnItems.RIGS.forEach(output::accept);
				MistinnItems.BACKPACKS.forEach(output::accept);
			})
			.build()
	);

	private MistinnTab() {
	}

	public static void init() {
		// Loading the class registers the tab.
	}
}
