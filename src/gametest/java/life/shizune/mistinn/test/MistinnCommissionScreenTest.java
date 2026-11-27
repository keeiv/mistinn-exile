package life.shizune.mistinn.test;

import java.nio.file.Path;
import java.util.Set;
import life.shizune.mistinn.client.terra.CommissionScreen;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.terra.Commissions;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.TerraService;
import life.shizune.mistinn.terra.Tiller;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The keeper's board on screen: right-clicking the elder of a woken homestead shows its commissions (not its wares);
 * taking one is done by the server and the screen follows; the commission shows in the top-left corner once the board
 * is closed; the elder's wares are a button away. Pictures at the test window and the smallest the game allows.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnCommissionScreenTest implements FabricClientGameTest {
	private static final Path PHOTOS = Path.of("photos", "commissions");

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 1000");
			context.waitTicks(20);
			int[] elder = new int[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(60, 0, 0));
				Domain domain = TerraService.settleAt(level, centre, 240);
				Settlements.Settlement built = Settlements.buildNow(level, domain);
				check(Settlements.light(level, built.hub(), player), "a homestead is woken");
				Settlements.Settlement home = Settlements.of(server, domain.id());
				Tiller keeper = level.getEntitiesOfClass(Tiller.class, new AABB(home.hub()).inflate(24.0), t -> t.role() == Tiller.Role.ELDER).getFirst();
				keeper.setNoAi(true);
				elder[0] = keeper.getId();
				player.teleportTo(level, keeper.getX() + 1.5, keeper.getY(), keeper.getZ(), Set.of(), 90.0F, 10.0F, true);
			});
			context.waitTicks(20);

			// right-clicked, the elder shows its board
			world.getServer().runOnServer(server -> {
				Tiller keeper = (Tiller) server.overworld().getEntity(elder[0]);
				// (the test world is flat: the settlement's deck lies under its floor, so stand by the elder as it is asked)
				near(player(server), keeper);
				keeper.interact(player(server), InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
			});
			context.waitFor(mc -> mc.gui.screen() instanceof CommissionScreen, 100);
			check(context.computeOnClient(mc -> ((CommissionScreen) mc.gui.screen()).board().offers().size()) == Commissions.BOARD,
				"the elder shows three commissions");
			context.waitTicks(5);
			shot(context, "board");
			context.getInput().resizeWindow(640, 480);
			context.waitTicks(10);
			shot(context, "board-640");
			context.getInput().resizeWindow(854, 480);
			context.waitTicks(10);

			// taking one: the server decides, the screen follows
			click(context, context.computeOnClient(mc -> ((CommissionScreen) mc.gui.screen()).buttonCentre("gui.mistinn.commission.take", 0)));
			context.waitFor(mc -> mc.player.hasAttached(Commissions.TAKEN), 100);
			check(world.getServer().computeOnServer(server -> player(server).hasAttached(Commissions.TAKEN)), "the server has it taken");
			context.waitTicks(5);
			check(context.computeOnClient(mc -> ((CommissionScreen) mc.gui.screen()).board().offers().size()) == Commissions.BOARD - 1,
				"and the board shows it gone");
			shot(context, "taken");

			// closed, the commission is in the corner
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(10);
			shot(context, "hud");

			// the elder's wares, a button away
			world.getServer().runOnServer(server -> {
				Tiller keeper = (Tiller) server.overworld().getEntity(elder[0]);
				near(player(server), keeper);
				keeper.interact(player(server), InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
			});
			context.waitFor(mc -> mc.gui.screen() instanceof CommissionScreen, 100);
			click(context, context.computeOnClient(mc -> ((CommissionScreen) mc.gui.screen()).buttonCentre("gui.mistinn.commission.trade", 0)));
			context.waitFor(mc -> mc.gui.screen() instanceof MerchantScreen, 100);
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof MerchantScreen), "the trade button opens the elder's wares");
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(5);
		}
	}

	private static void near(final ServerPlayer player, final Tiller keeper) {
		// held in the air by the elder: the flat test world has nothing under the settlement's deck
		player.getAbilities().mayfly = true;
		player.getAbilities().flying = true;
		player.onUpdateAbilities();
		player.teleportTo((ServerLevel) keeper.level(), keeper.getX() + 1.5, keeper.getY(), keeper.getZ(), Set.of(), 90.0F, 10.0F, true);
	}

	private static void click(final ClientGameTestContext context, final double[] gui) {
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		context.getInput().setCursorPos(gui[0] * scale, gui[1] * scale);
		context.waitTicks(2);
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
	}

	private static void shot(final ClientGameTestContext context, final String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(PHOTOS));
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn commission screen] ok: " + what);
	}
}
