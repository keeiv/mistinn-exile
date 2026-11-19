package life.shizune.mistinn.residue;

import life.shizune.mistinn.block.GravestoneBlockEntity;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.salt.SaltService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The death penalty. When a hunter falls, all the salt they carry turns into a wisp of residue
 * (殘香) held by a gravestone where they fell, and the spot is marked on their map. Hitting the
 * stone gives the salt back. Falling again first leaves the old residue to fade: only the newest
 * stone can be opened.
 */
public final class ResidueService {
	private static final int SEARCH_RADIUS = 3;

	private ResidueService() {
	}

	public static void onDeath(final ServerPlayer player) {
		ServerLevel level = player.level();
		int salt = SaltService.get(player);
		int serial = player.getAttachedOrElse(MistinnAttachments.RESIDUE_SERIAL, 0) + 1;
		BlockPos pos = findSpot(level, player.blockPosition());

		player.setAttached(MistinnAttachments.RESIDUE_SERIAL, serial);
		player.setAttached(MistinnAttachments.DEATH_MARK, new DeathMark(GlobalPos.of(level.dimension(), pos), serial, salt));
		SaltService.set(player, 0);
		// the stone rises once the body has turned to smoke, at the end of the death scene
		PENDING.add(new Stone(level, pos, player.getStringUUID(), player.getPlainTextName(), salt, serial, STONE_DELAY));
	}

	/** Ticks from death to the gravestone: the length of the client's death scene, to the smoke. */
	public static final int STONE_DELAY = 70;

	private record Stone(ServerLevel level, BlockPos pos, String owner, String name, int salt, int serial, int ticks) {
	}

	private static final java.util.List<Stone> PENDING = new java.util.ArrayList<>();

	/** Called every server tick: raises the stones whose time has come. */
	public static void tick(final net.minecraft.server.MinecraftServer server) {
		for (int i = PENDING.size() - 1; i >= 0; i--) {
			Stone stone = PENDING.get(i);
			if (stone.ticks() > 0) {
				PENDING.set(i, new Stone(stone.level(), stone.pos(), stone.owner(), stone.name(), stone.salt(), stone.serial(), stone.ticks() - 1));
				continue;
			}
			PENDING.remove(i);
			place(stone);
		}
	}

	/** Raises a pending stone at once (tests and photo scenes that cannot wait). */
	public static void placeNow() {
		PENDING.forEach(ResidueService::place);
		PENDING.clear();
	}

	private static void place(final Stone stone) {
		ServerLevel level = stone.level();
		BlockPos pos = stone.pos();
		level.setBlockAndUpdate(pos, MistinnBlocks.GRAVESTONE.defaultBlockState());
		if (level.getBlockEntity(pos) instanceof GravestoneBlockEntity entity) {
			entity.fill(stone.owner(), stone.name(), stone.salt(), stone.serial());
		}
		level.playSound(null, pos, MistinnSounds.RESIDUE_FORM, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 40, 0.3, 0.6, 0.3, 0.02);
	}

	/**
	 * The hunter hits a gravestone. Only its owner can take the salt back, and only from the
	 * newest stone; any other stone of theirs has faded and crumbles.
	 *
	 * @return true when the stone was handled (the hit should not do anything else)
	 */
	public static boolean open(final ServerPlayer player, final BlockPos pos) {
		ServerLevel level = player.level();
		if (!(level.getBlockEntity(pos) instanceof GravestoneBlockEntity stone)) {
			return false;
		}
		boolean owner = stone.owner().equals(player.getStringUUID());
		if (!owner) {
			if (player.isCreative() && player.isShiftKeyDown()) {
				crumble(level, pos);
				return true;
			}
			player.sendSystemMessage(Component.translatable("message.mistinn.residue.not_yours", stone.ownerName()), true);
			return true;
		}

		DeathMark mark = player.getAttached(MistinnAttachments.DEATH_MARK);
		boolean newest = mark != null && mark.serial() == stone.serial();
		if (newest) {
			SaltService.add(player, stone.salt());
			player.removeAttached(MistinnAttachments.DEATH_MARK);
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			level.playSound(null, pos, MistinnSounds.RESIDUE_RECOVER, SoundSource.PLAYERS, 1.0F, 1.0F);
			level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 30, 0.3, 0.5, 0.3, 0.06);
			player.sendSystemMessage(Component.translatable("message.mistinn.residue.recovered", stone.salt()), true);
		} else {
			crumble(level, pos);
			player.sendSystemMessage(Component.translatable("message.mistinn.residue.faded"), true);
		}
		return true;
	}

	private static void crumble(final ServerLevel level, final BlockPos pos) {
		level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		level.sendParticles(ParticleTypes.ASH, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.01);
	}

	/**
	 * Where the gravestone goes: the death spot if it is free, otherwise the nearest free spot
	 * around it, and in the void the surface of the column.
	 */
	static BlockPos findSpot(final ServerLevel level, final BlockPos death) {
		int minY = level.getMinY() + 1;
		int maxY = level.getMaxY() - 1;
		BlockPos start = death;
		if (death.getY() < minY || death.getY() > maxY) {
			int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, death.getX(), death.getZ());
			start = new BlockPos(death.getX(), Math.max(minY, Math.min(surface, maxY)), death.getZ());
		}
		for (int r = 0; r <= SEARCH_RADIUS; r++) {
			for (int dy = 0; dy <= SEARCH_RADIUS; dy++) {
				for (int sign : new int[] {1, -1}) {
					for (int dx = -r; dx <= r; dx++) {
						for (int dz = -r; dz <= r; dz++) {
							if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
								continue;
							}
							BlockPos candidate = start.offset(dx, dy * sign, dz);
							if (candidate.getY() >= minY && candidate.getY() <= maxY && canHold(level, candidate)) {
								return candidate;
							}
						}
					}
				}
			}
		}
		return start;
	}

	private static boolean canHold(final ServerLevel level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return (state.isAir() || state.canBeReplaced()) && !state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
	}
}
