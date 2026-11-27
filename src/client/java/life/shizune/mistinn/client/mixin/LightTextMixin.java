package life.shizune.mistinn.client.mixin;

import java.util.Map;
import life.shizune.mistinn.client.ContentLevel;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * At the light content level, a line with a gentle version ({@code key.light}) is told that way, wherever it is shown:
 * story, staff, death screen, items, bestiary, guide, achievements, and words the server sends (each client reads its own).
 */
@Mixin(ClientLanguage.class)
public abstract class LightTextMixin {
	@Shadow
	@Final
	private Map<String, String> storage;

	@Inject(method = "getOrDefault", at = @At("HEAD"), cancellable = true)
	private void mistinn$gently(final String key, final String defaultValue, final CallbackInfoReturnable<String> cir) {
		if (ContentLevel.light() && !key.endsWith(".light")) {
			String gentle = this.storage.get(key + ".light");
			if (gentle != null) {
				cir.setReturnValue(gentle);
			}
		}
	}
}
