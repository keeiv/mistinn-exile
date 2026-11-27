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

	/** Whether the frightening parts are shown (the client's setting "scares", on unless set off). */
	public static boolean scares() {
		return life.shizune.mistinn.client.ClientSettings.on(life.shizune.mistinn.client.ClientSettings.SCARES);
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
		return Component.translatable(key(base), ledger.drops(), ledger.sent(), net.minecraft.client.Minecraft.getInstance().getUser().getName());
	}

	/** The picture of a story card as the hunter sees it now. */
	public static Identifier picture(final String card) {
		Ledger ledger = LedgerClient.ledger();
		Ledger.Stage stage = ledger.stage();
		String tone = card.startsWith("path_") ? (ledger.kindPath() ? "kind" : ledger.cruelPath() ? "cruel" : null)
			: stage != Ledger.Stage.FIRST && ledger.kindStory() ? "kind" : null;
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
