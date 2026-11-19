package life.shizune.mistinn.story;

import java.util.List;
import java.util.Map;

/**
 * What is said and shown, by id. The words themselves live in the language files: a radio line
 * at {@code story.mistinn.radio.<sequence>.<n>}, a story card's lines at
 * {@code story.mistinn.card.<card>.<n>}, and who speaks at {@code story.mistinn.speaker.<id>}.
 */
public final class StoryScript {
	/** Who is speaking. NARRATION has no name: it describes what happens. */
	public static final String HELM = "helm";
	public static final String SNUFFER = "snuffer";
	public static final String NARRATION = "narration";

	/** One line of a sequence. */
	public record Line(String speaker, String key) {
	}

	/** A full-screen story card: a picture, then its lines one by one; a chapter title above, if any. */
	public record Card(String id, int lines, String title) {
		public String image() {
			return "textures/gui/story/" + this.id + ".png";
		}

		public String line(final int n) {
			return "story.mistinn.card." + this.id + "." + n;
		}
	}

	// ── Story cards ──────────────────────────────────────────────────
	/** The prologue opens: the pod falls into the mist. */
	public static final Card DROP = new Card("drop", 5, "story.mistinn.chapter.0");
	/** Chapter 1 opens on the dead lighthouses of the coast. */
	public static final Card LIGHTHOUSE = new Card("lighthouse", 3, "story.mistinn.chapter.1");
	/** Chapter 1 ends: the salt in the pod, and no answer. */
	public static final Card UNANSWERED = new Card("unanswered", 4, "");

	private static final Map<String, Card> CARDS = Map.of(DROP.id(), DROP, LIGHTHOUSE.id(), LIGHTHOUSE, UNANSWERED.id(), UNANSWERED);

	// ── Sequences ────────────────────────────────────────────────────
	/** Helm's first call over the mended radio. */
	public static final String FIRST_CALL = "first_call";
	/** The Snuffer's last words as it falls. */
	public static final String SNUFFER_END = "snuffer_end";
	/** The salt block goes into the pod; nothing answers. */
	public static final String NO_ANSWER = "no_answer";

	private static final Map<String, List<String>> SPEAKERS = Map.of(
		FIRST_CALL, List.of(HELM, HELM, HELM, HELM, HELM, HELM, HELM, HELM, HELM),
		SNUFFER_END, List.of(SNUFFER, SNUFFER, SNUFFER, SNUFFER),
		NO_ANSWER, List.of(NARRATION, NARRATION, NARRATION)
	);

	private StoryScript() {
	}

	public static List<Line> sequence(final String id) {
		List<String> speakers = SPEAKERS.getOrDefault(id, List.of());
		java.util.ArrayList<Line> lines = new java.util.ArrayList<>();
		for (int i = 0; i < speakers.size(); i++) {
			lines.add(new Line(speakers.get(i), "story.mistinn.radio." + id + "." + i));
		}
		return lines;
	}

	public static Card card(final String id) {
		return CARDS.get(id);
	}

	/** Every sequence, in the order the story reaches them: for the journal. */
	public static List<String> sequences() {
		return List.of(FIRST_CALL, SNUFFER_END, NO_ANSWER);
	}
}
