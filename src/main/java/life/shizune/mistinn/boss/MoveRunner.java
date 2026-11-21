package life.shizune.mistinn.boss;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import life.shizune.mistinn.body.BodyService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Runs one move at a time along its timeline: the tell's sound at the start and what is seen through the
 * windup, the strikes at their ticks, the body carried by the move's motion (stopped by whatever it runs
 * into), and the script for anything else. A script may hold the clock (waiting in the air for a landing)
 * or skip ahead. A boss that dies stops at once.
 */
public final class MoveRunner {
	private @Nullable BossMove current;
	private int ticks;
	private boolean held;
	private int delayLeft;
	private float facing;
	private final Map<String, Object> notes = new HashMap<>();
	/** Who a carried blow has already struck, by group: it lands once on each. */
	private final Set<String> struck = new HashSet<>();
	/** The room the body may not be carried out of (null: anywhere). */
	private @Nullable Vec3 leashCentre;
	private double leashRadius;

	/** Begins a move from its first tick; whatever ran before is dropped. */
	public void start(final Mob boss, final BossMove move) {
		this.current = move;
		this.ticks = 0;
		this.held = false;
		this.delayLeft = 0;
		this.facing = boss.getYRot();
		this.notes.clear();
		this.struck.clear();
		if (move.tell() != null && boss.level() instanceof ServerLevel level) {
			level.playSound(null, boss.blockPosition(), move.tell(), SoundSource.HOSTILE, 1.3F, 1.0F);
		}
	}

	public boolean running() {
		return this.current != null;
	}

	public @Nullable BossMove current() {
		return this.current;
	}

	public int ticks() {
		return this.ticks;
	}

	/** Whether the clock stood still on the last tick (the client keeps the pose instead of running the clip on). */
	public boolean held() {
		return this.held;
	}

	/** Past the blows (or, for a feint, any time): from here another move may take over, as the links allow. */
	public boolean inChainWindow() {
		return this.current != null && (this.current.kind() == BossMove.Kind.FEINT || this.ticks >= this.current.windup() + this.current.active());
	}

	/** Holds the blow {@code ticks} longer at the last tick of the tell, to wait out a roll begun too early. */
	public void delay(final int ticks) {
		this.delayLeft = Math.max(0, ticks);
	}

	public void tick(final ServerLevel level, final Mob boss, final @Nullable LivingEntity target) {
		BossMove move = this.current;
		if (move == null) {
			return;
		}
		if (!boss.isAlive()) {
			this.stop();
			return;
		}
		if (this.ticks < move.windup() && move.tellEffect() != null) {
			move.tellEffect().tick(level, boss, target, this.ticks);
		}
		if (this.delayLeft > 0 && this.ticks == move.windup() - 1) {
			// the pose held at the end of the tell; nothing lands and nothing moves until the delay runs out
			this.delayLeft--;
			this.held = true;
			return;
		}
		this.held = false;
		if (move.script() != null) {
			move.script().tick(new Context(level, boss, target));
		}
		if (this.current != move) {
			return;
		}
		for (BossMove.Strike strike : move.strikes()) {
			if (strike.tick() == this.ticks) {
				this.blow(level, boss, strike);
			}
		}
		if (!this.held) {
			Vec3 step = move.motion().at(this.ticks + 1).subtract(move.motion().at(this.ticks));
			if (step.lengthSqr() > 1.0E-8) {
				// moved as a body moves: a wall stops it, and so does the edge of its room
				boss.move(MoverType.SELF, this.leashed(boss.position(), this.world(step)));
			}
			this.ticks++;
		}
		if (this.ticks >= move.length()) {
			this.stop();
		}
	}

	public void stop() {
		this.current = null;
		this.ticks = 0;
		this.held = false;
		this.delayLeft = 0;
		this.notes.clear();
	}

	/** Keeps whatever a move carries inside a room: no step takes the body further than {@code radius} from {@code centre}. */
	public void leash(final @Nullable Vec3 centre, final double radius) {
		this.leashCentre = centre;
		this.leashRadius = radius;
	}

