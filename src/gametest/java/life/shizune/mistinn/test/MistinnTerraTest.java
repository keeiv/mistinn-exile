package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.client.terra.ZoneTitle;
import life.shizune.mistinn.domain.Domain;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Terra: the world settles six highlands far out from spawn, well apart; one laid down near the
 * hunter takes its three rings (biome and ground), the crown grows lattice spires, every client
 * sees it, and walking into a ring shows its name.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnTerraTest implements FabricClientGameTest {
	private static final int RADIUS = 60;
	private static final int OFF = 48;

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

			// six of them, far out and apart
			world.getServer().runOnServer(server -> {
				List<Domain> terra = DomainService.of(server, Domain.Kind.TERRA);
				check(terra.size() == TerraService.COUNT, "a world holds six Terra highlands: " + terra.size());
				BlockPos spawn = server.overworld().getRespawnData().pos();
				for (Domain a : terra) {
					double out = Math.hypot(a.centre().getX() - spawn.getX(), a.centre().getZ() - spawn.getZ());
					check(out >= TerraService.NEAREST - 1 && out <= TerraService.FARTHEST + 1, "far out from spawn: " + out);
					for (Domain b : terra) {
						if (a != b) {
							check(a.distance(b.centre().getX() + 0.5, b.centre().getZ() + 0.5) >= TerraService.APART - 1, "and well apart");
						}
					}
				}
			});

			// one laid down near the hunter
			BlockPos[] centre = new BlockPos[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				BlockPos column = player.blockPosition().offset(OFF, 0, 0);
				centre[0] = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				TerraService.settleAt(level, centre[0], RADIUS);
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
				check(level.getBiome(centre[0]).is(TerraShaper.CROWN), "the middle is the crown");
				check(level.getBiome(centre[0].offset(0, 0, 30)).is(TerraShaper.TERRACES), "halfway out, the terraces");
				check(level.getBiome(centre[0].offset(0, 0, 47)).is(TerraShaper.VEINS), "near the edge, the crystal veins");
				int changed = 0;
				int columns = 0;
				for (int dx = -40; dx <= 40; dx += 5) {
					for (int dz = -40; dz <= 40; dz += 5) {
						BlockPos column = centre[0].offset(dx, 0, dz);
						if (Math.hypot(dx, dz) > RADIUS - 10) {
							continue;
						}
						columns++;
						BlockState top = level.getBlockState(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below());
						if (terra(top)) {
							changed++;
						}
					}
				}
				check(changed * 3 >= columns, "most of the ground is Terra's: " + changed + " of " + columns);
				boolean spire = false;
				for (int dx = -18; dx <= 18 && !spire; dx++) {
					for (int dz = -18; dz <= 18 && !spire; dz++) {
						BlockPos column = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, centre[0].offset(dx, 0, dz));
						for (int down = 1; down <= 12; down++) {
							spire |= level.getBlockState(column.below(down)).is(MistinnBlocks.LATTICE_CRYSTAL);
						}
					}
				}
				if (!spire) {
					java.util.Map<String, Integer> tops = new java.util.TreeMap<>();
					for (int dx = -18; dx <= 18; dx += 3) {
						for (int dz = -18; dz <= 18; dz += 3) {
							BlockPos column = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, centre[0].offset(dx, 0, dz));
							tops.merge(level.getBlockState(column.below()).getBlock().getDescriptionId() + "@" + (column.getY() - centre[0].getY()), 1, Integer::sum);
						}
					}
					throw new AssertionError("failed: lattice spires rise in the crown; tops " + tops + " centre " + centre[0]
						+ " biome " + level.getBiome(centre[0]).unwrapKey());
				}
			});
			check(context.computeOnClient(mc -> mc.level.getBiome(centre[0].offset(0, 0, 30)).is(TerraShaper.TERRACES)), "the client sees the terraces");

			// walking in: the ring's name, and a look at each ring
			look(context, world, centre[0].offset(-6, 0, 30), 90.0F, 8.0F, "terra-terraces");
			check(context.computeOnClient(mc -> TerraShaper.TERRACES.equals(ZoneTitle.shown())), "walking into the terraces shows their name");
			look(context, world, centre[0].offset(-24, 0, 4), -90.0F, -6.0F, "terra-crown");
			look(context, world, centre[0].offset(0, 0, 47), 180.0F, 4.0F, "terra-veins");
		}
	}

	private static void look(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos column, final float yaw, final float pitch,
		final String name) {
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			player(server).teleportTo(level, ground.getX() + 0.5, ground.getY() + 1.5, ground.getZ() + 0.5, Set.of(), yaw, pitch, true);
		});
		context.waitTicks(40);
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("terra")));
	}

	private static boolean terra(final BlockState state) {
		return state.is(MistinnBlocks.JADE_TURF) || state.is(MistinnBlocks.VEIN_SOIL) || state.is(MistinnBlocks.VEIN_STONE) || state.is(MistinnBlocks.VEIN_CRYSTAL_ORE)
			|| state.is(MistinnBlocks.LATTICE_CRYSTAL) || state.is(MistinnBlocks.TERRA_CRYSTAL_BLOCK) || state.is(MistinnBlocks.MIST_PINE_LEAVES)
			|| state.is(MistinnBlocks.MIST_PINE_LOG) || state.is(MistinnBlocks.TERRA_CRYSTAL_CLUSTER) || state.is(MistinnBlocks.TERRA_CRYSTAL_LARGE_BUD)
			|| state.is(MistinnBlocks.TERRA_CRYSTAL_MEDIUM_BUD) || state.is(MistinnBlocks.TERRA_CRYSTAL_SMALL_BUD) || state.is(MistinnBlocks.CRYSTAL_SPROUT);
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
