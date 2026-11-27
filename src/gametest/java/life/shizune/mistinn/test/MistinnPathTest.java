package life.shizune.mistinn.test;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.client.title.ModeSelectScreen;
import life.shizune.mistinn.food.RustAnchorFood;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.path.PathService;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * 憧憬的屍之道: picked on the mode screen it is always Rust Anchor; the world starts at the
 * highland nearest spawn, the hunter one of its tillers by the unlit hub with a stone spear and
 * bread; their food does not go damp; six crystal dust opens the first chapter; a death hands
 * the way to the next of the settlement, by the hub; and when the twelfth falls the way is cut.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnPathTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		// the mode screen: the path never asks how hard, it is Rust Anchor
		WorldMode[] mode = new WorldMode[1];
		Hardship[] hardship = new Hardship[1];
		context.runOnClient(mc -> new ModeSelectScreen((m, h) -> {
			mode[0] = m;
			hardship[0] = h;
		}, () -> { }).pick(WorldMode.PATH));
		check(mode[0] == WorldMode.PATH && hardship[0] == Hardship.RUST_ANCHOR, "picking the path goes on at Rust Anchor: " + hardship[0]);
		check(!ModeSelectScreen.asksHardship(WorldMode.PATH), "and does not ask how hard");

		WorldMode.choose(WorldMode.PATH);
		Hardship.choose(Hardship.RUST_ANCHOR);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runOnServer(server -> {
				check(WorldMode.of(server) == WorldMode.PATH, "the world walks the path");
				check(Hardship.of(server) == Hardship.RUST_ANCHOR, "at Rust Anchor");
				check(PathService.line(server) != null, "the way starts from a highland");
				check(PathService.line(server).stage() == PathService.Step.GRIT, "with the prologue");
			});

			// the settlement rises and the hunter is set down by its hub
			boolean placed = false;
			for (int i = 0; i < 180 && !placed; i++) {
				context.waitTicks(20);
				placed = world.getServer().computeOnServer(server -> player(server).getAttachedOrElse(PathService.PLACED, false));
			}
			check(placed, "the hunter is brought to the settlement once it stands");
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				Settlements.Settlement home = Settlements.of(server, PathService.line(server).domain());
				check(home != null && !home.lit(), "the settlement stands, its hub unlit");
				check(near(player, home.hub(), 6.0), "the hunter is by the hub: " + player.blockPosition() + " / " + home.hub());
				check(player.getInventory().countItem(MistinnItems.WEAPONS.get(WeaponStyle.THRUST).get(WeaponMaterial.STONE)) == 1, "with a stone spear");
				check(player.getInventory().countItem(net.minecraft.world.item.Items.BREAD) == 6, "and bread");
				check(!RustAnchorFood.applies(player), "a tiller's food does not go damp");
				life.shizune.mistinn.terra.Elders.Crown crown = life.shizune.mistinn.terra.Elders.crown(server, home.domain());
				check(crown == null || !crown.out(), "the crown elder sleeps on, though the hub is near its heart");
			});
			check(context.computeOnClient(mc -> mc.player.getAttached(PathService.VIEW) != null), "the client is told of the way");
			String[] firstLook = new String[1];
			firstLook[0] = context.computeOnClient(mc -> mc.player.getSkin().body().texturePath().getPath());
			check(firstLook[0].contains("tiller"), "the hunter wears a tiller's clothes: " + firstLook[0]);
			context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			context.takeScreenshot("path-look");
			context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
			world.getConnection().waitForChunksRender();
			context.waitTicks(40);
			context.takeScreenshot("path-start");

			// six crystal dust, carried, opens the first chapter
			world.getServer().runOnServer(server -> player(server).getInventory().add(new ItemStack(MistinnItems.CRYSTAL_DUST, PathService.GRIT)));
			context.waitTicks(30);
			check(stage(world) == PathService.Step.LIGHT, "six dust moves on to lighting the hub: " + stage(world));
			check(context.computeOnClient(mc -> mc.player.getAttached(PathService.VIEW).stage() == PathService.Step.LIGHT), "and the panel shows it");

			// the walker falls: the next of the settlement takes up the way, at the hub
			die(context, world);
			world.getServer().runOnServer(server -> {
				PathService.Line line = PathService.line(server);
				check(line.fallen().equals(List.of(0)) && line.walker() == 1, "the first has fallen, the second walks: " + line);
				check(line.stage() == PathService.Step.LIGHT, "from where the way had come");
				Settlements.Settlement home = Settlements.of(server, line.domain());
				check(near(player(server), home.hub(), 6.0), "the successor sets out from the hub: " + player(server).blockPosition());
				check(player(server).getInventory().countItem(MistinnItems.WEAPONS.get(WeaponStyle.THRUST).get(WeaponMaterial.STONE)) >= 1, "with a spear of their own");
				check(player(server).gameMode() != GameType.SPECTATOR, "and walks it");
			});
			context.waitTicks(20);
			check(context.computeOnClient(mc -> mc.player.getAttached(PathService.VIEW).fallen() == 1), "the panel counts the fallen");
			String nextLook = context.computeOnClient(mc -> mc.player.getSkin().body().texturePath().getPath());
			check(nextLook.contains("tiller") && !nextLook.equals(firstLook[0]), "and the successor is plainly someone else: " + nextLook);
			context.takeScreenshot("path-fallen");

			// the way comes to the sea: out past the highland, on a beach
			int[] shore = new int[3];
			world.getServer().runOnServer(server -> {
				PathService.Line line = PathService.line(server);
				server.overworld().setAttached(PathService.LINE,
					new PathService.Line(line.domain(), line.fallen(), line.walker(), PathService.Step.SEA.ordinal(), line.tide()));
				life.shizune.mistinn.domain.Domain home = life.shizune.mistinn.domain.DomainService.get(server, line.domain());
				shore[0] = home.centre().getX() + home.radius() + PathService.BEYOND + 60;
				shore[1] = home.centre().getZ();
				server.overworld().getChunk(shore[0] >> 4, shore[1] >> 4);
				shore[2] = server.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, shore[0], shore[1]);
			});
			world.getServer().runCommand("tp @a " + shore[0] + " " + shore[2] + " " + shore[1]);
			world.getConnection().waitForChunksRender();
			context.waitTicks(30);
			check(stage(world) == PathService.Step.SEA, "far out on plain ground is not the sea yet");
			world.getServer().runCommand("fillbiome " + (shore[0] - 8) + " " + (shore[2] - 2) + " " + (shore[1] - 8) + " "
				+ (shore[0] + 8) + " " + (shore[2] + 8) + " " + (shore[1] + 8) + " minecraft:beach");
			context.waitTicks(30);
			check(stage(world) == PathService.Step.ENDED, "a beach past the highland ends the way: " + stage(world));
			check(world.getServer().computeOnServer(server -> life.shizune.mistinn.achievement.AchievementService.has(player(server),
				life.shizune.mistinn.achievement.Achievement.SEA_SEEN)), "and the sea is remembered");
			context.waitFor(mc -> mc.gui.screen() instanceof life.shizune.mistinn.client.path.PathEndScreen end && end.sea(), 300);
			context.waitTicks(130);
			check(context.computeOnClient(mc -> mc.getSituationalMusic() == life.shizune.mistinn.client.path.PathClient.END), "the end has its own music");
			context.takeScreenshot("path-sea-end");
			context.runOnClient(mc -> ((life.shizune.mistinn.client.path.PathEndScreen) mc.gui.screen()).advance());
			context.waitTicks(2);
			check(context.computeOnClient(mc -> mc.gui.screen() == null), "the end closes once all of it has come up");

			// eleven down, the last falls: the way is cut
			world.getServer().runOnServer(server -> {
				PathService.Line line = PathService.line(server);
				List<Integer> eleven = new ArrayList<>();
				for (int i = 0; i < PathService.PEOPLE - 1; i++) {
					eleven.add(i);
				}
				server.overworld().setAttached(PathService.LINE,
					new PathService.Line(line.domain(), List.copyOf(eleven), PathService.PEOPLE - 1, PathService.Step.LIGHT.ordinal(), line.tide()));
			});
			die(context, world);
			world.getServer().runOnServer(server -> {
				PathService.Line line = PathService.line(server);
				check(line.stage() == PathService.Step.CUT && line.fallen().size() == PathService.PEOPLE, "the twelfth falls and the way is cut: " + line);
				check(player(server).gameMode() == GameType.SPECTATOR, "the world can only be watched");
			});
			context.waitFor(mc -> mc.gui.screen() instanceof life.shizune.mistinn.client.path.PathEndScreen end && !end.sea(), 300);
			context.waitTicks(250);
			context.takeScreenshot("path-cut-end");
		}
	}

	private static void die(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runCommand("kill @a");
		context.waitFor(mc -> mc.gui.screen() instanceof life.shizune.mistinn.client.death.MissionEndScreen, 400);
		context.waitFor(mc -> life.shizune.mistinn.client.death.DeathScene.seconds() > life.shizune.mistinn.client.death.DeathScene.SCREEN + 1.8F, 400);
		context.waitTicks(5);
		// the mission-end screen's own button is pressed in the hardship test; here only what follows matters
		context.runOnClient(mc -> mc.player.respawn());
		context.waitFor(mc -> mc.player != null && mc.player.isAlive(), 200);
		context.waitTicks(10);
	}

	private static PathService.Step stage(final TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> PathService.line(server).stage());
	}

	private static boolean near(final ServerPlayer player, final BlockPos at, final double reach) {
		return Math.hypot(player.getX() - (at.getX() + 0.5), player.getZ() - (at.getZ() + 0.5)) <= reach;
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn path] ok: " + what);
	}
}
