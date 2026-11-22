package life.shizune.mistinn.keeper;

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

/**
 * 霧牆: once a hunter has come up into a keeper's room, the mist fills the way they came up by, and there is no going
 * back down until one of them falls. While the hunter is in the room, every opening in its floor is shut, unseen, and
 * mist pours up out of it; when the keeper dies, or no hunter is left in the room for a breath (they fell, or they left by
 * some other way), the way opens again. A world closed mid-fight opens it again too, since no one is in the room then.
 * Each keeper says where its room is ({@link Keeper#sealedRoom}); a keeper standing in no such room shuts nothing.
 */
public final class MistWall {
	/** How long the room must stand empty before the way opens. */
	private static final int EMPTY = 40;

	/** A keeper's room: the middle of its floor, the floor's height, how far the floor reaches, and how far in a hunter counts as in it. */
	public record Room(BlockPos centre, int floor, double reach, double inside) {
	}

	private static final Map<Keeper, State> STATES = new WeakHashMap<>();

	private static final class State {
		private boolean sealed;
		private int empty;
		/** The room as it was when the mist shut it: opened again from this, even if the room can no longer be told. */
		private @org.jspecify.annotations.Nullable Room room;
	}

	private MistWall() {
	}

	/** Runs with the keeper, every few ticks. */
	static void tick(final Keeper keeper, final ServerLevel level) {
		if (keeper.tickCount % 5 != 0) {
			return;
		}
		Room room = keeper.sealedRoom(level);
		State state = STATES.computeIfAbsent(keeper, k -> new State());
		if (room == null) {
			// the room can no longer be told (a lamp stand broken, the bell's lip knocked away): what was shut opens
			if (state.room != null) {
				open(level, state.room);
				state.room = null;
				state.sealed = false;
			}
			return;
		}
		BlockPos centre = room.centre();
		boolean hunterIn = false;
		for (ServerPlayer player : level.players()) {
			double dx = player.getX() - (centre.getX() + 0.5);
			double dz = player.getZ() - (centre.getZ() + 0.5);
			if (player.isAlive() && !player.isSpectator() && !player.isCreative() && dx * dx + dz * dz <= room.inside() * room.inside()
				&& player.getY() >= room.floor() + 0.5 && player.getY() <= room.floor() + 7.0) {
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
						player.sendSystemMessage(Component.translatable("message.mistinn." + keeper.key() + ".sealed"), true);
					}
				}
			}
			shut(level, room);
			state.room = room;
			return;
		}
		if (++state.empty >= EMPTY / 5 || !keeper.isAlive()) {
			state.sealed = false;
			state.room = null;
			open(level, room);
		}
	}

	/** The keeper has fallen: the way opens at once. */
	static void fallen(final Keeper keeper, final ServerLevel level) {
		Room room = keeper.sealedRoom(level);
		State state = STATES.remove(keeper);
		if (state != null && state.room != null) {
			open(level, state.room);
		}
		if (room != null) {
			open(level, room);
		}
	}

	/** Shuts every opening in the floor, and mist pours up out of each. */
	private static void shut(final ServerLevel level, final Room room) {
		int r = (int) Math.ceil(room.reach());
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (x * x + z * z > room.reach() * room.reach()) {
					continue;
				}
				BlockPos at = new BlockPos(room.centre().getX() + x, room.floor(), room.centre().getZ() + z);
				if (level.getBlockState(at).isAir()) {
					level.setBlockAndUpdate(at, Blocks.BARRIER.defaultBlockState());
				}
				if (level.getBlockState(at).is(Blocks.BARRIER)) {
					level.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, room.floor() + 1.1, at.getZ() + 0.5, 2, 0.3, 0.2, 0.3, 0.01);
				}
			}
		}
	}

	/** Opens the floor again: what the mist shut goes back to air. */
	private static void open(final ServerLevel level, final Room room) {
		int r = (int) Math.ceil(room.reach());
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				BlockPos at = new BlockPos(room.centre().getX() + x, room.floor(), room.centre().getZ() + z);
				if (level.getBlockState(at).is(Blocks.BARRIER)) {
					level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	/** Whether the keeper's room has its floor shut right now (a test hook). */
	public static boolean shut(final Keeper keeper, final ServerLevel level) {
		Room room = keeper.sealedRoom(level);
		if (room == null) {
			return false;
		}
		int r = (int) Math.ceil(room.reach());
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (level.getBlockState(new BlockPos(room.centre().getX() + x, room.floor(), room.centre().getZ() + z)).is(Blocks.BARRIER)) {
					return true;
				}
			}
		}
		return false;
	}
}
