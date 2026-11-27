package life.shizune.mistinn.client.ledger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.loader.api.FabricLoader;
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
	private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("mistinn-client.properties");
	private static final Map<Identifier, Boolean> PICTURES = new HashMap<>();
	private static Boolean scares;

	private Haunting() {
	}

	/** Whether the frightening parts are shown (the client's setting "scares", on unless set off). */
	public static boolean scares() {
		if (scares == null) {
			scares = true;
			if (Files.isRegularFile(CONFIG)) {
				Properties properties = new Properties();
				try (var in = Files.newBufferedReader(CONFIG)) {
					properties.load(in);
					scares = !"false".equalsIgnoreCase(properties.getProperty("scares", "true"));
				} catch (IOException ignored) {
					scares = true;
				}
			}
		}
		return scares;
	}

	/** The version of this line the hunter reads now. */
	public static String key(final String base) {
		Ledger.Stage stage = LedgerClient.ledger().stage();
		Language language = Language.getInstance();
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
		return Component.translatable(key(base), ledger.drops(), ledger.sent());
	}

	/** The picture of a story card as the hunter sees it now. */
	public static Identifier picture(final String card) {
		Ledger.Stage stage = LedgerClient.ledger().stage();
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
