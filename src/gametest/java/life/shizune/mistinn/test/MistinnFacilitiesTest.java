package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.block.HubSpireBlock;
import life.shizune.mistinn.block.RelayPylonBlock;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.TerraService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The crystal tillers' facilities: a settlement's hub stands on its plinth under a finned spire,
 * its relay pylons rise in a base, a lattice mast and a head; waking the hub lights the spire and
 * every pylon round it. Photographed asleep at noon and awake at dusk.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnFacilitiesTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			context.waitTicks(20);
			int[] id = new int[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player(server).blockPosition().offset(30, 0, 0));
				id[0] = TerraService.settleAt(level, centre, 60).id();
			});
			Settlements.Settlement[] built = new Settlements.Settlement[1];
			for (int i = 0; i < 60 && built[0] == null; i++) {
				context.waitTicks(20);
				built[0] = world.getServer().computeOnServer(server -> Settlements.of(server, id[0]));
			}
			check(built[0] != null, "the settlement is built");
			BlockPos hub = built[0].hub();
			int[] pylons = new int[3];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				check(level.getBlockState(hub.above()).is(MistinnBlocks.HUB_SPIRE) && !level.getBlockState(hub.above()).getValue(HubSpireBlock.LIT),
					"a dark spire stands over the sleeping hub");
				for (BlockPos at : BlockPos.betweenClosed(hub.offset(-Settlements.DECK, 0, -Settlements.DECK), hub.offset(Settlements.DECK, Settlements.PYLON, Settlements.DECK))) {
					if (level.getBlockState(at).is(MistinnBlocks.RELAY_PYLON)) {
						RelayPylonBlock.Part part = level.getBlockState(at).getValue(RelayPylonBlock.PART);
						pylons[part.ordinal()]++;
						check(!level.getBlockState(at).getValue(RelayPylonBlock.LIT), "every pylon piece dark before the hub wakes");
					}
				}
			});
			check(world.getServer().computeOnServer(server -> count(server.overworld(), hub, MistinnBlocks.REFINERY)) == 1, "a refinery stands on the deck");
			check(world.getServer().computeOnServer(server -> {
				for (BlockPos at : BlockPos.betweenClosed(hub.offset(-2, 0, -2), hub.offset(2, 1, 2))) {
					if (server.overworld().getBlockState(at).is(MistinnBlocks.FACILITY_FRAME) || server.overworld().getBlockState(at).is(MistinnBlocks.FACILITY_LAMP)) {
						return false;
					}
				}
				return true;
			}), "the hub stands clear of the old corner posts");
			check(pylons[0] == 3 && pylons[2] == 3 && pylons[1] == 3 * (Settlements.PYLON - 2),
				"three pylons, each a base, a mast and a head: " + pylons[0] + "/" + pylons[1] + "/" + pylons[2]);
			world.getConnection().waitForChunksRender();
			look(context, world, hub.offset(-5, 1, -4), hub.above(), "hub-asleep");
			look(context, world, hub.offset(9, 2, 9), hub.offset(0, 2, 0), "settlement-noon");

			// three cores wake the hub, and with it the spire and the pylons
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.CRYSTAL_CORE, Settlements.CORES));
				check(Settlements.light(server.overworld(), hub, player), "the hub wakes");
			});
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				check(level.getBlockState(hub.above()).getValue(HubSpireBlock.LIT), "the spire lights with it");
				int lit = 0;
				for (BlockPos at : BlockPos.betweenClosed(hub.offset(-Settlements.DECK, 0, -Settlements.DECK), hub.offset(Settlements.DECK, Settlements.PYLON, Settlements.DECK))) {
					if (level.getBlockState(at).is(MistinnBlocks.RELAY_PYLON) && level.getBlockState(at).getValue(RelayPylonBlock.LIT)) {
						lit++;
					}
				}
				check(lit == 3 * Settlements.PYLON, "and every piece of every pylon: " + lit);
				boolean refinery = false;
				boolean cables = true;
				for (BlockPos at : BlockPos.betweenClosed(hub.offset(-Settlements.DECK, 0, -Settlements.DECK), hub.offset(Settlements.DECK, 2, Settlements.DECK))) {
					if (level.getBlockState(at).is(MistinnBlocks.REFINERY)) {
						refinery = level.getBlockState(at).getValue(life.shizune.mistinn.block.RefineryBlock.LIT);
					} else if (level.getBlockState(at).is(MistinnBlocks.FACILITY_CONDUIT)) {
						cables &= level.getBlockState(at).getValue(life.shizune.mistinn.block.ConduitBlock.LIT);
					}
				}
				check(refinery && cables, "the refinery and the cables light too");
			});
			world.getServer().runCommand("time set 13200");
			world.getConnection().waitForChunksRender();
			look(context, world, hub.offset(-5, 1, -4), hub.above(), "hub-awake");
			look(context, world, hub.offset(9, 2, 9), hub.offset(0, 2, 0), "settlement-dusk");
			BlockPos refinery = world.getServer().computeOnServer(server -> {
				for (BlockPos at : BlockPos.betweenClosed(hub.offset(-Settlements.DECK, 0, -Settlements.DECK), hub.offset(Settlements.DECK, 1, Settlements.DECK))) {
					if (server.overworld().getBlockState(at).is(MistinnBlocks.REFINERY)) {
						return at.immutable();
					}
				}
				return hub;
			});
			look(context, world, refinery.offset(hub.getX() > refinery.getX() ? 4 : -3, 1, hub.getZ() > refinery.getZ() ? 4 : -3), refinery, "refinery-dusk");
			context.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
	}

	private static void look(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos from, final BlockPos at, final String name) {
		world.getServer().runOnServer(server -> {
			double dx = at.getX() - from.getX();
			double dz = at.getZ() - from.getZ();
			double dy = at.getY() + 0.5 - (from.getY() + 1.62);
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
			ServerPlayer player = player(server);
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			player.teleportTo(server.overworld(), from.getX() + 0.5, from.getY(), from.getZ() + 0.5, Set.of(), yaw, pitch, true);
		});
		context.waitTicks(40);
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("facilities")));
	}

	private static int count(final ServerLevel level, final BlockPos hub, final net.minecraft.world.level.block.Block block) {
		int count = 0;
		for (BlockPos at : BlockPos.betweenClosed(hub.offset(-Settlements.CLEAR, 0, -Settlements.CLEAR), hub.offset(Settlements.CLEAR, 2, Settlements.CLEAR))) {
			if (level.getBlockState(at).is(block)) {
				count++;
			}
		}
		return count;
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn facilities] ok: " + what);
	}
}
