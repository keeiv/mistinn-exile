package life.shizune.mistinn.client.ledger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 冊子: what this game remembers of its hunter, kept in the game's own folder and not in any world, so it is
 * carried from one world to the next: every world come into (each one a drop, of its kind), every ending, every
 * fall, every tiller fallen by name, and what has been seen once and is not shown again.
 */
public final class Ledger {
	/** How far the story has gone wrong for this hunter. */
	public enum Stage {
		/** The first drop: nothing is different. */
		FIRST,
		/** Dropped before: a pause, a word. */
		AGAIN,
		/** Dropped often, or the salt sent up: the record begins to show. */
		RECORD,
		/** Many drops and an ending behind them: this time, it is wrong, and something looks back. */
		WRONG,
		/** It has been seen: everything is as it was, almost. */
		AFTER
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Set<String> HUNTER_WORLDS = Set.of("story", "free", "training");

	/** World id to the kind of world it is. */
	private Map<String, String> worlds = new HashMap<>();
	/** World id and event, for what counts once in a world. */
	private Set<String> once = new HashSet<>();
	/** How many times each thing has happened, across every world. */
	private Map<String, Integer> counts = new HashMap<>();
	/** What has been shown once and is not shown again. */
	private Set<String> seen = new HashSet<>();

	private transient Path file;

	/** Where the ledger is kept: the game's folder, unless a test points it elsewhere. */
	public static Path defaultFile() {
		String override = System.getProperty("mistinn.ledger");
		return override != null ? Path.of(override) : FabricLoader.getInstance().getGameDir().resolve("mistinn").resolve("ledger.json");
	}

	/** Reads the ledger at {@code file}, or starts an empty one. */
	public static Ledger load(final Path file) {
		Ledger ledger = null;
		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				ledger = GSON.fromJson(reader, Ledger.class);
			} catch (IOException | RuntimeException e) {
				Mistinn.LOGGER.warn("Could not read {}; starting it again", file, e);
			}
		}
		if (ledger == null) {
			ledger = new Ledger();
		}
		ledger.file = file;
		ledger.worlds = ledger.worlds == null ? new HashMap<>() : new HashMap<>(ledger.worlds);
		ledger.once = ledger.once == null ? new HashSet<>() : new HashSet<>(ledger.once);
		ledger.counts = ledger.counts == null ? new HashMap<>() : new HashMap<>(ledger.counts);
		ledger.seen = ledger.seen == null ? new HashSet<>() : new HashSet<>(ledger.seen);
		return ledger;
	}

	private void save() {
		if (this.file == null) {
			return;
		}
		try {
			Files.createDirectories(this.file.getParent());
			try (Writer writer = Files.newBufferedWriter(this.file, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			Mistinn.LOGGER.warn("Could not write {}", this.file, e);
		}
	}

	/** The hunter came into a world: a drop, the first time. */
	public void world(final String world, final String mode) {
		if (this.worlds.putIfAbsent(world, mode) == null) {
			this.save();
		}
	}

	/** Something happened to the hunter in a world; {@code once}: only its first time there is counted. */
	public void event(final String world, final String event, final boolean once) {
		if (once && !this.once.add(world + ":" + event)) {
			return;
		}
		this.counts.merge(event, 1, Integer::sum);
		this.save();
	}

	/** How many times this has happened. */
	public int count(final String event) {
		return this.counts.getOrDefault(event, 0);
	}

	/** How many times the hunter has been dropped: every story, free and training world come into. */
	public int drops() {
		return (int) this.worlds.values().stream().filter(HUNTER_WORLDS::contains).count();
	}

	/** How many worlds of this kind. */
	public int worlds(final String mode) {
		return (int) this.worlds.values().stream().filter(mode::equals).count();
	}

	/** Endings reached, of both kinds. */
	public int endings() {
		return this.count("ending.anchor") + this.count("ending.inn");
	}

	/** How many have been sent down: every drop, and one more for every time the salt went up. */
	public int sent() {
		return this.drops() + this.count("ending.anchor");
	}

	/** How many worlds this tiller (by place in the line) has fallen in. */
	public int fallen(final int walker) {
		return this.count("path.fallen." + walker);
	}

	public boolean seen(final String what) {
		return this.seen.contains(what);
	}

	/** Remembers that this has been shown. */
	public void see(final String what) {
		if (this.seen.add(what)) {
			this.save();
		}
	}

	/** How far the story has gone wrong. */
	public Stage stage() {
		if (this.seen("scare.snuffer")) {
			return Stage.AFTER;
		}
		int drops = this.drops();
		if (drops >= 4 && this.endings() >= 1) {
			return Stage.WRONG;
		}
		if (drops >= 3 || this.count("ending.anchor") >= 1) {
			return Stage.RECORD;
		}
		return drops >= 2 ? Stage.AGAIN : Stage.FIRST;
	}
}
