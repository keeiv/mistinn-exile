package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.terra.ResearchShip;
import life.shizune.mistinn.terra.TerraLake;
import life.shizune.mistinn.terra.TerraService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * An older world, as the lakes find it: a highland of the old width whose ground was turned before there were lakes,
 * on real land, the hunter standing over it. Then the lakes come (and the highland widens): through the way it happens in
 * play (the chunks waiting since they loaded, the highland's spread going on, each chunk dug when its turn comes), the
 * lake is placed and dug, it is not left waiting, and its ship is settled.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnLakeOldSaveTest implements FabricClientGameTest {
	private static final int OLD = 180;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> s.setSeed("20261128")).create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 6000");
			context.waitTicks(20);
			int[] id = new int[1];
			BlockPos[] lakeAt = new BlockPos[1];
			try {
				world.getServer().runOnServer(server -> {
					ServerLevel level = server.overworld();
					ServerPlayer player = player(server);
					DomainService.pace = 40;
					TerraLake.dormant = true;
					ResearchShip.chance = 1.0;
					// an older highland, somewhere well clear of the world's own
					BlockPos centre = null;
					for (int i = 0; i < 16 && centre == null; i++) {
						double angle = i * Math.PI / 8.0;
						BlockPos at = player.blockPosition().offset((int) (Math.cos(angle) * 700), 0, (int) (Math.sin(angle) * 700));
						boolean clear = true;
						for (Domain other : DomainService.all(server)) {
							clear &= other.distance(at.getX() + 0.5, at.getZ() + 0.5) - other.radius() - TerraService.GAP > TerraService.RADIUS + 20;
						}
						centre = clear ? at : null;
					}
					check(centre != null, "(a clear place for an older highland)");
					Domain old = TerraService.settleAt(level, centre, OLD);
					id[0] = old.id();
					// where its lake will lie once it is widened
					Domain wide = new Domain(old.id(), old.kind(), old.centre(), TerraService.RADIUS, old.phase(), old.since(), old.spread());
					BlockPos middle = TerraLake.middle(wide);
					lakeAt[0] = middle;
					player.setGameMode(GameType.CREATIVE);
					player.getAbilities().flying = true;
					player.onUpdateAbilities();
					player.teleportTo(level, middle.getX() + 0.5, 190, middle.getZ() + 0.5, Set.of(), 0.0F, 60.0F, true);
				});
				// the old world's ground is turned, with no lake yet
				for (int i = 0; i < 120; i++) {
					context.waitTicks(10);
					if (i > 10 && world.getServer().computeOnServer(server -> DomainService.waiting() == 0)) {
						break;
					}
				}
				// the lakes come, and the highland widens
				world.getServer().runOnServer(server -> {
					check(TerraLake.waiting() > 0, "the chunks loaded meanwhile wait for the lake: " + TerraLake.waiting());
					TerraLake.dormant = false;
					TerraService.widen(server);
				});
				boolean done = false;
				for (int i = 0; i < 240 && !done; i++) {
					context.waitTicks(10);
					done = world.getServer().computeOnServer(server -> ResearchShip.of(server.overworld(), id[0]) != null);
				}
				// and the rest of the chunks loaded meanwhile are worked through (none left waiting for ever)
				for (int i = 0; i < 180 && world.getServer().computeOnServer(server -> TerraLake.waiting() >= 40); i++) {
					context.waitTicks(10);
				}
				world.getServer().runOnServer(server -> {
					ServerLevel level = server.overworld();
					Domain domain = DomainService.of(server, Domain.Kind.TERRA).stream().filter(d -> d.id() == id[0]).findFirst().orElseThrow();
					check(domain.radius() == TerraService.RADIUS, "the highland widened to " + domain.radius());
					TerraLake.Lake lake = TerraLake.placed(level, id[0]);
					check(lake != null, "the lake is placed");
					// its middle is dug into the old land, and wherever it is not, someone's work is near (or the water was sealed)
					int water = 0;
					int columns = 0;
					int unexplained = 0;
					for (int dx = -18; dx <= 18; dx += 3) {
						for (int dz = -18; dz <= 18; dz += 3) {
							if (Math.hypot(dx, dz) > 18) {
								continue;
							}
							columns++;
							BlockPos at = new BlockPos(lake.x() + dx, lake.level(), lake.z() + dz);
							net.minecraft.world.level.block.state.BlockState here = level.getBlockState(at);
							if (here.is(Blocks.WATER)) {
								water++;
							} else if (!here.is(Blocks.SAND) && !someonesNear(level, lake, at)) {
								unexplained++;
								System.out.println("[mistinn old lake] dry with nothing near: " + dx + "," + dz + " (" + TerraLake.why(level,
									DomainService.of(server, Domain.Kind.TERRA).stream().filter(d -> d.id() == id[0]).findFirst().orElseThrow(), at.getX(), at.getZ()) + ")");
							}
						}
					}
					check(water * 2 >= columns, "the lake's middle is dug into the old land: " + water + " of " + columns + " columns water at " + lake.level());
					check(unexplained <= 2, "and where it is not, someone's work is near: " + unexplained + " columns dry with nothing near");
					ResearchShip.Ship ship = ResearchShip.of(level, id[0]);
					check(ship != null, "its ship is settled: " + (ship == null ? "not" : ship.present() ? "it came" : "none (something in the water)"));
					check(TerraLake.waiting() < 40, "and nothing is left waiting for ever: " + TerraLake.waiting());
					ServerPlayer player = player(server);
					player.teleportTo(level, lake.x() + 0.5, lake.level() + 24, lake.z() - 60.5, Set.of(), 0.0F, 18.0F, true);
				});
				context.waitTicks(60);
				world.getConnection().waitForChunksRender();
				context.takeScreenshot(TestScreenshotOptions.of("old-lake").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("oldlake")));
			} finally {
				world.getServer().runOnServer(server -> {
					DomainService.pace = 1;
					TerraLake.dormant = false;
					ResearchShip.chance = 0.7;
				});
			}
		}
	}

	/** Whether there is anything near a column the land did not grow (a structure, someone's things), or a block entity. */
	private static boolean someonesNear(final ServerLevel level, final TerraLake.Lake lake, final BlockPos at) {
		for (int dx = -TerraLake.KEEP_CLEAR; dx <= TerraLake.KEEP_CLEAR; dx++) {
			for (int dz = -TerraLake.KEEP_CLEAR; dz <= TerraLake.KEEP_CLEAR; dz++) {
				for (int y = lake.level() - 17; y <= lake.level() + 40; y++) {
					BlockPos near = new BlockPos(at.getX() + dx, y, at.getZ() + dz);
					net.minecraft.world.level.block.state.BlockState state = level.getBlockState(near);
					if (level.getBlockEntity(near) != null) {
						return true;
					}
					if (Math.abs(dx) <= TerraLake.MARGIN && Math.abs(dz) <= TerraLake.MARGIN && !state.isAir() && !state.is(net.minecraft.tags.BlockTags.LOGS)
						&& !(state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) && !TerraLake.natural(state)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn old lake] ok: " + what);
	}
}
