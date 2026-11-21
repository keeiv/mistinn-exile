package life.shizune.mistinn.boss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One move of a boss: what announces it, when and where it strikes, how it carries the body, and what
 * it is for. Ticks count from the move's start; the windup is the tell, so every move but a reaction
 * opens with its own sound and at least the windup its weight allows.
 *
 * @param tell       the sound the move opens with (null only for reactions)
 * @param strikes    the blows, at fixed ticks; a strike at tick {@link Strike#SCRIPTED} is dealt by the script
 * @param motion     how the body is carried, along its facing at the move's start
 * @param range      the distance to its target the move is meant for
 * @param families   what kind of move it is, for signals and the planner (for example "thrust", "hook")
 * @param imitates   for a feint: the move whose tell it borrows
 * @param signals    for a signal: the family of the next attack it promises
 * @param tellEffect what is seen through the windup (particles), besides the clip
 * @param script     whatever the move does beyond fixed strikes and motion: a leap, a pull
 */
public record BossMove(String id, Kind kind, Weight weight, @Nullable SoundEvent tell, int windup, int active, int recovery,
	List<Strike> strikes, Motion motion, Range range, int cooldown, Set<String> families,
	@Nullable String imitates, @Nullable String signals, @Nullable TellEffect tellEffect, @Nullable Script script) {

	public enum Kind {
		ATTACK, MOVEMENT, FEINT, SIGNAL, IDLE,
		/** Not chosen, only suffered: the stagger. */
		REACTION
	}

	/** How heavy a move is, and the shortest windup that weight allows. */
	public enum Weight {
		LIGHT(8), HEAVY(14), GRAB(16), NONE(0);

		public final int minWindup;

		Weight(final int minWindup) {
			this.minWindup = minWindup;
		}
	}

	/**
	 * A blow: when it lands, what it can reach, which part it hits and how hard. Blows sharing a group above 0
	 * are one blow checked over several ticks (a slide along the floor): each hunter takes it at most once.
	 */
	public record Strike(int tick, HitShape shape, PartRule part, float damage, int group) {
		/** The tick of a strike the move's script deals itself, at a moment of its own (a landing, the end of a pull). */
		public static final int SCRIPTED = -1;

		public Strike(final int tick, final HitShape shape, final PartRule part, final float damage) {
			this(tick, shape, part, damage, 0);
		}
	}

	/** How the move carries the body: keys of offsets along its facing (forward, to its right, up), linear between. */
	public record Motion(List<Key> keys) {
		public static final Motion NONE = new Motion(List.of());

		public record Key(int tick, double forward, double side, double up) {
		}

		/** The offset reached by a tick, along the facing the move started with. */
		public Vec3 at(final float tick) {
			if (this.keys.isEmpty()) {
				return Vec3.ZERO;
			}
			Key previous = new Key(0, 0, 0, 0);
			for (Key key : this.keys) {
				if (tick <= key.tick) {
					float span = Math.max(1, key.tick - previous.tick);
					double t = Mth.clamp((tick - previous.tick) / span, 0.0F, 1.0F);
					return new Vec3(Mth.lerp(t, previous.forward, key.forward), Mth.lerp(t, previous.up, key.up), Mth.lerp(t, previous.side, key.side));
				}
				previous = key;
			}
			return new Vec3(previous.forward, previous.up, previous.side);
		}
	}

	/** The distance to its target a move is meant for. */
	public record Range(double min, double max) {
		public static final Range ANY = new Range(0.0, 64.0);

		public boolean contains(final double distance) {
			return distance >= this.min && distance <= this.max;
		}
	}

	/** Particles or anything else seen through the windup, besides the clip. */
	@FunctionalInterface
	public interface TellEffect {
		void tick(ServerLevel level, Mob boss, @Nullable LivingEntity target, int tick);
	}

	/** What a move does beyond its fixed strikes and motion, each tick it runs. */
	@FunctionalInterface
	public interface Script {
		void tick(MoveRunner.Context context);
	}

	/** Ticks from the start to the end of the recovery. */
	public int length() {
		return this.windup + this.active + this.recovery;
	}

	/** Whether the brain may choose it (a reaction is only ever suffered). */
	public boolean plannable() {
		return this.kind != Kind.REACTION;
	}

	public boolean in(final String family) {
		return this.families.contains(family);
	}

	public static Builder builder(final String id, final Kind kind) {
		return new Builder(id, kind);
	}

	public static final class Builder {
		private final String id;
		private final Kind kind;
		private Weight weight = Weight.NONE;
		private @Nullable SoundEvent tell;
		private int windup;
		private int active = 1;
		private int recovery;
		private final List<Strike> strikes = new ArrayList<>();
		private Motion motion = Motion.NONE;
		private Range range = Range.ANY;
		private int cooldown;
		private Set<String> families = Set.of();
		private @Nullable String imitates;
		private @Nullable String signals;
		private @Nullable TellEffect tellEffect;
		private @Nullable Script script;

		private Builder(final String id, final Kind kind) {
			this.id = id;
			this.kind = kind;
		}

		public Builder weight(final Weight weight) {
			this.weight = weight;
			return this;
		}

		public Builder tell(final SoundEvent sound, final int windup) {
			this.tell = sound;
			this.windup = windup;
			return this;
		}

		/** A reaction's windup: it is not announced. */
		public Builder unannounced(final int windup) {
			this.windup = windup;
			return this;
		}

		public Builder timing(final int active, final int recovery) {
			this.active = active;
			this.recovery = recovery;
			return this;
		}

		public Builder strike(final int tick, final HitShape shape, final PartRule part, final float damage) {
			this.strikes.add(new Strike(tick, shape, part, damage));
			return this;
		}

		/** One blow carried along from tick {@code from} through {@code to}: whoever it reaches first takes it, once. */
		public Builder along(final int from, final int to, final HitShape shape, final PartRule part, final float damage) {
			int group = 1 + (int) this.strikes.stream().mapToInt(Strike::group).max().orElse(0);
			for (int tick = from; tick <= to; tick++) {
				this.strikes.add(new Strike(tick, shape, part, damage, group));
			}
			return this;
		}

		/** Straight ahead, {@code distance} blocks from tick {@code from} to tick {@code to}. */
		public Builder dash(final int from, final int to, final double distance) {
			this.motion = new Motion(List.of(new Motion.Key(from, 0.0, 0.0, 0.0), new Motion.Key(to, distance, 0.0, 0.0)));
			return this;
		}

		public Builder motion(final Motion motion) {
			this.motion = motion;
			return this;
		}

		public Builder range(final double min, final double max) {
			this.range = new Range(min, max);
			return this;
		}

		public Builder cooldown(final int ticks) {
			this.cooldown = ticks;
			return this;
		}

		/** The first family is the one its tell shows; the order is kept. */
		public Builder families(final String... families) {
			this.families = Collections.unmodifiableSet(new LinkedHashSet<>(List.of(families)));
			return this;
		}

		public Builder imitates(final String move) {
			this.imitates = move;
			return this;
		}

		public Builder signals(final String family) {
			this.signals = family;
			return this;
		}

		public Builder seen(final TellEffect effect) {
			this.tellEffect = effect;
			return this;
		}

		public Builder script(final Script script) {
			this.script = script;
			return this;
		}

		public BossMove build() {
			if (this.kind == Kind.FEINT && (!this.strikes.isEmpty() || this.imitates == null)) {
				throw new IllegalStateException("a feint borrows a tell and lands nothing: " + this.id);
			}
			if (this.kind == Kind.SIGNAL && this.signals == null) {
				throw new IllegalStateException("a signal promises a family: " + this.id);
			}
			return new BossMove(this.id, this.kind, this.weight, this.tell, this.windup, this.active, this.recovery, List.copyOf(this.strikes),
				this.motion, this.range, this.cooldown, this.families, this.imitates, this.signals, this.tellEffect, this.script);
		}
	}
}
