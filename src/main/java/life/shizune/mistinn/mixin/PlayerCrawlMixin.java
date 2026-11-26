package life.shizune.mistinn.mixin;

import life.shizune.mistinn.body.LostLimbs;
import life.shizune.mistinn.team.DownService;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A hunter with both legs lost cannot stand: they crawl, on either side. A downed hunter lies as low
 * (their own pose is drawn over it), and the creatures stop seeing them as prey.
 */
@Mixin(Player.class)
public abstract class PlayerCrawlMixin {
	@Inject(method = "updatePlayerPose", at = @At("TAIL"))
	private void mistinn$crawl(final CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if ((LostLimbs.legs(self) == 2 || DownService.isDown(self)) && !self.isPassenger() && !self.isSleeping() && !self.isFallFlying()
			&& !self.getAbilities().flying && !self.isSpectator()) {
			self.setPose(Pose.SWIMMING);
		}
	}

	/** Monsters let a downed hunter be: they turn to whoever is still standing. */
	@Inject(method = "canBeSeenAsEnemy", at = @At("RETURN"), cancellable = true)
	private void mistinn$downedIsNoPrey(final CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && DownService.isDown((Player) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
