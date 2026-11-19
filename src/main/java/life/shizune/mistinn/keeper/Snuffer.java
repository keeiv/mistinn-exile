package life.shizune.mistinn.keeper;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 熄燈人, the first Lighthouse Keeper: tall and thin, an unlit lantern for a head, a long hook in
 * hand. Each move strikes a different part of the body, so the hunter's gear matters: the sweep
 * cuts across the torso and arms, the slam's shockwave takes the legs, and the hook drags the
 * hunter in for a blow to the face, where only a mask helps.
 * Below half health the lamp room goes dark. Lighting all four lamp stands throws it off balance
 * for a few seconds, taking half again as much damage, until it snuffs them out one by one.
 */
public class Snuffer extends Monster {
	public static final float MAX_HEALTH = 300.0F;
	/** Players this close to the keeper see its health bar. */
	private static final double BAR_RANGE = 32.0;
	/** Lamp stands this close to the keeper belong to its room. */
	private static final int ROOM_RADIUS = 12;
	public static final int STAGGER_TICKS = 100;
	public static final float STAGGER_DAMAGE = 1.5F;

	/** The moves, with their wind-up (the tell), in ticks. */
	public enum Move {
		SWEEP(16, 7.0F),
		SLAM(20, 6.0F),
		HOOK(14, 8.0F);

		public final int windup;
		public final float damage;

		Move(final int windup, final float damage) {
			this.windup = windup;
			this.damage = damage;
		}
	}

