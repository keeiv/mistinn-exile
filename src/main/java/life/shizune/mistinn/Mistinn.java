package life.shizune.mistinn;

import life.shizune.mistinn.bell.BellService;
import life.shizune.mistinn.grid.GridActionPayload;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnBlockEntities;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnComponents;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.registry.MistinnTab;
import life.shizune.mistinn.residue.ResidueService;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.weapon.Weapons;
import life.shizune.mistinn.world.MistinnWorld;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 霧宿：追放 (Mistinn: Exile). */
public class Mistinn implements ModInitializer {
	public static final String MOD_ID = "mistinn";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		MistinnSounds.init();
		MistinnComponents.init();
		MistinnBlocks.init();
		MistinnItems.init();
		MistinnBlockEntities.init();
		MistinnAttachments.init();
		MistinnTab.init();
		GridActionPayload.register();
		life.shizune.mistinn.stamina.RollService.register();
		life.shizune.mistinn.medical.TreatmentService.register();
		life.shizune.mistinn.registry.MistinnEntities.init();
		MistinnWorld.init();
		life.shizune.mistinn.story.WorldMode.init();
		life.shizune.mistinn.story.StoryService.init();

		// A fallen hunter leaves their salt as residue; a defeated creature gives salt.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				GridService.onDeath(player);
				ResidueService.onDeath(player);
			} else if (source.getEntity() instanceof ServerPlayer killer) {
				SaltService.reward(killer, entity);
			}
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				BellService.queueRespawn(newPlayer);
			}
			GridService.applyWeight(newPlayer);
			life.shizune.mistinn.medical.TreatmentService.reset(newPlayer);
			BodyService.applyPenalties(newPlayer, BodyService.get(newPlayer));
		});
		ServerPlayerEvents.JOIN.register(player -> {
			GridService.applyWeight(player);
			// the worn mask, backpack and rig tiers are not saved on their own: show them again from the grid
			GridService.showWorn(player, GridService.get(player));
			// part penalties are transient attribute modifiers: put them back on every join
			BodyService.applyPenalties(player, BodyService.get(player));
			// every hunter arrives with the guide, once
			if (!player.getAttachedOrElse(MistinnAttachments.GUIDE_GIVEN, false)) {
				player.setAttached(MistinnAttachments.GUIDE_GIVEN, true);
				ItemStack guide = new ItemStack(MistinnItems.HUNTER_GUIDE);
				// on the hotbar where it is seen at once, else wherever it fits
				int slot = -1;
				for (int i = 0; i < net.minecraft.world.entity.player.Inventory.getSelectionSize() && slot < 0; i++) {
					if (player.getInventory().getItem(i).isEmpty()) {
						slot = i;
					}
				}
				if (slot >= 0) {
					player.getInventory().setItem(slot, guide);
				} else if (!player.getInventory().add(guide)) {
					player.drop(guide, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(BellService::tick);
		ServerTickEvents.END_SERVER_TICK.register(BodyService::tick);
		ServerTickEvents.END_SERVER_TICK.register(life.shizune.mistinn.body.WoundService::tick);
		ServerTickEvents.END_SERVER_TICK.register(life.shizune.mistinn.residue.ResidueService::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> life.shizune.mistinn.residue.ResidueService.placeNow());
		ServerTickEvents.END_SERVER_TICK.register(life.shizune.mistinn.stamina.StaminaService::tick);
		ServerTickEvents.END_SERVER_TICK.register(life.shizune.mistinn.stamina.RollService::tick);
		ServerTickEvents.END_SERVER_TICK.register(life.shizune.mistinn.medical.TreatmentService::tick);
		// nothing may live in the hidden vanilla storage slots, however it got there
		ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(GridService::sweepHiddenSlots));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				server.getPlayerList().getPlayers().forEach(GridService::tidy);
			}
		});

		// Left click opens a gravestone. Accepting on the client sends the hit to the server.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (!level.getBlockState(pos).is(MistinnBlocks.GRAVESTONE)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				ResidueService.open(serverPlayer, pos);
			}
			return InteractionResult.SUCCESS;
		});

		// Each weapon branch has its own impact sound.
		AttackEntityCallback.EVENT.register((player, level, hand, target, hitResult) -> {
			// striking breaks off a treatment in progress
			if (player instanceof ServerPlayer striker) {
				life.shizune.mistinn.medical.TreatmentService.interrupt(striker);
			}
			Weapons.Profile weapon = Weapons.profile(player.getItemInHand(hand));
			if (weapon != null && !level.isClientSide()) {
				float pitch = weapon.pitch() * (0.92F + level.getRandom().nextFloat() * 0.16F);
				level.playSound(null, target.getX(), target.getY(0.5), target.getZ(), weapon.hit(), SoundSource.PLAYERS, 1.0F, pitch);
			}
			return InteractionResult.PASS;
		});

		LOGGER.info("Mistinn: Exile loaded");
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
