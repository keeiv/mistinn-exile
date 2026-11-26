package life.shizune.mistinn.client.path;

import life.shizune.mistinn.path.PathService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * The way's end comes to a hunter's screen a few seconds after its title, so the title is seen
 * over the world first (over the real sea, when it is the sea).
 */
public final class PathClient {
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
