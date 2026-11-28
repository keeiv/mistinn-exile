package life.shizune.mistinn.mixin;

import life.shizune.mistinn.expedition.Grounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No explosion takes a block of an expedition's ground: it still hurts whoever stands near. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "interactsWithBlocks", at = @At("HEAD"), cancellable = true)
	private void mistinn$groundHolds(final CallbackInfoReturnable<Boolean> cir) {
		if (Grounds.is(this.level)) {
			cir.setReturnValue(false);
		}
	}
}
