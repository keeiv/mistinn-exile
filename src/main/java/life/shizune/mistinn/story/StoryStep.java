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

	// ── 第二章「鹽之路」 ──────────────────────────────────────────────
	/** Rust Anchor does not answer; at night a bell sounds from the sea, three times. */
	LISTEN("listen", 2),
	/** Follow the road of salt cairns earlier hunters piled toward the bell, each with a tag under its stone. */
	CAIRN_1("cairn_1", 2),
	CAIRN_2("cairn_2", 2),
	CAIRN_3("cairn_3", 2),
	/** Reach the fog-bell station on its reef. */
	BELL_STATION("bell_station", 2),

	// ── 第三章「沉鐘」 ────────────────────────────────────────────────
	/** Rest at the golden bell by its jetty. */
	BELL_REST("bell_rest", 3),
	/** Climb to the belfry. */
	BELFRY("belfry", 3),
	/** Bring down the Sunken Bell. */
	SUNKEN_BELL("sunken_bell", 3),
	/** Ring the great bell, as it said not to. */
	RING("ring", 3),

	// ── 第四章「鹽坊」 ────────────────────────────────────────────────
	/** Far off, the station answered with a pod: find where it came down. */
	POD("pod", 4),
	/** Its radio sends the hunter inland, to the salt works whose salt stopped coming. */
	SALT_WORKS("salt_works", 4),
	/** Rest at the golden bell by its gate. */
	WORKS_BELL("works_bell", 4),
	/** Walk into the walled yard of the wheel. */
	YARD("yard", 4),
	/** Stop the Rust Wheel. */
	RUST_WHEEL("rust_wheel", 4),
	/** The end of chapter 4 before chapter 5 was written: a hunter left here goes on to chapter 5. */
	WAIT("wait", 4),

	// ── 第五章「錨」 ──────────────────────────────────────────────────
	/** At a radio, Rust Anchor asks for the salt; another voice cuts in and asks for it not to go. */
	CALL("call", 5),
	/** Send it up from a radio, or throw it into the Mist Inn's fire: the first done ends the story of this world. */
	CHOOSE("choose", 5),
	/** The story of this world has ended; the world goes on. */
	ENDED("ended", 5);

	/** How many chapters have a title so far. */
	public static final int CHAPTERS = 6;

	/** The salt cairns of the road, in order. */
	public static final java.util.List<StoryStep> CAIRNS = java.util.List.of(CAIRN_1, CAIRN_2, CAIRN_3);

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
