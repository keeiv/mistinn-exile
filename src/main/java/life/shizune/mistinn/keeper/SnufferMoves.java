package life.shizune.mistinn.keeper;

import java.util.List;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.boss.BossMove;
import life.shizune.mistinn.boss.BossMove.Kind;
import life.shizune.mistinn.boss.BossMove.Weight;
import life.shizune.mistinn.boss.HitShape;
import life.shizune.mistinn.boss.MoveRunner;
import life.shizune.mistinn.boss.MoveSet;
import life.shizune.mistinn.boss.PartRule;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 熄燈人's moves. Each opens with its own sound and a body that can be read: the lantern flares and
 * the keeper leans back for the sweep, salt rises where the slam will land, the hook is drawn back on
 * its rattling chain. The clip of a move is {@code anim/keeper/<id>}, over the move's whole length.
 */
public final class SnufferMoves {
	private SnufferMoves() {
	}

	/** Torso, or the arm on the side the pole comes from. */
	private static final PartRule SWEEP_PART = (boss, player, random) -> {
		float roll = random.nextFloat();
		if (roll < 0.5F) {
			return BodyService.Hit.on(BodyPart.TORSO, false);
		}
		return BodyService.Hit.on(roll < 0.75F ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM, false);
	};

	/** A wide horizontal swing in front: the torso, or an arm. */
	public static final BossMove SWEEP = BossMove.builder("sweep", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SNUFFER_SWEEP, 16).timing(1, 23)
		.strike(16, new HitShape.Sector(5.0, 101.5, 3.0), SWEEP_PART, 7.0F)
		.range(0.0, 4.5).families("sweep")
		.seen((level, boss, target, tick) -> level.sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getEyeY() + 0.4, boss.getZ(), 3, 0.15, 0.15, 0.15, 0.01))
		.script(context -> {
			if (context.tick() == 16) {
				context.boss.swing(InteractionHand.MAIN_HAND, context.boss.getMainHandItem().getAttackAnimation(), true);
				context.level.playSound(null, context.boss.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.6F);
			}
		})
		.build();

	/** Leaps at the target and lands: a ring of salt that takes the legs of anyone standing in it. */
	public static final BossMove SLAM = BossMove.builder("slam", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SNUFFER_SLAM, 20).timing(10, 26)
		.strike(BossMove.Strike.SCRIPTED, new HitShape.Circle(5.5, true), PartRule.always(BodyService.Hit.LEGS), 6.0F)
		.range(0.0, 12.0).families("leap")
		.seen((level, boss, target, tick) -> {
			if (target != null) {
				level.sendParticles(ParticleTypes.WHITE_ASH, target.getX(), target.getY() + 0.1, target.getZ(), 12, 1.6, 0.05, 1.6, 0.0);
			}
		})
		.script(SnufferMoves::slam)
		.build();

	/** Throws the hook: the target is dragged in, then struck in the face. */
	public static final BossMove HOOK = BossMove.builder("hook", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SNUFFER_HOOK, 14).timing(10, 16)
		.strike(BossMove.Strike.SCRIPTED, new HitShape.Circle(4.0, false), PartRule.always(BodyService.Hit.on(BodyPart.HEAD, true)), 8.0F)
		.range(0.0, 12.5).families("hook")
		.seen((level, boss, target, tick) -> level.sendParticles(ParticleTypes.CRIT, boss.getX(), boss.getEyeY(), boss.getZ(), 2, 0.3, 0.3, 0.3, 0.05))
		.script(SnufferMoves::hook)
		.build();

	/** Drops into a crouch, then slides feet first along the floor six blocks: whoever it reaches loses their footing, legs first. */
	public static final BossMove SLIDE = BossMove.builder("slide", Kind.MOVEMENT).weight(Weight.LIGHT)
		.tell(MistinnSounds.SNUFFER_SLIDE, 10).timing(8, 14)
		.dash(10, 18, 6.0)
		.along(10, 18, new HitShape.Capsule(1.4, 0.8, 1.2), PartRule.always(BodyService.Hit.LEGS), 5.0F)
		.range(2.0, 7.0).families("slide", "lunge")
		.seen((level, boss, target, tick) -> level.sendParticles(ParticleTypes.WHITE_ASH, boss.getX(), boss.getY() + 0.1, boss.getZ(), 4, 0.4, 0.02, 0.4, 0.0))
		.script(context -> {
			if (context.tick() >= 10 && context.tick() <= 18) {
				context.level.sendParticles(ParticleTypes.WHITE_ASH, context.boss.getX(), context.boss.getY() + 0.05, context.boss.getZ(), 10, 0.5, 0.05, 0.5, 0.02);
			}
		})
		.build();

	/** Coils, then bursts eight blocks in under half a second and drives the spear into the body at the end of it. */
	public static final BossMove RUSH = BossMove.builder("rush", Kind.MOVEMENT).weight(Weight.HEAVY)
		.tell(MistinnSounds.SNUFFER_RUSH, 14).timing(10, 18)
		.dash(14, 22, 8.0)
		.strike(22, new HitShape.Capsule(3.2, 0.7, 2.0), PartRule.always(BodyService.Hit.on(BodyPart.TORSO, false)), 8.0F)
		.range(5.0, 11.0).families("rush", "thrust")
		.seen((level, boss, target, tick) -> level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, boss.getX(), boss.getEyeY() + 0.3, boss.getZ(), 1, 0.1, 0.1, 0.1, 0.01))
		.script(context -> {
			if (context.tick() >= 14 && context.tick() < 22) {
				context.level.sendParticles(ParticleTypes.CLOUD, context.boss.getX(), context.boss.getY() + 0.3, context.boss.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
			}
			if (context.tick() == 22) {
				context.boss.swing(InteractionHand.MAIN_HAND, context.boss.getMainHandItem().getAttackAnimation(), true);
				context.level.playSound(null, context.boss.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 1.4F, 0.6F);
			}
		})
		.build();

	// ── Parts a blow can be aimed at ──────────────────────────────

	private static final PartRule TORSO = PartRule.always(BodyService.Hit.on(BodyPart.TORSO, false));
	private static final PartRule HEAD = PartRule.always(BodyService.Hit.on(BodyPart.HEAD, false));
	private static final PartRule FACE = PartRule.always(BodyService.Hit.on(BodyPart.HEAD, true));
	private static final PartRule LEGS = PartRule.always(BodyService.Hit.LEGS);
	private static final PartRule ARMS = (boss, player, random) -> BodyService.Hit.on(random.nextBoolean() ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM, false);

	/** The pole whipped through: the swing shown, the air cut, at each given tick. */
	private static BossMove.Script swings(final float pitch, final int... ticks) {
		return context -> {
			for (int tick : ticks) {
				if (context.tick() == tick) {
					context.boss.swing(InteractionHand.MAIN_HAND, context.boss.getMainHandItem().getAttackAnimation(), true);
					context.level.playSound(null, context.boss.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.4F, pitch);
				}
			}
		};
	}

	private static BossMove.Motion keys(final BossMove.Motion.Key... keys) {
		return new BossMove.Motion(List.of(keys));
	}

	private static BossMove.Motion.Key key(final int tick, final double forward, final double side, final double up) {
		return new BossMove.Motion.Key(tick, forward, side, up);
	}

	// ── Movement ──────────────────────────────────────────────────

	/** A long hop to its left, landing low to come back in from the side. */
	public static final BossMove STEP_LEFT = BossMove.builder("step_left", Kind.MOVEMENT).weight(Weight.LIGHT)
		.tell(MistinnSounds.SNUFFER_STEP_LEFT, 8).timing(6, 6)
		.motion(keys(key(8, 0, 0, 0), key(14, 0, -2.5, 0))).range(0.0, 6.0).families("step").build();

	/** The same, to its right. */
	public static final BossMove STEP_RIGHT = BossMove.builder("step_right", Kind.MOVEMENT).weight(Weight.LIGHT)
		.tell(MistinnSounds.SNUFFER_STEP_RIGHT, 8).timing(6, 6)
		.motion(keys(key(8, 0, 0, 0), key(14, 0, 2.5, 0))).range(0.0, 6.0).families("step").build();

	/** Rocks forward as if to come in, then springs three blocks back: room for the hook or the leap. */
	public static final BossMove BACK_STEP = BossMove.builder("back_step", Kind.MOVEMENT).weight(Weight.LIGHT)
		.tell(MistinnSounds.SNUFFER_BACK_STEP, 8).timing(6, 8)
		.motion(keys(key(8, 0, 0, 0), key(14, -3.0, 0, 0))).range(0.0, 5.0).families("step").build();

	/** Stalks round the hunter at middle distance, never looking away, waiting. */
	public static final BossMove CIRCLE = BossMove.builder("circle", Kind.MOVEMENT).weight(Weight.LIGHT)
		.tell(MistinnSounds.SNUFFER_CIRCLE, 8).timing(20, 4)
		.motion(keys(key(8, 0, 0, 0), key(18, 0.8, 2.2, 0), key(28, 1.0, 4.0, 0))).range(3.0, 9.0).cooldown(20).families("step").build();

	/** Pressed too close: a crouch, and a leap back out of reach. */
	public static final BossMove BACK_JUMP = BossMove.builder("back_jump", Kind.MOVEMENT).weight(Weight.LIGHT)
		.tell(MistinnSounds.SNUFFER_BACK_JUMP, 8).timing(8, 8)
		.motion(keys(key(8, 0, 0, 0), key(12, -2.5, 0, 0.9), key(16, -5.0, 0, 0))).range(0.0, 3.5).cooldown(30).families("step").build();

	// ── Close in ──────────────────────────────────────────────────

	/** The sweep, and the backhand straight after it. */
	public static final BossMove DOUBLE_SWEEP = BossMove.builder("double_sweep", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SNUFFER_DOUBLE_SWEEP, 16).timing(12, 20)
		.strike(16, new HitShape.Sector(5.0, 100.0, 3.0), SWEEP_PART, 6.0F)
		.strike(26, new HitShape.Sector(5.0, 100.0, 3.0), SWEEP_PART, 6.0F)
		.motion(keys(key(20, 0, 0, 0), key(26, 0.8, 0, 0))).range(0.0, 4.5).cooldown(20).families("sweep")
		.seen((level, boss, target, tick) -> level.sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getEyeY() + 0.4, boss.getZ(), 3, 0.15, 0.15, 0.15, 0.01))
		.script(swings(0.6F, 16, 26)).build();

	/** Spins all the way round with the pole flat: for whoever stands too close, on any side. */
	public static final BossMove SPIN = BossMove.builder("spin", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SNUFFER_SPIN, 18).timing(8, 22)
		.along(20, 24, new HitShape.Circle(3.8, false), SWEEP_PART, 6.0F)
		.range(0.0, 3.5).cooldown(30).families("spin")
		.script(swings(0.5F, 20, 23)).build();

	/** The spear drawn back level and lunged straight out: long and narrow. */
	public static final BossMove THRUST = BossMove.builder("thrust", Kind.ATTACK).weight(Weight.LIGHT)
		.tell(MistinnSounds.SNUFFER_THRUST, 10).timing(2, 14)
		.strike(10, new HitShape.Capsule(5.5, 0.6, 2.0), TORSO, 6.0F)
		.motion(keys(key(6, 0, 0, 0), key(10, 1.5, 0, 0))).range(0.0, 6.5).families("thrust")
		.script(swings(1.1F, 10)).build();

	/** Three thrusts, the last lunged deepest and hardest. */
	public static final BossMove TRIPLE_THRUST = BossMove.builder("triple_thrust", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SNUFFER_TRIPLE_THRUST, 14).timing(14, 18)
		.strike(14, new HitShape.Capsule(5.0, 0.6, 2.0), TORSO, 4.0F)
		.strike(20, new HitShape.Capsule(5.0, 0.6, 2.0), TORSO, 4.0F)
		.strike(27, new HitShape.Capsule(6.0, 0.6, 2.0), TORSO, 8.0F)
		.motion(keys(key(13, 0, 0, 0), key(15, 0.6, 0, 0), key(20, 0.6, 0, 0), key(21, 1.2, 0, 0), key(26, 1.2, 0, 0), key(28, 2.2, 0, 0)))
		.range(0.0, 6.0).cooldown(30).families("thrust")
		.script(swings(1.0F, 14, 20, 27)).build();

	/** The spear raised high in both claws and brought straight down on the head. */
	public static final BossMove OVERHEAD = BossMove.builder("overhead", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SNUFFER_OVERHEAD, 16).timing(2, 20)
		.strike(16, new HitShape.Capsule(4.5, 0.6, 3.0), HEAD, 8.0F)
		.range(0.0, 5.0).cooldown(10).families("overhead")
		.script(context -> {
			if (context.tick() == 16) {
				context.boss.swing(InteractionHand.MAIN_HAND, context.boss.getMainHandItem().getAttackAnimation(), true);
				context.level.playSound(null, context.boss.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.7F, 0.5F);
			}
		})
		.build();

	/** Ripped up from low on the left, across the arms. Quick. */
	public static final BossMove UPSWING = BossMove.builder("upswing", Kind.ATTACK).weight(Weight.LIGHT)
		.tell(MistinnSounds.SNUFFER_UPSWING, 9).timing(2, 14)
		.strike(9, new HitShape.Sector(4.0, 60.0, 3.0), ARMS, 5.0F)
		.range(0.0, 4.0).families("upswing")
		.script(swings(0.9F, 9)).build();

	/** The pole swept round at ankle height: the legs. */
	public static final BossMove LEG_SWEEP = BossMove.builder("leg_sweep", Kind.ATTACK).weight(Weight.LIGHT)
		.tell(MistinnSounds.SNUFFER_LEG_SWEEP, 10).timing(2, 14)
		.strike(10, new HitShape.Sector(4.5, 95.0, 1.0), LEGS, 5.0F)
		.range(0.0, 4.5).families("legsweep")
		.script(swings(0.7F, 10)).build();

	/** Spear turned round, butt first, rammed into the chest to drive a close hunter off. */
	public static final BossMove BUTT = BossMove.builder("butt", Kind.ATTACK).weight(Weight.LIGHT)
		.tell(MistinnSounds.SNUFFER_BUTT, 8).timing(2, 10)
		.strike(8, new HitShape.Capsule(2.2, 0.7, 2.0), TORSO, 4.0F)
		.range(0.0, 2.5).cooldown(20).families("butt")
		.script(context -> {
			if (context.tick() == 8) {
				for (Player player : context.level.getEntitiesOfClass(Player.class, context.boss.getBoundingBox().inflate(3.0))) {
					if (context.move().strikes().getFirst().shape().hits(context.boss, player)) {
						Vec3 away = player.position().subtract(context.boss.position()).multiply(1.0, 0.0, 1.0);
						Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(1.1);
						player.setDeltaMovement(push.x, 0.35, push.z);
						player.needsSync = true;
					}
				}
				context.level.playSound(null, context.boss.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
			}
		})
		.build();

	/** The lantern head drawn back on its neck and smashed into the face; in the dark it burns. */
	public static final BossMove LANTERN_BASH = BossMove.builder("lantern_bash", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SNUFFER_LANTERN_BASH, 14).timing(2, 18)
		.strike(14, new HitShape.Capsule(2.8, 0.7, 3.0), FACE, 7.0F)
		.motion(keys(key(10, 0, 0, 0), key(14, 0.8, 0, 0))).range(0.0, 3.0).cooldown(10).families("lantern")
		.seen((level, boss, target, tick) -> level.sendParticles(boss instanceof Snuffer keeper && keeper.dark() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
			boss.getX(), boss.getEyeY() + 0.6, boss.getZ(), 2, 0.2, 0.2, 0.2, 0.02))
		.script(context -> {
			if (context.tick() == 14) {
				context.level.playSound(null, context.boss.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.8F, 0.6F);
				if (context.boss instanceof Snuffer keeper && keeper.dark()) {
					for (Player player : context.level.getEntitiesOfClass(Player.class, context.boss.getBoundingBox().inflate(4.0))) {
						if (context.move().strikes().getFirst().shape().hits(context.boss, player)) {
							player.igniteForSeconds(3.0F);
						}
					}
				}
			}
		})
		.build();

	/** Both claws reach out; whoever they close on is hauled up and slammed into the floor. */
	public static final BossMove GRAB_THROW = BossMove.builder("grab_throw", Kind.ATTACK).weight(Weight.GRAB)
		.tell(MistinnSounds.SNUFFER_GRAB_THROW, 16).timing(12, 22)
		.strike(BossMove.Strike.SCRIPTED, new HitShape.Grab(2.8), TORSO, 9.0F)
		.range(0.0, 2.8).cooldown(40).families("grab")
		.script(SnufferMoves::grab)
		.build();

	/** Tick the claws close (on whoever is in reach); held until the throw at {@link #THROW}. */
	private static final int CLUTCH = 16;
	private static final int THROW = 28;

	/** Catches the target in reach at {@link #CLUTCH}, holds them up before it, and slams them down at {@link #THROW}; lets go of anyone gone. */
	private static void grab(final MoveRunner.Context context) {
		int tick = context.tick();
		BossMove.Strike strike = context.move().strikes().getFirst();
		if (tick == CLUTCH) {
			if (context.target instanceof ServerPlayer player && player.isAlive() && strike.shape().hits(context.boss, player)) {
				context.note("held", player);
				context.level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.2F, 0.6F);
			}
			return;
		}
		ServerPlayer held = context.note("held");
		if (held == null || tick < CLUTCH || tick > THROW) {
			return;
		}
		// dead, gone elsewhere, or no longer a hunter: let go
		if (!held.isAlive() || held.level() != context.level || held.isSpectator() || held.isCreative() || held.distanceTo(context.boss) > 5.0) {
			context.note("held", null);
			return;
		}
		Vec3 facing = HitShape.facing(context.boss);
		Vec3 front = context.boss.position().add(facing.scale(1.4));
		if (tick < THROW) {
			double lift = 0.6 + (tick - CLUTCH) * 0.09;
			held.teleportTo(context.level, front.x, context.boss.getY() + lift, front.z, java.util.Set.of(), held.getYRot(), held.getXRot(), false);
			held.setDeltaMovement(Vec3.ZERO);
			held.needsSync = true;
			return;
		}
		held.teleportTo(context.level, front.x, context.boss.getY(), front.z, java.util.Set.of(), held.getYRot(), held.getXRot(), false);
		context.strikeOne(held, strike);
		held.setDeltaMovement(0.0, 0.25, 0.0);
		held.needsSync = true;
		context.level.playSound(null, held.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6F, 1.6F);
		context.level.sendParticles(ParticleTypes.WHITE_ASH, held.getX(), held.getY() + 0.1, held.getZ(), 40, 0.8, 0.1, 0.8, 0.05);
		context.note("held", null);
	}

	/** Thrown off balance by the lit lamps: hunched and swaying. Suffered, never chosen. */
	public static final BossMove STAGGER = BossMove.builder("stagger", Kind.REACTION)
		.unannounced(0).timing(0, Snuffer.STAGGER_TICKS)
		.build();

	public static final MoveSet SET = new MoveSet(List.of(
		SWEEP, SLAM, HOOK, SLIDE, RUSH,
		STEP_LEFT, STEP_RIGHT, BACK_STEP, CIRCLE, BACK_JUMP,
		DOUBLE_SWEEP, SPIN, THRUST, TRIPLE_THRUST, OVERHEAD, UPSWING, LEG_SWEEP, BUTT, LANTERN_BASH, GRAB_THROW,
		STAGGER), List.of(
		// what flows into what: a combo is a path through these, chosen as it goes
		new MoveSet.Link("sweep", "thrust", 17, 28),
		new MoveSet.Link("step_left", "sweep", 14, 20), new MoveSet.Link("step_left", "thrust", 14, 20), new MoveSet.Link("step_left", "overhead", 14, 20),
		new MoveSet.Link("step_right", "sweep", 14, 20), new MoveSet.Link("step_right", "thrust", 14, 20), new MoveSet.Link("step_right", "overhead", 14, 20),
		new MoveSet.Link("back_step", "hook", 14, 22), new MoveSet.Link("back_step", "slam", 14, 22),
		new MoveSet.Link("thrust", "leg_sweep", 12, 20),
		new MoveSet.Link("leg_sweep", "overhead", 12, 20),
		new MoveSet.Link("upswing", "overhead", 11, 18),
		new MoveSet.Link("slide", "upswing", 18, 26),
		new MoveSet.Link("back_jump", "slam", 16, 24),
		new MoveSet.Link("circle", "rush", 20, 32), new MoveSet.Link("circle", "thrust", 20, 32)));

	/** The leap at tick 20; in the air through 30, held there until it comes down; the landing strikes. */
	private static void slam(final MoveRunner.Context context) {
		int tick = context.tick();
		if (tick == 20) {
			Vec3 to = context.target == null ? Vec3.ZERO : context.target.position().subtract(context.boss.position());
			Vec3 flat = new Vec3(to.x, 0, to.z);
			double reach = Math.min(flat.length(), 8.0) / 8.0;
			Vec3 push = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(0.9 * reach);
			context.boss.setDeltaMovement(push.x, 0.75, push.z);
			context.boss.needsSync = true;
			context.note("air", 0);
			return;
		}
		Integer air = context.note("air");
		if (air == null || tick < 20 || tick > 30) {
			return;
		}
		air++;
		context.note("air", air);
		boolean landed = air > 2 && context.boss.onGround();
		if (landed || air > 60) {
			land(context);
			context.note("air", null);
			context.skipTo(30);
		} else if (tick == 30) {
			context.hold();
		}
	}

	private static void land(final MoveRunner.Context context) {
		var level = context.level;
		var boss = context.boss;
		level.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.4F);
		level.sendParticles(ParticleTypes.WHITE_ASH, boss.getX(), boss.getY() + 0.1, boss.getZ(), 80, 2.5, 0.1, 2.5, 0.05);
		level.sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 0.2, boss.getZ(), 1, 0, 0, 0, 0);
		BossMove.Strike strike = context.move().strikes().getFirst();
		for (Player player : level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(6.0, 1.5, 6.0))) {
			if (strike.shape().hits(boss, player)) {
				context.strikeOne(player, strike);
				player.setDeltaMovement(player.getDeltaMovement().add(0, 0.35, 0));
				player.needsSync = true;
			}
		}
	}

	/** The throw at tick 14 drags the target in; ten ticks later the pole strikes the face, if they are still there. */
	private static void hook(final MoveRunner.Context context) {
		int tick = context.tick();
		if (tick == 14) {
			if (!(context.target instanceof ServerPlayer player) || context.boss.distanceTo(player) > 12.5 || !context.boss.hasLineOfSight(player)) {
				return;
			}
			Vec3 pull = context.boss.position().subtract(player.position());
			Vec3 flat = new Vec3(pull.x, 0, pull.z);
			if (flat.lengthSqr() > 1.0E-4) {
				Vec3 drag = flat.normalize().scale(Math.min(1.6, 0.25 * flat.length()));
				player.setDeltaMovement(drag.x, 0.3, drag.z);
				player.needsSync = true;
			}
			context.level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.5F, 0.8F);
			context.note("hooked", player);
		} else if (tick == 24) {
			ServerPlayer player = context.note("hooked");
			context.note("hooked", null);
			if (player != null && player.isAlive() && player.level() == context.level && context.boss.distanceTo(player) <= 4.0) {
				context.boss.swing(InteractionHand.MAIN_HAND, context.boss.getMainHandItem().getAttackAnimation(), true);
				context.strikeOne(player, context.move().strikes().getFirst());
			}
		}
	}
}
