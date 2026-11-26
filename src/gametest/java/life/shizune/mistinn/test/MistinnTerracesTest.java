package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.terra.TerraService;
import life.shizune.mistinn.terra.TerraShaper;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The valley's terraces: a grassy hill in the terraced ring is stepped, every three blocks from the
 * sea's level, its step faces walled in vein stone bricks.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnTerracesTest implements FabricClientGameTest {
	/** The hill: how far it reaches and how high it rises. */
	private static final int HILL = 16;
	private static final int RISE = 13;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set day");
			context.waitTicks(20);

			BlockPos[] hill = new BlockPos[1];
			int[] id = new int[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				hill[0] = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(24, 0, 0));
				// a round grassy hill, a block higher every block and a half in towards its top
				for (int dx = -HILL; dx <= HILL; dx++) {
					for (int dz = -HILL; dz <= HILL; dz++) {
						double off = Math.hypot(dx, dz);
						int rise = (int) Math.round(RISE * Math.max(0.0, 1.0 - off / HILL));
						for (int y = 0; y < rise; y++) {
							level.setBlock(hill[0].offset(dx, y, dz), (y == rise - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 2);
						}
					}
				}
				// the highland laid so the hill lies in its terraced ring
				id[0] = TerraService.settleAt(level, hill[0].offset(-30, 0, 0), 60).id();
			});
			for (int i = 0; i < 80; i++) {
				context.waitTicks(10);
				if (world.getServer().computeOnServer(server -> DomainService.waiting() == 0) && i > 3) {
					break;
				}
			}
			context.waitTicks(20);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				int sea = level.getSeaLevel();
				life.shizune.mistinn.domain.Domain domain = DomainService.get(server, id[0]);
				int stepped = 0;
				int raised = 0;
				int columns = 0;
				int walls = 0;
				for (int dx = -HILL + 2; dx <= HILL - 2; dx += 2) {
					for (int dz = -HILL + 2; dz <= HILL - 2; dz += 2) {
						BlockPos column = hill[0].offset(dx, 0, dz);
						if (TerraShaper.zone(domain, column.getX(), column.getZ(), domain.distance(column.getX() + 0.5, column.getZ() + 0.5))
							!= TerraShaper.Zone.TERRACES) {
							continue;
						}
						int top = ground(level, column);
						if (top < sea || top <= hill[0].getY()) {
							continue;
						}
						columns++;
						if (Math.floorMod(top - sea, 3) == 0) {
							stepped++;
						}
						raised++;
						for (int y = hill[0].getY(); y < top; y++) {
							if (level.getBlockState(column.atY(y)).is(MistinnBlocks.VEIN_STONE_BRICKS)) {
								walls++;
								break;
							}
						}
					}
				}
				check(columns > 20, "the hill stands in the valley: " + columns);
				check(stepped * 10 >= columns * 8, "the hill is stepped: " + stepped + " of " + columns);
				check(walls > 4, "the step faces are walled in stone: " + walls);
				check(raised > 0, "and nothing was cut away");
			});
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				player.teleportTo(server.overworld(), hill[0].getX() - 26.5, hill[0].getY() + 16, hill[0].getZ() - 22.5, Set.of(), -50.0F, 24.0F, true);
			});
			context.waitTicks(40);
			context.takeScreenshot(TestScreenshotOptions.of("terraces").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("terra")));
		}
	}

	/** The ground under a column, looking past trees, growth and crystal; water counts as ground (a paddy). */
	private static int ground(final ServerLevel level, final BlockPos column) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(column.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1,
			column.getZ());
		for (int i = 0; i < 48; i++, at.move(net.minecraft.core.Direction.DOWN)) {
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
			if (!state.getFluidState().isEmpty()) {
				return at.getY();
			}
			if (state.isAir() || state.canBeReplaced() || state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.LEAVES)
				|| state.getBlock() instanceof net.minecraft.world.level.block.AmethystClusterBlock || state.is(MistinnBlocks.LATTICE_CRYSTAL)
				|| state.is(MistinnBlocks.TERRA_CRYSTAL_BLOCK) || state.is(MistinnBlocks.MIST_REED) || state.is(MistinnBlocks.CRYSTAL_SPROUT)) {
				continue;
			}
			return at.getY();
		}
		return at.getY();
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
	}
}
