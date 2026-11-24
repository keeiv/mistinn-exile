package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.boss.BossBrain;
import life.shizune.mistinn.boss.BossMove;
import life.shizune.mistinn.boss.HitShape;
import life.shizune.mistinn.boss.MoveRunner;
import life.shizune.mistinn.boss.MoveSet;
import life.shizune.mistinn.boss.PartRule;
import life.shizune.mistinn.boss.Observation;
import life.shizune.mistinn.boss.PlayerAction;
import life.shizune.mistinn.boss.PlayerModel;
import life.shizune.mistinn.boss.PlayerTrace;
import life.shizune.mistinn.boss.Planner;
import life.shizune.mistinn.boss.Response;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.keeper.SnufferModel;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.keeper.SnufferMoves;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnSounds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
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
		context.runOnClient(mc -> idlesPlayWhenWaiting());
		seesFourTicksLate();
		predictsStraightRuns();
		learnsRollSide();
		neverLooksPastSix();
		staysInBudget();
		picksWhatLands();
		punishesTreatment();
		variesItself();
		baitsEarlyRollers();
		signalsAreHonest();
		signalsAreNeverFaked();
		learnsALeftRoller();
		brainsAreIndependent();
		closeInMovesLand();
		coversALeftRoller();
		carriedBlowsCount();
		darkMovesStayInTheDark();
		feintsBorrowTheirTell();
		eachSignalIsKept();
		distanceMovesLand();
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
			feintsNeverHurt(context, world, stage);
			delayHoldsTheBlow(context, world, stage);
			forgetsAfterTheFight(world, stage);
			ignoresNonHunters(context, world, stage);
			dashStopsAtWalls(context, world, stage);
			slideTakesTheLegs(context, world, stage);
			rushClosesTheGap(context, world, stage);
			grabLetsGoOfTheGone(context, world, stage);
			reachesACorneredHunter(context, world, stage);
			lampsAreTheRooms(context, world, stage);
			seesHuntersInTheCorners(context, world, stage);
			interruptedMovesLetGo(context, world, stage);
			mistAmbushHasATell(context, world, stage);
			slamWaveReachesFurther(context, world, stage);
			delayedSlamHangs(context, world, stage);
			hookThrustMeetsThePulled(context, world, stage);
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

	// ── Thinking ahead ────────────────────────────────────────────

	/** However things stand, the planner never looks further than six moves ahead. */
	private static void neverLooksPastSix() {
		Planner planner = new Planner(SnufferMoves.SET, RandomSource.create(1));
		RandomSource random = RandomSource.create(7);
		for (int i = 0; i < 300; i++) {
			Planner.Plan plan = planner.plan(situation(random), 3_000_000L);
			check(plan.depth() <= Planner.MAX_DEPTH && plan.steps().size() <= Planner.MAX_DEPTH, "looked " + plan.depth() + " moves ahead");
		}
	}

	/**
	 * Thinking costs under a millisecond on average and under three almost always. The rare sample that a
	 * collector's pause or the scheduler stops mid-thought is logged, not failed: no budget can cut short a
	 * pause it is inside, and in the game that pause stops the whole server tick anyway.
	 */
	private static void staysInBudget() {
		Planner planner = new Planner(SnufferMoves.SET, RandomSource.create(2));
		RandomSource random = RandomSource.create(11);
		for (int i = 0; i < 300; i++) {
			planner.plan(situation(random), 3_000_000L);
		}
		long[] nanos = new long[1000];
		long total = 0;
		for (int i = 0; i < nanos.length; i++) {
			nanos[i] = planner.plan(situation(random), 3_000_000L).nanos();
			total += nanos[i];
		}
		java.util.Arrays.sort(nanos);
		long average = total / nanos.length;
		long p99 = nanos[nanos.length * 99 / 100];
		System.out.println("[boss] planner: " + average / 1000 + " µs on average, " + p99 / 1000 + " µs at the 99th percentile, " + nanos[nanos.length - 1] / 1000 + " µs at worst");
		check(average < 1_000_000L, "thinks for " + average / 1000 + " µs on average");
		check(p99 < 3_000_000L, "and " + p99 / 1000 + " µs at the 99th percentile");
	}

	/** A hunter standing still four blocks in front is attacked with something that reaches. */
	private static void picksWhatLands() {
		Planner planner = new Planner(SnufferMoves.SET, RandomSource.create(3));
		Planner.Plan plan = planner.plan(standing(new Vec3(0.0, 0.0, 4.0), PlayerAction.IDLE), 3_000_000L);
		BossMove first = plan.first();
		System.out.println("[boss] four blocks away: " + plan.steps().stream().map(BossMove::id).toList() + " score " + plan.score());
		check(first != null && first.kind() == BossMove.Kind.ATTACK && first.range().contains(4.0), "four blocks away, it attacks with what reaches: " + plan.steps());
	}

	/** A hunter healing ten blocks away is closed on and attacked within two moves. */
	private static void punishesTreatment() {
		Planner planner = new Planner(SnufferMoves.SET, RandomSource.create(4));
		Planner.Plan plan = planner.plan(standing(new Vec3(0.0, 0.0, 10.0), PlayerAction.TREAT), 3_000_000L);
		boolean punished = false;
		for (int i = 0; i < Math.min(2, plan.steps().size()); i++) {
			punished |= plan.steps().get(i).kind() == BossMove.Kind.ATTACK;
		}
		System.out.println("[boss] healing ten blocks away: " + plan.steps().stream().map(BossMove::id).toList());
		check(punished, "a hunter healing ten blocks away is attacked within two moves: " + plan.steps());
	}

	/** Asked the same thing twenty times, it never does the same move more than three times running. */
	private static void variesItself() {
		Planner planner = new Planner(SnufferMoves.SET, RandomSource.create(5));
		Planner.Situation same = standing(new Vec3(0.0, 0.0, 4.0), PlayerAction.IDLE);
		String previous = null;
		int run = 0;
		String previousFamily = null;
		int familyRun = 0;
		List<String> picks = new java.util.ArrayList<>();
		for (int i = 0; i < 20; i++) {
			BossMove first = planner.plan(same, 3_000_000L).first();
			check(first != null, "it always does something");
			planner.commit(first);
			picks.add(first.id());
			run = first.id().equals(previous) ? run + 1 : 1;
			previous = first.id();
			check(run <= 3, "the same move four times running: " + picks);
			String family = planner.family(first);
			familyRun = family.equals(previousFamily) ? familyRun + 1 : 1;
			previousFamily = family;
			check(familyRun <= 3, "the same kind of move (" + family + ") four times running: " + picks);
		}
		System.out.println("[boss] twenty picks for one situation: " + picks);
	}

	/** The boss at the centre of a twelve-block room facing south, the hunter where given, nothing on cooldown. */
	static Planner.Situation standing(final Vec3 hunter, final PlayerAction action) {
		return new Planner.Situation(Vec3.ZERO, 0.0F, "light", java.util.Map.of(), null, 0, hunter, Vec3.ZERO, action, 100.0F, new PlayerModel(),
			null, 0, Vec3.ZERO, 12.0);
	}

	/** Anything that could be: anywhere in the room, running or not, some moves cooling down, sometimes mid-move. */
	static Planner.Situation situation(final RandomSource random) {
		Vec3 hunter = new Vec3(random.nextDouble() * 24.0 - 12.0, 0.0, random.nextDouble() * 24.0 - 12.0);
		Vec3 velocity = new Vec3(random.nextDouble() * 0.6 - 0.3, 0.0, random.nextDouble() * 0.6 - 0.3);
		java.util.Map<String, Integer> cooldowns = new java.util.HashMap<>();
		for (BossMove move : SnufferMoves.SET.moves()) {
			if (random.nextFloat() < 0.3F) {
				cooldowns.put(move.id(), random.nextInt(60));
			}
		}
		BossMove last = null;
		int lastTicks = 0;
		if (random.nextFloat() < 0.3F) {
			last = SnufferMoves.SET.byIndex(random.nextInt(SnufferMoves.SET.size()));
			lastTicks = last.windup() + last.active() + random.nextInt(Math.max(1, last.recovery()));
		}
		PlayerModel model = new PlayerModel();
		for (int i = 0; i < random.nextInt(20); i++) {
			model.learn("sweep", Response.values()[random.nextInt(Response.values().length)]);
		}
		PlayerAction action = PlayerAction.values()[random.nextInt(PlayerAction.values().length)];
		return new Planner.Situation(Vec3.ZERO, random.nextFloat() * 360.0F, random.nextBoolean() ? "light" : "dark", cooldowns, last, lastTicks,
			hunter, velocity, action, random.nextFloat() * 100.0F, model, null, 0, Vec3.ZERO, 12.0);
	}

	// ── Feints, delays and signals ────────────────────────────────

	/** A thrust: a lunge forward with the spear, a long narrow band. */
	static final BossMove TEST_THRUST = BossMove.builder("thrust", BossMove.Kind.ATTACK).weight(BossMove.Weight.LIGHT)
		.tell(MistinnSounds.SNUFFER_HOOK, 10).timing(2, 14)
		.strike(10, new HitShape.Capsule(6.0, 0.6, 2.0), PartRule.always(BodyService.Hit.on(BodyPart.TORSO, false)), 6.0F)
		.motion(new BossMove.Motion(List.of(new BossMove.Motion.Key(10, 1.5, 0.0, 0.0)))).range(0.0, 7.0).families("thrust").build();
	/** The sweep's tell, then nothing: it hands over to the thrust. */
	static final BossMove TEST_FAKE_SWEEP = BossMove.builder("fake_sweep", BossMove.Kind.FEINT)
		.tell(MistinnSounds.SNUFFER_SWEEP, 16).timing(0, 8).imitates("sweep").range(0.0, 4.5).families("sweep").build();
	/** The spear spun once in the hand: a thrust is coming. */
	static final BossMove TEST_TWIRL = BossMove.builder("twirl", BossMove.Kind.SIGNAL)
		.tell(MistinnSounds.SNUFFER_HOOK, 0).timing(0, 30).signals("thrust").build();
	static final MoveSet TRICKS = new MoveSet(List.of(SnufferMoves.SWEEP, TEST_FAKE_SWEEP, TEST_THRUST, TEST_TWIRL),
		List.of(new MoveSet.Link("fake_sweep", "thrust", 10, 16)));

	/** A feint can carry no blow, and running one through hurts nobody. */
	private static void feintsNeverHurt(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		boolean refused = false;
		try {
			BossMove.builder("bad_feint", BossMove.Kind.FEINT).tell(MistinnSounds.SNUFFER_SWEEP, 16).imitates("sweep")
				.strike(16, new HitShape.Circle(3.0, false), PartRule.always(BodyService.Hit.LEGS), 5.0F).build();
		} catch (IllegalStateException expected) {
			refused = true;
		}
		check(refused, "a feint with a blow in it is refused");
		MoveRunner runner = new MoveRunner();
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 3.0, Set.of(), 180.0F, 0.0F, true);
			BodyService.restoreAll(player);
			runner.start(keeper(server, stage), TEST_FAKE_SWEEP);
		});
		for (int i = 0; i < TEST_FAKE_SWEEP.length() + 2; i++) {
			world.getServer().runOnServer(server -> runner.tick(server.overworld(), keeper(server, stage), player(server)));
			context.waitTick();
		}
		world.getServer().runOnServer(server -> {
			check(!runner.running(), "the feint runs to its end");
			check(BodyService.get(player(server)).total() == life.shizune.mistinn.body.Body.FULL.total(), "and hurts nobody: " + BodyService.get(player(server)));
			clear(server, stage);
		});
	}

	/** A delayed blow holds at the end of its tell and lands late, after an early roll would be spent. */
	private static void delayHoldsTheBlow(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		MoveRunner runner = new MoveRunner();
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 3.0, Set.of(), 180.0F, 0.0F, true);
			BodyService.restoreAll(player);
			runner.start(keeper(server, stage), SnufferMoves.SWEEP);
			runner.delay(8);
		});
		float[] seen = new float[2];
		for (int i = 0; i < 30; i++) {
			int tick = i;
			world.getServer().runOnServer(server -> {
				runner.tick(server.overworld(), keeper(server, stage), player(server));
				if (tick == 18) {
					seen[0] = BodyService.get(player(server)).total();
				}
				if (tick == 29) {
					seen[1] = BodyService.get(player(server)).total();
				}
			});
			context.waitTick();
		}
		float full = life.shizune.mistinn.body.Body.FULL.total();
		check(seen[0] == full, "eight ticks of delay: nothing has landed when the sweep would have");
		check(seen[1] < full, "and the sweep lands once the delay is over");
		world.getServer().runOnServer(server -> clear(server, stage));
	}

	/** A hunter who rolls away early from the sweep's tell is shown the tell and then caught. */
	private static void baitsEarlyRollers() {
		PlayerModel model = new PlayerModel();
		for (int i = 0; i < 8; i++) {
			model.learn("sweep", Response.ROLL_BACK);
			model.learnRoll("sweep", 14);
		}
		model.learn("sweep", Response.STAY);
		model.learn("sweep", Response.STAY);
		int feints = 0;
		List<String> firsts = new java.util.ArrayList<>();
		for (int seed = 0; seed < 40; seed++) {
			Planner.Plan plan = new Planner(TRICKS, RandomSource.create(seed)).plan(new Planner.Situation(Vec3.ZERO, 0.0F, "light", java.util.Map.of(), null, 0,
				new Vec3(0.0, 0.0, 3.5), Vec3.ZERO, PlayerAction.IDLE, 100.0F, model, null, 0, Vec3.ZERO, 12.0), 3_000_000L);
			firsts.add(plan.first() == null ? "nothing" : plan.first().id());
			if (plan.first() == TEST_FAKE_SWEEP) {
				feints++;
			}
		}
		System.out.println("[boss] against an early roller: " + firsts);
		check(feints > 20, "an early roller is baited more often than not: " + firsts);
	}

	/** While a signal is owed, the next attack is of its family, and nothing feints or signals before it. */
	private static void signalsAreHonest() {
		Planner planner = new Planner(TRICKS, RandomSource.create(9));
		RandomSource random = RandomSource.create(13);
		for (int i = 0; i < 300; i++) {
			Planner.Situation any = situation(random);
			Planner.Situation owed = new Planner.Situation(any.bossPos(), any.bossYaw(), any.phase(), java.util.Map.of(), null, 0, any.hunterPos(),
				any.hunterVelocity(), any.hunterAction(), any.hunterStamina(), any.hunter(), "thrust", 60, any.roomCentre(), any.roomRadius());
			Planner.Plan plan = planner.plan(owed, 3_000_000L);
			for (int step = 0; step < plan.steps().size() && plan.starts().get(step) < 60; step++) {
				BossMove move = plan.steps().get(step);
				check(move.kind() != BossMove.Kind.FEINT && move.kind() != BossMove.Kind.SIGNAL, "nothing feints or signals while a signal is owed: " + plan.steps());
				if (move.kind() == BossMove.Kind.ATTACK) {
					check(move.in("thrust"), "the spear's twirl promised a thrust: " + plan.steps());
					break;
				}
			}
		}
	}

	/** No feint borrows a signal: a signal always means what it says. */
	private static void signalsAreNeverFaked() {
		boolean refused = false;
		try {
			BossMove fake = BossMove.builder("fake_twirl", BossMove.Kind.FEINT).tell(MistinnSounds.SNUFFER_HOOK, 8).timing(0, 8).imitates("twirl").build();
			new MoveSet(List.of(TEST_TWIRL, fake), List.of());
		} catch (IllegalArgumentException expected) {
			refused = true;
		}
		check(refused, "a move set with a feint of a signal is refused");
		for (BossMove move : SnufferMoves.SET.moves()) {
			if (move.imitates() != null) {
				check(SnufferMoves.SET.get(move.imitates()).kind() != BossMove.Kind.SIGNAL, move.id() + " fakes a signal");
			}
		}
	}

	// ── The brain in the keeper ───────────────────────────────────

	/** A hunter who rolls to their left out of every sweep is seen doing it, and expected to. */
	private static void learnsALeftRoller() {
		BossBrain brain = new BossBrain(SnufferMoves.SET, new Planner(SnufferMoves.SET, RandomSource.create(1)));
		UUID hunter = UUID.randomUUID();
		long time = 0;
		for (int round = 0; round < 6; round++) {
			brain.started(SnufferMoves.SWEEP, 0, hunter, Vec3.ZERO, time);
			// south of the keeper and facing it, so their left is west; the roll comes a few ticks into the tell
			Vec3 last = new Vec3(0.0, 0.0, 3.5);
			for (int t = 0; t < SnufferMoves.SWEEP.length(); t++) {
				boolean rolling = t >= 6 && t < 12;
				Vec3 pos = t < 6 ? new Vec3(0.0, 0.0, 3.5) : new Vec3(-0.55 * Math.min(6, t - 5), 0.0, 3.5);
				brain.see(hunter, new Observation(time, pos, pos.subtract(last), 180.0F, rolling ? PlayerAction.ROLL : PlayerAction.IDLE, 100.0F, false, 3.0),
					Vec3.ZERO, time);
				last = pos;
				time++;
			}
		}
		PlayerModel model = brain.model(hunter);
		check(model != null, "the brain keeps a model of the hunter it watched");
		double left = model.responses("sweep").get(Response.ROLL_LEFT);
		check(left > 0.6, "six rolls to the left make a left roll likely, not " + left + " " + model.responses("sweep"));
	}

	/** Two keepers learn each on their own: what one sees, the other does not know. */
	private static void brainsAreIndependent() {
		BossBrain one = new BossBrain(SnufferMoves.SET, new Planner(SnufferMoves.SET, RandomSource.create(1)));
		BossBrain two = new BossBrain(SnufferMoves.SET, new Planner(SnufferMoves.SET, RandomSource.create(1)));
		UUID hunter = UUID.randomUUID();
		one.started(SnufferMoves.SWEEP, 0, hunter, Vec3.ZERO, 0);
		for (int t = 0; t < 40; t++) {
			one.see(hunter, observation(t, new Vec3(0.0, 0.0, 3.5), Vec3.ZERO), Vec3.ZERO, t);
		}
		check(one.model(hunter) != null, "the first keeper watched the hunter");
		check(two.model(hunter) == null, "the second never did");
	}

	/** Once the room has been empty of hunters for the length of a fight's end, the keeper forgets them; not a tick sooner. */
	private static void forgetsAfterTheFight(final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Snuffer keeper = keeper(server, stage);
			keeper.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(40.0);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 3.5, Set.of(), 180.0F, 0.0F, true);
			Vec3 centre = Vec3.atBottomCenterOf(stage);
			keeper.brain().observe(server.overworld(), keeper, centre, 12.0);
			check(keeper.brain().model(player.getUUID()) != null, "a hunter in the room is watched");
			// gone from the room, though not from the world
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY() + 40, stage.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			for (int i = 0; i < BossBrain.FIGHT_END - 1; i++) {
				keeper.brain().observe(server.overworld(), keeper, centre, 12.0);
			}
			check(keeper.brain().model(player.getUUID()) != null, "a moment short of the fight's end, it still remembers");
			keeper.brain().observe(server.overworld(), keeper, centre, 12.0);
			check(keeper.brain().model(player.getUUID()) == null, "and then it forgets");
			clear(server, stage);
		});
	}

	/** A player in creative is not a hunter: the keeper neither targets nor watches them, until they are one. */
	private static void ignoresNonHunters(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runCommand("gamemode creative @a");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 3.5, Set.of(), 180.0F, 0.0F, true);
			keeper(server, stage).getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(40.0);
		});
		context.waitTicks(10);
		world.getServer().runOnServer(server -> {
			Snuffer keeper = keeper(server, stage);
			check(keeper.getTarget() == null, "a creative player is not targeted: " + keeper.getTarget());
			check(keeper.brain().model(player(server).getUUID()) == null, "nor watched");
		});
		world.getServer().runCommand("gamemode survival @a");
		context.waitTicks(10);
		world.getServer().runOnServer(server -> {
			Snuffer keeper = keeper(server, stage);
			check(keeper.getTarget() == player(server), "a hunter is: " + keeper.getTarget());
			clear(server, stage);
		});
	}

	// ── The body ──────────────────────────────────────────────────

	/** Standing with nothing to do, the keeper now and then plays one of its idles; each has its clip, and keepers are not in step. */
	private static void idlesPlayWhenWaiting() {
		java.util.Set<String> seen = new java.util.HashSet<>();
		int quiet = 0;
		for (int age = 0; age < 3000; age++) {
			String idle = SnufferModel.idle(17, age);
			if (idle == null) {
				quiet++;
			} else {
				seen.add(idle);
			}
		}
		for (String idle : SnufferModel.IDLES) {
			check(Clip.find("keeper/" + idle) != null, idle + " has its clip");
			check(seen.contains(idle), idle + " is played now and then");
		}
		check(quiet > 0, "and now and then it does nothing at all");
		java.util.Set<String> together = new java.util.HashSet<>();
		for (int id = 0; id < 20; id++) {
			together.add(String.valueOf(SnufferModel.idle(id, 10)));
		}
		check(together.size() > 1, "twenty keepers do not all idle in step: " + together);
	}

	// ── Slide and rush ────────────────────────────────────────────

	/** Sliding or rushing at a wall, the keeper stops in front of it, never inside it; nor does it rush out of its room. */
	private static void dashStopsAtWalls(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		for (String move : List.of("slide", "rush")) {
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				// the hunter out of the way: this is about the keeper and the wall
				player(server).teleportTo(level, stage.getX() - 7.5, stage.getY(), stage.getZ() - 7.5, Set.of(), 0.0F, 0.0F, true);
				for (BlockPos wall : BlockPos.betweenClosed(stage.offset(-3, 0, 4), stage.offset(3, 3, 4))) {
					level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
				}
				keeper(server, stage).perform(move);
			});
			context.waitTicks(SnufferMoves.SET.get(move).length() + 2);
			world.getServer().runOnServer(server -> {
				Snuffer keeper = keeper(server, stage);
				check(keeper.getZ() < stage.getZ() + 4.0 - keeper.getBbWidth() / 2.0 + 0.01, move + " stops at the wall: " + keeper.position());
				check(server.overworld().noCollision(keeper), move + " leaves the keeper clear of the blocks: " + keeper.position());
				for (BlockPos wall : BlockPos.betweenClosed(stage.offset(-3, 0, 4), stage.offset(3, 3, 4))) {
					server.overworld().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
				}
				clear(server, stage);
			});
		}
		// at the edge of its room, facing out: the rush stops at the room's edge
		world.getServer().runOnServer(server -> {
			Snuffer keeper = keeper(server, stage);
			keeper.leash(Vec3.atBottomCenterOf(stage), 6.0);
			keeper.snapTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 3.5, 0.0F, 0.0F);
			keeper.perform("rush");
		});
		context.waitTicks(SnufferMoves.SET.get("rush").length() + 2);
		world.getServer().runOnServer(server -> {
			Snuffer keeper = keeper(server, stage);
			double out = keeper.position().subtract(Vec3.atBottomCenterOf(stage)).horizontalDistance();
			check(out <= 6.05, "the rush stops at the edge of the room, not " + out + " out");
			clear(server, stage);
		});
	}

	/** The slide runs along the floor and takes the legs, and only the legs. */
	private static void slideTakesTheLegs(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 4.5, Set.of(), 180.0F, 0.0F, true);
			BodyService.restoreAll(player);
			keeper(server, stage).perform("slide");
		});
		context.waitTicks(SnufferMoves.SET.get("slide").length() + 2);
		world.getServer().runOnServer(server -> {
			life.shizune.mistinn.body.Body body = BodyService.get(player(server));
			check(body.get(BodyPart.LEFT_LEG) < BodyPart.MAX && body.get(BodyPart.RIGHT_LEG) < BodyPart.MAX, "the slide takes both legs: " + body);
			check(body.get(BodyPart.HEAD) == BodyPart.MAX && body.get(BodyPart.TORSO) == BodyPart.MAX, "and nothing above them: " + body);
			clear(server, stage);
		});
	}

	/** A hunter ten blocks away is reached by the rush and struck in the body within a second of its tell. */
	private static void rushClosesTheGap(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 9.5, Set.of(), 180.0F, 0.0F, true);
			BodyService.restoreAll(player);
			keeper(server, stage).perform("rush");
		});
		BossMove rush = SnufferMoves.SET.get("rush");
		context.waitTicks(rush.windup() + 20);
		world.getServer().runOnServer(server -> {
			life.shizune.mistinn.body.Body body = BodyService.get(player(server));
			check(body.get(BodyPart.TORSO) < BodyPart.MAX, "nine blocks away, the rush lands within a second of its tell: " + body);
			clear(server, stage);
		});
	}

	// ── The move set ──────────────────────────────────────────────

	/**
	 * Every fixed blow of the given moves reaches a hunter in front of where the keeper stands when it lands, and
	 * misses one behind it (unless it goes all the way round), and aims where it should.
	 */
	static void blowsLandWhereTheyAim(final java.util.Map<String, PartRule.Aim> aims, final Set<String> allRound) {
		Vec3 facing = new Vec3(0.0, 0.0, 1.0);
		for (java.util.Map.Entry<String, PartRule.Aim> entry : aims.entrySet()) {
			BossMove move = SnufferMoves.SET.get(entry.getKey());
			check(!move.strikes().isEmpty(), move.id() + " has a blow");
			for (BossMove.Strike strike : move.strikes()) {
				check(strike.part().aim() == entry.getValue(), move.id() + " aims at " + entry.getValue() + ", not " + strike.part().aim());
				if (strike.tick() == BossMove.Strike.SCRIPTED) {
					continue;
				}
				Vec3 local = move.motion().at(strike.tick());
				// facing south: forward is +z, and its right is -x
				Vec3 boss = new Vec3(-local.z, local.y, local.x);
				double reach = switch (strike.shape()) {
					case HitShape.Sector sector -> sector.radius();
					case HitShape.Capsule capsule -> capsule.reach();
					case HitShape.Circle circle -> circle.radius();
					case HitShape.Grab grab -> grab.reach();
				};
				Vec3 front = boss.add(facing.scale(reach * 0.6));
				check(strike.shape().reaches(boss, facing, front, 0.3, true), move.id() + " at tick " + strike.tick() + " reaches " + reach * 0.6 + " in front");
				if (!allRound.contains(move.id())) {
					check(!strike.shape().reaches(boss, facing, boss.subtract(facing.scale(2.5)), 0.3, true), move.id() + " at tick " + strike.tick() + " misses behind");
				} else {
					check(strike.shape().reaches(boss, facing, boss.subtract(facing.scale(2.0)), 0.3, true), move.id() + " goes all the way round");
				}
			}
		}
	}

	/** The close-in moves: each blow where it aims. */
	private static void closeInMovesLand() {
		blowsLandWhereTheyAim(java.util.Map.ofEntries(
			java.util.Map.entry("double_sweep", PartRule.Aim.BODY),
			java.util.Map.entry("spin", PartRule.Aim.BODY),
			java.util.Map.entry("thrust", PartRule.Aim.BODY),
			java.util.Map.entry("triple_thrust", PartRule.Aim.BODY),
			java.util.Map.entry("overhead", PartRule.Aim.HEAD),
			java.util.Map.entry("upswing", PartRule.Aim.BODY),
			java.util.Map.entry("leg_sweep", PartRule.Aim.LEGS),
			java.util.Map.entry("butt", PartRule.Aim.BODY),
			java.util.Map.entry("lantern_bash", PartRule.Aim.HEAD),
			java.util.Map.entry("grab_throw", PartRule.Aim.BODY)), Set.of("spin"));
		for (String step : List.of("step_left", "step_right", "back_step", "circle", "back_jump")) {
			BossMove move = SnufferMoves.SET.get(step);
			check(move.kind() == BossMove.Kind.MOVEMENT && move.motion().at(move.length()).length() > 2.0, step + " carries the keeper somewhere");
		}
		check(SnufferMoves.SET.get("step_left").motion().at(20).z < 0 && SnufferMoves.SET.get("step_right").motion().at(20).z > 0, "the side steps go to their own sides");
	}

	/** A hunter caught by the claws and then gone is let go, not hauled back or slammed. */
	private static void grabLetsGoOfTheGone(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 2.0, Set.of(), 180.0F, 0.0F, true);
			Snuffer keeper = keeper(server, stage);
			keeper.setTarget(player);
			keeper.perform("grab_throw");
		});
		for (int tick = 0; tick < 60 && world.getServer().computeOnServer(server -> keeper(server, stage).moveTicks() < 20); tick++) {
			context.waitTick();
		}
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(player.getY() > stage.getY() + 0.5, "the claws have the hunter up off the floor: " + player.position());
			player.teleportTo(server.overworld(), stage.getX() + 8.5, stage.getY(), stage.getZ() - 8.5, Set.of(), 0.0F, 0.0F, true);
		});
		context.waitTicks(20);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(player.distanceToSqr(stage.getX() + 8.5, stage.getY(), stage.getZ() - 8.5) < 9.0, "a hunter gone from the claws stays gone: " + player.position());
			check(BodyService.get(player).get(BodyPart.TORSO) == BodyPart.MAX, "and is not slammed: " + BodyService.get(player));
			clear(server, stage);
		});
	}

	/** Against a hunter who rolls left early from every tell, the keeper picks blows that still cover where they roll to. */
	private static void coversALeftRoller() {
		PlayerModel roller = new PlayerModel();
		for (BossMove move : SnufferMoves.SET.moves()) {
			for (String family : move.families()) {
				for (int i = 0; i < 10; i++) {
					roller.learn(family, Response.ROLL_LEFT);
					roller.learnRoll(family, 15);
				}
			}
		}
		Vec3 hunter = new Vec3(0.0, 0.0, 3.5);
		// facing the keeper (north), their left is west
		Vec3 rolledTo = hunter.add(-3.2, 0.0, 0.0);
		int neutral = 0;
		int learned = 0;
		List<String> picks = new java.util.ArrayList<>();
		// among blows that fall at fixed spots, where covering the roll is a choice (a hook's pull reaches anyone in range)
		MoveSet fixed = new MoveSet(List.of(SnufferMoves.THRUST, SnufferMoves.LEG_SWEEP, SnufferMoves.UPSWING, SnufferMoves.OVERHEAD), List.of());
		List<String> neutralPicks = new java.util.ArrayList<>();
		for (int seed = 0; seed < 40; seed++) {
			BossMove before = new Planner(fixed, RandomSource.create(seed)).plan(standing(hunter, PlayerAction.IDLE), 3_000_000L).first();
			neutralPicks.add(before == null ? "nothing" : before.id());
			BossMove after = new Planner(fixed, RandomSource.create(seed)).plan(new Planner.Situation(Vec3.ZERO, 0.0F, "light", java.util.Map.of(), null, 0,
				hunter, Vec3.ZERO, PlayerAction.IDLE, 100.0F, roller, null, 0, Vec3.ZERO, 12.0), 3_000_000L).first();
			neutral += covers(before, rolledTo) ? 1 : 0;
			learned += covers(after, rolledTo) ? 1 : 0;
			picks.add(after == null ? "nothing" : after.id());
		}
		System.out.println("[boss] against a left roller: " + learned + "/40 cover the roll (" + neutral + "/40 before learning): " + picks + " before " + neutralPicks);
		check(learned > neutral, "learning a left roller makes it cover their roll more often: " + learned + " against " + neutral + " " + picks);
	}

	/** Whether a move's first blow, aimed south from the centre, reaches where a hunter rolled to. */
	private static boolean covers(final @org.jspecify.annotations.Nullable BossMove move, final Vec3 at) {
		if (move == null || move.strikes().isEmpty()) {
			return false;
		}
		BossMove.Strike strike = move.strikes().getFirst();
		if (strike.tick() == BossMove.Strike.SCRIPTED) {
			return move.range().contains(at.horizontalDistance());
		}
		Vec3 local = move.motion().at(strike.tick());
		Vec3 boss = new Vec3(-local.z, local.y, local.x);
		return strike.shape().reaches(boss, new Vec3(0.0, 0.0, 1.0), at, 0.3, true);
	}

	/** The moves at a distance: each blow where it aims. */
	private static void distanceMovesLand() {
		blowsLandWhereTheyAim(java.util.Map.of(
			"chain_sweep", PartRule.Aim.LEGS,
			"slam_wave", PartRule.Aim.LEGS,
			"delayed_slam", PartRule.Aim.LEGS,
			"hook_thrust", PartRule.Aim.BODY), Set.of());
		BossMove hookSlam = SnufferMoves.SET.get("hook_slam");
		check(hookSlam.strikes().get(1).part().aim() == PartRule.Aim.HEAD, "the hook slam ends with a blow to the head");
		check(SnufferMoves.SET.get("delayed_slam").tell() == SnufferMoves.SET.get("slam").tell(), "the delayed slam opens with the slam's own tell");
	}

	/** The slam's wave runs out along the floor past where the landing reaches, and takes the legs there. */
	private static void slamWaveReachesFurther(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 7.5, Set.of(), 180.0F, 0.0F, true);
			keeper(server, stage).perform("slam_wave");
		});
		// look as soon as the wave is spent: the keeper is free to start another move after it, and that one may land too
		for (int i = 0; i < 70; i++) {
			context.waitTicks(1);
			if (world.getServer().computeOnServer(server -> keeper(server, stage).currentMove() != life.shizune.mistinn.keeper.SnufferMoves.SLAM_WAVE)) {
				break;
			}
		}
		world.getServer().runOnServer(server -> {
			life.shizune.mistinn.body.Body body = BodyService.get(player(server));
			check(body.get(BodyPart.LEFT_LEG) < BodyPart.MAX && body.get(BodyPart.TORSO) == BodyPart.MAX, "seven blocks out, the wave takes the legs: " + body);
			clear(server, stage);
		});
	}

	/** The delayed slam hangs at the top of its leap well past when the slam would have landed, then comes down. */
	private static void delayedSlamHangs(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			player(server).teleportTo(server.overworld(), stage.getX() - 7.5, stage.getY(), stage.getZ() - 7.5, Set.of(), 0.0F, 0.0F, true);
			keeper(server, stage).perform("delayed_slam");
		});
		context.waitTicks(46);
		world.getServer().runOnServer(server -> check(keeper(server, stage).getY() > stage.getY() + 1.0, "still hanging in the air at tick 46: " + keeper(server, stage).position()));
		context.waitTicks(50);
		world.getServer().runOnServer(server -> {
			Snuffer keeper = keeper(server, stage);
			check(keeper.getY() < stage.getY() + 0.2 && !keeper.isNoGravity(), "and down again afterwards: " + keeper.position());
			clear(server, stage);
		});
	}

	/** Hooked at eight blocks and yanked in, the hunter runs onto the spear. */
	private static void hookThrustMeetsThePulled(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 6.5, Set.of(), 180.0F, 0.0F, true);
			Snuffer keeper = keeper(server, stage);
			keeper.setTarget(player);
			keeper.perform("hook_thrust");
		});
		context.waitTicks(30);
		world.getServer().runOnServer(server -> {
			life.shizune.mistinn.body.Body body = BodyService.get(player(server));
			check(body.get(BodyPart.TORSO) < BodyPart.MAX, "the hooked hunter runs onto the spear: " + body + " at " + player(server).position());
			clear(server, stage);
		});
	}

	/** Each feint shows exactly the tell of the move it imitates (sound and windup), lands nothing, and hands over to something. */
	private static void feintsBorrowTheirTell() {
		int feints = 0;
		for (BossMove move : SnufferMoves.SET.moves()) {
			if (move.kind() != BossMove.Kind.FEINT) {
				continue;
			}
			feints++;
			BossMove real = SnufferMoves.SET.get(move.imitates());
			check(move.tell() == real.tell() && move.windup() == real.windup(), move.id() + " shows " + real.id() + "'s tell, sound and length");
			check(move.strikes().isEmpty(), move.id() + " lands nothing");
			check(SnufferMoves.SET.links().stream().anyMatch(link -> link.from().equals(move.id())), move.id() + " turns into something");
		}
		check(feints >= 3, "the keeper has its feints: " + feints);
	}

	/** Each of the keeper's four signals is kept: for the next three seconds its next attack is of the family it showed. */
	private static void eachSignalIsKept() {
		java.util.Map<String, String> signals = java.util.Map.of("twirl", "thrust", "lantern_swing", "lantern", "chain_pull", "hook", "crouch", "lunge");
		RandomSource random = RandomSource.create(21);
		for (java.util.Map.Entry<String, String> signal : signals.entrySet()) {
			BossMove shown = SnufferMoves.SET.get(signal.getKey());
			check(shown.kind() == BossMove.Kind.SIGNAL && signal.getValue().equals(shown.signals()), signal.getKey() + " promises " + signal.getValue());
			check(SnufferMoves.SET.moves().stream().anyMatch(m -> m.kind() != BossMove.Kind.FEINT && !m.strikes().isEmpty() && m.in(signal.getValue()) && !m.in("dark")),
				"the promise of " + signal.getKey() + " can be kept in the light");
			Planner planner = new Planner(SnufferMoves.SET, RandomSource.create(31));
			for (int i = 0; i < 100; i++) {
				Planner.Situation any = situation(random);
				Planner.Situation owed = new Planner.Situation(any.bossPos(), any.bossYaw(), any.phase(), java.util.Map.of(), null, 0, any.hunterPos(),
					any.hunterVelocity(), any.hunterAction(), any.hunterStamina(), any.hunter(), signal.getValue(), Planner.SIGNAL_TICKS, any.roomCentre(), any.roomRadius());
				Planner.Plan plan = planner.plan(owed, 3_000_000L);
				for (int step = 0; step < plan.steps().size() && plan.starts().get(step) < Planner.SIGNAL_TICKS; step++) {
					BossMove move = plan.steps().get(step);
					check(move.kind() != BossMove.Kind.FEINT && move.kind() != BossMove.Kind.SIGNAL, "nothing feints or signals while " + signal.getKey() + " is owed: " + plan.steps());
					if (!move.strikes().isEmpty()) {
						check(move.in(signal.getValue()), signal.getKey() + " promised " + signal.getValue() + ": " + plan.steps());
						break;
					}
				}
			}
		}
	}

	/** The dark half's moves are planned only in the dark, and land where they aim. */
	private static void darkMovesStayInTheDark() {
		blowsLandWhereTheyAim(java.util.Map.of("mist_ambush", PartRule.Aim.BODY, "soul_sweep", PartRule.Aim.BODY), Set.of());
		Planner planner = new Planner(SnufferMoves.SET, RandomSource.create(41));
		RandomSource random = RandomSource.create(43);
		boolean seenInDark = false;
		for (int i = 0; i < 300; i++) {
			Planner.Situation any = situation(random);
			Planner.Plan plan = planner.plan(any, 3_000_000L);
			for (BossMove move : plan.steps()) {
				check(!move.in("dark") || "dark".equals(any.phase()), move.id() + " is planned in the light");
				seenInDark |= move.in("dark");
			}
		}
		check(seenInDark, "and the dark moves are planned in the dark");
	}

	/** The ambush is announced long before it shows itself: gone, then behind the hunter, then the blow. */
	private static void mistAmbushHasATell(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		BossMove ambush = SnufferMoves.SET.get("mist_ambush");
		check(ambush.tell() != null && SnufferMoves.AMBUSH_APPEARS >= 14, "its sound comes at least 14 ticks before it reappears");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 6.5, Set.of(), 180.0F, 0.0F, true);
			Snuffer keeper = keeper(server, stage);
			keeper.setTarget(player);
			keeper.perform("mist_ambush");
		});
		context.waitTicks(10);
		world.getServer().runOnServer(server -> check(keeper(server, stage).isInvisible(), "gone into the dark"));
		context.waitTicks(SnufferMoves.AMBUSH_APPEARS - 10 + 2);
		world.getServer().runOnServer(server -> {
			Snuffer keeper = keeper(server, stage);
			ServerPlayer player = player(server);
			Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
			Vec3 to = keeper.position().subtract(player.position());
			check(!keeper.isInvisible(), "back out of the dark");
			check(look.x * to.x + look.z * to.z < 0.0, "behind the hunter: " + keeper.position() + " hunter " + player.position());
		});
		context.waitTicks(12);
		world.getServer().runOnServer(server -> {
			check(BodyService.get(player(server)).get(BodyPart.TORSO) < BodyPart.MAX, "and the blow lands from behind: " + BodyService.get(player(server)));
			clear(server, stage);
		});
	}

	/** Cut off mid-ambush or mid-hang (lit lamps stagger it), the keeper is seen again and falls: nothing outlives the move that did it. */
	private static void interruptedMovesLetGo(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 6.5, Set.of(), 180.0F, 0.0F, true);
			Snuffer keeper = keeper(server, stage);
			keeper.setTarget(player);
			keeper.perform("mist_ambush");
		});
		context.waitTicks(10);
		world.getServer().runOnServer(server -> {
			Snuffer keeper = keeper(server, stage);
			check(keeper.isInvisible(), "gone into the dark");
			keeper.perform("stagger");
		});
		context.waitTicks(2);
		world.getServer().runOnServer(server -> check(!keeper(server, stage).isInvisible(), "a staggered keeper is seen, even cut off mid-ambush"));
		world.getServer().runOnServer(server -> clear(server, stage));
		world.getServer().runOnServer(server -> {
			player(server).teleportTo(server.overworld(), stage.getX() - 7.5, stage.getY(), stage.getZ() - 7.5, Set.of(), 0.0F, 0.0F, true);
			keeper(server, stage).perform("delayed_slam");
		});
		context.waitTicks(36);
		world.getServer().runOnServer(server -> {
			Snuffer keeper = keeper(server, stage);
			check(keeper.isNoGravity(), "hanging at the top of the leap");
			keeper.perform("stagger");
		});
		context.waitTicks(40);
		world.getServer().runOnServer(server -> {
			Snuffer keeper = keeper(server, stage);
			check(!keeper.isNoGravity() && keeper.getY() < stage.getY() + 0.2, "and a stagger brings it down: " + keeper.position());
			clear(server, stage);
		});
	}

	/** A hunter backed into a corner of a square room, further from its centre than the room's radius, is still seen and gone after. */
	private static void seesHuntersInTheCorners(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		BlockPos corner = stage.offset(12, -1, 12);
		world.getServer().runOnServer(server -> {
			server.overworld().setBlockAndUpdate(corner, Blocks.STONE.defaultBlockState());
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), corner.getX() + 0.5, stage.getY(), corner.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			keeper(server, stage).getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(40.0);
		});
		context.waitTicks(10);
		world.getServer().runOnServer(server -> {
			Snuffer keeper = keeper(server, stage);
			check(keeper.getTarget() == player(server), "a hunter in the corner, 17 blocks from the centre, is still the keeper's: " + keeper.getTarget());
			server.overworld().setBlockAndUpdate(corner, Blocks.AIR.defaultBlockState());
			clear(server, stage);
		});
	}

	/** A blow carried along a dash (the slide) counts where it reaches along the way, not only where it starts. */
	private static void carriedBlowsCount() {
		double landing = new Planner(SnufferMoves.SET, RandomSource.create(3)).landing(standing(new Vec3(0.0, 0.0, 6.0), PlayerAction.IDLE), SnufferMoves.SLIDE);
		check(landing > 0.3, "six blocks away, the slide reaches the hunter along the way: " + landing);
	}

	/** Its four lamps are its room's, wherever in the room it stands: lit while it is off to one side, they still stagger it. */
	private static void lampsAreTheRooms(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		List<BlockPos> lamps = List.of(stage.offset(-8, 0, -8), stage.offset(8, 0, -8), stage.offset(-8, 0, 8), stage.offset(8, 0, 8));
		world.getServer().runOnServer(server -> {
			for (BlockPos lamp : lamps) {
				server.overworld().setBlockAndUpdate(lamp, life.shizune.mistinn.registry.MistinnBlocks.LAMP_STAND.defaultBlockState());
			}
			keeper(server, stage).setHealth(Snuffer.MAX_HEALTH * 0.4F);
		});
		context.waitTicks(5);
		world.getServer().runOnServer(server -> {
			Snuffer keeper = keeper(server, stage);
			check(keeper.dark(), "below half, the room has gone dark");
			String bar = keeper.barName().getString();
			check(bar.contains("◇◇◇◇"), "in the dark its health bar shows its lamps, none lit: " + bar);
			// off to one side of its room: the far lamps are more than twelve blocks from where it stands
			keeper.teleportTo(stage.getX() + 6.5, stage.getY(), stage.getZ() + 6.5);
			for (BlockPos lamp : lamps) {
				server.overworld().setBlockAndUpdate(lamp, server.overworld().getBlockState(lamp).setValue(life.shizune.mistinn.keeper.LampStandBlock.LIT, true));
			}
		});
		context.waitTicks(3);
		world.getServer().runOnServer(server -> {
			check(keeper(server, stage).staggered(), "four lit lamps stagger it, wherever in its room it stands");
			String bar = keeper(server, stage).barName().getString();
			check(bar.contains("◆◆◆◆"), "and its bar shows all four lit: " + bar);
			for (BlockPos lamp : lamps) {
				server.overworld().setBlockAndUpdate(lamp, Blocks.AIR.defaultBlockState());
			}
			clear(server, stage);
		});
	}

	/** A hunter standing in a far corner of the room is walked down and struck, not thrust at from out of reach. */
	private static void reachesACorneredHunter(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			for (BlockPos floor : BlockPos.betweenClosed(stage.offset(9, -1, 9), stage.offset(14, -1, 14))) {
				server.overworld().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
			}
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), stage.getX() + 12.5, stage.getY(), stage.getZ() + 12.5, Set.of(), 0.0F, 0.0F, true);
			BodyService.restoreAll(player);
			keeper(server, stage).getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(40.0);
		});
		boolean struck = false;
		List<String> moves = new java.util.ArrayList<>();
		for (int i = 0; i < 20 && !struck; i++) {
			context.waitTicks(10);
			struck = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				// pinned in the corner: the hunter does not move
				player.teleportTo(server.overworld(), stage.getX() + 12.5, stage.getY(), stage.getZ() + 12.5, Set.of(), player.getYRot(), player.getXRot(), true);
				BossMove move = keeper(server, stage).currentMove();
				moves.add((move == null ? "-" : move.id()) + "@" + String.format("%.1f", keeper(server, stage).distanceTo(player)));
				return BodyService.get(player).total() < life.shizune.mistinn.body.Body.FULL.total();
			});
		}
		check(struck, "a cornered hunter is reached and struck within ten seconds: " + moves);
		world.getServer().runOnServer(server -> {
			for (BlockPos floor : BlockPos.betweenClosed(stage.offset(11, -1, 11), stage.offset(14, -1, 14))) {
				server.overworld().setBlockAndUpdate(floor, Blocks.AIR.defaultBlockState());
			}
			clear(server, stage);
		});
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
		// a wound left open by one check would go on bleeding into the next
		life.shizune.mistinn.body.WoundService.clearAll(player(server));
	}

	static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
