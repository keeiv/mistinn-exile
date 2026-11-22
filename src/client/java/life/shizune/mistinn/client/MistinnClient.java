package life.shizune.mistinn.client;

import com.mojang.blaze3d.platform.InputConstants;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.anim.SwordQi;
import life.shizune.mistinn.client.body.BodyHud;
import life.shizune.mistinn.client.enemy.ExecutionView;
import life.shizune.mistinn.client.map.MinimapHud;
import life.shizune.mistinn.client.map.MinimapTexture;
import life.shizune.mistinn.client.multiplayer.HostRoomScreen;
import life.shizune.mistinn.client.tier.TierText;
import life.shizune.mistinn.client.training.TrainingClient;
import life.shizune.mistinn.client.training.TrainingGuide;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
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
		net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register(
			(type, renderer, helper, context) -> {
				if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar) {
					helper.register(new life.shizune.mistinn.client.enemy.HeadshotArrowLayer<>(avatar, context));
				}
			});
		HostRoomScreen.register();
		KeyMapping zoom = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.mistinn.minimap_zoom", InputConstants.KEY_M, CATEGORY)
		);
		KeyMapping roll = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.mistinn.roll", InputConstants.KEY_LALT, CATEGORY)
		);
		// the guide opens its own screen, and hears back from the server about skills it bought
		life.shizune.mistinn.story.HunterGuideItem.opener = () -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new life.shizune.mistinn.client.guide.HunterGuideScreen());
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(life.shizune.mistinn.skill.SkillService.UnlockResultPayload.TYPE,
			(payload, context) -> life.shizune.mistinn.client.guide.HunterGuideScreen.answered(payload));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(life.shizune.mistinn.weapon.HeavyService.HeavyAnimPayload.TYPE,
			(payload, context) -> life.shizune.mistinn.client.anim.HeavyClient.received(payload));
		life.shizune.mistinn.weapon.WeaponItem.releasedOnClient = life.shizune.mistinn.client.anim.HeavyClient::released;
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			life.shizune.mistinn.client.anim.HeavyClient.forgetAll();
			life.shizune.mistinn.client.achievement.AchievementToast.forgetAll();
		});
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(life.shizune.mistinn.achievement.AchievementService.EarnedPayload.TYPE,
			(payload, context) -> life.shizune.mistinn.client.achievement.AchievementToast.push(payload.id()));
		life.shizune.mistinn.weapon.HeavyService.clientBusy = life.shizune.mistinn.client.move.RollClient::busy;
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(life.shizune.mistinn.stamina.RollService.RollAnimPayload.TYPE,
			(payload, context) -> life.shizune.mistinn.client.move.RollClient.received(payload));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(life.shizune.mistinn.medical.TreatmentService.TreatmentPayload.TYPE,
			(payload, context) -> {
				life.shizune.mistinn.client.medical.TreatmentClient.received(payload);
				life.shizune.mistinn.client.medical.TreatmentSounds.received(payload);
			});
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
		HudElementRegistry.addLast(Mistinn.id("training"), TrainingClient::draw);
		LevelExtractionEvents.END_EXTRACTION.register(TrainingGuide::extract);
		LevelExtractionEvents.END_EXTRACTION.register(SwordQi::extract);
		LevelRenderEvents.COLLECT_SUBMITS.register(TrainingGuide::submit);
		LevelRenderEvents.COLLECT_SUBMITS.register(SwordQi::submit);
		// "正在使用 …" and its progress bar above the hotbar
		HudElementRegistry.addLast(Mistinn.id("treatment"), new life.shizune.mistinn.client.medical.TreatmentHud());
		// "achievement earned", sliding in at the top right
		HudElementRegistry.addLast(Mistinn.id("achievement"), new life.shizune.mistinn.client.achievement.AchievementToast());
		// the heavy blow's charge, under the crosshair
		HudElementRegistry.addLast(Mistinn.id("charge"), new life.shizune.mistinn.client.combat.ChargeHud());
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
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(life.shizune.mistinn.registry.MistinnEntities.STATION_HAND, life.shizune.mistinn.client.station.StationHandRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(life.shizune.mistinn.registry.MistinnEntities.SALT_CORPSE, life.shizune.mistinn.client.shore.SaltCorpseRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(life.shizune.mistinn.client.shore.LampThrallModel.LAYER, life.shizune.mistinn.client.shore.LampThrallModel::definition);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(life.shizune.mistinn.registry.MistinnEntities.LAMP_THRALL, life.shizune.mistinn.client.shore.LampThrallRenderer::new);
		net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer(life.shizune.mistinn.client.shore.MistShadeModel.LAYER, life.shizune.mistinn.client.shore.MistShadeModel::definition);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(life.shizune.mistinn.registry.MistinnEntities.MIST_SHADE, life.shizune.mistinn.client.shore.MistShadeRenderer::new);
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(life.shizune.mistinn.training.StationCeremony.DropPayload.TYPE,
			(payload, context) -> life.shizune.mistinn.client.training.TrainingClient.drop(payload.ticks()));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(life.shizune.mistinn.world.ShoreMist.MistPayload.TYPE,
			(payload, context) -> life.shizune.mistinn.client.mist.ShoreMistClient.receive(payload.lighthouses()));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(life.shizune.mistinn.training.StationArrival.ArrivalPayload.TYPE,
			(payload, context) -> life.shizune.mistinn.client.training.DropCinematic.arrive(payload.stand(), payload.top()));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(life.shizune.mistinn.station.StationBroadcast.Payload.TYPE,
			(payload, context) -> life.shizune.mistinn.client.training.TrainingClient.broadcast(payload.line()));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(life.shizune.mistinn.station.StationTalk.OpenPayload.TYPE,
			(payload, context) -> context.client().gui.setScreen(new life.shizune.mistinn.client.station.StationTalkScreen(
				payload.entity(), payload.role(), payload.opening(), payload.topics())));
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> TierText.decorate(stack, lines));
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> life.shizune.mistinn.client.combat.WeaponText.decorate(stack, lines));

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			life.shizune.mistinn.client.move.RollClient.tick(minecraft, roll);
			life.shizune.mistinn.client.medical.TreatmentClient.tick(minecraft);
			life.shizune.mistinn.client.medical.TreatmentSounds.tick(minecraft);
			life.shizune.mistinn.client.music.KeeperMusic.tick(minecraft);
			ExecutionView.tick(minecraft);
			life.shizune.mistinn.client.death.DeathScene.tick(minecraft);
			life.shizune.mistinn.client.mist.ShoreMistClient.tick(minecraft);
			life.shizune.mistinn.client.story.StoryClient.tick(minecraft);
			TrainingClient.tick(minecraft);
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
