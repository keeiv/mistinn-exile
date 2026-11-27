package life.shizune.mistinn.client.mixin;

import java.util.List;
import java.util.function.Function;
import life.shizune.mistinn.client.title.ContentWarning;
import life.shizune.mistinn.client.title.ContentWarningScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The content warning joins the screens the game shows once it has loaded, before the title screen or a launcher's
 * straight join into a friend's room (both of which come after these screens): answered, it goes on to whichever it was.
 */
@Mixin(Gui.class)
public abstract class ContentWarningMixin {
	@Inject(method = "addInitialScreens", at = @At("RETURN"))
	private void mistinn$contentWarning(final List<Function<Runnable, Screen>> screens, final CallbackInfoReturnable<Boolean> cir) {
		if (ContentWarning.due()) {
			screens.add(ContentWarningScreen::new);
		}
	}
}
