package life.shizune.mistinn.client.team;

import com.mojang.blaze3d.platform.InputConstants;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.team.DownService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The rescuer's side: the downed teammate within reach, F held to bring them back where they lie,
 * H to start or stop dragging them. While someone can be rescued, F does not also swap the hands.
 */
public final class RescueClient {
	private static KeyMapping rescue;
	private static KeyMapping drag;
	/** Whom F is held for now, or -1. */
	private static int holding = -1;

	private RescueClient() {
	}

	public static void register(final KeyMapping.Category category) {
		rescue = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mistinn.rescue", InputConstants.KEY_F, category));
		drag = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mistinn.drag", InputConstants.KEY_H, category));
		ClientTickEvents.START_CLIENT_TICK.register(RescueClient::tick);
	}

	/** The downed teammate nearest within reach, or null. */
	public static @Nullable Player target(final Minecraft mc) {
		LocalPlayer self = mc.player;
		if (self == null || mc.level == null || DownService.isDown(self) || self.isSpectator() || !self.isAlive()) {
			return null;
		}
		Player best = null;
		double bestDistance = DownService.REACH;
		for (Player other : mc.level.players()) {
			if (other != self && DownService.isDown(other)) {
				double distance = self.distanceTo(other);
				if (distance <= bestDistance) {
					best = other;
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	/** The downed teammate this hunter is helping now (in place or dragging), or null. */
	public static @Nullable Player helping(final Minecraft mc) {
		LocalPlayer self = mc.player;
		if (self == null || mc.level == null) {
			return null;
		}
		for (Player other : mc.level.players()) {
			DownService.Down down = DownService.get(other);
			if (down != null && down.rescuer() == self.getId()) {
				return other;
			}
		}
		return null;
	}

	private static void tick(final Minecraft mc) {
		if (mc.player == null || rescue == null) {
			holding = -1;
			return;
		}
		Player target = target(mc);
		if (target != null) {
			// F is the rescue here, not the hand swap
			while (mc.options.keySwapOffhand.consumeClick()) {
				// dropped
			}
		}
		boolean down = rescue.isDown() && mc.gui.screen() == null;
		if (down && holding < 0 && target != null) {
			holding = target.getId();
			ClientPlayNetworking.send(new DownService.RescuePayload(DownService.RescuePayload.HOLD, holding));
		} else if (!down && holding >= 0) {
			// always said, even before the server's answer arrives: a quick tap must not leave a rescue running
			ClientPlayNetworking.send(new DownService.RescuePayload(DownService.RescuePayload.RELEASE, holding));
			holding = -1;
		}
		while (drag.consumeClick()) {
			Player helped = helping(mc);
			Player who = helped != null ? helped : target;
			if (who != null) {
				ClientPlayNetworking.send(new DownService.RescuePayload(DownService.RescuePayload.DRAG, who.getId()));
			}
		}
	}

	/** The keys as the player has bound them, for the HUD's prompt. */
	static KeyMapping rescueKey() {
		return rescue;
	}

	static KeyMapping dragKey() {
		return drag;
	}

	/** Translation key of a rescue line, for the HUD. */
	static String key(final String name) {
		return "hud." + Mistinn.MOD_ID + ".down." + name;
	}
}
