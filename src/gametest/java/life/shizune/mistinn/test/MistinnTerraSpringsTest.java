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
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The valley's springs: a tall step face in the terraced ring has springs welling out of it just
 * under its edge, walled in by the face's stone, and their water falls to the ground below.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnTerraSpringsTest implements FabricClientGameTest {
	/** The plateau: how far it reaches each way, and how high it stands. */
	private static final int LONG = 10;
	private static final int WIDE = 5;
	private static final int HIGH = 9;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		// every face that may have a spring has one, so there is something to see
		TerraShaper.springs = 100.0;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set day");
			context.waitTicks(20);

			BlockPos[] base = new BlockPos[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				base[0] = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player(server).blockPosition().offset(24, 0, 0));
				// a plateau with sheer sides, nine blocks up
				for (int dx = -LONG; dx <= LONG; dx++) {
					for (int dz = -WIDE; dz <= WIDE; dz++) {
						for (int y = 0; y < HIGH; y++) {
							level.setBlock(base[0].offset(dx, y, dz), (y == HIGH - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 2);
						}
					}
				}
				// the highland laid so the plateau stands in its terraced ring
				TerraService.settleAt(level, base[0].offset(-40, 0, 0), 90);
			});
			for (int i = 0; i < 80; i++) {
				context.waitTicks(10);
				if (world.getServer().computeOnServer(server -> DomainService.waiting() == 0) && i > 3) {
					break;
				}
			}
			context.waitTicks(100);

			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				int springs = 0;
				int walled = 0;
				int falls = 0;
				for (int dx = -LONG; dx <= LONG; dx++) {
					for (int dz = -WIDE; dz <= WIDE; dz++) {
						if (Math.abs(dx) != LONG && Math.abs(dz) != WIDE) {
							continue;
						}
						BlockPos column = base[0].offset(dx, 0, dz);
						for (int y = -2; y <= HIGH + 4; y++) {
							BlockPos at = column.above(y);
							if (!level.getFluidState(at).isSourceOfType(net.minecraft.world.level.material.Fluids.WATER)) {
								continue;
							}
							springs++;
							if (level.getBlockState(at.below()).is(MistinnBlocks.VEIN_STONE_BRICKS)) {
								walled++;
							}
							break;
						}
						// one step out from the face: water falling or lying at its foot
						Direction out = Math.abs(dx) == LONG ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
						BlockPos foot = column.relative(out);
						for (int y = -2; y <= HIGH; y++) {
							if (level.getFluidState(foot.above(y)).is(FluidTags.WATER)) {
								falls++;
								break;
							}
						}
					}
				}
				check(springs >= 8, "springs well out of the plateau's faces: " + springs);
				check(walled * 2 >= springs, "in the face's stone: " + walled + " of " + springs);
				check(falls >= 8, "and their water falls to the foot of the face: " + falls);
			});

			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				// in front of the north face, a little above its edge, the trees between cleared away
				ServerLevel level = server.overworld();
				for (int dx = -6; dx <= 6; dx++) {
					for (int dz = -WIDE - 12; dz < -WIDE; dz++) {
						for (int dy = 1; dy <= HIGH + 12; dy++) {
							BlockPos at = base[0].offset(dx, dy, dz);
							if (!level.getBlockState(at).isAir() && level.getFluidState(at).isEmpty()) {
								level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
							}
						}
					}
				}
				player.teleportTo(level, base[0].getX() + 0.5, base[0].getY() + HIGH + 1.0, base[0].getZ() - WIDE - 11.5, Set.of(), 0.0F, 18.0F, true);
			});
			// the place's name fades first
			context.waitTicks(160);
			context.takeScreenshot(TestScreenshotOptions.of("springs").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("terra")));
		} finally {
			TerraShaper.springs = 1.0;
		}
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
