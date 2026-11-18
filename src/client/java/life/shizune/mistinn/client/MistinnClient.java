package life.shizune.mistinn.client;

import com.mojang.blaze3d.platform.InputConstants;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.map.MinimapHud;
import life.shizune.mistinn.client.map.MinimapTexture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

public class MistinnClient implements ClientModInitializer {
	/** Blocks per minimap pixel, cycled with the zoom key. */
	private static final int[] SCALES = {1, 2, 4};
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Mistinn.id("keys"));

	private static int scaleIndex = 1;
	private static int refreshTimer;

	@Override
	public void onInitializeClient() {
		KeyMapping zoom = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.mistinn.minimap_zoom", InputConstants.KEY_M, CATEGORY)
		);

		HudElementRegistry.addLast(Mistinn.id("minimap"), new MinimapHud());

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (zoom.consumeClick()) {
				scaleIndex = (scaleIndex + 1) % SCALES.length;
				refreshTimer = 0;
				if (minecraft.player != null) {
					minecraft.player.sendOverlayMessage(Component.translatable("message.mistinn.minimap_zoom", SCALES[scaleIndex]));
				}
			}
			if (minecraft.level == null || minecraft.player == null) {
				return;
			}
			if (--refreshTimer <= 0) {
				refreshTimer = 5;
				MinimapTexture.refresh(minecraft, SCALES[scaleIndex]);
			}
		});
	}
}
