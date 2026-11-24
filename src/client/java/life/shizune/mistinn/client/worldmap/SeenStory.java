package life.shizune.mistinn.client.worldmap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The story cards this computer has shown, in any world, so the world map (opened from the title
 * screen, where no world is loaded) knows how far the story has been walked here. Places past
 * that stay in the fog unless the hunter chooses to see everything. Kept in the config folder.
 */
public final class SeenStory {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mistinn-story-seen.properties");
	private static final Set<String> CARDS = new LinkedHashSet<>();
	private static boolean revealAll;
	private static boolean loaded;

	private SeenStory() {
	}

	/** Whether the card has been shown on this computer. */
	public static boolean seen(final String card) {
		load();
		return CARDS.contains(card);
	}

	/** Remembers that the card has been shown. */
	public static void saw(final String card) {
		load();
		if (CARDS.add(card)) {
			save();
		}
	}

	/** Whether the map shows every place, seen or not. */
	public static boolean revealAll() {
		load();
		return revealAll;
	}

	public static void setRevealAll(final boolean reveal) {
		load();
		revealAll = reveal;
		save();
	}

	/** Reads the record again from the config folder, as when the game starts. */
	public static void reload() {
		loaded = false;
		load();
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		CARDS.clear();
		revealAll = false;
		if (!Files.isRegularFile(FILE)) {
			return;
		}
		Properties properties = new Properties();
		try (var in = Files.newBufferedReader(FILE)) {
			properties.load(in);
		} catch (IOException e) {
			return;
		}
		String cards = properties.getProperty("cards", "");
		Arrays.stream(cards.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(CARDS::add);
		revealAll = Boolean.parseBoolean(properties.getProperty("reveal_all", "false"));
	}

	private static void save() {
		Properties properties = new Properties();
		properties.setProperty("cards", String.join(",", CARDS));
		properties.setProperty("reveal_all", Boolean.toString(revealAll));
		try {
			Files.createDirectories(FILE.getParent());
			try (var out = Files.newBufferedWriter(FILE)) {
				properties.store(out, "Mistinn: Exile story cards seen on this computer, for the world map");
			}
		} catch (IOException ignored) {
			// the record still holds for this session
		}
	}
}
