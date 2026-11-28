package life.shizune.mistinn.client.ledger;

import java.util.HashMap;
import java.util.Map;
import life.shizune.mistinn.Mistinn;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * How the story reads for this hunter, as the ledger has it. Any line of the story may have later versions beside
 * it in the language files, a suffix for each stage ({@code .again}, {@code .record}, {@code .wrong}): the furthest
 * one the ledger has reached is shown, else the one before it, else the line as written. Once the wrong has been seen
 * ({@code AFTER}) every line is as written again, except those with an {@code .after} of their own. A story card's
 * picture may have the same, beside it as {@code <id>_<stage>.png}.
 */
public final class Haunting {
	private static final String[] SUFFIX = {"", "again", "record", "wrong", "after"};
	/**
	 * What the ledger marks in the world where the Snuffer was seen to look out. In that world only, a line with a
	 * {@code .witnessed} version of its own reads that instead, whatever the stage or the hunter's leaning.
	 */
	public static final String WITNESSED = "scare.witnessed";
	private static final Map<Identifier, Boolean> PICTURES = new HashMap<>();

	private Haunting() {
	}

	/** Whether the frightening pictures and sounds are shown: only at full content. */
	public static boolean scares() {
		return life.shizune.mistinn.client.ContentLevel.scenes();
	}

	/** The sounds that frighten, which a client with scares off leaves out: the sting, and the thirteenth's scream. */
	private static final java.util.Set<Identifier> FRIGHTENING = java.util.Set.of(Mistinn.id("scare.sting"), Mistinn.id("entity.thirteenth.scream"),
		Mistinn.id("entity.thirteenth.scream_echo"), Mistinn.id("entity.thirteenth.scream_low"));

	/** Whether a sound is one that frightens. */
	public static boolean frightening(final Identifier sound) {
		return FRIGHTENING.contains(sound);
	}

	/** The version of this line the hunter reads now. */
	public static String key(final String base) {
		Ledger ledger = LedgerClient.ledger();
		Ledger.Stage stage = ledger.stage();
		Language language = Language.getInstance();
		// the tillers' way answers how the hunter walked it, from the first time on
		if (base.startsWith("path.mistinn.")) {
			String tone = ledger.kindPath() ? ".kind" : ledger.cruelPath() ? ".cruel" : null;
			return tone != null && language.has(base + tone) ? base + tone : base;
		}
		// where it was seen, the next of them to fall knows it was
		String world = LedgerClient.world();
		if (world != null && ledger.happenedIn(world, WITNESSED) && language.has(base + ".witnessed")) {
			return base + ".witnessed";
		}
		// a hunter who let others live reads the kind version, once they have been down before
		if (stage != Ledger.Stage.FIRST && ledger.kindStory()) {
			if (stage == Ledger.Stage.AFTER && language.has(base + ".after.kind")) {
				return base + ".after.kind";
			}
			if (language.has(base + ".kind")) {
				return base + ".kind";
			}
		}
		if (stage == Ledger.Stage.AFTER) {
			return language.has(base + ".after") ? base + ".after" : base;
		}
		for (int s = stage.ordinal(); s >= 1; s--) {
			String key = base + "." + SUFFIX[s];
			if (language.has(key)) {
				return key;
			}
		}
		return base;
	}

	/** The line as the hunter reads it now: with how many times they have been dropped, and how many have been sent down. */
	public static Component text(final String base) {
		Ledger ledger = LedgerClient.ledger();
		// the fourth: the name the epic was written under, in a world of the epic (3412's number until then)
		return Component.translatable(key(base), ledger.drops(), ledger.sent(), net.minecraft.client.Minecraft.getInstance().getUser().getName(),
			life.shizune.mistinn.client.epic.EpicClient.name());
	}

	/**
	 * The story pictures that are heavy in the first sense CONTRIBUTING gives (the dead, or a wound, seen plainly): at the
	 * lightest content level their cards are words alone.
	 */
	private static final java.util.Set<String> HEAVY = java.util.Set.of("path_stitched");

	/** Whether a story card's picture may be shown at this content level, wherever it is shown (a card, the world map). */
	public static boolean shown(final String card) {
		return !(life.shizune.mistinn.client.ContentLevel.light() && HEAVY.contains(card));
	}

	/**
	 * The picture of a story card as the hunter sees it now, or null for none. The pictures that have gone wrong (a figure
	 * in the fog, someone at the lamp room's window, the fallen standing watching) are a staged turn of the fourth wall:
	 * with scares off the card keeps its own picture, or its kind one. At the lightest level a heavy picture is not shown.
	 */
	public static @org.jspecify.annotations.Nullable Identifier picture(final String card) {
		if (!shown(card)) {
			return null;
		}
		Ledger ledger = LedgerClient.ledger();
		Ledger.Stage stage = scares() ? ledger.stage() : Ledger.Stage.FIRST;
		String tone = card.startsWith("path_") ? (ledger.kindPath() ? "kind" : ledger.cruelPath() && scares() ? "cruel" : null)
			: ledger.stage() != Ledger.Stage.FIRST && ledger.kindStory() ? "kind" : null;
		if (tone != null) {
			Identifier toned = Mistinn.id("textures/gui/story/" + card + "_" + tone + ".png");
			if (exists(toned)) {
				return toned;
			}
		}
		if (stage == Ledger.Stage.AFTER) {
			Identifier after = Mistinn.id("textures/gui/story/" + card + "_after.png");
			return exists(after) ? after : Mistinn.id("textures/gui/story/" + card + ".png");
		}
		for (int s = stage.ordinal(); s >= 1; s--) {
			Identifier changed = Mistinn.id("textures/gui/story/" + card + "_" + SUFFIX[s] + ".png");
			if (exists(changed)) {
				return changed;
			}
		}
		return Mistinn.id("textures/gui/story/" + card + ".png");
	}

	private static boolean exists(final Identifier id) {
		return PICTURES.computeIfAbsent(id, k -> Minecraft.getInstance().getResourceManager().getResource(k).isPresent());
	}
}
