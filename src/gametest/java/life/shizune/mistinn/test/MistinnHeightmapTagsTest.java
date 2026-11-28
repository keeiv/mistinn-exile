package life.shizune.mistinn.test;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The heightmaps that ask what stops movement (where rain falls to, where things land and spawn)
 * go by a block tag: every solid block of the mod is in it, and a column topped with one of them
 * has its top where the block is, not under it.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnHeightmapTagsTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runOnServer(server -> {
				List<String> missing = new ArrayList<>();
				for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
					if (!id.getNamespace().equals(Mistinn.MOD_ID)) {
						continue;
					}
					BlockState state = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
					// a sign is solid in name only: walked through, as vanilla's are, and kept out of the heightmaps like them
					if (state.isSolid() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.ALL_SIGNS) && !state.is(BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP_NO_LEAVES)) {
						missing.add(id.toString());
					}
				}
				check(missing.isEmpty(), "every solid block of the mod stops movement in the heightmaps; missing " + missing.size() + ": " + String.join(" ", missing));

				// a column topped with the mod's stone: its top is the stone
				ServerLevel level = server.overworld();
				BlockPos at = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, server.getPlayerList().getPlayers().getFirst().blockPosition().offset(6, 0, 0));
				for (int y = 0; y < 3; y++) {
					level.setBlock(at.above(y), MistinnBlocks.VEIN_STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
				}
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
				check(top == at.getY() + 3, "the heightmap stands on the mod's stone: " + (top - at.getY()));
			});
		}
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
	}
}
