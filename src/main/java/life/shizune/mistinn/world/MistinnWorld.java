package life.shizune.mistinn.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.LevelData;

/** The border world's buildings: scattered by world generation, and one at every world's spawn. */
public final class MistinnWorld {
	/** Set on the overworld once its spawn station stands, so it is built only once. */
	public static final AttachmentType<Boolean> SPAWN_STATION = AttachmentRegistry.<Boolean>builder()
		.persistent(Codec.BOOL)
		.buildAndRegister(Mistinn.id("spawn_station"));

	private MistinnWorld() {
	}

	/**
	 * A world-generation feature that builds one structure where the ground allows: dry, above
	 * the sea, and roughly level out to {@code reach} blocks from the centre.
	 */
	private record StructureFeature(String name, int maxSlope, int reach, boolean allowSand, Consumer<Builder> build) implements Feature {
		@Override
		public MapCodec<? extends Feature> codec() {
			return CODECS.get(this.name);
		}

		@Override
		public boolean place(final WorldGenLevel level, final ChunkGenerator generator, final RandomSource random, final BlockPos origin) {
			BlockPos floor = origin.below();
			if (!level.getBlockState(floor).getFluidState().isEmpty() || floor.getY() < level.getSeaLevel() - 1) {
				return false;
			}
			if (!this.allowSand && level.getBlockState(floor).is(net.minecraft.tags.BlockTags.SAND)) {
				return false;
			}
			// only on ground that is roughly level: compare the corners and edges with the centre
			int r = this.reach;
			int[][] samples = {{-r, -r}, {r, -r}, {-r, r}, {r, r}, {0, -r}, {0, r}, {-r, 0}, {r, 0}};
			for (int[] c : samples) {
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, floor.getX() + c[0], floor.getZ() + c[1]) - 1;
				if (Math.abs(y - floor.getY()) > this.maxSlope) {
					return false;
				}
			}
			this.build.accept(new Builder(level, floor, random));
			return true;
		}
	}

	/**
	 * A world-generation feature for what stands in the sea: on a reef in shallow water off the shore, or at the water's
	 * very edge. Its origin is the height of the sea's surface, whatever the sea floor does beneath it.
	 */
	private record ReefFeature(String name, int shallowest, int deepest, Consumer<Builder> build) implements Feature {
		@Override
		public MapCodec<? extends Feature> codec() {
			return CODECS.get(this.name);
		}

		@Override
		public boolean place(final WorldGenLevel level, final ChunkGenerator generator, final RandomSource random, final BlockPos origin) {
			int sea = level.getSeaLevel();
			int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX(), origin.getZ()) - 1;
			if (floor > sea - this.shallowest || floor < sea - this.deepest) {
				return false;
			}
			// the sea must be there round it: water at the surface on most sides
			int wet = 0;
			for (int[] c : new int[][] {{-9, 0}, {9, 0}, {0, -9}, {0, 9}, {-7, -7}, {7, 7}, {-7, 7}, {7, -7}}) {
				if (!level.getBlockState(new BlockPos(origin.getX() + c[0], sea - 1, origin.getZ() + c[1])).getFluidState().isEmpty()) {
					wet++;
				}
			}
			if (wet < 4) {
				return false;
			}
			this.build.accept(new Builder(level, new BlockPos(origin.getX(), sea, origin.getZ()), random));
			return true;
		}
	}

	private static final java.util.Map<String, MapCodec<? extends Feature>> CODECS = new java.util.HashMap<>();

	private static void feature(final String name, final int maxSlope, final int reach, final boolean allowSand, final Consumer<Builder> build) {
		StructureFeature feature = new StructureFeature(name, maxSlope, reach, allowSand, build);
		MapCodec<StructureFeature> codec = MapCodec.unit(feature);
		CODECS.put(name, codec);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Mistinn.id(name), codec);
	}

	private static void reef(final String name, final int shallowest, final int deepest, final Consumer<Builder> build) {
		ReefFeature feature = new ReefFeature(name, shallowest, deepest, build);
		MapCodec<ReefFeature> codec = MapCodec.unit(feature);
		CODECS.put(name, codec);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Mistinn.id(name), codec);
	}

	private static ResourceKey<PlacedFeature> placed(final String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, Mistinn.id(name));
	}

	public static void init() {
		feature("drop_station", 2, 5, false, b -> Structures.station(b, Structures.Station.INTACT));
		feature("salt_cairn", 2, 4, true, Structures::saltCairn);
		feature("anchor_wreck", 3, 6, true, Structures::anchorWreck);
		feature("watchpost", 2, 4, false, Structures::watchpost);
		feature("lighthouse", 3, 6, true, Structures::lighthouse);
		reef("bell_tower", 1, 7, BellTower::build);
		feature("salt_works", 3, 12, false, SaltWorks::build);

		GenerationStep.Decoration step = GenerationStep.Decoration.SURFACE_STRUCTURES;
		BiomeModifications.addFeature(
			BiomeSelectors.foundInOverworld().and(MistinnWorld::outsideUdasru).and(c -> !c.hasTag(BiomeTags.IS_OCEAN) && !c.hasTag(BiomeTags.IS_RIVER) && !c.hasTag(BiomeTags.IS_BEACH)),
			step,
			placed("drop_station")
		);
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_BEACH).or(BiomeSelectors.tag(BiomeTags.IS_BADLANDS)), step, placed("salt_cairn"));
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_BEACH), step, placed("anchor_wreck"));
		BiomeModifications.addFeature(
			BiomeSelectors.foundInOverworld().and(MistinnWorld::outsideUdasru).and(c -> !c.hasTag(BiomeTags.IS_OCEAN) && !c.hasTag(BiomeTags.IS_RIVER)),
			step,
			placed("watchpost")
		);

		// the forbidden zone's lighthouses stand on the coast
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_BEACH), step, placed("lighthouse"));
		// and out on the reefs in the shallows off the shore, rarer still, the fog-bell stations
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_BEACH)
			.or(BiomeSelectors.includeByKey(net.minecraft.world.level.biome.Biomes.OCEAN, net.minecraft.world.level.biome.Biomes.LUKEWARM_OCEAN,
				net.minecraft.world.level.biome.Biomes.WARM_OCEAN, net.minecraft.world.level.biome.Biomes.COLD_OCEAN)), step, placed("bell_tower"));

		// inland on level ground, rarest of the keepers' places: the salt works
		BiomeModifications.addFeature(
			BiomeSelectors.foundInOverworld().and(MistinnWorld::outsideUdasru).and(c -> !c.hasTag(BiomeTags.IS_OCEAN) && !c.hasTag(BiomeTags.IS_RIVER) && !c.hasTag(BiomeTags.IS_BEACH)),
			step,
			placed("salt_works")
		);

		// redstone soil, so the radio's redstone is never far: grassy patches on the surface, veins in the dirt beneath
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, placed("redstone_soil_grass"));
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, placed("redstone_soil_dirt"));

		ServerLifecycleEvents.SERVER_STARTED.register(MistinnWorld::ensureSpawnStation);
		ShoreMist.init();
		life.shizune.mistinn.shore.ShoreSpawner.init();
	}

	/** Udasru's land is drawn and built by hand: the coast's scattered buildings never stand on it. */
	private static boolean outsideUdasru(final net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext context) {
		return !context.getBiomeKey().toString().contains("mistinn:udasru_");
	}

	/**
	 * Every world's spawn is a drop station, broken or counterfeit (never a real one: hunters are
	 * not dropped where new players wake). The spawn point is set on its pad, in clear air, and the
	 * respawn radius to 0 so no one is scattered into a wall.
	 */
	public static void ensureSpawnStation(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		// the epic wakes at the Mist Inn, not at a drop station; asked of the land itself, since a new world's mode is
		// settled after this runs
		if (level.getAttachedOrElse(SPAWN_STATION, false) || life.shizune.mistinn.story.WorldMode.of(server) == life.shizune.mistinn.story.WorldMode.EPIC
			|| level.getChunkSource().getGenerator().getBiomeSource() instanceof life.shizune.mistinn.udasru.UdasruBiomes) {
			return;
		}
		BlockPos spawn = level.getRespawnData().pos();
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()) - 1;
		ground = Math.max(ground, level.getSeaLevel());
		BlockPos floor = new BlockPos(spawn.getX(), ground, spawn.getZ());
		RandomSource random = RandomSource.create(level.getSeed() ^ 0x4D697374696E6EL);
		Builder builder = new Builder(level, floor, random);
		// clear trees and anything else over the pad before building
		builder.clear(-6, 1, -6, 6, 12, 6);
		Structures.Station kind = random.nextBoolean() ? Structures.Station.BROKEN : Structures.Station.COUNTERFEIT;
		BlockPos stand = floor.offset(Structures.station(builder, kind));
		Structures.plate(builder, kind);
		level.setRespawnData(new LevelData.RespawnData(GlobalPos.of(Level.OVERWORLD, stand), 0.0F, 0.0F));
		level.getGameRules().set(GameRules.RESPAWN_RADIUS, 0, server);
		level.setAttached(SPAWN_STATION, true);
		Mistinn.LOGGER.info("Built a {} drop station at the world spawn {}", kind.name().toLowerCase(java.util.Locale.ROOT), stand);
	}

	/** Where the spawn pod's number plate hangs, from the stand on its pad. */
	public static BlockPos plate(final BlockPos stand) {
		return stand.offset(1, 1, 1);
	}

	/** The middle of the spawn pod, from the stand on its pad. */
	public static net.minecraft.world.phys.Vec3 pod(final BlockPos stand) {
		return new net.minecraft.world.phys.Vec3(stand.getX() + 0.5, stand.getY() + 1.5, stand.getZ() + 3.5);
	}

	/**
	 * Puts the number plate up on a spawn pod built before pods had plates; nothing if one hangs
	 * there already, or if there is no pod by the stand (a spawn moved off the station). Which kind
	 * of pod it is is read off its shell: clay for a copy, copper for a broken one.
	 */
	public static void ensurePlate(final ServerLevel level, final BlockPos stand) {
		if (level.getBlockState(plate(stand)).getBlock() instanceof net.minecraft.world.level.block.WallSignBlock) {
			return;
		}
		BlockPos floor = stand.offset(0, -1, 3);
		boolean clay = false;
		boolean copper = false;
		for (int y = 1; y <= 3; y++) {
			for (int x = -1; x <= 1; x += 2) {
				for (int z = -1; z <= 1; z += 2) {
					net.minecraft.world.level.block.state.BlockState shell = level.getBlockState(floor.offset(x, y, z));
					clay |= shell.is(net.minecraft.world.level.block.Blocks.TERRACOTTA) || shell.is(net.minecraft.world.level.block.Blocks.DYED_TERRACOTTA.orange());
					copper |= net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(shell.getBlock()).getPath().contains("copper");
				}
			}
		}
		if (!clay && !copper) {
			return;
		}
		Structures.plate(new Builder(level, floor, RandomSource.create(level.getSeed())),
			clay ? Structures.Station.COUNTERFEIT : Structures.Station.BROKEN);
	}
}
