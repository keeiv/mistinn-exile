package life.shizune.mistinn.bell;

import life.shizune.mistinn.body.BodyService;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import life.shizune.mistinn.block.GoldenBellBlock;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Resting at a golden bell, and coming back to it after death. */
public final class BellService {
	private BellService() {
	}

	/** The player rests at the bell: healed, fed, and bound to this bell. */
	public static void rest(final ServerPlayer player, final BlockPos pos, final boolean oneTime) {
		ServerLevel level = player.level();
		player.setAttached(MistinnAttachments.BELL, GlobalPos.of(level.dimension(), pos));
		player.setHealth(player.getMaxHealth());
		BodyService.restoreAll(player);
		life.shizune.mistinn.body.WoundService.clearAll(player);
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(5.0F);
		player.clearFire();
		level.playSound(null, pos, MistinnSounds.GOLDEN_BELL_REST, SoundSource.BLOCKS, 1.0F, oneTime ? 1.12F : 1.0F);
		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 24, 0.4, 0.5, 0.4, 0.02);
		player.sendSystemMessage(Component.translatable(oneTime ? "message.mistinn.bell.rest_once" : "message.mistinn.bell.rest"), true);
		life.shizune.mistinn.story.StoryService.rested(player);
	}

	/** Respawned players waiting to be moved to their bell, with the ticks left to wait. */
	private static final Map<UUID, Integer> PENDING = new HashMap<>();
	/**
	 * Vanilla finishes placing a respawned player after the respawn event, so moving them inside
	 * the event is undone; wait a couple of ticks first.
	 */
	private static final int RESPAWN_DELAY_TICKS = 2;

	public static void queueRespawn(final ServerPlayer player) {
		PENDING.put(player.getUUID(), RESPAWN_DELAY_TICKS);
	}

	/** Called at the end of every server tick. */
	public static void tick(final MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Integer>> entries = PENDING.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<UUID, Integer> entry = entries.next();
			int left = entry.getValue() - 1;
			if (left > 0) {
				entry.setValue(left);
				continue;
			}
			entries.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null && player.isAlive()) {
				onRespawn(player);
			}
		}
	}

	/** After a death, bring the player back to the bell they rested at, if it still stands. */
	static void onRespawn(final ServerPlayer player) {
		GlobalPos bell = player.getAttached(MistinnAttachments.BELL);
		if (bell == null) {
			return;
		}
		ServerLevel level = player.level().getServer().getLevel(bell.dimension());
		BlockState state = level == null ? null : level.getBlockState(bell.pos());
		if (level == null || state == null || !(state.getBlock() instanceof GoldenBellBlock block)) {
			player.removeAttached(MistinnAttachments.BELL);
			player.sendSystemMessage(Component.translatable("message.mistinn.bell.gone"), false);
			return;
		}
		BlockPos stand = standingSpot(level, bell.pos());
		player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Set.of(), player.getYRot(), 0.0F, true);
		if (block.isOneTime()) {
			level.setBlockAndUpdate(bell.pos(), Blocks.AIR.defaultBlockState());
			level.playSound(null, bell.pos(), MistinnSounds.ORE_BELL_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.sendParticles(ParticleTypes.WAX_OFF, bell.pos().getX() + 0.5, bell.pos().getY() + 0.5, bell.pos().getZ() + 0.5, 30, 0.3, 0.4, 0.3, 0.05);
			player.removeAttached(MistinnAttachments.BELL);
			player.sendSystemMessage(Component.translatable("message.mistinn.bell.cracked"), false);
		} else {
			level.playSound(null, bell.pos(), MistinnSounds.GOLDEN_BELL_REST, SoundSource.BLOCKS, 0.6F, 0.8F);
			player.sendSystemMessage(Component.translatable("message.mistinn.bell.returned"), true);
		}
	}

	/** A free spot beside the bell with room for a player, or above it as a last resort. */
	private static BlockPos standingSpot(final ServerLevel level, final BlockPos bell) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			for (int dy = 0; dy <= 1; dy++) {
				BlockPos feet = bell.relative(direction).above(dy);
				boolean room = level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
				boolean floor = !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
				if (room && floor) {
					return feet;
				}
			}
		}
		return bell.above();
	}
}
