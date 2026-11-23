package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.map.MinimapHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game's toasts (an achievement earned, a recipe unlocked) slide in at the top right, where the minimap is: while
 * the minimap is up they are drawn to its left instead, so neither covers the other.
 */
@Mixin(ToastManager.class)
public abstract class ToastBesideMinimapMixin {
	@Unique
	private boolean mistinn$shifted;

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void mistinn$besideMinimap(final GuiGraphicsExtractor g, final CallbackInfo ci) {
		int reserved = MinimapHud.reserved();
		this.mistinn$shifted = reserved > 0;
		if (this.mistinn$shifted) {
			g.pose().pushMatrix();
			g.pose().translate(-reserved, 0.0F);
		}
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void mistinn$restore(final GuiGraphicsExtractor g, final CallbackInfo ci) {
		if (this.mistinn$shifted) {
			g.pose().popMatrix();
			this.mistinn$shifted = false;
		}
	}
}
