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

	/** A world-generation feature that builds one structure where the ground allows. */
	private record StructureFeature(String name, int maxSlope, boolean allowSand, Consumer<Builder> build) implements Feature {
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
			// only on ground that is roughly level: compare the corners with the centre
			int[][] corners = {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}};
			for (int[] c : corners) {
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, floor.getX() + c[0], floor.getZ() + c[1]) - 1;
				if (Math.abs(y - floor.getY()) > this.maxSlope) {
					return false;
				}
			}
			this.build.accept(new Builder(level, floor, random));
			return true;
		}
	}

	private static final java.util.Map<String, MapCodec<StructureFeature>> CODECS = new java.util.HashMap<>();

	private static void feature(final String name, final int maxSlope, final boolean allowSand, final Consumer<Builder> build) {
		StructureFeature feature = new StructureFeature(name, maxSlope, allowSand, build);
		MapCodec<StructureFeature> codec = MapCodec.unit(feature);
		CODECS.put(name, codec);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Mistinn.id(name), codec);
	}

	private static ResourceKey<PlacedFeature> placed(final String name) {
		return ResourceKey.create(Registries.PLACED_FEATURE, Mistinn.id(name));
	}

	public static void init() {
		feature("drop_station", 2, false, b -> Structures.station(b, Structures.Station.INTACT));
		feature("salt_cairn", 2, true, Structures::saltCairn);
		feature("anchor_wreck", 3, true, Structures::anchorWreck);
		feature("watchpost", 2, false, Structures::watchpost);

		GenerationStep.Decoration step = GenerationStep.Decoration.SURFACE_STRUCTURES;
		BiomeModifications.addFeature(
			BiomeSelectors.foundInOverworld().and(c -> !c.hasTag(BiomeTags.IS_OCEAN) && !c.hasTag(BiomeTags.IS_RIVER) && !c.hasTag(BiomeTags.IS_BEACH)),
			step,
			placed("drop_station")
		);
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_BEACH).or(BiomeSelectors.tag(BiomeTags.IS_BADLANDS)), step, placed("salt_cairn"));
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_BEACH), step, placed("anchor_wreck"));
		BiomeModifications.addFeature(
			BiomeSelectors.foundInOverworld().and(c -> !c.hasTag(BiomeTags.IS_OCEAN) && !c.hasTag(BiomeTags.IS_RIVER)),
			step,
			placed("watchpost")
		);

		ServerLifecycleEvents.SERVER_STARTED.register(MistinnWorld::ensureSpawnStation);
	}

	/**
	 * Every world's spawn is a drop station, broken or counterfeit (never a real one: hunters are
	 * not dropped where new players wake). The spawn point is set on its pad, in clear air, and the
	 * respawn radius to 0 so no one is scattered into a wall.
	 */
	public static void ensureSpawnStation(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level.getAttachedOrElse(SPAWN_STATION, false)) {
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
		level.setRespawnData(new LevelData.RespawnData(GlobalPos.of(Level.OVERWORLD, stand), 0.0F, 0.0F));
		level.getGameRules().set(GameRules.RESPAWN_RADIUS, 0, server);
		level.setAttached(SPAWN_STATION, true);
		Mistinn.LOGGER.info("Built a {} drop station at the world spawn {}", kind.name().toLowerCase(java.util.Locale.ROOT), stand);
	}
}
