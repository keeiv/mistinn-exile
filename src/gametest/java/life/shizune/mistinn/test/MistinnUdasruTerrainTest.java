package life.shizune.mistinn.test;

import life.shizune.mistinn.udasru.Udasru;
import life.shizune.mistinn.udasru.UdasruBiomes;
import life.shizune.mistinn.udasru.UdasruMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Udasru's ground grows as it was drawn: a world made with its preset has the drawn heights (within the overworld's
 * own folds), and keeps the drawing when it is closed and opened again.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnUdasruTerrainTest implements FabricClientGameTest {
	/** Columns to look at, each away from the others. */
	private static final int[][] COLUMNS = {{0, 0}, {600, 0}, {0, -900}, {-1200, 300}, {1460, 0}, {0, 1800}};

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		TestWorldSave save;
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> s.setWorldType(
			new WorldCreationUiState.WorldTypeEntry(s.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(Udasru.PRESET))))
			.create()) {
			save = world.getWorldSave();
			world.getServer().runOnServer(MistinnUdasruTerrainTest::drawn);
		}
		try (TestSingleplayerContext world = save.open()) {
			world.getServer().runOnServer(server -> {
				check(server.overworld().getChunkSource().getGenerator().getBiomeSource() instanceof UdasruBiomes, "opened again, the world is still Udasru");
				drawn(server);
			});
		}
	}

	private static void drawn(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		check(level.getChunkSource().getGenerator().getBiomeSource() instanceof UdasruBiomes, "the overworld is drawn by the map of Udasru");
		UdasruMap map = UdasruMap.get();
		for (int[] column : COLUMNS) {
			int x = column[0];
			int z = column[1];
			level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
			int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
			float drawn = map.height(x, z);
			String biome = level.getBiome(new net.minecraft.core.BlockPos(x, 64, z)).getRegisteredName();
			System.out.println("[mistinn udasru terrain] column " + x + "," + z + ": ground " + top + ", drawn " + drawn + ", biome " + biome);
			// peaks may break upward where the region lets them; elsewhere the ground keeps to the drawing
			boolean jagged = life.shizune.mistinn.udasru.UdasruRegion.byId(map.region(x, z)).jaggedness > 0.0F;
			check(jagged ? top >= drawn - 24 && top <= drawn + 90 : Math.abs(top - drawn) <= 12,
				"the ground at " + x + "," + z + " keeps to the drawing: " + top + " for " + drawn);
		}
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn udasru terrain] ok: " + what);
	}
}
