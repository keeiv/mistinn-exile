package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.medical.TreatmentClient;
import life.shizune.mistinn.client.tier.TierText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The name shown above the hotbar when switching items: "◆ name" in the tier's colour. While a
 * medical or repair use is on screen its own box sits there, so the name is dropped (and not
 * brought back once the box fades).
 */
@Mixin(Hud.class)
public abstract class HeldNameMixin {
	@Shadow
	private ItemStack lastToolHighlight;

	@Shadow
	private int toolHighlightTimer;

	@Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
	private void mistinn$quietDuringUse(final GuiGraphicsExtractor graphics, final CallbackInfo ci) {
		if (TreatmentClient.local() != null) {
			this.toolHighlightTimer = 0;
			ci.cancel();
		}
	}

	@ModifyVariable(method = "extractSelectedItemName", at = @At("STORE"), ordinal = 0)
	private MutableComponent mistinn$tierName(final MutableComponent original) {
		return this.lastToolHighlight.isEmpty() ? original : TierText.name(this.lastToolHighlight);
	}
}
