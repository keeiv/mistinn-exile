package life.shizune.mistinn.test.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The game tests' window is made hidden, so a run never comes up over what the person at the computer is doing (and
 * never shows them a frightening picture). The game draws into its own render target and the tests' screenshots read
 * that, so nothing is lost. {@code -Pvisible} shows it as before.
 */
@Mixin(Window.class)
public abstract class QuietWindowMixin {
	/** SDL's flag for a window made without being shown. */
	private static final long SDL_WINDOW_HIDDEN = 0x8L;

	@ModifyArg(method = "createWindow", at = @At(value = "INVOKE",
		target = "Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"), index = 3)
	private long mistinn$hidden(final long flags) {
		return Boolean.getBoolean("mistinn.visible") ? flags : flags | SDL_WINDOW_HIDDEN;
	}
}
