package life.shizune.mistinn;

import life.shizune.mistinn.bell.BellService;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnBlockEntities;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.registry.MistinnTab;
import life.shizune.mistinn.residue.ResidueService;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.weapon.WeaponStyle;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 霧宿：追放 (Mistinn: Exile). */
public class Mistinn implements ModInitializer {
	public static final String MOD_ID = "mistinn";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		MistinnSounds.init();
		MistinnBlocks.init();
		MistinnItems.init();
		MistinnBlockEntities.init();
		MistinnAttachments.init();
		MistinnTab.init();

		// A fallen hunter leaves their salt as residue; a defeated creature gives salt.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				ResidueService.onDeath(player);
			} else if (source.getEntity() instanceof ServerPlayer killer) {
				SaltService.reward(killer, entity);
			}
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				BellService.queueRespawn(newPlayer);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(BellService::tick);

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
			WeaponStyle style = WeaponStyle.of(player.getItemInHand(hand));
			if (style != null && !level.isClientSide()) {
				level.playSound(null, target.getX(), target.getY(0.5), target.getZ(), style.hitSound(), SoundSource.PLAYERS, 1.0F, 0.92F + level.getRandom().nextFloat() * 0.16F);
			}
			return InteractionResult.PASS;
		});

		LOGGER.info("Mistinn: Exile loaded");
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
