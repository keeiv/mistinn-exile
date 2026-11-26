package life.shizune.mistinn.test;

import java.util.List;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.rift.RiftBeaconItem;
import life.shizune.mistinn.rift.RiftService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * The rift beacon: it does not answer in peace; held up, a rift tears open a long walk off and the
 * beacon is spent; while that rift hangs open another beacon does not answer, and is not spent.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnRiftBeaconTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("difficulty peaceful");
			context.waitTicks(20);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				check(RiftBeaconItem.refusal(level) != null, "in peace the sky does not answer");
				ItemStack beacons = new ItemStack(MistinnItems.RIFT_BEACON, 2);
				player.setItemInHand(InteractionHand.MAIN_HAND, beacons);
				beacons.getItem().finishUsingItem(beacons, level, player);
				check(beacons.getCount() == 2 && open(server).isEmpty(), "and the beacon is not spent");
			});
			world.getServer().runCommand("difficulty normal");
			// rifts never come near the world's spawn: out on open land, well away from it
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				int x = (int) player.getX() + 500;
				int z = (int) player.getZ();
				player.teleportTo(level, x + 0.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z) + 1.0, z + 0.5,
					java.util.Set.of(), 0.0F, 0.0F, true);
			});
			context.waitTicks(40);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				check(RiftBeaconItem.refusal(level) == null, "on a normal world it answers");
				ItemStack beacons = player.getMainHandItem();
				beacons.getItem().finishUsingItem(beacons, level, player);
				List<Domain> rifts = open(server);
				check(rifts.size() == 1, "a rift tears open: " + rifts.size());
				double off = rifts.getFirst().distance(player.getX(), player.getZ());
				check(off >= RiftService.NEAR - 1 && off <= RiftService.FAR + 1, "a long walk off: " + off);
				check(beacons.getCount() == 1, "and the beacon is spent");
				check(life.shizune.mistinn.achievement.AchievementService.has(player, life.shizune.mistinn.achievement.Achievement.RIFT_CALLED), "the sky called down is remembered");

				check(RiftBeaconItem.refusal(level) != null, "while it hangs open another does not answer");
				beacons.getItem().finishUsingItem(beacons, level, player);
				check(beacons.getCount() == 1 && open(server).size() == 1, "and is not spent");
			});
		}
	}

	private static List<Domain> open(final MinecraftServer server) {
		return DomainService.of(server, Domain.Kind.RIFT).stream().filter(Domain::open).toList();
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
