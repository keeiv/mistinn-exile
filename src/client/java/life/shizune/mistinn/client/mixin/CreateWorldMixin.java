package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.title.ModeSelectScreen;
import life.shizune.mistinn.client.training.TrainingClient;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.story.WorldMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The world list asks which Mistinn experience to create, and for story and training how hard,
 * before opening the settings or course.
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
		minecraft.gui.setScreen(new ModeSelectScreen((mode, hardship) -> {
			if (mode == WorldMode.TRAINING) {
				TrainingClient.createWorld(minecraft, hardship);
				return;
			}
			if (mode == WorldMode.EPIC) {
				// the heart in the dark, then Udasru
				minecraft.gui.setScreen(new life.shizune.mistinn.client.epic.EpicOpeningScreen(
					() -> life.shizune.mistinn.client.epic.EpicClient.createWorld(minecraft)));
				return;
			}
			WorldMode.choose(mode);
			Hardship.choose(hardship);
			mistinn$chosen = true;
			try {
				CreateWorldScreen.openFresh(minecraft, () -> {
					WorldMode.choose(null);
					Hardship.choose(null);
					onClose.run();
				});
			} finally {
				mistinn$chosen = false;
			}
		}, onClose));
	}
}
