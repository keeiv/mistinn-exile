package life.shizune.mistinn.registry;

import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

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
				output.accept(MistinnItems.GOLDEN_BELL);
				output.accept(MistinnItems.ORE_BELL);
				output.accept(MistinnItems.SALT);
				output.accept(MistinnItems.RUST_GOD_SALT);
			})
			.build()
	);

	private MistinnTab() {
	}

	public static void init() {
		// Loading the class registers the tab.
	}
}
