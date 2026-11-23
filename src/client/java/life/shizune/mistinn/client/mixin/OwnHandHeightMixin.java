package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.view.OwnBody;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The game lets a held item sink out of view after a swing that hit nothing and rise again as the next blow comes
 * ready. A branch weapon has its own swing from first beat to last, and a hand that is drawn holding its item should
 * not drop out of sight in the middle of its work: for those the item stays up. Changing items still lowers and
 * raises the hand.
 */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class OwnHandHeightMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
	private float mistinn$stayUp(final LocalPlayer player, final float partialTicks) {
		if (OwnBody.active() || WeaponStyle.of(player.getMainHandItem()) != null) {
			return 1.0F;
		}
		return player.getItemSwapScale(partialTicks);
	}
}
