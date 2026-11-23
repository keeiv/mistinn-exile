package life.shizune.mistinn.mixin;

import life.shizune.mistinn.hardship.Hardship;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The world's hardship sets how hungry work and wounds make a hunter. */
@Mixin(Player.class)
public abstract class PlayerHungerMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
	private float mistinn$hardshipHunger(final float exhaustion) {
		Player player = (Player) (Object) this;
		return player.level().isClientSide() ? exhaustion : exhaustion * Hardship.of(player).hunger;
	}
}
