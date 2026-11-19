package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.title.ModeSelectScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "Create New World" (from the world list, or when there is no world yet) first asks whether
 * the world is for the story or for free exploration, then opens the vanilla settings.
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldMixin {
	/** Set while the settings open after the choice, so the call is not stopped twice. */
	@Unique
	private static boolean mistinn$chosen;

	@Inject(method = "openFresh(Lnet/minecraft/client/Minecraft;Ljava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true)
	private static void mistinn$askMode(final Minecraft minecraft, final Runnable onClose, final CallbackInfo ci) {
		// only from the world list, where a player presses "Create New World"; worlds created by
		// code (the game test, other mods) open the settings directly
		if (mistinn$chosen || !(minecraft.gui.screen() instanceof SelectWorldScreen)) {
			return;
		}
		ci.cancel();
		minecraft.gui.setScreen(new ModeSelectScreen(() -> {
			mistinn$chosen = true;
			try {
				CreateWorldScreen.openFresh(minecraft, onClose);
			} finally {
				mistinn$chosen = false;
			}
		}, onClose));
	}
}
