package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.grid.GridPanel;
import life.shizune.mistinn.client.grid.HunterScreen;
import life.shizune.mistinn.client.title.MistinnTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swaps two vanilla screens for ours wherever the game opens them: the title screen always,
 * and the inventory for hunters (creative keeps its own screen).
 */
@Mixin(Gui.class)
public abstract class ScreenSwapMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen mistinn$ourScreens(final Screen screen) {
		if (screen == null) {
			return null;
		}
		if (screen.getClass() == TitleScreen.class) {
			return new MistinnTitleScreen();
		}
		Minecraft mc = Minecraft.getInstance();
		if (screen.getClass() == InventoryScreen.class && mc.player != null && GridPanel.active()) {
			return new HunterScreen(mc.player);
		}
		return screen;
	}
}
