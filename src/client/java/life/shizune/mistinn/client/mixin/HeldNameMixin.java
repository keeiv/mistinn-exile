package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.tier.TierText;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The name shown above the hotbar when switching items: "◆ name" in the tier's colour. */
@Mixin(Hud.class)
public abstract class HeldNameMixin {
	@Shadow
	private ItemStack lastToolHighlight;

	@ModifyVariable(method = "extractSelectedItemName", at = @At("STORE"), ordinal = 0)
	private MutableComponent mistinn$tierName(final MutableComponent original) {
		return this.lastToolHighlight.isEmpty() ? original : TierText.name(this.lastToolHighlight);
	}
}
