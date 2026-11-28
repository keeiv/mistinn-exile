package life.shizune.mistinn.mixin;

import life.shizune.mistinn.expedition.Grounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The forebears' work does not break: in an expedition's ground a hunter breaks no block, as in adventure mode (placing
 * and pouring are held back with the ability to build, see the expedition). Creative still may.
 */
@Mixin(Player.class)
public abstract class PlayerGroundMixin {
	@Inject(method = "blockActionRestricted", at = @At("HEAD"), cancellable = true)
	private void mistinn$groundHolds(final Level level, final BlockPos pos, final GameType mode, final CallbackInfoReturnable<Boolean> cir) {
		if (mode == GameType.SURVIVAL && Grounds.is(level)) {
			cir.setReturnValue(true);
		}
	}
}
