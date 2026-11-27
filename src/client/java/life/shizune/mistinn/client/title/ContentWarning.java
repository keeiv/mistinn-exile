package life.shizune.mistinn.client.title;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.ClientSettings;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * When the content warning is shown: before the title screen (or a launcher's straight join), the first time this
 * machine runs a version of the mod it has not answered the warning for. A fresh install has answered none; an update
 * has answered an older one. Answering it keeps the version, and the choice is the scares setting.
 */
public final class ContentWarning {
	/** The client game tests run without the warning (their run folder is fresh each time), unless a test asks for it. */
	private static final String TEST_MOD = "mistinn-test";
	private static boolean tested;

	private ContentWarning() {
	}

	/** The version of the mod running now. */
	public static String version() {
		return FabricLoader.getInstance().getModContainer(Mistinn.MOD_ID).map(mod -> mod.getMetadata().getVersion().getFriendlyString())
			.orElse("dev");
	}

	/** Whether a machine that last answered for {@code answered} (null: never) must be warned while running {@code running}. */
	public static boolean due(final @Nullable String answered, final String running) {
		return !running.equals(answered);
	}

	/** Whether the warning comes before the title screen now. */
	public static boolean due() {
		if (FabricLoader.getInstance().isModLoaded(TEST_MOD) && !tested) {
			return false;
		}
		return due(ClientSettings.get(ClientSettings.WARNED_VERSION), version());
	}

	/** The warning answered: the content level chosen, and this version remembered as warned. */
	public static void answer(final life.shizune.mistinn.client.ContentLevel level) {
		life.shizune.mistinn.client.ContentLevel.set(level);
		ClientSettings.put(ClientSettings.WARNED_VERSION, version());
	}

	/** For the client game test: whether the warning may come in a test run. */
	public static void test(final boolean on) {
		tested = on;
	}
}
