package life.shizune.mistinn.enemy;

import java.util.List;
import life.shizune.mistinn.body.BodyPart;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The finishing moves creatures use on a hunter whose legs are gone. The server times each move from
 * this table; the two animations of a move ({@code anim/execution/<id>/striker|victim}) are keyed to the
 * same ticks. All ticks count from the seizing contact.
 */
public enum ExecutionMove {
	GRAPPLE_BITE("grapple_bite", 70, 58, 0.40F, false,
		beat(14, BodyPart.RIGHT_ARM, true, false, Hit.SNAP, -0.08F, 0.86F), beat(34, BodyPart.HEAD, false, true, Hit.BITE, -0.45F, 0.85F),
		beat(48, BodyPart.HEAD, false, true, Hit.TEAR, -0.36F, 0.96F)),
	NECK_POUNCE("neck_pounce", 60, 48, 0.55F, false,
		beat(16, BodyPart.HEAD, false, true, Hit.BITE, -0.80F, 0.30F), beat(28, BodyPart.TORSO, false, true, Hit.BITE, -0.31F, 0.30F),
		beat(40, BodyPart.HEAD, false, true, Hit.TEAR, -0.80F, 0.30F)),
	LEAP_SLAM("leap_slam", 50, 40, 0.08F, false,
		beat(22, BodyPart.TORSO, true, false, Hit.SLAM, -0.72F, 0.30F)),
	FIST_SLAM("fist_slam", 70, 56, 0.90F, false,
		beat(20, BodyPart.TORSO, true, false, Hit.SLAM, -0.20F, 0.30F), beat(36, BodyPart.LEFT_ARM, true, false, Hit.SNAP, -0.43F, 0.30F),
		beat(46, BodyPart.RIGHT_ARM, true, false, Hit.SNAP, -0.43F, 0.30F)),
	CHARGE_MAUL("charge_maul", 60, 46, 1.00F, false,
		beat(20, BodyPart.TORSO, false, true, Hit.TEAR, -0.55F, 0.30F)),
	STOMP_STAB("stomp_stab", 60, 40, 0.28F, false,
		beat(24, BodyPart.TORSO, false, true, Hit.STAB, -0.50F, 0.30F)),
	SLASH("slash", 56, 42, 0.85F, false,
		beat(20, BodyPart.LEFT_ARM, false, true, Hit.STAB, 0.16F, 0.88F), beat(30, BodyPart.RIGHT_ARM, false, true, Hit.STAB, 0.15F, 0.87F)),
	NECK_TWIST("neck_twist", 50, 36, 1.50F, false,
		beat(12, BodyPart.LEFT_ARM, true, false, Hit.SNAP, 0.25F, 1.38F), beat(12, BodyPart.RIGHT_ARM, true, false, Hit.SNAP, 0.25F, 1.38F)),
	/**
	 * Aimed from where the archer stands. For this ranged move {@code kill} is the release; the arrow's impact is
	 * the fatal blow, and the hunter's animation picks up from the release tick at that moment.
	 */
	HEADSHOT("headshot", 40, 30, 0.0F, true);

	/** Kinds whose young are far smaller than the grown creature their move is drawn for. */
	private static final java.util.Set<String> SMALL = java.util.Set.of("zombie", "zombie_villager", "husk", "drowned", "zombified_piglin", "hoglin", "zoglin", "piglin");

	/** The sound and blood a contact makes. */
	public enum Hit { BITE, SNAP, STAB, SLAM, TEAR }

	/**
	 * One contact during a move: the wound it leaves on a part, if any, how it sounds, and where the animation has
	 * the wounded part at that tick ({@code along} the hunter's facing and {@code up} from their feet, in blocks).
	 */
	public record Beat(int tick, @Nullable BodyPart part, boolean fracture, boolean bleed, Hit sound, float along, float up) {
	}

	private final String id;
	private final int length;
	private final int kill;
	private final float gap;
	private final boolean ranged;
	private final List<Beat> beats;

	ExecutionMove(final String id, final int length, final int kill, final float gap, final boolean ranged, final Beat... beats) {
		this.id = id;
		this.length = length;
		this.kill = kill;
		this.gap = gap;
		this.ranged = ranged;
		this.beats = List.of(beats);
	}

	private static Beat beat(final int tick, final BodyPart part, final boolean fracture, final boolean bleed, final Hit sound, final float along, final float up) {
		return new Beat(tick, part, fracture, bleed, sound, along, up);
	}

	public String id() {
		return this.id;
	}

	/** Ticks from the seizing contact to the end of the move. */
	public int length() {
		return this.length;
	}

	/** The tick of the fatal blow; for a ranged move, the tick the shot is released. */
	public int kill() {
		return this.kill;
	}

	public boolean ranged() {
		return this.ranged;
	}

	public List<Beat> beats() {
		return this.beats;
	}

	/** Where the striker stands from the hunter's feet, in blocks: both bodies' half widths plus the move's gap. */
	public float spacing(final Mob mob) {
		return 0.3F + mob.getBbWidth() / 2.0F + this.gap;
	}

	public static @Nullable ExecutionMove of(final Mob mob) {
		Identifier id = EntityType.getKey(mob.getType());
		if (!id.getNamespace().equals("minecraft")) {
			return null;
		}
		if (mob.isBaby() && SMALL.contains(id.getPath())) {
			// a young one is too short for its kind's hold: it goes for the throat like the other small things
			return NECK_POUNCE;
		}
		return switch (id.getPath()) {
			case "zombie", "zombie_villager", "giant", "husk", "drowned", "zombified_piglin" -> GRAPPLE_BITE;
			case "spider", "cave_spider", "wolf", "silverfish", "endermite", "phantom" -> NECK_POUNCE;
			case "slime", "magma_cube", "sulfur_cube" -> LEAP_SLAM;
			case "warden", "creaking" -> FIST_SLAM;
			case "ravager", "hoglin", "zoglin", "polar_bear" -> CHARGE_MAUL;
			case "vindicator", "piglin", "piglin_brute", "vex" -> STOMP_STAB;
			case "wither_skeleton" -> SLASH;
			case "skeleton", "stray", "bogged", "parched" -> mob.isHolding(Items.BOW) ? HEADSHOT : SLASH;
			case "enderman" -> NECK_TWIST;
			default -> null;
		};
	}

	public static ExecutionMove byOrdinal(final int ordinal) {
		ExecutionMove[] moves = values();
		return moves[Math.floorMod(ordinal, moves.length)];
	}
}
