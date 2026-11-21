package life.shizune.mistinn.skill;

/** The four stats a hunter grows. Each starts at zero; every minor skill node adds one. */
public enum Stat {
	/** Melee damage; later also the charged blow and the weight a hunter can wield. */
	STRENGTH("strength"),
	/** Moving, swinging and rolling. */
	SPEED("speed"),
	/** How much each part and the stamina bar hold, and how often wounds open. */
	CONSTITUTION("constitution"),
	/** How quickly and how well treatment and repair work. */
	INTELLECT("intellect");

	/** No stat counts beyond this, however many nodes add to it. */
	public static final int CAP = 20;

	public final String id;

	Stat(final String id) {
		this.id = id;
	}

	/** Translation key of the stat's name. */
	public String key() {
		return "stat.mistinn." + this.id;
	}
}
