package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.epic.EpicClient;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In a world of the epic, a screen's buttons answer with the epic's own click: a small bronze bell. */
@Mixin(AbstractWidget.class)
public abstract class EpicClickMixin {
	@Inject(method = "playButtonClickSound", at = @At("HEAD"), cancellable = true)
	private static void mistinn$epicClick(final SoundManager sounds, final CallbackInfo ci) {
		if (EpicClient.inEpic()) {
			sounds.play(SimpleSoundInstance.forUI(MistinnSounds.EPIC_CLICK, 1.0F));
			ci.cancel();
		}
	}
}
