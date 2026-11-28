package life.shizune.mistinn.terra;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * 晶羊: a stocky sheep of Terra whose fleece grows clusters of lattice crystal. Left alone it grazes; struck, it lowers its
 * head and rams whoever struck it, a while. Shorn, its crystal comes away as grit, and grows back as it grazes on jade
 * turf. Two fed jade grass have a lamb.
 */
public class CrystalSheep extends Animal implements Shearable {
	private static final EntityDataAccessor<Boolean> DATA_SHORN = SynchedEntityData.defineId(CrystalSheep.class, EntityDataSerializers.BOOLEAN);
	/** How long it stays angry after it was last struck, and how often, shorn, it may regrow while grazing. */
	public static final int TEMPER = 200;
	public static final int REGROW = 1200;

	public CrystalSheep(final EntityType<? extends CrystalSheep> type, final Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes()
			.add(Attributes.MAX_HEALTH, 14.0)
			.add(Attributes.MOVEMENT_SPEED, 0.24)
			.add(Attributes.ATTACK_DAMAGE, 3.0)
			.add(Attributes.ATTACK_KNOCKBACK, 1.5);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SHORN, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.35, false));
		this.goalSelector.addGoal(2, new BreedGoal(this, 1.0));
		this.goalSelector.addGoal(3, new TemptGoal(this, 1.1, this::isFood, false));
		this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
		this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
	}

	public boolean shorn() {
		return this.entityData.get(DATA_SHORN);
	}

	public void shorn(final boolean shorn) {
		this.entityData.set(DATA_SHORN, shorn);
	}

	@Override
	public boolean isFood(final ItemStack stack) {
		return stack.is(MistinnItems.JADE_GRASS);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(final ServerLevel level, final AgeableMob other) {
		return MistinnEntities.CRYSTAL_SHEEP.create(level, EntitySpawnReason.BREEDING);
	}

	// ── Shearing ──────────────────────────────────────────────────

	@Override
	public boolean readyForShearing() {
		return this.isAlive() && !this.shorn() && !this.isBaby();
	}

	@Override
	public void shear(final ServerLevel level, final SoundSource source, final ItemStack tool) {
		level.playSound(null, this, SoundEvents.SHEEP_SHEAR, source, 1.0F, 1.0F);
		level.playSound(null, this, SoundEvents.AMETHYST_CLUSTER_BREAK, source, 0.8F, 1.3F);
		this.shorn(true);
		this.spawnAtLocation(level, new ItemStack(MistinnItems.CRYSTAL_DUST, 1 + this.random.nextInt(3)));
		if (this.random.nextInt(8) == 0) {
			this.spawnAtLocation(level, new ItemStack(MistinnItems.LATTICE_CHIP));
		}
	}

	@Override
	public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.SHEARS) && this.readyForShearing()) {
			if (this.level() instanceof ServerLevel level) {
				this.shear(level, SoundSource.PLAYERS, held);
				held.hurtAndBreak(1, player, hand.asEquipmentSlot());
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	// ── Temper and grazing ────────────────────────────────────────

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		// its temper passes
		if (this.getTarget() != null && this.tickCount - this.getLastHurtByMobTimestamp() > TEMPER) {
			this.setTarget(null);
		}
		// shorn, it grows its crystal back as it grazes the jade turf
		if (this.shorn() && this.random.nextInt(REGROW) == 0) {
			BlockState under = level.getBlockState(this.blockPosition().below());
			if (under.is(MistinnBlocks.JADE_TURF) || under.is(BlockTags.DIRT)) {
				this.shorn(false);
				level.playSound(null, this, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.2F);
			}
		}
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		// struck, it turns on its striker at once
		if (hurt && source.getEntity() instanceof net.minecraft.world.entity.LivingEntity striker && striker != this
			&& !(striker instanceof Player player && (player.isCreative() || player.isSpectator()))) {
			this.setTarget(striker);
		}
		return hurt;
	}

	@Override
	public boolean doHurtTarget(final ServerLevel level, final net.minecraft.world.entity.Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			level.playSound(null, this, SoundEvents.GOAT_RAM_IMPACT, SoundSource.NEUTRAL, 1.0F, 1.1F);
		}
		return hit;
	}

	/** Where one may come of itself: on jade turf (or any ground animals keep to), in the light. */
	public static boolean checkSpawnRules(final EntityType<CrystalSheep> type, final ServerLevelAccessor level, final EntitySpawnReason reason,
		final BlockPos pos, final RandomSource random) {
		BlockState below = level.getBlockState(pos.below());
		return (below.is(MistinnBlocks.JADE_TURF) || below.is(BlockTags.ANIMALS_SPAWNABLE_ON)) && level.getRawBrightness(pos, 0) > 8;
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("shorn", this.shorn());
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.shorn(input.getBooleanOr("shorn", false));
	}

	// ── The voice ─────────────────────────────────────────────────

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return SoundEvents.SHEEP_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.SHEEP_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SHEEP_DEATH;
	}

	@Override
	protected void playStepSound(final BlockPos pos, final BlockState state) {
		this.playSound(SoundEvents.SHEEP_STEP, 0.15F, 0.9F);
	}

	@Override
	public float getVoicePitch() {
		return (this.isBaby() ? 1.3F : 0.85F) + this.random.nextFloat() * 0.1F;
	}
}
