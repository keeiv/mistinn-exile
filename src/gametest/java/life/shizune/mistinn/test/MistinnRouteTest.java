package life.shizune.mistinn.test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import life.shizune.mistinn.client.training.TrainingClient;
import life.shizune.mistinn.training.TrainingGround;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;

/**
 * Walks the training course's route ({@link TrainingGround#ROUTE}) from the bunk to the pod and
 * reports every place a hunter could not take it as planned: a stop with no room to stand, a leg
 * no way leads along, or a leg that only goes by a long way round (a bridge turned the wrong way,
 * something standing in front of it). A husk, the size of a hunter and kept out of the water,
 * finds the way with the game's own pathfinding. The report is also written to route-report.txt.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnRouteTest implements FabricClientGameTest {
	/** How much longer than the straight line a leg may be before it counts as a way round. */
	private static final double DETOUR_FACTOR = 1.5;
	private static final double DETOUR_SLACK = 6.0;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		context.runOnClient(TrainingClient::createWorld);
		context.waitFor(mc -> mc.player != null && mc.player.level().dimension().equals(TrainingGround.KEY), 2000);
		context.waitTicks(40);
		// a save built by the second station, with its rails across the forge bridge and its bridge to
		// nowhere off the bell court: the third one repairs it
		server(context, player -> {
			ServerLevel level = player.level();
			level.setBlockAndUpdate(new BlockPos(-42, 77, -33), net.minecraft.world.level.block.Blocks.COPPER_BARS.waxed().oxidized().defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(28, 76, -90), net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS.defaultBlockState());
			TrainingGround.rebuildFrom(level, 2);
			check(level.getBlockState(new BlockPos(-42, 77, -33)).isAir(), "the old rail across the forge bridge is gone");
			check(!level.getBlockState(new BlockPos(28, 76, -90)).is(net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS), "and the old bridge to nowhere");
		});
		List<String> problems = new ArrayList<>();
		List<TrainingGround.Waypoint> route = TrainingGround.ROUTE;
		// the guide leads along the route: halfway along each leg a hunter walks on the way to a lesson,
		// it points at that leg's end (or at the lesson's own spot once that is near)
		int previous = 0;
		for (life.shizune.mistinn.training.TrainingLesson lesson : life.shizune.mistinn.training.TrainingLesson.values()) {
			int goal = TrainingGround.stop(lesson);
			for (int i = previous; i < goal; i++) {
				net.minecraft.world.phys.Vec3 mid = net.minecraft.world.phys.Vec3.atBottomCenterOf(route.get(i).pos())
					.add(net.minecraft.world.phys.Vec3.atBottomCenterOf(route.get(i + 1).pos())).scale(0.5);
				BlockPos lead = TrainingGround.nextStop(mid, lesson, lesson.target);
				if (!lead.equals(route.get(i + 1).pos()) && !lead.equals(lesson.target)) {
					problems.add("the guide for " + lesson.id + " points at " + lead.toShortString() + " from halfway along leg " + (i + 1)
						+ " instead of on to " + route.get(i + 1).name());
				}
			}
			previous = goal;
		}
		try {
			for (int i = 0; i + 1 < route.size(); i++) {
				TrainingGround.Waypoint from = route.get(i);
				TrainingGround.Waypoint to = route.get(i + 1);
				// stand at the start of the leg so its part of the station is loaded
				server(context, player -> {
					player.setNoGravity(true);
					player.teleportTo(player.level(), from.pos().getX() + 0.5, from.pos().getY() + 3.0, from.pos().getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
				});
				context.waitTicks(10);
				int leg = i + 1;
				server(context, player -> {
					String stand = standing(player.level(), from.pos());
					if (stand != null && leg == 1) {
						problems.add("stop 1 (" + from.name() + ") " + from.pos().toShortString() + ": " + stand);
					}
					String standTo = standing(player.level(), to.pos());
					if (standTo != null) {
						problems.add("stop " + (leg + 1) + " (" + to.name() + ") " + to.pos().toShortString() + ": " + standTo);
					}
					String walked = walk(player.level(), from.pos(), to.pos());
					if (walked != null) {
						problems.add("leg " + leg + " (" + from.name() + " -> " + to.name() + ") " + from.pos().toShortString()
							+ " -> " + to.pos().toShortString() + ": " + walked);
					}
				});
			}
		} finally {
			server(context, player -> player.setNoGravity(false));
		}
		String report = problems.isEmpty() ? "The route walks clean: " + (route.size() - 1) + " legs."
			: problems.size() + " problems on the route:\n" + String.join("\n", problems);
		System.out.println("ROUTE REPORT\n" + report);
		try {
			Files.writeString(java.nio.file.Path.of("route-report.txt"), report + "\n", StandardCharsets.UTF_8);
		} catch (java.io.IOException ignored) {
			// the report is in the log as well
		}
		closeWorld(context);
		if (!problems.isEmpty()) {
			throw new AssertionError(report);
		}
	}

	/** Why a hunter could not stand at this stop, or null: feet and head in the open, something solid underfoot. */
	private static String standing(final ServerLevel level, final BlockPos feet) {
		if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
			return "the feet are inside " + level.getBlockState(feet).getBlock().getName().getString();
		}
		if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
			return "the head is inside " + level.getBlockState(feet.above()).getBlock().getName().getString();
		}
		if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
			return "nothing underfoot";
		}
		return null;
	}

	/** Why a hunter could not walk this leg as planned, or null. */
	private static String walk(final ServerLevel level, final BlockPos from, final BlockPos to) {
		Mob walker = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
		if (walker == null) {
			return "no walker";
		}
		try {
			walker.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(256.0);
			walker.setPathfindingMalus(PathType.WATER, -1.0F);
			walker.setPathfindingMalus(PathType.WATER_BORDER, 8.0F);
			walker.snapTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0.0F, 0.0F);
			walker.setOnGround(true);
			level.addFreshEntity(walker);
			Path path = walker.getNavigation().createPath(to, 0);
			if (path == null) {
				return "no way found at all";
			}
			Node end = path.getEndNode();
			if (!path.canReach() || end == null) {
				String reached = end == null ? "nowhere" : end.asBlockPos().toShortString();
				double short_ = end == null ? from.distManhattan(to) : end.asBlockPos().distManhattan(to);
				return "no way through: the nearest a hunter gets is " + reached + ", " + (int) short_ + " blocks short";
			}
			// a hunter walks the station; only stairs (and slabs) take them up, never a jump over a rail or a post
			List<String> jumps = new ArrayList<>();
			for (int i = 1; i < path.getNodeCount() && jumps.size() < 3; i++) {
				BlockPos was = path.getNode(i - 1).asBlockPos();
				BlockPos now = path.getNode(i).asBlockPos();
				if (now.getY() > was.getY()) {
					var under = level.getBlockState(now.below());
					if (!(under.getBlock() instanceof net.minecraft.world.level.block.StairBlock)
						&& !(under.getBlock() instanceof net.minecraft.world.level.block.SlabBlock)) {
						jumps.add(under.getBlock().getName().getString() + " at " + now.below().toShortString());
					}
				}
			}
			if (!jumps.isEmpty()) {
				return "a hunter has to jump over " + String.join(", ", jumps);
			}
			double straight = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ()) + Math.abs(to.getY() - from.getY());
			int length = path.getNodeCount() - 1;
			if (length > straight * DETOUR_FACTOR + DETOUR_SLACK) {
				return "only by a way round: " + length + " steps for a " + (int) Math.round(straight) + "-block leg, by " + widest(path, from, to);
			}
			return null;
		} finally {
			walker.discard();
		}
	}

	/** The point of the path farthest off the straight line, to say where the way round goes. */
	private static String widest(final Path path, final BlockPos from, final BlockPos to) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		double len = Math.max(1.0, Math.hypot(dx, dz));
		BlockPos far = from;
		double most = -1.0;
		for (int i = 0; i < path.getNodeCount(); i++) {
			BlockPos p = path.getNode(i).asBlockPos();
			double off = Math.abs((p.getX() - from.getX()) * dz - (p.getZ() - from.getZ()) * dx) / len;
			if (off > most) {
				most = off;
				far = p;
			}
		}
		return far.toShortString();
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void server(final ClientGameTestContext context, final Consumer<ServerPlayer> action) {
		var server = context.computeOnClient(Minecraft::getSingleplayerServer);
		var done = server.submit(() -> action.accept(server.getPlayerList().getPlayers().getFirst()));
		context.waitFor(mc -> done.isDone(), 400);
		done.join();
	}

	private static void closeWorld(final ClientGameTestContext context) {
		var previous = context.computeOnClient(Minecraft::getSingleplayerServer);
		context.runOnClient(mc -> {
			if (mc.level != null) {
				mc.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
				mc.disconnectWithSavingScreen();
			}
		});
		context.waitFor(mc -> mc.level == null && (previous == null || !previous.getRunningThread().isAlive()), 1200);
		context.setScreen(TitleScreen::new);
	}
}
