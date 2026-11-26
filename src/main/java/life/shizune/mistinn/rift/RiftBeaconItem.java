package life.shizune.mistinn.rift;

import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.story.WorldMode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * 裂隙信標: rift shards bound round a crystal core. Held up for a few seconds it calls the sky
 * down: a rift tears open somewhere a long walk off, as one would of itself, for hunters who would
 * rather go and fight one than wait for it. Only under the open sky of the world, never while
 * another rift hangs open, and not where nothing hostile may be.
 */
public class RiftBeaconItem extends Item {
	/** How long it is held up before the sky answers. */
	public static final int CALLING = 60;
	private static final DustParticleOptions GLOW = new DustParticleOptions(0xE03A1E, 1.2F);

	public RiftBeaconItem(final Properties properties) {
		super(properties);
	}

	/** Why it would not answer here and now, or null when it would. */
	public static @Nullable String refusal(final ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD || WorldMode.of(level.getServer()) == WorldMode.TRAINING) {
			return "message.mistinn.beacon.not_here";
		}
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return "message.mistinn.beacon.peace";
		}
		if (DomainService.of(level.getServer(), Domain.Kind.RIFT).stream().anyMatch(Domain::open)) {
			return "message.mistinn.beacon.open";
		}
		return null;
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer hunter) {
			String refused = refusal(server);
			if (refused != null) {
				hunter.sendSystemMessage(Component.translatable(refused).withStyle(ChatFormatting.GRAY), true);
				return InteractionResult.FAIL;
			}
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.2F, 0.5F);
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(final ItemStack stack, final LivingEntity user) {
		return CALLING;
	}

	@Override
	public ItemUseAnimation getUseAnimation(final ItemStack stack) {
		return ItemUseAnimation.TOOT_HORN;
	}

	@Override
	public void onUseTick(final Level level, final LivingEntity user, final ItemStack stack, final int remaining) {
		if (level instanceof ServerLevel server && remaining % 4 == 0) {
			double turn = remaining * 0.4;
			server.sendParticles(GLOW, user.getX() + Math.cos(turn) * 0.9, user.getY() + 2.2 - remaining / (double) CALLING, user.getZ() + Math.sin(turn) * 0.9,
				2, 0.05, 0.05, 0.05, 0.0);
		}
	}

	@Override
	public ItemStack finishUsingItem(final ItemStack stack, final Level level, final LivingEntity user) {
		if (level instanceof ServerLevel server && user instanceof ServerPlayer hunter) {
			String refused = refusal(server);
			if (refused != null) {
				hunter.sendSystemMessage(Component.translatable(refused).withStyle(ChatFormatting.GRAY), true);
				return stack;
			}
			Domain torn = RiftService.tear(server);
			if (torn == null) {
				hunter.sendSystemMessage(Component.translatable("message.mistinn.beacon.nowhere").withStyle(ChatFormatting.GRAY), true);
				return stack;
			}
			server.playSound(null, hunter.getX(), hunter.getY(), hunter.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 0.5F);
			server.sendParticles(GLOW, hunter.getX(), hunter.getY() + 1.6, hunter.getZ(), 40, 0.4, 0.6, 0.4, 0.0);
			stack.consume(1, hunter);
		}
		return stack;
	}
}
