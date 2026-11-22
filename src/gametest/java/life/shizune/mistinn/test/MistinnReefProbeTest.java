package life.shizune.mistinn.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * 霧鐘台的探測: how often world generation finds a place for a fog-bell station, against a lighthouse. Over a wide
 * square of chunks it reads the terrain the generator would make (without building a single chunk) and counts the
 * chunks whose random spot passes each feature's own checks, then what the rarity leaves of them. A measurement, not a
 * check: runs only with -Dmistinn.reefprobe=true (build.gradle: -PreefProbe) and writes its numbers to the log.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnReefProbeTest implements FabricClientGameTest {
	/** Half the side of the square of chunks read, around the world's spawn. */
	private static final int HALF = 120;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass()) || !Boolean.getBoolean("mistinn.reefprobe")) {
			return;
		}
		for (String seed : new String[] {"mistinn-exile", "reef-probe-1", "reef-probe-2"}) {
			probe(context, seed);
		}
	}

	/** One world of real terrain (the tests' usual flat world has no coast), read over the square round its spawn. */
	private static void probe(final ClientGameTestContext context, final String seed) {
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> s.setSeed(seed)).create()) {
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ChunkGenerator generator = level.getChunkSource().getGenerator();
				RandomState state = level.getChunkSource().randomState();
				int sea = level.getSeaLevel();
				RandomSource random = RandomSource.create(42L);
				int cx0 = level.getRespawnData().pos().getX() >> 4;
				int cz0 = level.getRespawnData().pos().getZ() >> 4;
				long chunks = 0;
				long beach = 0;
				long ocean = 0;
				long reef = 0;
				long lighthouse = 0;
				for (int cx = cx0 - HALF; cx <= cx0 + HALF; cx++) {
					for (int cz = cz0 - HALF; cz <= cz0 + HALF; cz++) {
						chunks++;
						int x = (cx << 4) + random.nextInt(16);
						int z = (cz << 4) + random.nextInt(16);
						Holder<Biome> biome = level.getUncachedNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(sea), QuartPos.fromBlock(z));
						boolean isBeach = biome.is(Biomes.BEACH) || biome.is(Biomes.SNOWY_BEACH);
						boolean isShallowSea = is(biome, Biomes.OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.WARM_OCEAN, Biomes.COLD_OCEAN);
						if (isBeach) {
							beach++;
						}
						if (isShallowSea) {
							ocean++;
						}
						int floor = floor(generator, state, level, x, z);
						if ((isBeach || isShallowSea) && floor <= sea - 1 && floor >= sea - 7) {
							int wet = 0;
							for (int[] c : new int[][] {{-9, 0}, {9, 0}, {0, -9}, {0, 9}, {-7, -7}, {7, 7}, {-7, 7}, {7, -7}}) {
								if (floor(generator, state, level, x + c[0], z + c[1]) < sea - 1) {
									wet++;
								}
							}
							if (wet >= 4) {
								reef++;
							}
						}
						// the lighthouse's own checks, roughly: dry beach at or above the sea, roughly level out to six blocks
						if (isBeach && floor >= sea - 1) {
							boolean level6 = true;
							for (int[] c : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}, {0, -6}, {0, 6}, {-6, 0}, {6, 0}}) {
								if (Math.abs(floor(generator, state, level, x + c[0], z + c[1]) - floor) > 3) {
									level6 = false;
								}
							}
							if (level6) {
								lighthouse++;
							}
						}
					}
				}
				System.out.printf("[reefprobe] seed " + seed + ": chunks %d, beach %d, shallow-sea biomes %d; bell-tower spots %d (%.2f per 1000 chunks), lighthouse spots %d (%.2f per 1000)%n",
					chunks, beach, ocean, reef, 1000.0 * reef / chunks, lighthouse, 1000.0 * lighthouse / chunks);
				for (int rarity : new int[] {900, 600, 400, 300, 200}) {
					System.out.printf("[reefprobe] seed " + seed + ", rarity %d: about %.2f bell towers in this square (%d chunks); lighthouses at 700: %.2f%n",
						rarity, reef / (double) rarity, chunks, lighthouse / 700.0);
				}
			});
		}
	}

	@SafeVarargs
	private static boolean is(final Holder<Biome> biome, final ResourceKey<Biome>... keys) {
		for (ResourceKey<Biome> key : keys) {
			if (biome.is(key)) {
				return true;
			}
		}
		return false;
	}

	/** The top solid block the generator would make at (x, z), water ignored. */
	private static int floor(final ChunkGenerator generator, final RandomState state, final ServerLevel level, final int x, final int z) {
		return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, state) - 1;
	}
}
