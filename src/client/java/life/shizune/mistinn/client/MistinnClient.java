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
		KeyMapping roll = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.mistinn.roll", InputConstants.KEY_LALT, CATEGORY)
		);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(life.shizune.mistinn.stamina.RollService.RollAnimPayload.TYPE,
			(payload, context) -> life.shizune.mistinn.client.move.RollClient.received(payload));
		KeyMapping journal = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.mistinn.journal", InputConstants.KEY_K, CATEGORY)
		);
		life.shizune.mistinn.client.story.StoryClient.register();
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
		HudElementRegistry.addLast(Mistinn.id("story"), new life.shizune.mistinn.client.story.StoryHud());
		// hunters see their body parts where the hearts were
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, BodyHud::new);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HEALTH_BAR, Mistinn.id("stamina"), new life.shizune.mistinn.client.body.StaminaHud());
		// the mask over every player's face, the backpack on the back and the rig on the chest
		net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(life.shizune.mistinn.client.gear.MaskLayer.LAYER, life.shizune.mistinn.client.gear.MaskLayer::definition);
		net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(life.shizune.mistinn.client.gear.GearLayer.LAYER, life.shizune.mistinn.client.gear.GearLayer::definition);
		net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar) {
				@SuppressWarnings("unchecked")
				var parent = (net.minecraft.client.renderer.entity.RenderLayerParent<net.minecraft.client.renderer.entity.state.AvatarRenderState, net.minecraft.client.model.player.PlayerModel>) avatar;
				helper.register(new life.shizune.mistinn.client.gear.MaskLayer(parent, context));
				helper.register(new life.shizune.mistinn.client.gear.GearLayer(parent, context));
			}
		});
		net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(life.shizune.mistinn.client.keeper.SnufferModel.LAYER, life.shizune.mistinn.client.keeper.SnufferModel::definition);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(life.shizune.mistinn.registry.MistinnEntities.SNUFFER, life.shizune.mistinn.client.keeper.SnufferRenderer::new);
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> TierText.decorate(stack, lines));

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			life.shizune.mistinn.client.move.RollClient.tick(minecraft, roll);
			life.shizune.mistinn.client.death.DeathScene.tick(minecraft);
			life.shizune.mistinn.client.story.StoryClient.tick(minecraft);
			while (journal.consumeClick()) {
				if (minecraft.gui.screen() == null) {
					minecraft.gui.setScreen(new life.shizune.mistinn.client.story.JournalScreen());
				}
			}
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
