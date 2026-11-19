package life.shizune.mistinn.story;

/**
 * The story's objectives, in order. Each belongs to a chapter (0 is the prologue) and shows as
 * {@code story.mistinn.step.<id>} with a hint at {@code story.mistinn.step.<id>.hint}.
 */
public enum StoryStep {
	// ── 序章「投放」 ──────────────────────────────────────────────────
	/** Gather the first salt: kill something, or use salt picked up in the ruins. */
	SALT("salt", 0),
	/** Find the real drop station the guide points to. */
	STATION("station", 0),
	/** Mend its radio with redstone; Helm's first call comes through. */
	RADIO("radio", 0),

	// ── 第一章「熄燈」 ────────────────────────────────────────────────
	/** Reach the fog-shore lighthouse. */
	LIGHTHOUSE("lighthouse", 1),
	/** Rest at the golden bell by its door. */
	BELL("bell", 1),
	/** Climb to the lamp room. */
	LAMP_ROOM("lamp_room", 1),
	/** Bring down the Snuffer. */
	SNUFFER("snuffer", 1),
	/** Hand the Rust God's salt block in at the drop station. */
	DELIVER("deliver", 1),
	/** The end of what is written so far: Rust Anchor does not answer. */
	WAIT("wait", 1);

	/** How many chapters have a title so far. */
	public static final int CHAPTERS = 2;

	/** Salt the first objective asks for. */
	public static final int SALT_GOAL = 10;

	/** Redstone the broken radio needs. */
	public static final int RADIO_PARTS = 4;

	public final String id;
	public final int chapter;

	StoryStep(final String id, final int chapter) {
		this.id = id;
		this.chapter = chapter;
	}

	public static StoryStep byId(final String id) {
		for (StoryStep step : values()) {
			if (step.id.equals(id)) {
				return step;
			}
		}
		return SALT;
	}

	public StoryStep next() {
		return this.ordinal() + 1 < values().length ? values()[this.ordinal() + 1] : this;
	}

	public String key() {
		return "story.mistinn.step." + this.id;
	}
}