	private final ServerBossEvent bar = new ServerBossEvent(java.util.UUID.randomUUID(), Component.translatable("entity.mistinn.snuffer"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
	private @Nullable Move move;
	private int moveTimer;
	private int cooldown = 40;
	private boolean leaping;
	private int hookFollowUp = -1;
	private @Nullable ServerPlayer hooked;
	private boolean dark;
	private int staggered;
	private int snuffTimer;

	public Snuffer(final EntityType<? extends Snuffer> type, final Level level) {
		super(type, level);
		this.xpReward = 120;
		this.setPersistenceRequired();
		this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LANTERN));
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MistinnItems.MIST_SPEAR));
		this.setDropChance(EquipmentSlot.HEAD, 0.0F);
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, MAX_HEALTH)
			.add(Attributes.ARMOR, 8.0)
			.add(Attributes.ATTACK_DAMAGE, 6.0)
			.add(Attributes.MOVEMENT_SPEED, 0.27)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean removeWhenFarAway(final double distance) {
		return false;
	}

	// ── State, for tests and the client ───────────────────────────

	public @Nullable Move move() {
		return this.move;
	}

	public boolean dark() {
		return this.dark;
	}

	public boolean staggered() {
		return this.staggered > 0;
	}

	// ── Every tick ────────────────────────────────────────────────

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		this.bar.setProgress(this.getHealth() / this.getMaxHealth());
		this.updateBarViewers(level);
		if (!this.dark && this.getHealth() <= this.getMaxHealth() / 2.0F) {
			this.goDark(level);
		}
		if (this.dark) {
			this.darkRoom(level);
		}
		if (this.staggered > 0) {
			this.staggered--;
			this.getNavigation().stop();
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getEyeY() + 0.3, this.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
			return;
		}
		if (this.hookFollowUp >= 0) {
			this.hookFollowUp();
		}
		LivingEntity target = this.getTarget();
		if (this.move != null) {
			this.getNavigation().stop();
			if (target != null) {
				this.getLookControl().setLookAt(target, 30.0F, 30.0F);
			}
			this.tickMove(level, target);
			return;
		}
		if (target == null || !target.isAlive()) {
			return;
		}
		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
		double distance = this.distanceTo(target);
		if (distance > 3.0) {
			this.getNavigation().moveTo(target, 1.0);
		} else {
			this.getNavigation().stop();
		}
		if (--this.cooldown <= 0 && this.hasLineOfSight(target)) {
			Move chosen = this.choose(distance);
			if (chosen != null) {
				this.start(chosen);
			}
		}
	}

	private @Nullable Move choose(final double distance) {
		float roll = this.random.nextFloat();
		if (distance < 4.5) {
			return roll < 0.45F ? Move.SWEEP : roll < 0.75F ? Move.SLAM : Move.HOOK;
		}
		if (distance < 12.0) {
			return roll < 0.6F ? Move.HOOK : Move.SLAM;
		}
		return null;
	}

	/** Starts a move from its wind-up. Public so the client game test can call each one. */
	public void start(final Move chosen) {
		this.move = chosen;
		this.moveTimer = 0;
		this.leaping = false;
		if (this.level() instanceof ServerLevel level) {
			switch (chosen) {
				case SWEEP -> level.playSound(null, this.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 1.4F, 0.6F);
				case SLAM -> level.playSound(null, this.blockPosition(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.4F, 0.5F);
				case HOOK -> level.playSound(null, this.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 1.6F, 0.7F);
			}
		}
	}

	private void tickMove(final ServerLevel level, final @Nullable LivingEntity target) {
		Move current = this.move;
		if (current == null) {
			return;
		}
		this.moveTimer++;
		if (this.moveTimer < current.windup) {
			this.tell(level, current, target);
			return;
		}
		switch (current) {
			case SWEEP -> {
				this.sweep(level);
				this.finish(30);
			}
			case SLAM -> this.slam(level, target);
			case HOOK -> {
				this.hook(level, target);
				this.finish(40);
			}
		}
	}

	/** The tell before each move, so a careful hunter can read it. */
	private void tell(final ServerLevel level, final Move current, final @Nullable LivingEntity target) {
		switch (current) {
			// the lantern flares and the keeper leans back
			case SWEEP -> level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getEyeY() + 0.4, this.getZ(), 3, 0.15, 0.15, 0.15, 0.01);
			// salt grains rise where it will land
			case SLAM -> {
				if (target != null) {
					level.sendParticles(ParticleTypes.WHITE_ASH, target.getX(), target.getY() + 0.1, target.getZ(), 12, 1.6, 0.05, 1.6, 0.0);
				}
			}
			// the hook is drawn back, the chain rattles
			case HOOK -> level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getEyeY(), this.getZ(), 2, 0.3, 0.3, 0.3, 0.05);
		}
	}

	private void finish(final int rest) {
		this.move = null;
		this.moveTimer = 0;
		this.leaping = false;
		this.cooldown = rest + this.random.nextInt(20);
	}

	// ── The moves ─────────────────────────────────────────────────

	/** A wide horizontal swing in front: torso, or the arm on the side it comes from. */
	private void sweep(final ServerLevel level) {
		this.swing(net.minecraft.world.InteractionHand.MAIN_HAND, this.getMainHandItem().getAttackAnimation(), true);
		level.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.6F);
		Vec3 facing = Vec3.directionFromRotation(0.0F, this.getYRot());
		for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(5.0))) {
			Vec3 to = player.position().subtract(this.position()).multiply(1, 0, 1);
			if (to.length() > 5.0 || to.normalize().dot(facing) < -0.2) {
				continue;
			}
			this.strike(level, player, Move.SWEEP, this.sweepHit(player));
		}
	}

	private BodyService.Hit sweepHit(final Player player) {
		float roll = this.random.nextFloat();
		if (roll < 0.5F) {
			return BodyService.Hit.on(BodyPart.TORSO, false);
		}
		return BodyService.Hit.on(roll < 0.75F ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM, false);
	}

	/** Leaps at the target and lands: a ring of salt that takes the legs of anyone standing in it. */
	private void slam(final ServerLevel level, final @Nullable LivingEntity target) {
		if (!this.leaping) {
			this.leaping = true;
			Vec3 to = target == null ? Vec3.ZERO : target.position().subtract(this.position());
			Vec3 flat = new Vec3(to.x, 0, to.z);
			double reach = Math.min(flat.length(), 8.0) / 8.0;
			Vec3 push = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(0.9 * reach);
			this.setDeltaMovement(push.x, 0.75, push.z);
			this.needsSync = true;
			return;
		}
		// wait to come down, then strike on landing (or give up after a few seconds)
		if (!this.onGround() && this.moveTimer < this.move.windup + 60) {
			return;
		}
		level.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.4F);
		level.sendParticles(ParticleTypes.WHITE_ASH, this.getX(), this.getY() + 0.1, this.getZ(), 80, 2.5, 0.1, 2.5, 0.05);
		level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.2, this.getZ(), 1, 0, 0, 0, 0);
		for (Player player : level.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(5.0, 1.5, 5.0))) {
			if (player.onGround() && player.distanceTo(this) <= 5.5) {
				this.strike(level, player, Move.SLAM, BodyService.Hit.LEGS);
				player.setDeltaMovement(player.getDeltaMovement().add(0, 0.35, 0));
				player.needsSync = true;
			}
		}
		this.finish(40);
	}

	/** Throws the hook: the target is dragged in, then struck in the face. */
	private void hook(final ServerLevel level, final @Nullable LivingEntity target) {
		if (!(target instanceof ServerPlayer player) || this.distanceTo(player) > 12.5 || !this.hasLineOfSight(player)) {
			return;
		}
		Vec3 pull = this.position().subtract(player.position());
		Vec3 flat = new Vec3(pull.x, 0, pull.z);
		if (flat.lengthSqr() > 1.0E-4) {
			Vec3 drag = flat.normalize().scale(Math.min(1.6, 0.25 * flat.length()));
			player.setDeltaMovement(drag.x, 0.3, drag.z);
			player.needsSync = true;
		}
		level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.5F, 0.8F);
		this.hooked = player;
		this.hookFollowUp = 10;
	}

	private void hookFollowUp() {
		if (--this.hookFollowUp > 0) {
			return;
		}
		this.hookFollowUp = -1;
		ServerPlayer player = this.hooked;
		this.hooked = null;
		if (player != null && player.isAlive() && this.distanceTo(player) <= 4.0 && this.level() instanceof ServerLevel level) {
			this.swing(net.minecraft.world.InteractionHand.MAIN_HAND, this.getMainHandItem().getAttackAnimation(), true);
			this.strike(level, player, Move.HOOK, BodyService.Hit.on(BodyPart.HEAD, true));
		}
	}

	/** Hits a player on the chosen part; non-hunters (creative) just take the damage. */
	private void strike(final ServerLevel level, final Player player, final Move from, final BodyService.Hit hit) {
		DamageSource source = this.damageSources().mobAttack(this);
		if (player instanceof ServerPlayer serverPlayer && BodyService.applies(serverPlayer)) {
			BodyService.aim(serverPlayer, hit);
			player.hurtServer(level, source, from.damage);
			// a blocked or ignored hit must not leave its aim for the next one
			BodyService.clearAim(serverPlayer);
		} else {
			player.hurtServer(level, source, from.damage);
		}
	}

	/** Test hook: performs a move's strike at once, skipping the wind-up. */
	public void strikeNow(final ServerLevel level, final ServerPlayer player, final Move chosen) {
		switch (chosen) {
			case SWEEP -> this.strike(level, player, Move.SWEEP, this.sweepHit(player));
			case SLAM -> this.strike(level, player, Move.SLAM, BodyService.Hit.LEGS);
			case HOOK -> this.strike(level, player, Move.HOOK, BodyService.Hit.on(BodyPart.HEAD, true));
		}
	}

	// ── The dark half ─────────────────────────────────────────────

	private void goDark(final ServerLevel level) {
		this.dark = true;
		this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SOUL_LANTERN));
		for (BlockPos lamp : this.lamps(level)) {
			level.setBlockAndUpdate(lamp, level.getBlockState(lamp).setValue(LampStandBlock.LIT, false));
		}
		level.playSound(null, this.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0F, 0.5F);
		for (ServerPlayer player : this.bar.getPlayers()) {
			player.sendSystemMessage(Component.translatable("message.mistinn.snuffer.dark"), true);
		}
	}

	private void darkRoom(final ServerLevel level) {
		for (ServerPlayer player : this.bar.getPlayers()) {
			if (player.distanceTo(this) < ROOM_RADIUS + 4 && (this.tickCount % 40 == 0 || !player.hasEffect(MobEffects.DARKNESS))) {
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
			}
		}
		List<BlockPos> lamps = this.lamps(level);
		long lit = lamps.stream().filter(pos -> level.getBlockState(pos).getValue(LampStandBlock.LIT)).count();
		if (this.staggered <= 0 && lamps.size() >= 4 && lit == lamps.size()) {
			this.stagger(level);
		}
		// after a stagger it snuffs the lamps again, one at a time
		if (this.staggered <= 0 && lit > 0 && --this.snuffTimer <= 0) {
			this.snuffTimer = 60;
			for (BlockPos pos : lamps) {
				BlockState state = level.getBlockState(pos);
				if (state.getValue(LampStandBlock.LIT)) {
					level.setBlockAndUpdate(pos, state.setValue(LampStandBlock.LIT, false));
					level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10, 0.1, 0.1, 0.1, 0.02);
					break;
				}
			}
		}
	}

	private void stagger(final ServerLevel level) {
		this.staggered = STAGGER_TICKS;
		this.snuffTimer = STAGGER_TICKS + 40;
		this.move = null;
		this.hookFollowUp = -1;
		level.playSound(null, this.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2.0F, 0.6F);
		for (ServerPlayer player : this.bar.getPlayers()) {
			player.sendSystemMessage(Component.translatable("message.mistinn.snuffer.stagger"), true);
		}
	}

	/** The lamp stands of this keeper's room. */
	public List<BlockPos> lamps(final ServerLevel level) {
		List<BlockPos> found = new ArrayList<>();
		BlockPos centre = this.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-ROOM_RADIUS, -3, -ROOM_RADIUS), centre.offset(ROOM_RADIUS, 3, ROOM_RADIUS))) {
			if (level.getBlockState(pos).is(MistinnBlocks.LAMP_STAND)) {
				found.add(pos.immutable());
			}
		}
		return found;
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
		return super.hurtServer(level, source, this.staggered > 0 ? damage * STAGGER_DAMAGE : damage);
	}

	// ── Health bar and spoils ─────────────────────────────────────

	private void updateBarViewers(final ServerLevel level) {
		for (ServerPlayer player : List.copyOf(this.bar.getPlayers())) {
			if (!player.isAlive() || player.level() != level || player.distanceTo(this) > BAR_RANGE) {
				this.bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : level.getPlayers(p -> p.distanceTo(this) <= BAR_RANGE)) {
			this.bar.addPlayer(player);
		}
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
	protected void dropCustomDeathLoot(final ServerLevel level, final DamageSource source, final boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.spawnAtLocation(level, new ItemStack(MistinnItems.RUST_GOD_SALT));
		if (this.random.nextFloat() < 0.3F) {
			this.spawnAtLocation(level, new ItemStack(this.gearDrop()));
		}
		if (source.getEntity() instanceof ServerPlayer player) {
			SaltService.add(player, 3000);
		}
		this.bar.removeAllPlayers();
	}

	/** One piece of tier III or IV gear. */
	private net.minecraft.world.item.Item gearDrop() {
		int tier = 3 + this.random.nextInt(2);
		List<net.minecraft.world.item.Item> pool = List.of(
			MistinnItems.HELMETS.get(tier), MistinnItems.ARMORS.get(tier), MistinnItems.MASKS.get(tier),
			MistinnItems.RIGS.get(tier), MistinnItems.LEGGINGS.get(tier), MistinnItems.BOOTS.get(tier)
		);
		return pool.get(this.random.nextInt(pool.size()));
	}
}
