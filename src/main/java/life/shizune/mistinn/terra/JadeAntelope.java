package life.shizune.mistinn.terra;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * 玉角羚: the gentle grazer of Terra's valleys, a light antelope with two horns of jade crystal, kept in small herds on
 * the jade turf. Struck, it bolts, and the herd near it with it. It follows a hunter holding jade grass, and two fed jade
 * grass have a fawn. Brought down it leaves its hide (leather) and meat: what the highland gives those who live there.
 */
public class JadeAntelope extends Animal {
	/** How far the herd hears one of its own struck, and bolts with it. */
	public static final double HERD = 12.0;

	public JadeAntelope(final EntityType<? extends JadeAntelope> type, final Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes()
			.add(Attributes.MAX_HEALTH, 12.0)
			.add(Attributes.MOVEMENT_SPEED, 0.27);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new PanicGoal(this, 2.1));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.15, this::isFood, false));
		this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	/** Jade grass: it follows a hunter holding it, and two fed it have a fawn. */
	@Override
	public boolean isFood(final ItemStack stack) {
		return stack.is(MistinnItems.JADE_GRASS);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(final ServerLevel level, final AgeableMob other) {
		return MistinnEntities.JADE_ANTELOPE.create(level, EntitySpawnReason.BREEDING);
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof LivingEntity attacker) {
			// the herd bolts with it
			for (JadeAntelope near : level.getEntitiesOfClass(JadeAntelope.class, this.getBoundingBox().inflate(HERD), a -> a != this && a.isAlive())) {
				near.setLastHurtByMob(attacker);
			}
		}
		return hurt;
	}

	/** Where one may come of itself: on jade turf (or any ground animals keep to), in the light. */
	public static boolean checkSpawnRules(final EntityType<JadeAntelope> type, final ServerLevelAccessor level, final EntitySpawnReason reason,
		final BlockPos pos, final RandomSource random) {
		BlockState below = level.getBlockState(pos.below());
		return (below.is(MistinnBlocks.JADE_TURF) || below.is(BlockTags.ANIMALS_SPAWNABLE_ON)) && level.getRawBrightness(pos, 0) > 8;
	}

	// ── The voice ─────────────────────────────────────────────────

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return SoundEvents.GOAT_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.GOAT_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GOAT_DEATH;
	}

	@Override
	protected void playStepSound(final BlockPos pos, final BlockState state) {
		this.playSound(SoundEvents.GOAT_STEP, 0.15F, 1.2F);
	}

	@Override
	public float getVoicePitch() {
		return (this.isBaby() ? 1.6F : 1.3F) + this.random.nextFloat() * 0.1F;
	}
}
