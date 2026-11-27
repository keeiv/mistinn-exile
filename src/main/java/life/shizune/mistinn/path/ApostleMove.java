package life.shizune.mistinn.path;

import java.util.List;
import life.shizune.mistinn.weapon.WeaponStyle;
import org.jspecify.annotations.Nullable;

/**
 * 3412．鏽錨使徒's moves: each one a whole piece of choreography with its own clip ({@code anim/apostle/<id>}), timed
 * to the tick. A move names the branch it is done with, when its blows land and what they cover, when the body is
 * thrown (forward, to the side, up), from when the facing is set (before that they track the thing; after it, a
 * dodge is a dodge), and when nothing reaches them. What only one move does (the leap's arc, the draw-cut's step
 * through, the swap of arms, the guard) the hunter does in code, at the ticks named here.
 */
public enum ApostleMove {
	// ── Footwork and flourish ───────────────────────────────────────────────
	/** 拔刀: on arriving, the blade drawn and turned twice in the hand, then settled into the stance. */
	DRAW("draw", 32, WeaponStyle.SLASH, 32, List.of(), List.of(), -1, -1),
	/** 換手: the weapon thrown up turning, the next one caught (the arms change at the top of the throw). */
	SWAP("swap", 12, null, 12, List.of(), List.of(), -1, -1),
	/** 甩刀: a sharp flick of the blade down and out, then back to the stance. */
	FLICK("flick", 16, null, 16, List.of(), List.of(), -1, -1),
	/** 側步: a low hop to one side, weapon kept up. */
	STEP_LEFT("step_left", 12, null, 0, List.of(), List.of(new Impulse(2, 0.0, -0.78, 0.18)), -1, -1),
	STEP_RIGHT("step_right", 12, null, 0, List.of(), List.of(new Impulse(2, 0.0, 0.78, 0.18)), -1, -1),
	/** 後撤: a spring back out of reach, the weapon brought round in front. */
	BACKSTEP("backstep", 14, null, 0, List.of(), List.of(new Impulse(2, -0.9, 0.0, 0.28)), -1, -1),
	/** 翻滾: over the shoulder to the side and back, weapon tucked; nothing reaches them through the turn. */
	ROLL_LEFT("roll_left", 16, null, 0, List.of(), List.of(new Impulse(1, -0.45, -0.85, 0.12)), 0, 11),
	ROLL_RIGHT("roll_right", 16, null, 0, List.of(), List.of(new Impulse(1, -0.45, 0.85, 0.12)), 0, 11),
	/** 架: the weapon raised across the line of the blow; a blow from in front is turned aside. */
	GUARD("guard", 16, null, 16, List.of(), List.of(), -1, -1),

