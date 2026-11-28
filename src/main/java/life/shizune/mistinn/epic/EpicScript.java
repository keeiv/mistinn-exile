package life.shizune.mistinn.epic;

import java.util.List;
import life.shizune.mistinn.story.StoryScript;
import life.shizune.mistinn.udasru.UdasruPlaces;

/**
 * What the epic shows and says, by id, told through the story's own cards and lines: a card's lines at
 * {@code story.mistinn.card.<card>.<n>}, a sequence's at {@code story.mistinn.radio.<sequence>.<n>}.
 */
public final class EpicScript {
	/** Who speaks, besides the story's own: the inn, the tower keepers, the Chronicler and the Regent. */
	public static final String INNKEEPER = "innkeeper";
	public static final String TOWER_KEEPER = "tower_keeper";
	public static final String CHRONICLER = "chronicler";
	public static final String REGENT = "regent";
	private static final String APOSTLE = "apostle";
	private static final String HELM = StoryScript.HELM;
	private static final String NARRATION = StoryScript.NARRATION;

	// ── Cards ─────────────────────────────────────────────────────
	/** The opening: waking at the Mist Inn, the hand on them again. */
	public static final StoryScript.Card WAKE = new StoryScript.Card("epic_wake", 4, "epic.mistinn.part.0");
	/** Out of the fog: the land. */
	public static final StoryScript.Card LAND = new StoryScript.Card("epic_land", 4, "epic.mistinn.part.1");
	/** The Bell Capital. */
	public static final StoryScript.Card CAPITAL = new StoryScript.Card("epic_capital", 3, "epic.mistinn.part.2");
	/** The four great bells ring together. */
	public static final StoryScript.Card BELLS_ALL = new StoryScript.Card("epic_bells", 4, "epic.mistinn.part.4");
	/** The capital's bell rung: the tide goes out, and the chain lies bare. */
	public static final StoryScript.Card CROWN = new StoryScript.Card("epic_crown", 3, "");
	/** The Helm on the crown. */
	public static final StoryScript.Card HELM_CARD = new StoryScript.Card("epic_helm", 3, "");
	/** The Chronicler's book, waiting for a name. */
	public static final StoryScript.Card NAME_CARD = new StoryScript.Card("epic_name", 3, "epic.mistinn.part.5");
	/** Home, at dusk. */
	public static final StoryScript.Card HOME = new StoryScript.Card("epic_home", 5, "");

	// ── Sequences ─────────────────────────────────────────────────
	/** 3412 notices the hand on them again: plainly, warmly (a kind hand elsewhere), or warily (a cruel one). */
	public static final String HAND = "epic_hand";
	public static final String HAND_KIND = "epic_hand_kind";
	public static final String HAND_CRUEL = "epic_hand_cruel";
	/** The first bell tower rung: its keeper tells of Udasru and of the forbidden zone. */
	public static final String FIRST_TOWER = "epic_first_tower";
	/** The rift tears beside the capital; and is held. */
	public static final String RIFT = "epic_rift";
	public static final String RIFT_HELD = "epic_rift_held";
	/** The capital's great bell rung. */
	public static final String GREAT_BELL_RUNG = "epic_great_bell";
	/** The Helm rises on the crown; and falls. */
	public static final String HELM_RISES = "epic_helm_rises";
	public static final String HELM_DOWN = "epic_helm_down";
	/** The name given: 3412 asks the hand to let go. */
	public static final String LET_GO = "epic_let_go";

	private EpicScript() {
	}

	/** What is said as a great bell's beast falls. */
	public static String beastDown(final UdasruPlaces.GreatBell bell) {
		return "epic_beast_" + bell.id;
	}

	/** What is said as a great bell rings out. */
	public static String bellRung(final UdasruPlaces.GreatBell bell) {
		return "epic_bell_" + bell.id;
	}

	/** Puts the epic's cards and sequences beside the story's. */
	public static void register() {
		for (StoryScript.Card card : List.of(WAKE, LAND, CAPITAL, BELLS_ALL, CROWN, HELM_CARD, NAME_CARD, HOME)) {
			StoryScript.add(card);
		}
		StoryScript.add(HAND, List.of(NARRATION, APOSTLE, APOSTLE, APOSTLE));
		StoryScript.add(HAND_KIND, List.of(NARRATION, APOSTLE, APOSTLE, APOSTLE));
		StoryScript.add(HAND_CRUEL, List.of(NARRATION, APOSTLE, APOSTLE, APOSTLE));
		StoryScript.add(FIRST_TOWER, List.of(TOWER_KEEPER, TOWER_KEEPER, TOWER_KEEPER, TOWER_KEEPER, TOWER_KEEPER, APOSTLE));
		StoryScript.add(RIFT, List.of(NARRATION, REGENT, APOSTLE));
		StoryScript.add(RIFT_HELD, List.of(REGENT, APOSTLE));
		for (UdasruPlaces.GreatBell bell : UdasruPlaces.GreatBell.values()) {
			StoryScript.add(beastDown(bell), List.of(NARRATION, APOSTLE));
			StoryScript.add(bellRung(bell), List.of(NARRATION, APOSTLE));
		}
		StoryScript.add(GREAT_BELL_RUNG, List.of(NARRATION, NARRATION, CHRONICLER, APOSTLE));
		StoryScript.add(HELM_RISES, List.of(HELM, HELM, HELM, APOSTLE, HELM, APOSTLE));
		StoryScript.add(HELM_DOWN, List.of(HELM, HELM, APOSTLE));
		StoryScript.add(LET_GO, List.of(CHRONICLER, APOSTLE, APOSTLE, APOSTLE));
	}
}
