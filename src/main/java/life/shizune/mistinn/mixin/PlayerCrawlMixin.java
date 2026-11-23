package life.shizune.mistinn.mixin;

import life.shizune.mistinn.body.LostLimbs;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A hunter with both legs lost cannot stand: they crawl, on either side. */
@Mixin(Player.class)
public abstract class PlayerCrawlMixin {
	@Inject(method = "updatePlayerPose", at = @At("TAIL"))
	private void mistinn$crawl(final CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (LostLimbs.legs(self) == 2 && !self.isPassenger() && !self.isSleeping() && !self.isFallFlying() && !self.getAbilities().flying && !self.isSpectator()) {
			self.setPose(Pose.SWIMMING);
		}
	}
}
