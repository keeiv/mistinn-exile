package life.shizune.mistinn.mixin;

import life.shizune.mistinn.registry.VanillaCreatureSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Cave spiders have their own voice and step, not the common spider's. */
@Mixin(CaveSpider.class)
public abstract class CaveSpiderSoundMixin extends Spider {
	protected CaveSpiderSoundMixin(final EntityType<? extends Spider> type, final Level level) {
		super(type, level);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return VanillaCreatureSounds.CAVE_SPIDER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return VanillaCreatureSounds.CAVE_SPIDER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return VanillaCreatureSounds.CAVE_SPIDER_DEATH;
	}

	@Override
	protected void playStepSound(final BlockPos pos, final BlockState state) {
		this.playSound(VanillaCreatureSounds.CAVE_SPIDER_STEP, 0.15F, 1.0F);
	}
}
