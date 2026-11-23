package life.shizune.mistinn.keeper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 鏽輪's moves. It is a machine and does not swing: it rolls. It spins up in place with its rim grinding sparks off the
 * floor, then runs straight at a hunter; what it runs into stops it, and a wall or a pillar leaves it stuck there, its
 * back and the man chained to it bare. Close in it sweeps its rim, hops and slams; a hunter who stays at its back too
 * long gets the chain.
 */
public final class WheelMoves {
	private WheelMoves() {
	}

	private static final PartRule TORSO = PartRule.always(BodyService.Hit.on(BodyPart.TORSO, false));
	private static final PartRule LEGS = PartRule.always(BodyService.Hit.LEGS);

	/** How fast it rolls, blocks a tick, and how much faster for each layer of momentum. */
	public static final double ROLL_SPEED = 0.55;
	public static final double ROLL_SPEED_PER_MOMENTUM = 0.1;
	/** How near the rim's middle a hunter must stand to be run over. */
	public static final double RUN_OVER = 1.9;
	/** A step this much shorter than it should have been, the wheel ran into something. */
	private static final double BLOCKED = 0.35;

	/**
	 * One tick of a roll along {@code direction}: it moves, turns its face the way it goes, runs over whoever it reaches (once
	 * each, kept in the move's note {@code key}), and if something stops it short, crashes. Returns false once it crashed.
	 */
	private static boolean roll(final MoveRunner.Context context, final Vec3 direction, final double speed, final String key, final float damage) {
		Mob boss = context.boss;
		Vec3 step = direction.normalize().scale(speed);
		Vec3 before = boss.position();
		float yaw = (float) (Mth.atan2(-direction.x, direction.z) * Mth.RAD_TO_DEG);
		boss.setYRot(yaw);
		boss.setYBodyRot(yaw);
		boss.setYHeadRot(yaw);
		boolean inRoom = !(boss instanceof RustWheel wheel) || wheel.insideRoom(before.add(step));
		if (inRoom) {
			boss.move(MoverType.SELF, step);
		}
		double moved = boss.position().subtract(before).multiply(1.0, 0.0, 1.0).length();
		if (context.level.getGameTime() % 2 == 0) {
			context.level.sendParticles(ParticleTypes.CRIT, boss.getX() - step.x, boss.getY() + 0.1, boss.getZ() - step.z, 3, 0.4, 0.05, 0.4, 0.1);
		}
		Set<UUID> struck = context.note(key);
		if (struck == null) {
			struck = new HashSet<>();
			context.note(key, struck);
		}
		for (Player player : context.level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(1.0, 0.5, 1.0))) {
			if (!player.isAlive() || player.isSpectator() || struck.contains(player.getUUID())) {
				continue;
			}
			if (Math.hypot(player.getX() - boss.getX(), player.getZ() - boss.getZ()) <= RUN_OVER && Math.abs(player.getY() - boss.getY()) <= 1.6) {
				struck.add(player.getUUID());
				MoveRunner.hit(context.level, boss, player, LEGS, damage);
				// thrown aside, not carried along under the wheel
				Vec3 side = new Vec3(-direction.z, 0.0, direction.x).normalize();
				if (side.dot(player.position().subtract(boss.position())) < 0.0) {
					side = side.scale(-1.0);
				}
				player.setDeltaMovement(side.x * 0.9, 0.35, side.z * 0.9);
				if (player instanceof ServerPlayer hunter) {
					hunter.syncVelocity = true;
				}
			}
		}
		if (!inRoom || moved < speed * BLOCKED) {
			if (boss instanceof RustWheel wheel) {
				wheel.crash(context.level);
			}
			return false;
		}
		return true;
	}

	/** Where to roll: at the move's target if it has one, else straight on. */
	private static Vec3 aim(final MoveRunner.Context context) {
		Mob boss = context.boss;
		if (context.target != null) {
			Vec3 to = context.target.position().subtract(boss.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > 0.01) {
				return to.normalize();
			}
		}
		return HitShape.facing(boss);
	}

	private static double speed(final Mob boss) {
		return ROLL_SPEED + ROLL_SPEED_PER_MOMENTUM * (boss instanceof RustWheel wheel ? wheel.momentum() : 0);
	}

	private static void grind(final MoveRunner.Context context) {
		Mob boss = context.boss;
		if (context.tick() % 3 == 0) {
			context.level.sendParticles(ParticleTypes.LAVA, boss.getX(), boss.getY() + 0.1, boss.getZ(), 1, 0.4, 0.0, 0.4, 0.0);
			context.level.sendParticles(ParticleTypes.CRIT, boss.getX(), boss.getY() + 0.2, boss.getZ(), 6, 0.5, 0.1, 0.5, 0.25);
		}
	}

	// ── The roll ──────────────────────────────────────────────────

	/** Ticks of the ram: spinning up in place, then rolling; how far at most. */
	public static final int RAM_SPIN = 24;
	public static final int RAM_ROLL = 32;

	/**
	 * 輾壓衝撞: the wheel rocks back and spins in place, the rim grinding sparks off the floor; then it runs straight at
	 * the hunter. A wall or a pillar in the way leaves it stuck there. Once its momentum is up it runs faster and harder;
	 * the broken wheel turns once toward the hunter halfway.
	 */
	public static final BossMove RAM = BossMove.builder("ram", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.RUST_WHEEL_SPIN, RAM_SPIN).timing(RAM_ROLL, 14)
		.strike(BossMove.Strike.SCRIPTED, new HitShape.Capsule(18.0, RUN_OVER, 2.0), LEGS, 8.0F)
		.range(5.0, 18.0).cooldown(30).families("ram")
		.seen((level, boss, target, tick) -> {
			if (tick % 3 == 0) {
				level.sendParticles(ParticleTypes.CRIT, boss.getX(), boss.getY() + 0.15, boss.getZ(), 5, 0.5, 0.05, 0.5, 0.3);
			}
		})
		.script(context -> {
			int tick = context.tick();
			if (tick == RAM_SPIN) {
				context.note("dir", aim(context));
				context.level.playSound(null, context.boss.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 1.4F, 0.5F);
			}
			Vec3 dir = context.note("dir");
			if (dir == null || tick < RAM_SPIN || tick >= RAM_SPIN + RAM_ROLL) {
				return;
			}
			// the broken wheel turns toward the hunter once, halfway
			if (tick == RAM_SPIN + RAM_ROLL / 2 && context.boss instanceof RustWheel wheel && wheel.broken() && context.target != null) {
				dir = turnToward(dir, aim(context), 50.0);
				context.note("dir", dir);
			}
			float damage = context.move().strikes().getFirst().damage() + 2.0F * (context.boss instanceof RustWheel wheel ? wheel.momentum() : 0);
			if (!roll(context, dir, speed(context.boss), "run", damage)) {
				context.note("dir", null);
				return;
			}
			if (tick == RAM_SPIN + RAM_ROLL - 1 && context.boss instanceof RustWheel wheel) {
				wheel.gainMomentum();
			}
		})
		.build();

	/** {@code from} turned toward {@code to} by at most {@code degrees}. */
	static Vec3 turnToward(final Vec3 from, final Vec3 to, final double degrees) {
		double a = Math.atan2(from.z, from.x);
		double b = Math.atan2(to.z, to.x);
		double d = Mth.wrapDegrees((b - a) * Mth.RAD_TO_DEG);
		double turn = Mth.clamp(d, -degrees, degrees) * Mth.DEG_TO_RAD;
		return new Vec3(Math.cos(a + turn), 0.0, Math.sin(a + turn));
	}

	/**
	 * 回輾: braked hard with a screech, the wheel turning back on itself and rolling eight blocks the way it came. Only
	 * ever out of a ram that ran past: a hunter who keeps to its back gets the chain instead.
	 */
	public static final BossMove BACK_ROLL = BossMove.builder("back_roll", Kind.ATTACK).weight(Weight.LIGHT)
		.tell(MistinnSounds.RUST_WHEEL_SCREECH, 10).timing(16, 12)
		.strike(BossMove.Strike.SCRIPTED, new HitShape.Capsule(8.0, RUN_OVER, 2.0), LEGS, 6.0F)
		.range(0.0, 8.0).cooldown(40).families("back", life.shizune.mistinn.boss.Planner.FOLLOW_UP)
		.script(context -> {
			int tick = context.tick();
			if (tick == 10) {
				context.note("dir", HitShape.facing(context.boss).scale(-1.0));
			}
			Vec3 dir = context.note("dir");
			if (dir == null || tick < 10 || tick >= 26) {
				return;
			}
			// it rolls backward: its face stays where it was
			Vec3 face = dir.scale(-1.0);
			float yaw = (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG);
			if (!roll(context, dir, 0.5, "run", context.move().strikes().getFirst().damage())) {
				context.note("dir", null);
			}
			context.boss.setYRot(yaw);
			context.boss.setYBodyRot(yaw);
		})
		.build();

	/** 假衝: the same spin-up as the ram, and no ram: it grinds to a stop, and swings its rim, or hops. */
	public static final BossMove FAKE_RAM = BossMove.builder("fake_ram", Kind.FEINT)
		.tell(MistinnSounds.RUST_WHEEL_SPIN, RAM_SPIN).timing(0, 8).imitates("ram")
		.range(5.0, 18.0).cooldown(40).families("ram")
		.seen(RAM.tellEffect()).build();

	// ── Close in ──────────────────────────────────────────────────

	/** 輪緣橫掃: the wheel tips over on its side and swings its iron rim round through the front. */
	public static final BossMove RIM_SWEEP = BossMove.builder("rim_sweep", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.RUST_WHEEL_TILT, 16).timing(4, 18)
		.strike(16, new HitShape.Sector(4.5, 70.0, 2.5), TORSO, 7.0F)
		.range(0.0, 4.5).cooldown(10).families("sweep")
		.script(context -> {
			if (context.tick() == 16) {
				context.level.playSound(null, context.boss.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.5F);
				Vec3 facing = HitShape.facing(context.boss);
				for (int i = -3; i <= 3; i++) {
					Vec3 at = context.boss.position().add(HitShape.facing(context.boss).yRot(i * 0.35F).scale(3.0));
					context.level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 1.0, at.z, 3, 0.1, 0.2, 0.1, 0.05);
				}
				context.level.sendParticles(ParticleTypes.SWEEP_ATTACK, context.boss.getX() + facing.x * 2.0, context.boss.getY() + 1.0, context.boss.getZ() + facing.z * 2.0,
					1, 0.0, 0.0, 0.0, 0.0);
			}
		})
		.build();

	/** Ticks of the hop: when it leaves the floor; the last tick it may hang (held until it lands). */
	public static final int HOP_JUMP = 18;
	public static final int HOP_AIR = 30;
	/** How far the shock of its landing runs along the floor. */
	public static final double HOP_REACH = 5.0;

	/** 彈跳砸地: it sinks on its springs with a creak, hops, and slams down; the shock runs out along the floor. */
	public static final BossMove HOP_SLAM = BossMove.builder("hop_slam", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.RUST_WHEEL_CREAK, HOP_JUMP).timing(HOP_AIR - HOP_JUMP + 2, 18)
		.strike(BossMove.Strike.SCRIPTED, new HitShape.Circle(HOP_REACH, true), LEGS, 7.0F)
		.range(0.0, 6.0).cooldown(40).families("hop")
		.script(context -> {
			int tick = context.tick();
			Mob boss = context.boss;
			if (tick == HOP_JUMP) {
				Vec3 to = aim(context).scale(context.target == null ? 0.0 : Math.min(3.0, boss.distanceTo(context.target)) * 0.12);
				boss.setDeltaMovement(to.x, 0.62, to.z);
				boss.needsSync = true;
				context.note("air", 0);
				return;
			}
			Integer air = context.note("air");
			if (air == null) {
				return;
			}
			context.note("air", air + 1);
			if (air + 1 > 2 && boss.onGround() || air + 1 > 60) {
				context.note("air", null);
				slam(context);
				context.skipTo(HOP_AIR + 1);
			} else if (tick == HOP_AIR) {
				context.hold();
			}
		})
		.build();

	/** It comes down: the shock takes everyone on the floor near it; the broken wheel spills brine where it lands. */
	private static void slam(final MoveRunner.Context context) {
		Mob boss = context.boss;
		context.level.playSound(null, boss.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.6F, 0.45F);
		context.level.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 0.1, boss.getZ(), 40, HOP_REACH / 2.0, 0.05, HOP_REACH / 2.0, 0.04);
		for (Player player : context.level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(HOP_REACH, 2.0, HOP_REACH))) {
			if (player.isAlive() && !player.isSpectator() && player.onGround() && Math.abs(player.getY() - boss.getY()) <= 1.5
				&& Math.hypot(player.getX() - boss.getX(), player.getZ() - boss.getZ()) <= HOP_REACH) {
				MoveRunner.hit(context.level, boss, player, LEGS, context.move().strikes().getFirst().damage());
			}
		}
		if (boss instanceof RustWheel wheel && wheel.broken()) {
			wheel.spill(context.level, boss.blockPosition());
		}
	}

	/** How far back the chain reaches. */
	public static final double LASH_REACH = 6.0;

	/**
	 * 甩鍊: the man at its back swings the chain out behind it, low and wide. Not chosen: a hunter who stays at its back
	 * too long calls it ({@link RustWheel}).
	 */
	public static final BossMove CHAIN_LASH = BossMove.builder("chain_lash", Kind.REACTION)
		.tell(MistinnSounds.RUST_WHEEL_CHAIN, 12).timing(4, 16)
		.strike(BossMove.Strike.SCRIPTED, new HitShape.Circle(LASH_REACH, false), TORSO, 6.0F)
		.families("lash")
		.script(context -> {
			if (context.tick() != 12) {
				return;
			}
			Mob boss = context.boss;
			context.level.playSound(null, boss.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.4F, 0.6F);
			Vec3 back = HitShape.facing(boss).scale(-1.0);
			for (double r = 1.5; r <= LASH_REACH; r += 0.75) {
				context.level.sendParticles(ParticleTypes.CRIT, boss.getX() + back.x * r, boss.getY() + 0.6, boss.getZ() + back.z * r, 2, 0.6, 0.1, 0.6, 0.0);
			}
			for (Player player : context.level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(LASH_REACH, 2.0, LASH_REACH))) {
				if (player.isAlive() && !player.isSpectator() && RustWheel.behind(boss, player.position())
					&& Math.hypot(player.getX() - boss.getX(), player.getZ() - boss.getZ()) <= LASH_REACH) {
					MoveRunner.hit(context.level, boss, player, TORSO, context.move().strikes().getFirst().damage());
				}
			}
		})
		.build();

	// ── The broken wheel ──────────────────────────────────────────

	/** Ticks of the circuit: spinning up, round the edge of the yard, then cutting in at the hunter. */
	public static final int CIRCUIT_SPIN = 20;
	public static final int CIRCUIT_ROUND = 56;
	public static final int CIRCUIT_CUT = 18;

	/**
	 * 繞場: the broken wheel runs round the edge of its yard once, then cuts in straight at the hunter. A pillar in its way
	 * stops it as it stops a ram.
	 */
	public static final BossMove CIRCUIT = BossMove.builder("circuit", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.RUST_WHEEL_SPIN, CIRCUIT_SPIN).timing(CIRCUIT_ROUND + CIRCUIT_CUT, 16)
		.strike(BossMove.Strike.SCRIPTED, new HitShape.Circle(RUN_OVER, false), LEGS, 7.0F)
		.range(4.0, 14.0).cooldown(200).families("circuit", RustWheel.BROKEN)
		.seen(RAM.tellEffect())
		.script(context -> {
			int tick = context.tick();
			Mob boss = context.boss;
			if (tick < CIRCUIT_SPIN || !(boss instanceof RustWheel wheel) || context.note("crashed") != null) {
				return;
			}
			Vec3 centre = wheel.room();
			Vec3 dir;
			if (tick < CIRCUIT_SPIN + CIRCUIT_ROUND) {
				// round the edge: along the tangent, leaning in toward the ring it keeps to
				Vec3 out = boss.position().subtract(centre).multiply(1.0, 0.0, 1.0);
				if (out.lengthSqr() < 0.01) {
					out = HitShape.facing(boss);
				}
				Vec3 tangent = new Vec3(-out.z, 0.0, out.x).normalize();
				double keep = RustWheel.CIRCUIT_RADIUS - out.length();
				dir = tangent.add(out.normalize().scale(Mth.clamp(keep * 0.25, -0.6, 0.6)));
			} else {
				Vec3 cut = context.note("cut");
				if (cut == null) {
					cut = aim(context);
					context.note("cut", cut);
				}
				dir = cut;
			}
			if (!roll(context, dir, ROLL_SPEED + 0.1, "run", context.move().strikes().getFirst().damage())) {
				context.note("crashed", Boolean.TRUE);
			}
		})
		.build();

	// ── Getting about ─────────────────────────────────────────────

	private static BossMove.Motion keys(final BossMove.Motion.Key... keys) {
		return new BossMove.Motion(List.of(keys));
	}

	private static BossMove.Motion.Key key(final int tick, final double forward, final double side) {
		return new BossMove.Motion.Key(tick, forward, side, 0.0);
	}

	/** Ground round to its left on the rim, gears grumbling. */
	public static final BossMove GRIND_LEFT = BossMove.builder("grind_left", Kind.MOVEMENT).weight(Weight.LIGHT)
		.tell(MistinnSounds.RUST_WHEEL_GRIND, 8).timing(8, 6)
		.motion(keys(key(8, 0, 0), key(16, 0, -3.0))).range(0.0, 8.0).families("step")
		.script(WheelMoves::grind).build();

	/** The same, to its right. */
	public static final BossMove GRIND_RIGHT = BossMove.builder("grind_right", Kind.MOVEMENT).weight(Weight.LIGHT)
		.tell(MistinnSounds.RUST_WHEEL_GRIND, 8).timing(8, 6)
		.motion(keys(key(8, 0, 0), key(16, 0, 3.0))).range(0.0, 8.0).families("step")
		.script(WheelMoves::grind).build();

	/** Back four blocks: room for a ram. */
	public static final BossMove GRIND_BACK = BossMove.builder("grind_back", Kind.MOVEMENT).weight(Weight.LIGHT)
		.tell(MistinnSounds.RUST_WHEEL_GRIND, 8).timing(10, 6)
		.motion(keys(key(8, 0, 0), key(18, -4.0, 0))).range(0.0, 5.0).cooldown(20).families("step")
		.script(WheelMoves::grind).build();

	// ── Suffered ──────────────────────────────────────────────────

	/** Ticks it stays stuck after running into a wall or a pillar. */
	public static final int STUCK_TICKS = 50;

	/** 卡住: run into stone, the wheel jammed and spinning on the spot, its back and the man on it bare. Suffered, never chosen. */
	public static final BossMove STUCK = BossMove.builder("stuck", Kind.REACTION)
		.unannounced(0).timing(0, STUCK_TICKS)
		.script(context -> {
			if (context.tick() % 4 == 0) {
				context.level.sendParticles(ParticleTypes.LARGE_SMOKE, context.boss.getX(), context.boss.getY() + 1.6, context.boss.getZ(), 2, 0.4, 0.3, 0.4, 0.01);
			}
		})
		.build();

	/** Thrown off balance: reeling. Suffered, never chosen. */
	public static final BossMove STAGGER = BossMove.builder("stagger", Kind.REACTION)
		.unannounced(0).timing(0, Keeper.STAGGER_TICKS)
		.build();

	public static final MoveSet SET = new MoveSet(List.of(
		RAM, BACK_ROLL, FAKE_RAM, RIM_SWEEP, HOP_SLAM, CHAIN_LASH, CIRCUIT,
		GRIND_LEFT, GRIND_RIGHT, GRIND_BACK,
		STUCK, STAGGER), List.of(
		new MoveSet.Link("ram", "back_roll", RAM_SPIN + RAM_ROLL, RAM_SPIN + RAM_ROLL + 10),
		new MoveSet.Link("fake_ram", "rim_sweep", 18, 26), new MoveSet.Link("fake_ram", "hop_slam", 18, 26),
		new MoveSet.Link("rim_sweep", "hop_slam", 18, 26),
		new MoveSet.Link("grind_left", "ram", 14, 20), new MoveSet.Link("grind_right", "ram", 14, 20),
		new MoveSet.Link("grind_left", "rim_sweep", 14, 20), new MoveSet.Link("grind_right", "rim_sweep", 14, 20),
		new MoveSet.Link("grind_back", "ram", 16, 24),
		new MoveSet.Link("hop_slam", "rim_sweep", 30, 40)));

	/** Whether a move of this set (by id) rolls the wheel: the renderer spins it from the motion, and the tells from the spin-up. */
	public static boolean spinsUp(final @Nullable String move) {
		return "ram".equals(move) || "fake_ram".equals(move) || "circuit".equals(move);
	}
}
