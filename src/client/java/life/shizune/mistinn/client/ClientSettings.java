package life.shizune.mistinn.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The hunter's own settings on this machine ({@code config/mistinn-client.properties}): read once, and written back
 * whole whenever one changes, so changing one never forgets another.
 */
public final class ClientSettings {
	/** Whether the camera follows the body's motion through a swing. */
	public static final String SWING_CAMERA = "swing_camera";
	/** Whether the frightening parts are shown: the faces, the stings. */
	public static final String SCARES = "scares";

	private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("mistinn-client.properties");
	private static Properties properties;

	private ClientSettings() {
	}

	/** A switch, on unless set off. */
	public static boolean on(final String key) {
		return !"false".equalsIgnoreCase(load().getProperty(key, "true"));
	}

	/** Sets a switch, and keeps it. */
	public static void set(final String key, final boolean on) {
		load().setProperty(key, Boolean.toString(on));
		try {
			Files.createDirectories(CONFIG.getParent());
			try (var out = Files.newBufferedWriter(CONFIG)) {
				properties.store(out, "Mistinn: Exile client settings");
			}
		} catch (IOException ignored) {
			// it still holds for this session
		}
	}

	private static Properties load() {
		if (properties == null) {
			properties = new Properties();
			if (Files.isRegularFile(CONFIG)) {
				try (var in = Files.newBufferedReader(CONFIG)) {
					properties.load(in);
				} catch (IOException ignored) {
					// as if never set
				}
			}
		}
		return properties;
	}
}
