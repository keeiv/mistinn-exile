package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.boss.BossMove;
import life.shizune.mistinn.boss.Observation;
import life.shizune.mistinn.boss.PlayerAction;
import life.shizune.mistinn.boss.PlayerModel;
import life.shizune.mistinn.boss.PlayerTrace;
import life.shizune.mistinn.boss.Response;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.keeper.SnufferMoves;
import life.shizune.mistinn.registry.MistinnEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The bosses: their moves, the brain that picks them, and that the keeper's old rules still hold. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnBossTest implements FabricClientGameTest {
	private static final String TAG = "mistinn.test_boss";

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		context.runOnClient(mc -> everyMoveHasATell());
		seesFourTicksLate();
		predictsStraightRuns();
		learnsRollSide();
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("time set noon");
			BlockPos stage = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				BlockPos pos = player.blockPosition().above(12).immutable();
				for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-10, -1, -10), pos.offset(10, -1, 10))) {
					player.level().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
				}
				for (BlockPos air : BlockPos.betweenClosed(pos.offset(-10, 0, -10), pos.offset(10, 8, 10))) {
					player.level().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
				}
				return pos;
			});
			context.waitTicks(10);
			sweepHitsInFrontNotBehind(world, stage);
			deathStopsTheMove(context, world, stage);
			grabbedHunterLeavingReleases(context, world, stage);
		}
	}

	// ── Moves ─────────────────────────────────────────────────────

	/** Every move the keeper can make is announced: a sound, a windup no shorter than its weight allows, and a clip. */
	private static void everyMoveHasATell() {
		for (BossMove move : SnufferMoves.SET.moves()) {
			check(Clip.find("keeper/" + move.id()) != null, move.id() + " has its clip");
			if (move.kind() == BossMove.Kind.REACTION) {
				continue;
			}
			check(move.tell() != null, move.id() + " has a sound for its tell");
			check(move.windup() >= move.weight().minWindup, move.id() + " winds up for at least " + move.weight().minWindup + " ticks, has " + move.windup());
		}
	}

	private static void sweepHitsInFrontNotBehind(final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Snuffer keeper = keeper(server, stage);
			BossMove sweep = keeper.moves().get("sweep");
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 3.5, Set.of(), 180.0F, 0.0F, true);
			check(sweep.strikes().getFirst().shape().hits(keeper, player), "the sweep reaches a hunter three blocks in front");
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() - 2.5, Set.of(), 0.0F, 0.0F, true);
			check(!sweep.strikes().getFirst().shape().hits(keeper, player), "and not one three blocks behind");
			clear(server, stage);
		});
	}

	/** A keeper that dies in the middle of a move neither strikes nor leaps afterwards. */
	private static void deathStopsTheMove(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 3.5, Set.of(), 180.0F, 0.0F, true);
			BodyService.restoreAll(player);
			keeper(server, stage).perform("slam");
		});
		context.waitTicks(10);
		world.getServer().runOnServer(server -> {
			Snuffer keeper = keeper(server, stage);
			keeper.hurtServer(server.overworld(), server.overworld().damageSources().generic(), 10_000.0F);
			check(keeper.isDeadOrDying(), "the keeper is down");
		});
		context.waitTicks(40);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(BodyService.get(player).total() == life.shizune.mistinn.body.Body.FULL.total(), "a dead keeper's slam never lands: " + BodyService.get(player));
			server.overworld().getEntitiesOfClass(Snuffer.class, new AABB(stage).inflate(12)).forEach(keeper ->
				check(keeper.isRemoved() || keeper.getY() < stage.getY() + 0.5, "and it never leaps"));
			clear(server, stage);
		});
	}

	/** A hunter hooked and then gone (dead, logged out, sent away) is let go, not dragged back or struck. */
	private static void grabbedHunterLeavingReleases(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 6.5, Set.of(), 180.0F, 0.0F, true);
			BodyService.restoreAll(player);
			Snuffer keeper = keeper(server, stage);
			keeper.setTarget(player);
			keeper.perform("hook");
		});
		for (int tick = 0; tick < 60 && world.getServer().computeOnServer(server -> keeper(server, stage).moveTicks() < 16); tick++) {
			context.waitTick();
		}
		// sent to the far corner of the stage, twelve blocks from the keeper and still on the floor
		world.getServer().runOnServer(server -> player(server).teleportTo(server.overworld(), stage.getX() + 8.5, stage.getY(), stage.getZ() - 8.5,
			Set.of(), 0.0F, 0.0F, true));
		context.waitTicks(30);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(BodyService.get(player).get(BodyPart.HEAD) == 80.0F, "the hook's blow does not follow a hunter who is gone");
			check(player.distanceToSqr(stage.getX() + 8.5, stage.getY(), stage.getZ() - 8.5) < 9.0, "and the hunter is not dragged back: " + player.position());
			clear(server, stage);
		});
	}

	// ── Seeing and guessing ───────────────────────────────────────

	/** The brain sees a hunter as they were four ticks ago, never as they are now. */
	private static void seesFourTicksLate() {
		PlayerTrace trace = new PlayerTrace();
		trace.record(observation(10, Vec3.ZERO, Vec3.ZERO));
		check(trace.seen(13) == null, "what happened at tick 10 is not seen at 13");
		Observation seen = trace.seen(14);
		check(seen != null && seen.time() == 10, "and is seen at 14: " + seen);
	}

	/** A hunter running straight is met where they will be, not where they were. */
	private static void predictsStraightRuns() {
		PlayerTrace trace = new PlayerTrace();
		Vec3 velocity = new Vec3(0.28, 0.0, -0.1);
		for (long time = 0; time <= 20; time++) {
			trace.record(observation(time, velocity.scale(time), velocity));
		}
		Vec3 guess = new PlayerModel().predict(trace, 20, 10);
		Vec3 truth = velocity.scale(30);
		check(guess.distanceTo(truth) < 0.3, "ten ticks ahead of a straight run, off by " + guess.distanceTo(truth));
	}

	/** A hunter who mostly rolls left from the sweep is expected to roll left. */
	private static void learnsRollSide() {
		PlayerModel model = new PlayerModel();
		for (int i = 0; i < 6; i++) {
			model.learn("sweep", Response.ROLL_LEFT);
		}
		model.learn("sweep", Response.ROLL_RIGHT);
		double left = model.responses("sweep").get(Response.ROLL_LEFT);
		check(left > 0.6, "six left rolls in seven make a left roll likely, not " + left);
	}

	static Observation observation(final long time, final Vec3 pos, final Vec3 velocity) {
		return new Observation(time, pos, velocity, 0.0F, PlayerAction.WALK, 100.0F, false, 3.0);
	}

	// ── Helpers ───────────────────────────────────────────────────

	static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** The stage's keeper, made if there is none: it faces south (+z) and picks no fights of its own. */
	static Snuffer keeper(final MinecraftServer server, final BlockPos stage) {
		ServerLevel level = server.overworld();
		var found = level.getEntitiesOfClass(Snuffer.class, new AABB(stage).inflate(12), keeper -> keeper.entityTags().contains(TAG) && keeper.isAlive());
		if (!found.isEmpty()) {
			return found.getFirst();
		}
		Snuffer keeper = MistinnEntities.SNUFFER.create(level, EntitySpawnReason.COMMAND);
		keeper.snapTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5, 0.0F, 0.0F);
		keeper.setYHeadRot(0.0F);
		keeper.setYBodyRot(0.0F);
		keeper.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(0.0);
		keeper.addTag(TAG);
		level.addFreshEntity(keeper);
		return keeper;
	}

	static void clear(final MinecraftServer server, final BlockPos stage) {
		server.overworld().getEntitiesOfClass(Snuffer.class, new AABB(stage).inflate(40), keeper -> keeper.entityTags().contains(TAG))
			.forEach(net.minecraft.world.entity.Entity::discard);
		BodyService.restoreAll(player(server));
	}

	static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
