package life.shizune.mistinn.mixin;

import life.shizune.mistinn.stamina.RollService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Blows pass through a hunter in the middle of a roll, and meet a raised guard; the void and /kill do not. */
@Mixin(Player.class)
public abstract class PlayerDodgeMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void mistinn$dodge(final ServerLevel level, final DamageSource source, final float damage, final CallbackInfoReturnable<Boolean> cir) {
		if (RollService.dodging((Player) (Object) this) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (source.getEntity() != null && (Object) this instanceof net.minecraft.server.level.ServerPlayer hunter) {
				life.shizune.mistinn.achievement.AchievementService.grant(hunter, life.shizune.mistinn.achievement.Achievement.PERFECT_ROLL);
				life.shizune.mistinn.training.TrainingService.dodged(hunter);
			}
			cir.setReturnValue(false);
			return;
		}
		// a guard raised against the blow: parried whole, or blocked in part
		if ((Object) this instanceof net.minecraft.server.level.ServerPlayer hunter) {
			Boolean met = life.shizune.mistinn.weapon.ParryService.meet(hunter, level, source, damage);
			if (met != null) {
				cir.setReturnValue(met);
			}
		}
	}
}
