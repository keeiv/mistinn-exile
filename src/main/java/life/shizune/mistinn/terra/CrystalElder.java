package life.shizune.mistinn.terra;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 晶冠古龍: the ancient drake grown at the heart of a Terra highland, two and a half times the
 * size of the others, crowned in pale gold crystal. It flies and stoops as they do, wider and
 * higher; besides, it calls the lattice up out of the ground under its quarry (five rings of light,
 * and a moment later the crystal erupts), and once, badly hurt, it calls two drakes to it. Brought
 * down, it leaves crystal cores enough to wake a hub, and the crown grows another in a few days.
 */
public class CrystalElder extends CrystalDrake {
	/** How often it calls the lattice up, how long the rings burn first, and how hard and wide the eruption. */
	public static final int ERUPT_EVERY = 160;
	public static final int ERUPT_WARNING = 30;
	public static final float ERUPT_DAMAGE = 8.0F;
	public static final double ERUPT_REACH = 1.8;
	private static final DustParticleOptions RING = new DustParticleOptions(0x8FF5FF, 1.4F);

	private final ServerBossEvent bar = new ServerBossEvent(java.util.UUID.randomUUID(), this.getDisplayName(), BossEvent.BossBarColor.BLUE,
		BossEvent.BossBarOverlay.NOTCHED_10);
	private int erupt = ERUPT_EVERY / 2;
	private int erupting;
	private final List<Vec3> rings = new ArrayList<>();
	private boolean called;

	public CrystalElder(final EntityType<? extends CrystalElder> type, final Level level) {
		super(type, level);
		this.xpReward = 120;
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 320.0)
			.add(Attributes.ARMOR, 10.0)
			.add(Attributes.ATTACK_DAMAGE, 14.0)
			.add(Attributes.ATTACK_KNOCKBACK, 1.5)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FLYING_SPEED, 0.6)
			.add(Attributes.FOLLOW_RANGE, 64.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.9);
	}

	@Override
	protected double over() {
		return 15.0;
	}

	@Override
	protected double wheel() {
		return 18.0;
	}

	@Override
	protected void abilities(final ServerLevel level, final @Nullable LivingEntity target) {
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		if (!this.called && this.getHealth() < this.getMaxHealth() * 0.5F) {
			this.called = true;
			this.call(level, target);
		}
		if (!this.rings.isEmpty()) {
			if (--this.erupting <= 0) {
				this.eruptAll(level);
			} else if (this.tickCount % 3 == 0) {
				for (Vec3 at : this.rings) {
					for (int i = 0; i < 12; i++) {
						double angle = i * Math.PI / 6.0;
						level.sendParticles(RING, at.x + Math.cos(angle) * ERUPT_REACH, at.y + 0.15, at.z + Math.sin(angle) * ERUPT_REACH, 1, 0.0, 0.0, 0.0, 0.0);
					}
				}
			}
			return;
		}
		if (--this.erupt <= 0 && target != null && this.distanceTo(target) < 48.0F && this.hasLineOfSight(target)) {
			this.erupt = ERUPT_EVERY;
			this.rings.add(ground(level, target.position()));
			for (int i = 0; i < 4; i++) {
				double angle = i * Math.PI / 2.0 + this.random.nextDouble() * 0.8;
				double reach = 3.0 + this.random.nextDouble() * 3.0;
				this.rings.add(ground(level, target.position().add(Math.cos(angle) * reach, 0.0, Math.sin(angle) * reach)));
			}
			this.erupting = ERUPT_WARNING;
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0F, 0.5F);
		}
	}

	private static Vec3 ground(final ServerLevel level, final Vec3 at) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
		return new Vec3(at.x, Math.abs(top.getY() - at.y) < 6.0 ? top.getY() : at.y, at.z);
	}

	/** The lattice erupts under every ring at once. */
	private void eruptAll(final ServerLevel level) {
		BlockParticleOption shards = new BlockParticleOption(ParticleTypes.BLOCK, MistinnBlocks.LATTICE_CRYSTAL.defaultBlockState());
		for (Vec3 at : this.rings) {
			level.sendParticles(shards, at.x, at.y + 0.8, at.z, 40, 0.5, 0.9, 0.5, 0.25);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 12, 0.3, 1.2, 0.3, 0.06);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 1.6F, 0.6F);
			for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(ERUPT_REACH, 3.0, ERUPT_REACH),
				e -> e.isAlive() && !(e instanceof CrystalDrake) && e.position().distanceTo(at) <= ERUPT_REACH + 0.5)) {
				hit.hurtServer(level, level.damageSources().mobAttack(this), ERUPT_DAMAGE);
				hit.setDeltaMovement(hit.getDeltaMovement().add(0.0, 0.7, 0.0));
				hit.needsSync = true;
			}
		}
		this.rings.clear();
	}

	/** Badly hurt, it calls two drakes out of the crown to it. */
	private void call(final ServerLevel level, final @Nullable LivingEntity target) {
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 1.4F);
		for (int i = 0; i < 2; i++) {
			CrystalDrake drake = MistinnEntities.CRYSTAL_DRAKE.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (drake != null) {
				drake.snapTo(this.getX() + (i == 0 ? 6.0 : -6.0), this.getY() + 2.0, this.getZ(), this.getYRot(), 0.0F);
				drake.setTarget(target);
				level.addFreshEntity(drake);
				level.sendParticles(ParticleTypes.END_ROD, drake.getX(), drake.getY() + 1.0, drake.getZ(), 30, 1.0, 0.6, 1.0, 0.08);
			}
		}
	}

	/** Where the rift will erupt next, for tests. */
	public List<Vec3> rings() {
		return List.copyOf(this.rings);
	}

	public boolean called() {
		return this.called;
	}

	@Override
	public void die(final DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel level) {
			Elders.died(level, this);
			ServerPlayer killer = source.getEntity() instanceof ServerPlayer player ? player
				: this.getLastHurtByPlayer() instanceof ServerPlayer striker ? striker : null;
			if (killer != null) {
				AchievementService.grant(killer, AchievementService.ELDER_SLAIN);
				List<net.minecraft.world.item.Item> pieces = MistinnItems.gearOfTier(5);
				this.spawnAtLocation(level, new ItemStack(pieces.get(level.getRandom().nextInt(pieces.size()))));
			}
		}
	}

	@Override
	public void startSeenByPlayer(final ServerPlayer player) {
		super.startSeenByPlayer(player);
		this.bar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(final ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bar.removePlayer(player);
	}

	@Override
	public void remove(final RemovalReason reason) {
		this.bar.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	public boolean removeWhenFarAway(final double distance) {
		return false;
	}

	@Override
	protected float getSoundVolume() {
		return 4.0F;
	}

	@Override
	public float getVoicePitch() {
		return 0.45F + this.random.nextFloat() * 0.1F;
	}
}
