package life.shizune.mistinn.test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.boss.BossMove;
import life.shizune.mistinn.enemy.NoiseService;
import life.shizune.mistinn.enemy.PackService;
import life.shizune.mistinn.enemy.WindupService;
import life.shizune.mistinn.hardship.Ferocity;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.keeper.SnufferMoves;
import life.shizune.mistinn.registry.MistinnEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;

/**
 * Rust Anchor's creatures and keepers fight without mercy, and Normal's as they were made: a zombie's
 * windup, its feints and held blows, how soon it strikes again, a pack, its ears and its speed; a
 * keeper's moves, run faster, and how late it sees.
 */
public final class MistinnFerocityTest implements FabricClientGameTest {
	private static final String MOVE = "sweep";

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel")) {
			return;
		}
		// the numbers: Normal is the game as made
		check(Ferocity.of(Hardship.NORMAL) == Ferocity.MADE && Ferocity.of(Hardship.EASY) == Ferocity.MADE, "Easy and Normal fight as made");
		check(Ferocity.MADE.windupTicks(7) == 7 && Ferocity.gap(20, Ferocity.MADE.attackGap()) == 20, "made windups and gaps are untouched");
		check(Ferocity.MERCILESS.windupTicks(7) == 4 && Ferocity.MERCILESS.windupTicks(3) == 2, "a Rust Anchor windup is barely half: " + Ferocity.MERCILESS.windupTicks(7));
		check(Ferocity.gap(20, Ferocity.MERCILESS.attackGap()) == 10 && Ferocity.gap(20, Ferocity.MERCILESS.bowGap()) == 11, "blows and shots come about twice as often");
		List<PackService.Member> four = List.of(member(1), member(2), member(3), member(4));
		check(PackService.assign(four, 0.0).stream().filter(PackService.Order::strike).count() == 2, "a made pack sends two in");
		check(PackService.assign(four, 0.0, Ferocity.MERCILESS.strikers()).stream().filter(PackService.Order::strike).count() == 3,
			"a Rust Anchor pack sends three in");

		Hardship.choose(Hardship.RUST_ANCHOR);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runOnServer(server -> {
				check(Hardship.of(server) == Hardship.RUST_ANCHOR, "the world is a Rust Anchor world");
				ServerPlayer player = player(server);
				player.setGameMode(GameType.SURVIVAL);
				check(NoiseService.radius(player, NoiseService.Noise.SWING) == NoiseService.Noise.SWING.radius * 1.5, "a swing carries half as far again");
			});
			context.waitTicks(5);
			checkWindups(context, world);
			checkGap(context, world);
			checkKeeper(context, world);
			WindupService.force(null);
		}
	}

	/** A zombie's windup in each world, and what feints and held blows do. */
	private static void checkWindups(final ClientGameTestContext context, final TestSingleplayerContext world) {
		int[] zombie = new int[1];
		world.getServer().runOnServer(server -> {
			Zombie z = zombie(server, 3.0, true);
			zombie[0] = z.getId();
			check(z.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Mistinn.id("rust_anchor_hunt")) != null, "a Rust Anchor zombie is quicker");
			check(z.getAttribute(Attributes.MOVEMENT_SPEED).getValue() > z.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue(),
				"and moves faster for it");
			WindupService.force(WindupService.Kind.PLAIN);
			check(length(server, z) == 4, "a Rust Anchor zombie winds up for 4 ticks instead of 7: " + length(server, z));
			WindupService.interrupt(z);
			WindupService.force(WindupService.Kind.HELD);
			int held = length(server, z);
			check(held >= 7 && held <= 11, "a held blow waits a few ticks past its windup: " + held);
			WindupService.interrupt(z);
			WindupService.force(WindupService.Kind.FEINT);
			check(length(server, z) == 7 && WindupService.feinting(z), "a feint shows the whole windup as made");
		});
		long[] feintStart = new long[1];
		world.getServer().runOnServer(server -> feintStart[0] = windup(server, zombie[0]).start());
		context.waitTicks(9);
		world.getServer().runOnServer(server -> {
			Zombie z = (Zombie) server.overworld().getEntity(zombie[0]);
			WindupService.Windup windup = z.getAttached(WindupService.WINDUP);
			check(windup != null && !WindupService.feinting(z) && windup.start() > feintStart[0] && windup.length() == 2,
				"the feint stops short and the real blow comes from a 2-tick windup: " + windup);
		});
		context.waitTicks(4);
		world.getServer().runOnServer(server -> {
			WindupService.Windup windup = windup(server, zombie[0]);
			check(windup == null || windup.released(), "and lets it go");
			// the same zombie in a normal world
			server.overworld().setAttached(Hardship.WORLD, Hardship.NORMAL);
			Zombie z = (Zombie) server.overworld().getEntity(zombie[0]);
			WindupService.interrupt(z);
			z.removeAttached(WindupService.WINDUP);
			WindupService.force(null);
			check(length(server, z) == 7 && !WindupService.feinting(z), "a normal zombie winds up for its 7 ticks, no tricks");
			WindupService.interrupt(z);
			check(NoiseService.radius(player(server), NoiseService.Noise.SWING) == NoiseService.Noise.SWING.radius, "a normal shore hears as made");
			z.discard();
			server.overworld().setAttached(Hardship.WORLD, Hardship.RUST_ANCHOR);
		});
		context.waitTicks(2);
	}

	/** How often a zombie with its own mind comes at the hunter, in each world. */
	private static void checkGap(final ClientGameTestContext context, final TestSingleplayerContext world) {
		WindupService.force(WindupService.Kind.PLAIN);
		int merciless = countWindups(context, world, Hardship.RUST_ANCHOR);
		int made = countWindups(context, world, Hardship.NORMAL);
		world.getServer().runOnServer(server -> server.overworld().setAttached(Hardship.WORLD, Hardship.RUST_ANCHOR));
		check(merciless >= made + 2, "a Rust Anchor zombie strikes far more often: " + merciless + " windups against " + made);
		WindupService.force(null);
	}

	private static int countWindups(final ClientGameTestContext context, final TestSingleplayerContext world, final Hardship hardship) {
		int[] id = new int[1];
		world.getServer().runOnServer(server -> {
			server.overworld().setAttached(Hardship.WORLD, hardship);
			// the hunter stands where they are, so only how soon the zombie strikes again counts
			player(server).getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
			Zombie z = zombie(server, 1.2, false);
			z.setTarget(player(server));
			id[0] = z.getId();
		});
		Set<Long> starts = new HashSet<>();
		for (int tick = 0; tick < 120; tick++) {
			context.waitTick();
			Long start = world.getServer().computeOnServer(server -> {
				// only the blows' timing is measured: the hunter is made whole after each
				mendHunter(player(server));
				var z = server.overworld().getEntity(id[0]);
				WindupService.Windup windup = z == null ? null : z.getAttached(WindupService.WINDUP);
				return windup == null ? null : windup.start();
			});
			if (start != null) {
				starts.add(start);
			}
		}
		world.getServer().runOnServer(server -> {
			var z = server.overworld().getEntity(id[0]);
			if (z != null) {
				z.discard();
			}
		});
		return starts.size();
	}

	/** A keeper's move runs faster in Rust Anchor, its clip with it, and it sees its hunter sooner. */
	private static void checkKeeper(final ClientGameTestContext context, final TestSingleplayerContext world) {
		int[] id = new int[1];
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			Snuffer keeper = MistinnEntities.SNUFFER.create(level, EntitySpawnReason.COMMAND);
			keeper.snapTo(player.getX() + 8.0, player.getY(), player.getZ(), 0.0F, 0.0F);
			keeper.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(0.0);
			level.addFreshEntity(keeper);
			id[0] = keeper.getId();
		});
		context.waitTicks(3);
		int merciless = timeMove(context, world, id[0]);
		world.getServer().runOnServer(server -> {
			Snuffer keeper = (Snuffer) server.overworld().getEntity(id[0]);
			check(keeper.brain().sight() == 1 && keeper.ferocity().poise() == 4, "a Rust Anchor keeper sees at once and takes four heavy blows to rock");
			keeper.perform(MOVE);
		});
		Set<Float> rates = new HashSet<>();
		for (int tick = 0; tick < 6; tick++) {
			context.waitTick();
			rates.add(world.getServer().computeOnServer(server -> ((Snuffer) server.overworld().getEntity(id[0])).moveRate()));
		}
		check(rates.equals(Set.of(1.0F, 2.0F)), "the client is told each tick how many steps the tell takes next, one and two by turns: " + rates);
		context.waitTicks(80);
		world.getServer().runOnServer(server -> server.overworld().setAttached(Hardship.WORLD, Hardship.NORMAL));
		context.waitTicks(2);
		int made = timeMove(context, world, id[0]);
		BossMove move = SnufferMoves.SET.get(MOVE);
		check(made >= move.length() - 1 && made <= move.length() + 1, "a normal keeper's move takes its " + move.length() + " ticks: " + made);
		check(merciless <= made * 0.75, "a Rust Anchor keeper's move is far quicker: " + merciless + " against " + made);
		world.getServer().runOnServer(server -> {
			Snuffer keeper = (Snuffer) server.overworld().getEntity(id[0]);
			check(keeper.brain().sight() == 4 && keeper.ferocity().poise() == 3, "a normal keeper sees and staggers as made");
			keeper.discard();
			server.overworld().setAttached(Hardship.WORLD, Hardship.RUST_ANCHOR);
		});
	}

	/** Ticks from starting the move until the keeper is out of it (done, or chained into another). */
	private static int timeMove(final ClientGameTestContext context, final TestSingleplayerContext world, final int id) {
		world.getServer().runOnServer(server -> ((Snuffer) server.overworld().getEntity(id)).perform(MOVE));
		BossMove move = SnufferMoves.SET.get(MOVE);
		for (int tick = 1; tick <= 200; tick++) {
			context.waitTick();
			boolean done = world.getServer().computeOnServer(server -> ((Snuffer) server.overworld().getEntity(id)).currentMove() != move);
			if (done) {
				context.waitTicks(20);
				return tick;
			}
		}
		throw new AssertionError("the keeper never finished its move");
	}

	private static void mendHunter(final ServerPlayer player) {
		life.shizune.mistinn.body.BodyService.set(player, life.shizune.mistinn.body.Body.FULL);
		player.setHealth(player.getMaxHealth());
		player.getFoodData().setFoodLevel(20);
	}

	private static Zombie zombie(final MinecraftServer server, final double ahead, final boolean still) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		z.snapTo(player.getX() + ahead, player.getY(), player.getZ(), 90.0F, 0.0F);
		z.setNoAi(still);
		z.setPersistenceRequired();
		level.addFreshEntity(z);
		return z;
	}

	/** Starts a windup at the hunter and reads how long it is. */
	private static int length(final MinecraftServer server, final Zombie z) {
		if (!z.hasAttached(WindupService.WINDUP)) {
			WindupService.intercept(z, server.overworld(), player(server));
		}
		return z.getAttached(WindupService.WINDUP).length();
	}

	private static WindupService.Windup windup(final MinecraftServer server, final int id) {
		var z = server.overworld().getEntity(id);
		return z == null ? null : z.getAttached(WindupService.WINDUP);
	}

	private static PackService.Member member(final int id) {
		return new PackService.Member(id, id, 3.0, false, false, false);
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
