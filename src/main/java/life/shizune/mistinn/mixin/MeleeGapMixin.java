package life.shizune.mistinn.mixin;

import life.shizune.mistinn.hardship.Ferocity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Rust Anchor creature comes at the hunter again sooner: the wait between melee blows is cut ({@link Ferocity#attackGap}).
 * The goal sets its wait from a fixed 20 ticks, not from {@code getAttackInterval}, so the wait itself is cut after it is set.
 * A windup plants the creature, which ends the goal; the goal looks again whether it may start only every 20 ticks, and that
 * look is cut the same way, or the creature would still strike once a second.
 */
@Mixin(MeleeAttackGoal.class)
public abstract class MeleeGapMixin {
	@Shadow
	@Final
	protected PathfinderMob mob;

	@Shadow
	private int ticksUntilNextAttack;

	@Shadow
	private long lastCanUseCheck;

	/** Only the hostile: a tamed wolf or an iron golem fights as it always did. */
	private float mistinn$scale() {
		return this.mob instanceof Enemy ? Ferocity.of(this.mob.level()).attackGap() : 1.0F;
	}

	@Inject(method = "canUse", at = @At("HEAD"))
	private void mistinn$lookAgainSooner(final CallbackInfoReturnable<Boolean> cir) {
		float scale = this.mistinn$scale();
		if (scale < 1.0F) {
			long now = this.mob.level().getGameTime();
			if (now - this.lastCanUseCheck >= Ferocity.gap(20, scale)) {
				// as if the full 20 ticks had passed
				this.lastCanUseCheck = Math.min(this.lastCanUseCheck, now - 20L);
			}
		}
	}

	@Inject(method = "resetAttackCooldown", at = @At("RETURN"))
	private void mistinn$soonerAgain(final CallbackInfo ci) {
		this.ticksUntilNextAttack = Ferocity.gap(this.ticksUntilNextAttack, this.mistinn$scale());
	}

	@Inject(method = "getAttackInterval", at = @At("RETURN"), cancellable = true)
	private void mistinn$sooner(final CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(Ferocity.gap(cir.getReturnValueI(), this.mistinn$scale()));
	}
}
