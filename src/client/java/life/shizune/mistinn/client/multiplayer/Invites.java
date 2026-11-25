package life.shizune.mistinn.client.multiplayer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.room.Invite;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jspecify.annotations.Nullable;

/**
 * The invites this player has joined with, by the address they lead to, so the login can answer
 * the room's question and a reconnect still works. Kept in {@code config/mistinn-invites.properties}
 * (the last ten). The launcher hands one over at start with {@code -Dmistinn.invite=<code>}.
 */
public final class Invites {
	private static final int KEEP = 10;
	private static final Map<String, Invite> BY_ADDRESS = new LinkedHashMap<>();
	private static boolean loaded;

	private Invites() {
	}

	/** This copy of the mod's version, which an invite must match. */
	public static String modVersion() {
		return FabricLoader.getInstance().getModContainer(Mistinn.MOD_ID)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?");
	}

	/** Remembers an invite (and saves the list). */
	public static synchronized void remember(final Invite invite) {
		load();
		BY_ADDRESS.remove(invite.address());
		BY_ADDRESS.put(invite.address(), invite);
		while (BY_ADDRESS.size() > KEEP) {
			BY_ADDRESS.remove(BY_ADDRESS.keySet().iterator().next());
		}
		save();
	}

	/** The invite for the server at {@code ip} as typed or stored ({@code host}, {@code host:port}). */
	public static synchronized @Nullable Invite forAddress(final String ip) {
		load();
		if (!ServerAddress.isValidAddress(ip)) {
			return null;
		}
		ServerAddress address = ServerAddress.parseString(ip);
		return BY_ADDRESS.get(Invite.address(address.getHost(), address.getPort()));
	}

	/** Forgets every invite (the tests start clean with it). */
	public static synchronized void clear() {
		load();
		BY_ADDRESS.clear();
		save();
	}

	/** Reads the saved list and the launcher's invite, once. */
	public static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path file = file();
		if (Files.isRegularFile(file)) {
			Properties saved = new Properties();
			try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				saved.load(in);
			} catch (IOException unreadable) {
				Mistinn.LOGGER.warn("Could not read {}", file, unreadable);
			}
			for (String name : saved.stringPropertyNames()) {
				Invite invite = Invite.parse(saved.getProperty(name));
				if (invite != null) {
					BY_ADDRESS.put(invite.address(), invite);
				}
			}
		}
		String fromLauncher = System.getProperty("mistinn.invite");
		if (fromLauncher != null) {
			Invite invite = Invite.parse(fromLauncher);
			if (invite != null) {
				BY_ADDRESS.remove(invite.address());
				BY_ADDRESS.put(invite.address(), invite);
				save();
			} else {
				Mistinn.LOGGER.warn("The launcher's invite could not be read");
			}
		}
	}

	private static void save() {
		Properties out = new Properties();
		int i = 0;
		for (Invite invite : BY_ADDRESS.values()) {
			out.setProperty("invite." + i++, invite.code());
		}
		try {
			Files.createDirectories(file().getParent());
			try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
				out.store(writer, "Invites this player joined with");
			}
		} catch (IOException unwritable) {
			Mistinn.LOGGER.warn("Could not save invites", unwritable);
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("mistinn-invites.properties");
	}
}
