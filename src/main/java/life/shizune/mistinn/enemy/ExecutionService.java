package life.shizune.mistinn.enemy;

import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.WeakHashMap;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.body.WoundService;
import life.shizune.mistinn.medical.TreatmentService;
import life.shizune.mistinn.stamina.RollService;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A finisher seizes a hunter whose legs are gone: the striker takes its move's stance, both bodies are held
 * for the whole move, each contact leaves its wound, and only another player can break the hold before the
 * fatal blow. The server owns every tick of it; clients play the move's two animations on the same clock.
 */
public final class ExecutionService {
	public static final int REPEAT_DELAY = 40;
	/** Ticks the striker takes to slide into its stance. */
	public static final int SLIDE = 3;
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Mistinn.id("execution"));
	private static final net.minecraft.resources.Identifier HOLD = Mistinn.id("execution_hold");
	private static final DustParticleOptions BLOOD = new DustParticleOptions(0x8A0A12, 1.2F);
	/** Ticks a released headshot may fly before the move gives up on it. */
	public static final int FLIGHT = 40;
	/** The farthest an archer takes a headshot from, in blocks: vanilla's bow range. */
	private static final double SHOT_RANGE = 16.0;
	private static final String HEADSHOT_ARROW = "mistinn.headshot";

	/** A level clock is shared by tracking clients, including clients that start tracking mid-move. */
	public record Sequence(int counterpart, long start, float yaw, boolean striker, boolean completed, int move) {
		public static final StreamCodec<ByteBuf, Sequence> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Sequence::counterpart,
			ByteBufCodecs.VAR_LONG, Sequence::start,
			ByteBufCodecs.FLOAT, Sequence::yaw,
			ByteBufCodecs.BOOL, Sequence::striker,
			ByteBufCodecs.BOOL, Sequence::completed,
			ByteBufCodecs.VAR_INT, Sequence::move,
			Sequence::new
		);

		public ExecutionMove finisher() {
			return ExecutionMove.byOrdinal(this.move);
		}
	}

	/** Where the striker slides from and to, and the way it faces the hunter. */
	private record Stance(Vec3 from, Vec3 to, float yaw) {
	}

	public static final AttachmentType<Sequence> SEQUENCE = AttachmentRegistry.<Sequence>builder()
		.syncWith(Sequence.CODEC, AttachmentSyncPredicate.all())
		.buildAndRegister(Mistinn.id("execution_pose"));
	private static final AttachmentType<Long> NEXT_ATTEMPT = AttachmentRegistry.<Long>builder()
		.buildAndRegister(Mistinn.id("execution_next_attempt"));
	private static final Map<Mob, Stance> STANCES = new WeakHashMap<>();
	/** The arrow each aimed-at hunter is waiting for, by entity id, and the hunters it has reached. */
	private static final Map<ServerPlayer, Integer> SHOTS = new WeakHashMap<>();
	private static final java.util.Set<ServerPlayer> STRUCK = java.util.Collections.newSetFromMap(new WeakHashMap<>());
	/** The blow a finisher took over, between the body service and the end of vanilla's damage step. */
	private static @Nullable ServerPlayer seizedHunter;
	private static @Nullable DamageSource seizedBlow;

	private ExecutionService() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(ExecutionService::tick);
		ServerPlayerEvents.LEAVE.register(ExecutionService::release);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> unhold(newPlayer));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> rescue(entity, source));
	}

	public static boolean begins(final ServerPlayer player, final DamageSource source) {
		Mob attacker;
		boolean shot;
		if (source.getDirectEntity() instanceof Mob mob && source.getEntity() == mob && contact(source)) {
			attacker = mob;
			shot = false;
		} else if (source.getDirectEntity() instanceof AbstractArrow arrow && !arrow.entityTags().contains(HEADSHOT_ARROW)
			&& source.getEntity() instanceof Mob mob) {
			attacker = mob;
			shot = true;
		} else {
			return false;
		}
		long now = player.level().getGameTime();
		ExecutionMove move = ExecutionMove.of(attacker);
		// every finisher is measured on its kind's own size: a grown-large creature (a hulking elite) just kills
		if (move == null || move.ranged() != shot || player.hasAttached(SEQUENCE) || holdsMob(attacker)
			|| attacker.getAttributeValue(Attributes.SCALE) != 1.0
			|| now < player.getAttachedOrElse(NEXT_ATTEMPT, Long.MIN_VALUE)
			|| !(shot ? canAim(player, attacker) : eligible(player, attacker))) {
			return false;
		}
		// an archer shoots from where it stands; a melee striker steps into its stance
		Vec3 spot = shot ? attacker.position() : standingSpot(player, attacker, move.spacing(attacker));
		if (spot == null) {
			return false;
		}
		if (shot) {
			source.getDirectEntity().discard();
		}
		Vec3 toward = player.position().subtract(spot);
		float yaw = (float) Math.toDegrees(Mth.atan2(toward.z, toward.x)) - 90.0F;
		player.setAttached(SEQUENCE, new Sequence(attacker.getId(), now, Mth.wrapDegrees(yaw + 180.0F), false, false, move.ordinal()));
		attacker.setAttached(SEQUENCE, new Sequence(player.getId(), now, yaw, true, false, move.ordinal()));
		player.setAttached(NEXT_ATTEMPT, now + REPEAT_DELAY);
		// a melee striker slides into its stance; an archer stands where it is and takes its aim
		STANCES.put(attacker, new Stance(attacker.position(), spot, yaw));
		attacker.getNavigation().stop();
		hold(player);
		TreatmentService.interrupt(player);
		seize(player, source);
		return true;
	}

	/**
	 * Where the striker can stand for its move: on the line toward where it is now, or turned up to 90° either
	 * way, at the move's spacing from the hunter, with room for its body and ground under it. Null when nowhere fits.
	 */
	private static @Nullable Vec3 standingSpot(final ServerPlayer player, final Mob mob, final float spacing) {
		Vec3 out = mob.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		if (out.lengthSqr() < 1.0E-6) {
			out = Vec3.directionFromRotation(0.0F, player.getYRot());
		}
		out = out.normalize();
		ServerLevel level = player.level();
		for (int turn : new int[] {0, 30, -30, 60, -60, 90, -90}) {
			Vec3 spot = player.position().add(out.yRot(turn * Mth.DEG_TO_RAD).scale(spacing));
			BlockPos below = BlockPos.containing(spot.x, spot.y - 0.5, spot.z);
			boolean ground = !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
			if (ground && level.noCollision(mob, mob.getBoundingBox().move(spot.subtract(mob.position())))) {
				return spot;
			}
		}
		return null;
	}

	/** The hunter cannot walk or jump out of a move; the hold leaves with the move. */
	private static void hold(final ServerPlayer player) {
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		var jump = player.getAttribute(Attributes.JUMP_STRENGTH);
		if (speed != null && !speed.hasModifier(HOLD)) {
			speed.addTransientModifier(new AttributeModifier(HOLD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (jump != null && !jump.hasModifier(HOLD)) {
			jump.addTransientModifier(new AttributeModifier(HOLD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void unhold(final ServerPlayer player) {
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		var jump = player.getAttribute(Attributes.JUMP_STRENGTH);
		if (speed != null) {
			speed.removeModifier(HOLD);
		}
		if (jump != null) {
			jump.removeModifier(HOLD);
		}
	}

	/** The striker's own blows during the move are held back; contact comes from the move itself. */
	public static boolean holdsBlow(final ServerPlayer player, final DamageSource source) {
		if (source.getDirectEntity() instanceof Mob mob && source.getEntity() == mob && contact(source) && holdsMob(mob)) {
			seize(player, source);
			return true;
		}
		return false;
	}

	private static void seize(final ServerPlayer player, final DamageSource source) {
		seizedHunter = player;
		seizedBlow = source;
	}

	/**
	 * Whether this blow was taken over by a finisher. Vanilla would still flinch the hunter and knock them back
	 * after the damage step, which pushed them out of reach and cancelled the move a few ticks in.
	 */
	public static boolean takeSeized(final ServerPlayer player, final DamageSource source) {
		boolean seized = seizedHunter == player && seizedBlow == source;
		seizedHunter = null;
		seizedBlow = null;
		return seized;
	}

	private static boolean contact(final DamageSource source) {
		return source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
	}

	public static boolean holdsMob(final Mob mob) {
		Sequence sequence = mob.getAttached(SEQUENCE);
		if (sequence == null || !sequence.striker()) {
			return false;
		}
		ExecutionMove move = sequence.finisher();
		if (mob.level().getGameTime() - sequence.start() >= move.length() + (move.ranged() ? FLIGHT : 0)) {
			mob.removeAttached(SEQUENCE);
			STANCES.remove(mob);
			return false;
		}
		return true;
	}

	public static boolean finalBlow(final ServerPlayer player, final DamageSource source) {
		Sequence sequence = player.getAttached(SEQUENCE);
		return source.is(DAMAGE) && source.getDirectEntity() instanceof Mob mob && sequence != null
			&& sequence.counterpart() == mob.getId() && downed(BodyService.get(player))
			&& (sequence.finisher().ranged() || player.level().getGameTime() - sequence.start() == sequence.finisher().kill());
	}

	/** Both legs destroyed: the hunter can be finished. */
	public static boolean downed(final Body body) {
		return body.destroyed(BodyPart.LEFT_LEG) && body.destroyed(BodyPart.RIGHT_LEG);
	}

	/** Whether a seizing blow can start a finisher at all. Once seized, only {@link #held} is checked. */
	private static boolean eligible(final ServerPlayer player, final Mob mob) {
		return victimReady(player, mob) && mob.isAlive() && !mob.isNoAi() && !mob.isPassenger() && !mob.isVehicle() && mob.hurtTime <= 0
			&& mob.canAttack(player) && !mob.isAlliedTo(player) && mob.isWithinMeleeAttackRange(player) && EnemyRig.perceives(mob, player);
	}

	/** An archer can take its aim at a downed hunter it sees, within bow range. */
	private static boolean canAim(final ServerPlayer player, final Mob archer) {
		return victimReady(player, archer) && archer.isAlive() && !archer.isNoAi() && !archer.isPassenger()
			&& archer.hurtTime <= 0 && archer.canAttack(player) && !archer.isAlliedTo(player)
			&& archer.distanceTo(player) <= SHOT_RANGE && EnemyRig.perceives(archer, player);
	}

	private static boolean victimReady(final ServerPlayer player, final Mob mob) {
		return BodyService.applies(player) && player.isAlive() && !player.isSleeping() && !player.isPassenger()
			&& !player.isVehicle() && !RollService.dodging(player) && player.level() == mob.level()
			&& player.level().getDifficulty() != Difficulty.PEACEFUL && downed(BodyService.get(player));
	}

	/**
	 * The headshot's own arrow reaching its hunter: the blow is taken over (no flinch, no arrow damage) and the
	 * fatal blow follows on the next tick. Arrows that hit anything else stay ordinary arrows.
	 */
	public static boolean headshotLands(final ServerPlayer player, final DamageSource source) {
		if (!(source.getDirectEntity() instanceof AbstractArrow arrow) || !arrow.entityTags().contains(HEADSHOT_ARROW)) {
			return false;
		}
		Sequence sequence = player.getAttached(SEQUENCE);
		if (sequence == null || sequence.completed() || !sequence.finisher().ranged() || source.getEntity() == null
			|| source.getEntity().getId() != sequence.counterpart()) {
			return false;
		}
		arrow.discard();
		STRUCK.add(player);
		seize(player, source);
		return true;
	}

	/** A seized hunter stays held while both are alive in the same world and the legs are still gone. */
	private static boolean held(final ServerPlayer player, final Mob mob) {
		return BodyService.applies(player) && player.isAlive() && mob.isAlive() && !mob.isRemoved()
			&& player.level() == mob.level() && downed(BodyService.get(player));
	}

	/** Another player striking the striker before the fatal blow breaks the hold; the hunter's own blows do not. */
	private static void rescue(final LivingEntity entity, final DamageSource source) {
		if (!(entity instanceof Mob mob) || !(source.getEntity() instanceof Player rescuer)) {
			return;
		}
		Sequence striking = mob.getAttached(SEQUENCE);
		if (striking == null || !striking.striker() || striking.completed() || rescuer.getId() == striking.counterpart()
			|| !(mob.level().getEntity(striking.counterpart()) instanceof ServerPlayer victim)) {
			return;
		}
		Sequence held = victim.getAttached(SEQUENCE);
		if (held != null && !held.completed()) {
			clear(victim, held);
		}
	}

	private static void tick(final ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			Sequence sequence = player.getAttached(SEQUENCE);
			if (sequence == null) {
				continue;
			}
			ExecutionMove move = sequence.finisher();
			long elapsed = level.getGameTime() - sequence.start();
			Entity entity = level.getEntity(sequence.counterpart());
			if (player.isDeadOrDying() && sequence.completed()) {
				// Keep the victim's final pose through the mission screen. The attachment is not copied on respawn.
				if (elapsed >= move.length() && entity instanceof Mob mob) {
					clearMob(mob, player.getId(), sequence.start());
				}
				continue;
			}
			if (move.ranged()) {
				if (entity instanceof Mob archer && matching(archer, player.getId(), sequence.start()) && held(player, archer)) {
					aimAndShoot(level, player, archer, sequence, elapsed);
				} else {
					clear(player, sequence);
				}
				continue;
			}
			if (elapsed > move.kill() || !(entity instanceof Mob mob) || !matching(mob, player.getId(), sequence.start())
				|| !held(player, mob)) {
				clear(player, sequence);
				continue;
			}
			stand(mob, elapsed);
			for (ExecutionMove.Beat beat : move.beats()) {
				if (beat.tick() == elapsed) {
					contact(level, player, beat);
				}
			}
			if (elapsed == move.kill()) {
				strike(level, player, mob, sequence);
			}
		}
	}

	/** The archer draws on the hunter's head, releases one arrow, and the move lives until that arrow lands or is lost. */
	private static void aimAndShoot(final ServerLevel level, final ServerPlayer player, final Mob archer, final Sequence sequence, final long elapsed) {
		ExecutionMove move = sequence.finisher();
		if (STRUCK.remove(player)) {
			strike(level, player, archer, sequence);
			return;
		}
		if (elapsed < move.kill()) {
			Vec3 aim = player.getEyePosition().subtract(archer.getEyePosition());
			float yaw = (float) Math.toDegrees(Mth.atan2(aim.z, aim.x)) - 90.0F;
			float pitch = (float) -Math.toDegrees(Mth.atan2(aim.y, Math.hypot(aim.x, aim.z)));
			// it holds still for the shot: a bow skeleton would otherwise strafe round its target as it draws
			Stance stance = STANCES.get(archer);
			Vec3 at = stance == null ? archer.position() : stance.to();
			archer.getNavigation().stop();
			archer.snapTo(at.x, at.y, at.z, yaw, pitch);
			archer.setYHeadRot(yaw);
			archer.setYBodyRot(yaw);
			archer.setDeltaMovement(Vec3.ZERO);
			if (!EnemyRig.perceives(archer, player)) {
				clear(player, sequence);
			}
			return;
		}
		if (elapsed == move.kill()) {
			Arrow arrow = new Arrow(level, archer, new ItemStack(Items.ARROW), archer.getMainHandItem());
			Vec3 from = archer.getEyePosition().add(archer.getLookAngle().scale(0.4));
			Vec3 path = player.getEyePosition().subtract(from);
			arrow.setPos(from.x, from.y, from.z);
			arrow.shoot(path.x, path.y, path.z, 3.0F, 0.0F);
			arrow.setNoGravity(true);
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
			arrow.addTag(HEADSHOT_ARROW);
			level.addFreshEntity(arrow);
			SHOTS.put(player, arrow.getId());
			level.playSound(null, archer.getX(), archer.getY(), archer.getZ(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1.0F, 0.8F);
			return;
		}
		Entity arrow = level.getEntity(SHOTS.getOrDefault(player, -1));
		boolean lost = arrow == null || arrow.isRemoved() || arrow.getDeltaMovement().lengthSqr() < 0.01;
		if (lost || elapsed > move.kill() + FLIGHT) {
			// the arrow hit something else, stopped, or flew wide: the hunter lives
			if (arrow != null) {
				arrow.discard();
			}
			clear(player, sequence);
		}
	}

	/** The striker slides into its stance, then is kept there: the two animations are drawn for this spacing. */
	private static void stand(final Mob mob, final long elapsed) {
		Stance stance = STANCES.get(mob);
		if (stance == null) {
			return;
		}
		Vec3 at = stance.from.lerp(stance.to, Mth.clamp(elapsed / (double) SLIDE, 0.0, 1.0));
		mob.snapTo(at.x, at.y, at.z, stance.yaw, 0.0F);
		mob.setYHeadRot(stance.yaw);
		mob.setYBodyRot(stance.yaw);
		mob.setDeltaMovement(Vec3.ZERO);
	}

	private static void contact(final ServerLevel level, final ServerPlayer player, final ExecutionMove.Beat beat) {
		if (beat.part() != null) {
			WoundService.inflict(player, beat.part(), beat.fracture(), beat.bleed());
		}
		// the staged bodies are drawn away from where the hunter stands; the blood belongs where the wound is drawn
		Sequence sequence = player.getAttached(SEQUENCE);
		float facing = (sequence == null ? player.getYRot() : sequence.yaw()) * Mth.DEG_TO_RAD;
		double x = player.getX() - Mth.sin(facing) * beat.along();
		double z = player.getZ() + Mth.cos(facing) * beat.along();
		double y = player.getY() + beat.up();
		switch (beat.sound()) {
			case BITE, TEAR, STAB -> level.sendParticles(BLOOD, x, y, z, 14, 0.2, 0.15, 0.2, 0.02);
			case SNAP, SLAM -> level.sendParticles(ParticleTypes.CRIT, x, y, z, 10, 0.25, 0.2, 0.25, 0.1);
		}
		level.playSound(null, x, y, z, life.shizune.mistinn.registry.VanillaCreatureSounds.execution(beat.sound()), SoundSource.HOSTILE, 1.0F,
			0.92F + player.getRandom().nextFloat() * 0.16F);
	}

	public static boolean wasExecuted(final @Nullable DamageSource source) {
		return source != null && source.is(DAMAGE);
	}

	private static void strike(final ServerLevel level, final ServerPlayer player, final Mob mob, final Sequence sequence) {
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE), mob);
		if (player.hurtServer(level, source, 128.0F)) {
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 0.66F);
			level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.44, player.getZ(), 9, 0.28, 0.15, 0.28, 0.08);
		}
		if (!player.isAlive() && wasExecuted(player.getLastDamageSource())) {
			// a ranged move's animation resumes from its release tick at the moment the arrow lands
			long start = sequence.finisher().ranged() ? level.getGameTime() - sequence.finisher().kill() : sequence.start();
			if (sequence.finisher().ranged()) {
				clearMob(mob, player.getId(), sequence.start());
				SHOTS.remove(player);
			}
			player.setAttached(SEQUENCE, new Sequence(sequence.counterpart(), start, sequence.yaw(), false, true, sequence.move()));
			unhold(player);
		} else {
			clear(player, sequence);
		}
	}

	/** Ends whatever move holds this hunter, on both sides, and lifts the hold (also when the hunter leaves). */
	public static void release(final ServerPlayer player) {
		clear(player, player.getAttached(SEQUENCE));
		unhold(player);
	}

	private static void clear(final ServerPlayer player, final @Nullable Sequence sequence) {
		if (sequence == null) {
			return;
		}
		if (player.level().getEntity(sequence.counterpart()) instanceof Mob mob) {
			clearMob(mob, player.getId(), sequence.start());
		}
		player.removeAttached(SEQUENCE);
		SHOTS.remove(player);
		STRUCK.remove(player);
		unhold(player);
	}

	private static boolean matching(final Mob mob, final int playerId, final long start) {
		Sequence pose = mob.getAttached(SEQUENCE);
		return pose != null && pose.striker() && pose.counterpart() == playerId && pose.start() == start;
	}

	private static void clearMob(final Mob mob, final int playerId, final long start) {
		if (matching(mob, playerId, start)) {
			mob.removeAttached(SEQUENCE);
			STANCES.remove(mob);
		}
	}
}
