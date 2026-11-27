package life.shizune.mistinn.client;

/**
 * How much of the horror the player wants, for the words and the story's pictures only (not what creatures do):
 * <ul>
 * <li>{@link #FULL}: everything, the faces, the pictures gone wrong, the stings and screams, the words as written;</li>
 * <li>{@link #NORMAL}: no frightening pictures or sounds, the frightening words kept;</li>
 * <li>{@link #LIGHT}: the frightening words told gently too (a line's {@code .light} version where it has one), the
 * ledger's turns of the fourth wall kept in their gentle versions.</li>
 * </ul>
 * Every achievement can be earned at every level. Kept in {@link ClientSettings}; a machine that had turned the scares off
 * before there were levels is at {@link #NORMAL}.
 */
public enum ContentLevel {
	FULL("full"), NORMAL("normal"), LIGHT("light");

	/** The settings key. */
	public static final String KEY = "content";
	private static ContentLevel cached;

	public final String id;

	ContentLevel(final String id) {
		this.id = id;
	}

	/** The level this machine is at. */
	public static ContentLevel current() {
		if (cached == null) {
			String set = ClientSettings.get(KEY);
			ContentLevel level = null;
			for (ContentLevel each : values()) {
				if (each.id.equals(set)) {
					level = each;
				}
			}
			cached = level != null ? level : "false".equalsIgnoreCase(ClientSettings.get(ClientSettings.SCARES)) ? NORMAL : FULL;
		}
		return cached;
	}

	/** Sets the level and keeps it (the old scares switch kept in step, for anything still reading it). */
	public static void set(final ContentLevel level) {
		ClientSettings.put(KEY, level.id);
		ClientSettings.set(ClientSettings.SCARES, level == FULL);
		cached = level;
	}

	/** Whether the frightening pictures and sounds are shown. */
	public static boolean scenes() {
		return current() == FULL;
	}

	/** Whether the frightening words are told gently. */
	public static boolean light() {
		return current() == LIGHT;
	}

	/** The translation key of this level's name, and of what it means. */
	public String key() {
		return "settings.mistinn.content." + this.id;
	}

	public String about() {
		return this.key() + ".about";
	}
}