	/** The part of a step that stays in the room (all of it, if it does not head out past the edge). */
	private Vec3 leashed(final Vec3 from, final Vec3 step) {
		Vec3 centre = this.leashCentre;
		if (centre == null) {
			return step;
		}
		double fx = from.x - centre.x;
		double fz = from.z - centre.z;
		double nx = fx + step.x;
		double nz = fz + step.z;
		double r2 = this.leashRadius * this.leashRadius;
		if (nx * nx + nz * nz <= r2 || nx * nx + nz * nz <= fx * fx + fz * fz) {
			return step;
		}
		// the fraction of the step that reaches the edge: |f + t s| = r
		double a = step.x * step.x + step.z * step.z;
		double b = 2.0 * (fx * step.x + fz * step.z);
		double c = fx * fx + fz * fz - r2;
		double t = c >= 0.0 ? 0.0 : (-b + Math.sqrt(Math.max(0.0, b * b - 4.0 * a * c))) / (2.0 * a);
		t = Math.max(0.0, Math.min(1.0, t));
		return new Vec3(step.x * t, step.y, step.z * t);
	}

	/** A local step (forward, up, to the right) turned into the world along the facing the move began with. */
	private Vec3 world(final Vec3 local) {
		float yaw = this.facing * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		return forward.scale(local.x).add(0.0, local.y, 0.0).add(right.scale(local.z));
	}

	private void blow(final ServerLevel level, final Mob boss, final BossMove.Strike strike) {
		for (Player player : level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(16.0))) {
			if (player.isAlive() && !player.isSpectator() && strike.shape().hits(boss, player)) {
				if (strike.group() > 0 && !this.struck.add(strike.group() + ":" + player.getUUID())) {
					continue;
				}
				hit(level, boss, player, strike.part(), strike.damage());
			}
		}
	}

	/** Hits a player on the part the rule picks; players outside the body system (creative) take the damage plainly. */
	public static void hit(final ServerLevel level, final Mob boss, final Player player, final PartRule part, final float damage) {
		DamageSource source = boss.damageSources().mobAttack(boss);
		if (player instanceof ServerPlayer hunter && BodyService.applies(hunter)) {
			BodyService.aim(hunter, part.pick(boss, player, boss.getRandom()));
			player.hurtServer(level, source, damage);
			// a blocked or ignored hit must not leave its aim for the next one
			BodyService.clearAim(hunter);
		} else {
			player.hurtServer(level, source, damage);
		}
	}

	/** What a move's script sees and may do on one tick. */
	public final class Context {
		public final ServerLevel level;
		public final Mob boss;
		public final @Nullable LivingEntity target;

		private Context(final ServerLevel level, final Mob boss, final @Nullable LivingEntity target) {
			this.level = level;
			this.boss = boss;
			this.target = target;
		}

		public int tick() {
			return MoveRunner.this.ticks;
		}

		public BossMove move() {
			return MoveRunner.this.current;
		}

		/** The clock does not move on this tick: the move waits (in the air, for a landing). */
		public void hold() {
			MoveRunner.this.held = true;
		}

		/** Jumps the clock (a landing that came early goes straight to its own part of the move). */
		public void skipTo(final int tick) {
			MoveRunner.this.ticks = tick;
		}

		/** Deals a strike now, to every player it reaches. */
		public void strike(final BossMove.Strike strike) {
			MoveRunner.this.blow(this.level, this.boss, strike);
		}

		/** Deals a strike now to one player only, if it reaches them. */
		public void strikeOne(final Player player, final BossMove.Strike strike) {
			if (player.isAlive() && player.level() == this.level && strike.shape().hits(this.boss, player)) {
				hit(this.level, this.boss, player, strike.part(), strike.damage());
			}
		}

		@SuppressWarnings("unchecked")
		public <T> @Nullable T note(final String key) {
			return (T) MoveRunner.this.notes.get(key);
		}

		public void note(final String key, final @Nullable Object value) {
			if (value == null) {
				MoveRunner.this.notes.remove(key);
			} else {
				MoveRunner.this.notes.put(key, value);
			}
		}
	}
}
