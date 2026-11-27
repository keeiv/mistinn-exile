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

	/** The end's music while its screen is up, or null. */
	public static @Nullable Music music() {
		return Minecraft.getInstance().gui.screen() instanceof PathEndScreen ? END : null;
	}

	private static void tick(final Minecraft mc) {
		if (waiting == null) {
			return;
		}
		if (mc.player == null) {
			waiting = null;
			return;
		}
		if (--wait > 0 || mc.gui.screen() != null) {
			return;
		}
		PathService.EndPayload end = waiting;
		waiting = null;
		mc.gui.setScreen(new PathEndScreen(end.sea(), end.fallen(), end.walker()));
	}
}