	// ── Blows ────────────────────────────────────────────────────────────
	/** 回刺: off the turned blow, straight back in along the line it came from. */
	RIPOSTE("riposte", 14, WeaponStyle.SLASH, 4,
		List.of(new Hit(6, 3.6, 50.0F, 14.0F, 0.6, 0.0)), List.of(new Impulse(4, 0.55, 0.0, 0.0)), -1, -1),
	/** 直刃三連: a rising cut from low on the left, a flat cut back across, a stepping cut down through. */
	BLADE_STRING("blade_string", 38, WeaponStyle.SLASH, 6,
		List.of(new Hit(8, 3.4, 120.0F, 9.0F, 0.2, 0.0), new Hit(16, 3.4, 140.0F, 9.0F, 0.3, 0.0), new Hit(27, 3.6, 100.0F, 15.0F, 0.9, 0.0)),
		List.of(new Impulse(5, 0.42, 0.0, 0.0), new Impulse(13, 0.28, 0.0, 0.0), new Impulse(23, 0.55, 0.0, 0.0)), -1, -1),
	/** 大劍迴旋: the greatsword dragged low behind, then one full turn with it held out flat; it covers all round. */
	SPIN_CLEAVE("spin_cleave", 36, WeaponStyle.CLEAVE, 10,
		List.of(new Hit(16, 4.4, 360.0F, 20.0F, 1.3, 0.0)), List.of(new Impulse(12, 0.3, 0.0, 0.0)), -1, -1),
	/** 大劍空翻落斬: a crouch, a leap that turns over once in the air, the greatsword brought down through on landing. */
	LEAP_CLEAVE("leap_cleave", 42, WeaponStyle.CLEAVE, 9,
		List.of(new Hit(24, 3.2, 110.0F, 24.0F, 1.0, 0.0)), List.of(), -1, -1),
	/** 長槍三段: high, low, then the long one through the middle; the spear turned back into the stance. */
	SPEAR_TRIPLE("spear_triple", 36, WeaponStyle.THRUST, 5,
		List.of(new Hit(7, 4.6, 34.0F, 8.0F, 0.2, 0.0), new Hit(14, 4.6, 34.0F, 8.0F, 0.2, 0.0), new Hit(22, 5.0, 30.0F, 16.0F, 1.0, 0.0)),
		List.of(new Impulse(20, 0.6, 0.0, 0.0)), -1, -1),
	/** 長槍突進: coiled back side-on, then the whole body driven down the spear's length across the gap. */
	SPEAR_LUNGE("spear_lunge", 26, WeaponStyle.THRUST, 8,
		List.of(new Hit(11, 2.8, 70.0F, 18.0F, 0.9, 0.0), new Hit(13, 2.8, 70.0F, 18.0F, 0.9, 0.0)), List.of(new Impulse(9, 1.65, 0.0, 0.1)), -1, -1),
	/** 短刃反手舞: the grip turned over, a forehand, a backhand, a full turn cutting, a stab; the grip turned back. */
	DAGGER_DANCE("dagger_dance", 34, WeaponStyle.FLURRY, 4,
		List.of(new Hit(6, 3.0, 120.0F, 6.0F, 0.1, 0.0), new Hit(11, 3.0, 120.0F, 6.0F, 0.1, 0.0), new Hit(18, 3.0, 360.0F, 7.0F, 0.2, 0.0),
			new Hit(24, 3.2, 60.0F, 10.0F, 0.5, 0.0)),
		List.of(new Impulse(4, 0.3, 0.0, 0.0), new Impulse(22, 0.45, 0.0, 0.0)), -1, -1),
	/** 鐮刀雙旋: wound low, two full turns with the blade low and wide (it draws the thing in), then hooked up through. */
	REAPER_SPIN("reaper_spin", 42, WeaponStyle.REAP, 8,
		List.of(new Hit(13, 4.2, 360.0F, 10.0F, 0.0, 0.6), new Hit(21, 4.2, 360.0F, 12.0F, 0.0, 0.6), new Hit(29, 3.6, 90.0F, 12.0F, 0.5, 0.0)),
		List.of(new Impulse(10, 0.25, 0.0, 0.0)), -1, -1),
	/** 居合: the last one. Low and still, the blade held back at the hip; then through, and past; a long breath; it falls. */
	IAI("iai", 72, WeaponStyle.SLASH, 38, List.of(), List.of(), 38, 56);

	/** A blow: when it lands, how far and how wide it reaches (degrees, about the facing), what it takes, and how it throws. */
	public record Hit(int tick, double reach, float arc, float damage, double knockback, double pull) {
	}

	/** The body thrown at a tick: forward, to the right, and up, in blocks per tick, by the facing. */
	public record Impulse(int tick, double forward, double side, double up) {
	}

	/** The tick of the draw-cut's step through, and of its falling. */
	public static final int IAI_THROUGH = 38;
	public static final int IAI_FALLS = 50;
	/** The tick the leap leaves the ground, and the tick it comes down. */
	public static final int LEAP_OFF = 9;
	public static final int LEAP_DOWN = 22;
	/** The tick the swap's arms change hands. */
	public static final int SWAP_AT = 5;

	public final String id;
	public final int length;
	/** The branch it is done with, or null for whichever is in hand. */
	public final @Nullable WeaponStyle style;
	/** From this tick on the facing is set; before it they turn to follow the thing. */
	public final int lock;
	public final List<Hit> hits;
	public final List<Impulse> impulses;
	/** Nothing reaches them from {@code immuneFrom} to {@code immuneTo} (both -1: never). */
	public final int immuneFrom;
	public final int immuneTo;

	ApostleMove(final String id, final int length, final @Nullable WeaponStyle style, final int lock, final List<Hit> hits, final List<Impulse> impulses,
		final int immuneFrom, final int immuneTo) {
		this.id = id;
		this.length = length;
		this.style = style;
		this.lock = lock;
		this.hits = hits;
		this.impulses = impulses;
		this.immuneFrom = immuneFrom;
		this.immuneTo = immuneTo;
	}

	/** Whether nothing reaches them at this tick of the move. */
	public boolean immune(final int tick) {
		return this.immuneFrom >= 0 && tick >= this.immuneFrom && tick <= this.immuneTo;
	}

	/** Whether this move is a blow (rather than footwork, a flourish or the guard). */
	public boolean blow() {
		return !this.hits.isEmpty() || this == LEAP_CLEAVE || this == IAI;
	}
}
