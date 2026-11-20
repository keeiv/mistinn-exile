package life.shizune.mistinn.mixin;

import life.shizune.mistinn.registry.VanillaCreatureSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Giant.class)
public abstract class GiantSoundMixin extends Monster {
	protected GiantSoundMixin(final EntityType<? extends Monster> type, final Level level) {
		super(type, level);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return VanillaCreatureSounds.GIANT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return VanillaCreatureSounds.GIANT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return VanillaCreatureSounds.GIANT_DEATH;
	}

	@Override
	protected void playStepSound(final BlockPos pos, final BlockState state) {
		this.playSound(VanillaCreatureSounds.GIANT_STEP, 0.55F, 1.0F);
	}
}
