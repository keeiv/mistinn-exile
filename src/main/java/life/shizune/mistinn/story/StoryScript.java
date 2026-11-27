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
	public static final String SUNKEN_BELL = "sunken_bell";
	public static final String RUST_WHEEL = "rust_wheel";
	public static final String NARRATION = "narration";
	/** The hunters whose tags lie under the cairns of the salt road, by their numbers. */
	public static final String TAG_3388 = "tag_3388";
	public static final String TAG_3401 = "tag_3401";
	public static final String TAG_3409 = "tag_3409";

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
	/** Chapter 2 opens after the bell at night: a road of salt cairns toward the sea. */
	public static final Card SALT_ROAD = new Card("salt_road", 3, "story.mistinn.chapter.2");
	/** Chapter 2 ends at the last cairn, by the sea: the road stops, the tower ahead, three tags in the pocket. */
	public static final Card ROAD_END = new Card("road_end", 3, "");
	/** Chapter 3 opens at the fog-bell station. */
	public static final Card BELL_STATION = new Card("bell_station", 3, "story.mistinn.chapter.3");
	/** Chapter 3 ends: the great bell rung, and the station answering from beyond the fog. */
	public static final Card ANSWER = new Card("answer", 5, "");
	/** Chapter 4 opens after the pod's call: the salt works inland, and the wheel. */
	public static final Card SALT_WORKS = new Card("salt_works", 3, "story.mistinn.chapter.4");
	/** Chapter 4 ends: the wheel stopped, and far off the anchor's chain. */
	public static final Card STOPPED = new Card("stopped", 4, "");
	/** Chapter 5 opens after the call: the salt in hand, the station waiting, and one warm light in the fog. */
	public static final Card THE_CALL = new Card("the_call", 3, "story.mistinn.chapter.5");
	/** The ending The Anchor: the salt went up, and another pod is going down. */
	public static final Card ANCHOR = new Card("anchor", 4, "");
	/** The ending Mist Inn: no pod comes down again, and the inn's lamp stays lit. */
	public static final Card INN = new Card("inn", 3, "");
	/** After the inn: the soup, the wall of tags, the name, the morning. */
	public static final Card INN_HEARTH = new Card("inn_hearth", 5, "");
	public static final Card INN_WALL = new Card("inn_wall", 5, "");
	public static final Card INN_NAME = new Card("inn_name", 5, "");
	public static final Card INN_DAWN = new Card("inn_dawn", 6, "");
	/** The tillers' way, walked with no one lost: all twelve at the sea. */
	public static final Card PATH_SEA_ALL = new Card("path_sea_all", 4, "");
	/** 十三人斬: the twelve, stitched. */
	public static final Card PATH_STITCHED = new Card("path_stitched", 4, "");
	/** 十三人斬: the hunter going home. */
	public static final Card THIRTEEN_END = new Card("thirteen_end", 4, "");
	/** The hunter as they are when no one moves them. */
	public static final String APOSTLE = "apostle";
	/** The lamp room, lit, with no one in it. */
	public static final Card LAMP_LIT = new Card("lamp_lit", 1, "");
	/** The path of longing: a tiller at the field's edge, looking at the fog. */
	public static final Card PATH_FIELDS = new Card("path_fields", 3, "path.mistinn.chapter.0");
	/** The path of longing: the ancestors' tablet in the broken tower. */
	public static final Card PATH_RECORD = new Card("path_record", 4, "path.mistinn.chapter.2");
	/** The path of longing: the sea, at last. */
	public static final Card PATH_SEA = new Card("path_sea", 3, "path.mistinn.chapter.5");

	private static final Map<String, Card> CARDS = Map.ofEntries(Map.entry(DROP.id(), DROP), Map.entry(LIGHTHOUSE.id(), LIGHTHOUSE),
		Map.entry(UNANSWERED.id(), UNANSWERED), Map.entry(SALT_ROAD.id(), SALT_ROAD), Map.entry(ROAD_END.id(), ROAD_END),
		Map.entry(BELL_STATION.id(), BELL_STATION),
		Map.entry(ANSWER.id(), ANSWER), Map.entry(SALT_WORKS.id(), SALT_WORKS), Map.entry(STOPPED.id(), STOPPED), Map.entry(THE_CALL.id(), THE_CALL),
		Map.entry(ANCHOR.id(), ANCHOR), Map.entry(INN.id(), INN), Map.entry(PATH_FIELDS.id(), PATH_FIELDS), Map.entry(PATH_RECORD.id(), PATH_RECORD),
		Map.entry(PATH_SEA.id(), PATH_SEA), Map.entry(LAMP_LIT.id(), LAMP_LIT),
		Map.entry(PATH_STITCHED.id(), PATH_STITCHED), Map.entry(THIRTEEN_END.id(), THIRTEEN_END),
		Map.entry(INN_HEARTH.id(), INN_HEARTH), Map.entry(INN_WALL.id(), INN_WALL), Map.entry(INN_NAME.id(), INN_NAME),
		Map.entry(INN_DAWN.id(), INN_DAWN), Map.entry(PATH_SEA_ALL.id(), PATH_SEA_ALL));
	/** The cards that come on after a card, one after another: the inn's ending goes on into the morning. */
	private static final Map<String, Card> FOLLOWS = Map.of(INN.id(), INN_HEARTH, INN_HEARTH.id(), INN_WALL, INN_WALL.id(), INN_NAME,
		INN_NAME.id(), INN_DAWN);

	// ── Sequences ────────────────────────────────────────────────────
	/** Helm's first call over the mended radio. */
	public static final String FIRST_CALL = "first_call";
	/** The Snuffer's last words as it falls. */
	public static final String SNUFFER_END = "snuffer_end";
	/** The salt block goes into the pod; nothing answers. */
	public static final String NO_ANSWER = "no_answer";
	/** At night a bell sounds from the sea, three times, as on the day of the drop. */
	public static final String NIGHT_TOLL = "night_toll";
	/** What the hunters before wrote on the tags under the cairns, one sequence a cairn. */
	public static final String TAG_1 = "tag_1";
	public static final String TAG_2 = "tag_2";
	public static final String TAG_3 = "tag_3";
	/** The Sunken Bell's last words as it falls. */
	public static final String SUNKEN_BELL_END = "sunken_bell_end";
	/** The pod that answered the bell: empty, and its radio calling. */
	public static final String SUPPLY_CALL = "supply_call";
	/** What the man at the wheel says when it stops. */
	public static final String RUST_WHEEL_END = "rust_wheel_end";
	/** Chapter 5 at a radio: Helm asks for the salt, and tag 3401 cuts in. */
	public static final String STATION_CALL = "station_call";
	/** The salt sent up: Helm's thanks. */
	public static final String ANCHOR_END = "anchor_end";
	/** The salt thrown into the Mist Inn's fire, and that night. */
	public static final String INN_END = "inn_end";

	/** The tag sequences, in the order of the cairns they lie under. */
	public static final List<String> TAGS = List.of(TAG_1, TAG_2, TAG_3);
	/** The numbers on those tags, in the same order. */
	public static final List<Integer> TAG_NUMBERS = List.of(3388, 3401, 3409);

	private static final Map<String, List<String>> SPEAKERS = Map.ofEntries(
		Map.entry(FIRST_CALL, List.of(HELM, HELM, HELM, HELM, HELM, HELM, HELM, HELM, HELM)),
		Map.entry(SNUFFER_END, List.of(SNUFFER, SNUFFER, SNUFFER, SNUFFER)),
		Map.entry(NO_ANSWER, List.of(NARRATION, NARRATION, NARRATION)),
		Map.entry(NIGHT_TOLL, List.of(NARRATION, NARRATION, NARRATION, APOSTLE)),
		Map.entry(TAG_1, List.of(TAG_3388)),
		Map.entry(TAG_2, List.of(TAG_3401, TAG_3401)),
		Map.entry(TAG_3, List.of(TAG_3409, TAG_3409, TAG_3409)),
		Map.entry(SUNKEN_BELL_END, List.of(SUNKEN_BELL, SUNKEN_BELL, SUNKEN_BELL, SUNKEN_BELL, SUNKEN_BELL, SUNKEN_BELL)),
		Map.entry(SUPPLY_CALL, List.of(NARRATION, NARRATION, HELM, HELM, HELM, HELM, HELM, HELM, HELM)),
		Map.entry(RUST_WHEEL_END, List.of(RUST_WHEEL, RUST_WHEEL, RUST_WHEEL, RUST_WHEEL, RUST_WHEEL, RUST_WHEEL, RUST_WHEEL, APOSTLE)),
		Map.entry(STATION_CALL, List.of(HELM, HELM, HELM, NARRATION, TAG_3401, TAG_3401, TAG_3401, TAG_3401, HELM, APOSTLE)),
		Map.entry(ANCHOR_END, List.of(NARRATION, HELM, HELM, HELM, APOSTLE, NARRATION)),
		Map.entry(INN_END, List.of(NARRATION, APOSTLE, TAG_3401, NARRATION, NARRATION))
	);

	private StoryScript() {
	}

	/** The card that comes on after this one, or null. */
	public static @org.jspecify.annotations.Nullable Card follows(final String id) {
		return FOLLOWS.get(id);
	}

	public static List<Line> sequence(final String id) {
		// the hunter No. 3412, talking to themself: two lines on arriving, one at a time after
		List<String> speakers = id.startsWith("apostle_") ? (id.equals("apostle_intro") ? List.of(APOSTLE, APOSTLE) : List.of(APOSTLE))
			: SPEAKERS.getOrDefault(id, List.of());
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
		return List.of(FIRST_CALL, SNUFFER_END, NO_ANSWER, NIGHT_TOLL, TAG_1, TAG_2, TAG_3, SUNKEN_BELL_END, SUPPLY_CALL, RUST_WHEEL_END, STATION_CALL,
			ANCHOR_END, INN_END);
	}
}
