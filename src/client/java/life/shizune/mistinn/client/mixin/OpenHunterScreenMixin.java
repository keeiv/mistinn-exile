package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.grid.GridPanel;
import life.shizune.mistinn.client.grid.HunterScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Whenever the game would open the vanilla inventory for a hunter (the inventory key, the
 * tutorial, anything else), the hunter inventory opens instead. Creative keeps its own screen.
 */
@Mixin(Gui.class)
public abstract class OpenHunterScreenMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen mistinn$hunterInventory(final Screen screen) {
		Minecraft mc = Minecraft.getInstance();
		if (screen != null && screen.getClass() == InventoryScreen.class && mc.player != null && GridPanel.active()) {
			return new HunterScreen(mc.player);
		}
		return screen;
	}
}
