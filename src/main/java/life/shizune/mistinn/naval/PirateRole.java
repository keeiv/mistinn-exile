package life.shizune.mistinn.naval;

/** What a pirate does aboard. */
public enum PirateRole {
	/** 刀手: waits on the main deck with a cutlass and falls on whoever comes aboard. */
	BOARDER("boarder"),
	/** 火槍手: shoots from the quarterdeck and the bow; aims for a breath first, then reloads a long while. */
	MARKSMAN("marksman"),
	/** 炮手: works the guns; while one lives the guns fire. */
	GUNNER("gunner"),
	/** 船長: the ship's master, by the wheel; a pistol for those far off, a sabre for those near. */
	CAPTAIN("captain");

	public final String id;

	PirateRole(final String id) {
		this.id = id;
	}

	public static PirateRole byId(final String id) {
		for (PirateRole role : values()) {
			if (role.id.equals(id)) {
				return role;
			}
		}
		return BOARDER;
	}

	public static PirateRole byIndex(final int index) {
		PirateRole[] values = values();
		return values[Math.floorMod(index, values.length)];
	}

	/** Whether this one fires a gun: aims, shoots, reloads. */
	public boolean shoots() {
		return this == MARKSMAN || this == CAPTAIN;
	}
}
