package life.shizune.mistinn.naval;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 海盜夜襲: on a night by the sea, a hunter on the shore may hear oars: a landing party rows in
 * out of the fog and comes up the beach, boarders and a marksman, their boat left at the water's edge.
 * Beat them all and the shore is held; at dawn, or if the fight drags on, what is left of them
 * goes back into the sea, and a party that got away (some of it, or all) held nothing. Only the sea
 * brings them: the open sea must be in reach, and they land on the shore, not on a deck.
 * Each hunter is raided at most once every two days.
 */
public final class RaidService {
	/** After a raid on a hunter, the next comes no sooner than this. */
	public static final long COOLDOWN = 48000L;
	/** The chance, every few seconds, that a hunter on the shore at night is raided. */
	public static final float CHANCE = 0.06F;
	/** How far off along the shore they land, at the least and the most. */
	public static final int NEAR = 22;
	public static final int FAR = 36;
	/** A raid that has gone on this long ends: the rest go back to the sea. */
	public static final int LONGEST = 4800;

	/** When the next raid may come for this hunter. */
	public static final AttachmentType<Long> NEXT = AttachmentRegistry.<Long>builder()
		.persistent(Codec.LONG)
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("raid_next"));

	/** A landing party: who it came for, who is in it, when it landed, how many of it are dead and whether any fell to a hunter. */
	private static final class Raid {
		final UUID target;
		final List<UUID> crew = new ArrayList<>();
		final long landed;
		int fallen;
		boolean blooded;

		Raid(final UUID target, final long landed) {
			this.target = target;
			this.landed = landed;
		}
	}

	private static final Map<Integer, Raid> RAIDS = new HashMap<>();
	private static int next = 1;
	/** For tests: whether the chance always comes up. */
	public static boolean certain;

	private RaidService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RaidService::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RAIDS.clear());
	}

	/** Whether a raid is known (a raider from a raid the world has forgotten goes back to the sea). */
	public static boolean known(final int raid) {
		return RAIDS.containsKey(raid);
	}

	/** A raider died, of whatever cause; {@code byHunter} when a hunter brought it down (the raid was fought, not sat out). */
	public static void fell(final int raid, final boolean byHunter) {
		Raid known = RAIDS.get(raid);
		if (known != null) {
			known.fallen++;
			known.blooded |= byHunter;
		}
	}

	/** Whether this is woodwork (a deck, a pier, a jetty) rather than the shore itself. */
	static boolean built(final BlockState state) {
		return state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS) || state.is(BlockTags.WOODEN_SLABS) || state.is(BlockTags.WOODEN_STAIRS)
			|| state.is(BlockTags.WOODEN_TRAPDOORS);
	}

	/** Whether this water is the open sea's (or a beach's shallows), not a river's, a lake's or a swamp's. */
	static boolean openSea(final ServerLevel level, final BlockPos water) {
		return level.getBiome(water).is(BiomeTags.IS_OCEAN) || level.getBiome(water).is(BiomeTags.IS_BEACH);
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		for (Map.Entry<Integer, Raid> entry : List.copyOf(RAIDS.entrySet())) {
			follow(level, entry.getKey(), entry.getValue());
		}
		if (server.getTickCount() % 100 != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			// the fog tide brings them in twice as readily
			float chance = life.shizune.mistinn.tide.TideService.up(server) ? CHANCE * 2.0F : CHANCE;
			if (eligible(level, player) && (certain || level.getRandom().nextFloat() < chance)) {
				land(level, player);
			}
		}
	}

	/** Whether pirates may land on this hunter now: night, on the shore, not raided lately, nothing already after them. */
	public static boolean eligible(final ServerLevel level, final ServerPlayer player) {
		if (!player.isAlive() || player.isCreative() || player.isSpectator() || level.getDifficulty() == Difficulty.PEACEFUL
			|| !level.dimension().equals(Level.OVERWORLD) || player.level() != level || WorldMode.of(level.getServer()) == WorldMode.TRAINING
			|| !level.isDarkOutside() || player.isInWater() || player.getVehicle() instanceof AbstractBoat
			|| level.getGameTime() < player.getAttachedOrElse(NEXT, 0L)) {
			return false;
		}
		for (Raid raid : RAIDS.values()) {
			if (raid.target.equals(player.getUUID())) {
				return false;
			}
		}
		return beach(level, player) != null;
	}

	/**
	 * Where a boat could come in: a place on the water's edge some way off, with dry ground just
	 * inland of it toward the hunter, and the open sea in reach (a river mouth by the sea will do; a
	 * river or a lake far from it will not). Returns {water, land} (the land as the block to stand
	 * on), or null.
	 */
	static BlockPos @Nullable [] beach(final ServerLevel level, final ServerPlayer player) {
		int sea = level.getSeaLevel();
		double start = level.getRandom().nextDouble() * Math.PI * 2.0;
		List<BlockPos> waters = new ArrayList<>();
		boolean open = false;
		for (int radius = NEAR; radius <= FAR; radius += 7) {
			for (int i = 0; i < 24; i++) {
				double angle = start + Math.PI * 2.0 * i / 24.0;
				int x = Mth.floor(player.getX() + Math.cos(angle) * radius);
				int z = Mth.floor(player.getZ() + Math.sin(angle) * radius);
				if (!level.hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				BlockPos water = new BlockPos(x, top, z);
				if (top == sea - 1 && level.getFluidState(water).is(Fluids.WATER)) {
					waters.add(water);
					open |= openSea(level, water);
				}
			}
		}
		if (!open) {
			return null;
		}
		for (BlockPos water : waters) {
			// a few steps toward the hunter, the ground rises out of the water
			double radius = Math.hypot(player.getX() - water.getX(), player.getZ() - water.getZ());
			for (int step = 2; step <= 6; step++) {
				double t = step / radius;
				int lx = Mth.floor(water.getX() + (player.getX() - water.getX()) * t);
				int lz = Mth.floor(water.getZ() + (player.getZ() - water.getZ()) * t);
				int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);
				BlockPos land = new BlockPos(lx, ground, lz);
				// dry ground, and the shore's own: a ship's deck or a wooden pier is not where they land
				if (ground >= sea && ground <= sea + 6 && level.getFluidState(land.below()).isEmpty() && level.getBlockState(land).isAir()
					&& level.getBlockState(land.above()).isAir() && !built(level.getBlockState(land.below()))) {
					return new BlockPos[] {water, land};
				}
			}
		}
		return null;
	}

	/** A landing party comes ashore for a hunter. Returns its id, or -1 when no beach was found. */
	public static int land(final ServerLevel level, final ServerPlayer player) {
		BlockPos[] beach = beach(level, player);
		if (beach == null) {
			return -1;
		}
		int id = next++;
		Raid raid = new Raid(player.getUUID(), level.getGameTime());
		BlockPos land = beach[1];
		int notoriety = Math.min(NavalService.MAX_NOTORIETY, NavalService.sea(level.getServer()).taken());
		int boarders = 2 + Math.min(2, notoriety / 2);
		PirateLook[] looks = {PirateLook.CUTLASS_1, PirateLook.CUTLASS_2, PirateLook.DECKHAND};
		for (int i = 0; i <= boarders; i++) {
			boolean marksman = i == boarders;
			Pirate pirate = MistinnEntities.PIRATE.create(level, EntitySpawnReason.EVENT);
			int dx = i % 3 - 1;
			int dz = i / 3 - 1;
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, land.getX() + dx, land.getZ() + dz);
			BlockPos at = new BlockPos(land.getX() + dx, ground, land.getZ() + dz);
			if (!level.getFluidState(at.below()).isEmpty() || Math.abs(ground - land.getY()) > 2) {
				at = land;
			}
			float yaw = (float) Math.toDegrees(Math.atan2(player.getZ() - at.getZ(), player.getX() - at.getX())) - 90.0F;
			pirate.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
			pirate.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			pirate.enlist(-1, marksman ? PirateRole.MARKSMAN : PirateRole.BOARDER, marksman ? PirateLook.MUSKETEER : looks[(i + id) % looks.length],
				at, notoriety);
			pirate.raid(id);
			pirate.setTarget(player);
			level.addFreshEntity(pirate);
			raid.crew.add(pirate.getUUID());
		}
		// the boat they came in, left at the water's edge
		AbstractBoat boat = (AbstractBoat) EntityTypes.DARK_OAK_BOAT.create(level, EntitySpawnReason.EVENT);
		if (boat != null) {
			Vec3 water = Vec3.atBottomCenterOf(beach[0]).add(0.0, 0.6, 0.0);
			boat.snapTo(water.x, water.y, water.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(boat);
		}
		RAIDS.put(id, raid);
		player.setAttached(NEXT, level.getGameTime() + COOLDOWN);
		level.playSound(null, land.getX() + 0.5, land.getY() + 2.0, land.getZ() + 0.5, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 24.0F, 1.1F);
		for (ServerPlayer near : level.players()) {
			if (near.distanceToSqr(Vec3.atCenterOf(land)) < 96.0 * 96.0) {
				near.sendSystemMessage(Component.translatable("message.mistinn.raid.landing"));
			}
		}
		return id;
	}

	/**
	 * A raid going on: when no raider is left on the shore it ends, held only if every one of them is
	 * dead and a hunter fought them (not when some went back to the sea, or the hunter left them behind);
	 * at dawn or after a long while, they go back to the sea.
	 */
	private static void follow(final ServerLevel level, final int id, final Raid raid) {
		List<Pirate> left = new ArrayList<>();
		for (UUID member : raid.crew) {
			if (level.getEntity(member) instanceof Pirate pirate && pirate.isAlive() && !pirate.fleeing()) {
				left.add(pirate);
			}
		}
		if (left.isEmpty()) {
			RAIDS.remove(id);
			if (raid.blooded && raid.fallen >= raid.crew.size()) {
				ServerPlayer target = level.getServer().getPlayerList().getPlayer(raid.target);
				for (ServerPlayer hunter : level.players()) {
					boolean near = target != null && hunter.distanceToSqr(target) < 64.0 * 64.0 || hunter.getUUID().equals(raid.target);
					if (near) {
						hunter.sendSystemMessage(Component.translatable("message.mistinn.raid.held"));
						life.shizune.mistinn.achievement.AchievementService.grant(hunter, life.shizune.mistinn.achievement.AchievementService.SHORE_HELD);
					}
				}
			}
			return;
		}
		if (level.isBrightOutside() || level.getGameTime() - raid.landed > LONGEST) {
			int delay = 10;
			for (Pirate pirate : left) {
				pirate.abandon(delay);
				delay += 8;
			}
		}
	}

	/** Forgets every raid, for tests. */
	public static void reset() {
		RAIDS.clear();
	}
}
