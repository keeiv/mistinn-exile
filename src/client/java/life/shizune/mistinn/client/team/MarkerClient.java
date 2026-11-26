package life.shizune.mistinn.client.team;

import java.util.List;
import java.util.Locale;
import life.shizune.mistinn.team.MarkerService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * The team's beacons as the server last sent them, and the way they show in the view: a small
 * diamond in the beacon's colour over the place, its name and how far it is, like a signal seen
 * through the mist.
 */
public final class MarkerClient implements HudElement {
	/** Beacons further than this are left to the minimap. */
	public static final double VIEW_RANGE = 1000.0;
	private static volatile List<MarkerService.Marker> markers = List.of();

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MarkerService.MarkersPayload.TYPE, (payload, context) -> markers = List.copyOf(payload.markers()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> markers = List.of());
	}

	public static List<MarkerService.Marker> markers() {
		return markers;
	}

	/** What the beacon is called on screen: its own name, or "beacon". */
	public static Component label(final MarkerService.Marker marker) {
		return marker.name().isEmpty() ? Component.translatable("hud.mistinn.marker.unnamed") : Component.literal(marker.name());
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.gui.screen() != null || mc.getDebugOverlay().showDebugScreen()
			|| life.shizune.mistinn.client.body.BodyHud.headDestroyed()) {
			return;
		}
		Identifier here = mc.level.dimension().identifier();
		Vec3 eye = mc.gameRenderer.mainCamera().position();
		Vec3 look = mc.player.getViewVector(deltaTracker.getGameTimeDeltaPartialTick(true));
		Font font = mc.font;
		for (MarkerService.Marker marker : markers) {
			if (!marker.dimension().equals(here)) {
				continue;
			}
			Vec3 at = Vec3.atCenterOf(marker.pos()).add(0.0, 0.9, 0.0);
			Vec3 to = at.subtract(eye);
			double distance = to.length();
			// behind the eye, or too far to make out
			if (distance > VIEW_RANGE || distance < 1.0 || to.dot(look) <= 0.0) {
				continue;
			}
			Vec3 screen = mc.gameRenderer.projectPointToScreen(at);
			int x = (int) Math.round((screen.x + 1.0) / 2.0 * g.guiWidth());
			int y = (int) Math.round((1.0 - screen.y) / 2.0 * g.guiHeight());
			if (x < -20 || x > g.guiWidth() + 20 || y < -20 || y > g.guiHeight() + 20) {
				continue;
			}
			diamond(g, x, y, 0xFF000000 | marker.colour());
			Component line = Component.translatable("hud.mistinn.marker.distance", label(marker), String.format(Locale.ROOT, "%d", Math.round(distance)));
			g.text(font, line, x - font.width(line) / 2, y + 6, 0xFF000000 | marker.colour(), true);
		}
	}

	/** A beacon's sign: a small diamond in its colour with a dark rim. */
	public static void diamond(final GuiGraphicsExtractor g, final int x, final int y, final int argb) {
		for (int i = 0; i <= 3; i++) {
			g.fill(x - (3 - i) - 1, y - i - 1, x + (3 - i) + 2, y - i + 1, 0xFF14100A);
			g.fill(x - (3 - i) - 1, y + i - 1, x + (3 - i) + 2, y + i + 1, 0xFF14100A);
		}
		for (int i = 0; i <= 2; i++) {
			g.fill(x - (2 - i), y - i, x + (2 - i) + 1, y - i + 1, argb);
			g.fill(x - (2 - i), y + i, x + (2 - i) + 1, y + i + 1, argb);
		}
	}
}
