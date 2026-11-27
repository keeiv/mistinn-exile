package life.shizune.mistinn.client.mixin;

import java.util.List;
import java.util.function.Function;
import life.shizune.mistinn.client.title.ContentWarning;
import life.shizune.mistinn.client.title.ContentWarningScreen;
import life.shizune.mistinn.client.title.IntroScreen;
import life.shizune.mistinn.client.title.KeeivIntroScreen;
import life.shizune.mistinn.client.title.TeahouseIntroScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * What the game shows once it has loaded, before the title screen or a launcher's straight join into a friend's room
 * (both of which come after these screens): the opening (MoriTeahouse, then keeiv), then the content warning the first
 * time a version runs. Each goes on to the next, and the last to whichever was to come.
 */
@Mixin(Gui.class)
public abstract class OpeningScreensMixin {
	@Inject(method = "addInitialScreens", at = @At("RETURN"))
	private void mistinn$opening(final List<Function<Runnable, Screen>> screens, final CallbackInfoReturnable<Boolean> cir) {
		if (IntroScreen.due()) {
			screens.add(TeahouseIntroScreen::new);
			screens.add(KeeivIntroScreen::new);
		}
		if (ContentWarning.due()) {
			screens.add(ContentWarningScreen::new);
		}
	}
}
