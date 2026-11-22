package life.shizune.mistinn.keeper;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * 霧牆: once a hunter has come up into the lamp room with its keeper, the mist fills the hatch they
 * came up by, and there is no going back down until one of them falls. While the hunter is in the
 * room, every opening in its floor is shut, unseen, and mist pours up out of it; when the keeper
 * dies, or no hunter is left in the room for a breath (they fell, or they left by some other way),
 * the way opens again. A world closed mid-fight opens it again too, since no one is in the room then.
 */
public final class MistWall {
	/** How far from the room's middle its floor reaches, and how far in a hunter counts as in it. */
	private static final double FLOOR = 7.4;
	private static final double INSIDE = 6.6;
	/** How long the room must stand empty before the way opens. */
	private static final int EMPTY = 40;

	private static final Map<Snuffer, State> STATES = new WeakHashMap<>();

	private static final class State {
		private boolean sealed;
		private int empty;
	}

	private MistWall() {
	}

	/** The keeper's room: the middle of its four lamp stands, and the level of its floor; null if it has no such room. */
	private static BlockPos @Nullable [] room(final Snuffer keeper, final ServerLevel level) {
		List<BlockPos> lamps = keeper.lamps(level);
		if (lamps.size() < 4) {
			return null;
		}
		double x = 0.0;
		double z = 0.0;
		for (BlockPos lamp : lamps) {
			x += lamp.getX();
			z += lamp.getZ();
		}
		BlockPos centre = BlockPos.containing(x / lamps.size(), lamps.getFirst().getY(), z / lamps.size());
		return new BlockPos[] {centre, centre.below()};
	}

	/** Runs with the keeper, every few ticks. */
	static void tick(final Snuffer keeper, final ServerLevel level) {
		if (keeper.tickCount % 5 != 0) {
			return;
		}
		BlockPos[] room = room(keeper, level);
		if (room == null) {
			return;
		}
		BlockPos centre = room[0];
		int floor = room[1].getY();
		State state = STATES.computeIfAbsent(keeper, k -> new State());
		boolean hunterIn = false;
		for (ServerPlayer player : level.players()) {
			double dx = player.getX() - (centre.getX() + 0.5);
			double dz = player.getZ() - (centre.getZ() + 0.5);
			if (player.isAlive() && !player.isSpectator() && !player.isCreative() && dx * dx + dz * dz <= INSIDE * INSIDE
				&& player.getY() >= floor + 0.5 && player.getY() <= floor + 7.0) {
				hunterIn = true;
				break;
			}
		}
		if (hunterIn && keeper.isAlive()) {
			state.empty = 0;
			if (!state.sealed) {
				state.sealed = true;
				level.playSound(null, centre, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 2.0F, 0.4F);
				level.playSound(null, centre, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.0F, 0.5F);
				for (ServerPlayer player : level.players()) {
					if (player.distanceToSqr(centre.getX(), centre.getY(), centre.getZ()) < 24 * 24) {
						player.sendSystemMessage(Component.translatable("message.mistinn.snuffer.sealed"), true);
					}
				}
			}
			shut(level, centre, floor);
			return;
		}
		if (++state.empty >= EMPTY / 5 || !keeper.isAlive()) {
			state.sealed = false;
			open(level, centre, floor);
		}
	}

	/** The keeper has fallen: the way opens at once. */
	static void fallen(final Snuffer keeper, final ServerLevel level) {
		BlockPos[] room = room(keeper, level);
		STATES.remove(keeper);
		if (room != null) {
			open(level, room[0], room[1].getY());
		}
	}

	/** Shuts every opening in the floor, and mist pours up out of each. */
	private static void shut(final ServerLevel level, final BlockPos centre, final int floor) {
		int r = (int) Math.ceil(FLOOR);
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (x * x + z * z > FLOOR * FLOOR) {
					continue;
				}
				BlockPos at = new BlockPos(centre.getX() + x, floor, centre.getZ() + z);
				if (level.getBlockState(at).isAir()) {
					level.setBlockAndUpdate(at, Blocks.BARRIER.defaultBlockState());
				}
				if (level.getBlockState(at).is(Blocks.BARRIER)) {
					level.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, floor + 1.1, at.getZ() + 0.5, 2, 0.3, 0.2, 0.3, 0.01);
				}
			}
		}
	}

	/** Opens the floor again: what the mist shut goes back to air. */
	private static void open(final ServerLevel level, final BlockPos centre, final int floor) {
		int r = (int) Math.ceil(FLOOR);
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				BlockPos at = new BlockPos(centre.getX() + x, floor, centre.getZ() + z);
				if (level.getBlockState(at).is(Blocks.BARRIER)) {
					level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	/** Whether the room's floor is shut right now (a test hook). */
	public static boolean shut(final Snuffer keeper, final ServerLevel level) {
		BlockPos[] room = room(keeper, level);
		if (room == null) {
			return false;
		}
		int r = (int) Math.ceil(FLOOR);
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (level.getBlockState(new BlockPos(room[0].getX() + x, room[1].getY(), room[0].getZ() + z)).is(Blocks.BARRIER)) {
					return true;
				}
			}
		}
		return false;
	}
}
