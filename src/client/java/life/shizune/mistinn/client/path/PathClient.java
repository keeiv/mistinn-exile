package life.shizune.mistinn.client.path;

import life.shizune.mistinn.path.PathService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.Music;
import org.jspecify.annotations.Nullable;

/**
 * The way's end comes to a hunter's screen a few seconds after its title, so the title is seen
 * over the world first (over the real sea, when it is the sea).
 */
public final class PathClient {
	/** The end's music: cuts in over whatever was playing, and loops while the screen is up. */
	public static final Music END = new Music(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(MistinnSounds.MUSIC_PATH_END), 0, 0, true);
	/** Ticks between the end's title and its screen. */
	public static final int DELAY = 70;

	private static PathService.@Nullable EndPayload waiting;
	/** The end, held back while the sea's card is up. */
	private static PathService.@Nullable EndPayload after;
	private static int wait;

	private PathClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(PathService.EndPayload.TYPE, (payload, context) -> {
			waiting = payload;
			wait = DELAY;
		});
		ClientTickEvents.END_CLIENT_TICK.register(PathClient::tick);
	}

	/** The journal the K key opens: the way's record on a world walking it, the mission journal elsewhere. */
	public static net.minecraft.client.gui.screens.Screen journal(final Minecraft mc) {
		return mc.player != null && mc.player.hasAttached(PathService.VIEW) ? new PathJournalScreen() : new life.shizune.mistinn.client.story.JournalScreen();
	}

	/** The end's music while its screen is up, or null. */
	public static @Nullable Music music() {
		return Minecraft.getInstance().gui.screen() instanceof PathEndScreen ? END : null;
	}

	private static void tick(final Minecraft mc) {
		if (mc.player == null) {
			waiting = null;
			after = null;
			return;
		}
		if (after != null && mc.gui.screen() == null) {
			PathService.EndPayload end = after;
			after = null;
			mc.gui.setScreen(new PathEndScreen(end.sea(), end.fallen(), end.walker()));
			return;
		}
		if (waiting == null) {
			return;
		}
		if (--wait > 0 || mc.gui.screen() != null) {
			return;
		}
		PathService.EndPayload end = waiting;
		waiting = null;
		if (end.sea()) {
			// the sea's picture first, then the names
			after = end;
			// with no one lost, all twelve stand at the water after it
			mc.gui.setScreen(new life.shizune.mistinn.client.story.StoryCardScreen(life.shizune.mistinn.story.StoryScript.PATH_SEA,
				end.fallen().isEmpty() ? life.shizune.mistinn.story.StoryScript.PATH_SEA_ALL : null));
			return;
		}
		mc.gui.setScreen(new PathEndScreen(end.sea(), end.fallen(), end.walker()));
	}
}
