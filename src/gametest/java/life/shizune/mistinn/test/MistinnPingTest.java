package life.shizune.mistinn.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import life.shizune.mistinn.client.team.PingClient;
import life.shizune.mistinn.team.PingService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.Vec3;

/** Marks for the team: the key marks a creature under the crosshair (riding on it) or the ground; the server keeps them near and not too quick. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnPingTest implements FabricClientGameTest {
	private static int zombie;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("difficulty easy");
			context.waitTicks(10);
			// a husk (the sun does not burn it) that stands still, eight blocks ahead, high in open air
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.getAbilities().flying = true;
				player.getAbilities().mayfly = true;
				player.onUpdateAbilities();
				player.teleportTo(level, player.getX(), 140.0, player.getZ(), java.util.Set.of(), 0.0F, 0.0F, true);
				Husk target = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
				target.snapTo(player.getX(), 140.0, player.getZ() + 8.0, 180.0F, 0.0F);
				target.setNoAi(true);
				level.addFreshEntity(target);
				zombie = target.getId();
			});
			context.waitTicks(20);
			context.runOnClient(mc -> {
				// straight at the zombie's middle
				mc.player.setYRot(0.0F);
				mc.player.setXRot(4.0F);
			});
			context.waitTicks(2);
			context.getInput().pressKey(InputConstants.KEY_G);
			context.waitTicks(5);
			List<PingClient.Ping> pings = context.computeOnClient(mc -> PingClient.pings());
			check(pings.size() == 1, "one mark: " + pings);
			check(pings.getFirst().kind() == PingService.ENEMY && pings.getFirst().entity() == zombie, "the zombie under the crosshair, marked as an enemy: " + pings);
			context.takeScreenshot("mistinn-ping-enemy");
			// the server keeps marks near the hunter, and not too quick
			context.waitTicks(PingService.GAP + 2);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(!PingService.ping(player, new PingService.PingPayload(player.position().add(500.0, 0.0, 0.0), -1)), "a mark out of reach is not shown");
				Vec3 near = player.position().add(3.0, -2.0, 3.0);
				check(PingService.ping(player, new PingService.PingPayload(near, -1)), "a mark near is");
				check(!PingService.ping(player, new PingService.PingPayload(near, -1)), "but not a second at once");
			});
			context.waitTicks(5);
			pings = context.computeOnClient(mc -> PingClient.pings());
			check(pings.size() == 2 && pings.get(1).kind() == PingService.PLACE && pings.get(1).entity() == -1, "a place is marked as a place: " + pings);
			context.waitTicks(PingClient.LIFE + 10);
			check(context.computeOnClient(mc -> PingClient.pings()).isEmpty(), "and the marks go after a while");
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
