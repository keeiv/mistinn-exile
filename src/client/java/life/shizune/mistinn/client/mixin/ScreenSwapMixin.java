package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.death.MissionEndScreen;
import life.shizune.mistinn.client.grid.GridPanel;
import life.shizune.mistinn.client.grid.HunterScreen;
import life.shizune.mistinn.client.multiplayer.HostRoomScreen;
import life.shizune.mistinn.client.title.MistinnTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swaps vanilla screens for ours wherever the game opens them: the title screen always, and
 * for hunters (not creative) the inventory and the death screen, which plays the death scene.
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
		if (screen.getClass() == WorldOptionsScreen.class && mc.gui.screen() instanceof PauseScreen pause
			&& mc.getSingleplayerServer() != null) {
			return new HostRoomScreen(pause, mc.getSingleplayerServer());
		}
		// death plays its scene, then ends the mission
		if (screen.getClass() == DeathScreen.class && mc.player != null && GridPanel.active()) {
			DeathScreenAccessor vanilla = (DeathScreenAccessor) screen;
			return new MissionEndScreen(vanilla.mistinn$cause(), vanilla.mistinn$hardcore(), mc.player);
		}
		if (screen.getClass() == InventoryScreen.class && mc.player != null && GridPanel.active()) {
			return new HunterScreen(mc.player);
		}
		return screen;
	}
}
