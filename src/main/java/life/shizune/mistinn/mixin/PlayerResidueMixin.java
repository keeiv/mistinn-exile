package life.shizune.mistinn.mixin;

import life.shizune.mistinn.residue.ResidueService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A fallen hunter's things go into their gravestone, not onto the ground: taken before vanilla drops them. */
@Mixin(Player.class)
public abstract class PlayerResidueMixin {
	@Inject(method = "dropEquipment", at = @At("HEAD"))
	private void mistinn$keepInStone(final ServerLevel level, final CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			ResidueService.collect(player);
		}
	}
}
