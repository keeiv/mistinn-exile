package life.shizune.mistinn.body;

/** The six parts a hunter's health is split into. The face is a zone of the head, not a part. */
public enum BodyPart {
	HEAD("head"),
	TORSO("torso"),
	LEFT_ARM("left_arm"),
	RIGHT_ARM("right_arm"),
	LEFT_LEG("left_leg"),
	RIGHT_LEG("right_leg");

	/** Health of every part when whole, before constitution raises it (see {@code SkillService.partCeiling}). */
	public static final float MAX = 80.0F;
	/** No part ever holds more than this, whatever the hunter's constitution. */
	public static final float CEILING = 120.0F;
	/** Parts at zero that kill. */
	public static final int LETHAL_DESTROYED = 3;

	public final String id;

	BodyPart(final String id) {
		this.id = id;
	}

	public boolean isArm() {
		return this == LEFT_ARM || this == RIGHT_ARM;
	}

	public boolean isLeg() {
		return this == LEFT_LEG || this == RIGHT_LEG;
	}
}
