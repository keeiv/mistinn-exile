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
				output.accept(MistinnItems.SALT);
				output.accept(MistinnItems.RUST_GOD_SALT);
				output.accept(MistinnItems.LAMP_OIL);
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
