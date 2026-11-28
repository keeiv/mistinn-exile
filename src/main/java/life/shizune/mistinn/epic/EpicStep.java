package life.shizune.mistinn.epic;

/**
 * The epic's road, in order. Each belongs to a part (0 is the opening, 「醒」) and shows as {@code epic.mistinn.step.<id>}
 * with a hint at {@code epic.mistinn.step.<id>.hint}; the part's title is {@code epic.mistinn.part.<n>}.
 */
public enum EpicStep {
	// ── 序篇「醒」 ─────────────────────────────────────────────────
	/** Waking at the Mist Inn: go down to the innkeeper. */
	WAKE("wake", 0),
	/** The fog has thinned: walk north through the fog wall. */
	FOG_WALL("fog_wall", 0),

	// ── 第一篇「鐘樓」 ──────────────────────────────────────────────
	/** Climb the bell tower on the anchor hills and ring it. */
	FIRST_TOWER("first_tower", 1),

	// ── 第二篇「鐘都」 ──────────────────────────────────────────────
	/** Reach the Bell Capital. */
	CAPITAL("capital", 2),
	/** Find the Chronicler in the Great Library. */
	CHRONICLER("chronicler", 2),
	/** Go up to the palace and hear the Regent. */
	REGENT("regent", 2),

	// ── 第三篇「四鐘」 ──────────────────────────────────────────────
	/** Hunt the four great beasts and ring the four great bells, in any order. */
	BELLS("bells", 3),
	/** A rift opens over the capital: go back and hold it. */
	DEFEND("defend", 3),

	// ── 第四篇「錨冠」 ──────────────────────────────────────────────
	/** Ring the capital's great bell. */
	GREAT_BELL("great_bell", 4),
	/** Walk the anchor's chain out to its crown. */
	CROWN("crown", 4),
	/** Bring down the Helm. */
	HELM("helm", 4),

	// ── 終篇「成篇」 ────────────────────────────────────────────────
	/** The Chronicler asks for the epic's name. */
	NAME("name", 5),
	/** The epic is written; the land goes on. */
	ENDED("ended", 5);

	/** How many parts the epic has. */
	public static final int PARTS = 6;

	public final String id;
	public final int part;

	EpicStep(final String id, final int part) {
		this.id = id;
		this.part = part;
	}

	public String key() {
		return "epic.mistinn.step." + this.id;
	}

	public static EpicStep byOrdinal(final int ordinal) {
		EpicStep[] steps = values();
		return ordinal >= 0 && ordinal < steps.length ? steps[ordinal] : WAKE;
	}

	public boolean before(final EpicStep other) {
		return this.ordinal() < other.ordinal();
	}
}
