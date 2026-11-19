package life.shizune.mistinn.client;

import com.mojang.blaze3d.platform.InputConstants;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.body.BodyHud;
import life.shizune.mistinn.client.map.MinimapHud;
import life.shizune.mistinn.client.map.MinimapTexture;
import life.shizune.mistinn.client.tier.TierText;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
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
		KeyMapping swingCamera = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.mistinn.swing_camera", InputConstants.KEY_J, CATEGORY)
		);
		// the view jolts when one's own blow lands
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide()) {
				life.shizune.mistinn.client.anim.CameraSwing.hit();
			}
			return net.minecraft.world.InteractionResult.PASS;
		});

		HudElementRegistry.addLast(Mistinn.id("minimap"), new MinimapHud());
		// hunters see their body parts where the hearts were
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, BodyHud::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(life.shizune.mistinn.registry.MistinnEntities.SNUFFER, life.shizune.mistinn.client.keeper.SnufferRenderer::new);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> TierText.decorate(stack, lines));

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (swingCamera.consumeClick()) {
				life.shizune.mistinn.client.anim.CameraSwing.toggle();
				if (minecraft.player != null) {
					minecraft.player.sendOverlayMessage(Component.translatable(life.shizune.mistinn.client.anim.CameraSwing.enabled() ? "message.mistinn.swing_camera.on" : "message.mistinn.swing_camera.off"));
				}
			}
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
