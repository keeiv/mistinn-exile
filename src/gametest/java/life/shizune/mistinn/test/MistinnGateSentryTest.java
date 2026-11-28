package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.terra.GateSentry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * The gate sentry: knelt at its post it sleeps through a hunter standing off and one creeping close, and wakes for one who
 * walks up; it rises slowly. Close in front it raises its fists and slams the ground before it, striking what stands
 * there and not what stands behind; its fists stick fast after, and then its copper turns nothing. Its copper front turns
 * most of a blow, the crystal on its back takes more. Kept off, it charges; sidestepped, it runs on into a wall and is
 * stuck. With nothing to chase, it walks back to its post and kneels again.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnGateSentryTest implements FabricClientGameTest {
	private static final int Y = 200;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 6000");
			context.waitTicks(20);
			BlockPos[] post = new BlockPos[1];
			GateSentry[] sentry = new GateSentry[1];
			Cow[] cow = new Cow[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				post[0] = player.blockPosition().atY(Y);
				BlockPos p = post[0];
				for (int x = -10; x <= 10; x++) {
					for (int z = -18; z <= 12; z++) {
						level.setBlock(p.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
						for (int y = 0; y <= 4; y++) {
							level.setBlock(p.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
						}
					}
				}
				// it faces north, its front to -z
				sentry[0] = MistinnEntities.GATE_SENTRY.create(level, EntitySpawnReason.EVENT);
				sentry[0].snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 180.0F, 0.0F);
				sentry[0].post(p, 180.0F);
				// held still while the hunter is set down (a hunter carried far in a tick sounds like running)
				sentry[0].setNoAi(true);
				level.addFreshEntity(sentry[0]);
				player.teleportTo(level, p.getX() + 0.5, p.getY(), p.getZ() - 6.5, Set.of(), 0.0F, 10.0F, true);
			});
			settle(context, world, sentry[0]);
			context.waitTicks(30);
			check(world.getServer().computeOnServer(server -> sentry[0].dormant()), "knelt at its post, a hunter standing off does not wake it");
			context.takeScreenshot(TestScreenshotOptions.of("sentry-knelt").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("sentry")));
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.setShiftKeyDown(true);
				sentry[0].setNoAi(true);
				player.teleportTo(server.overworld(), post[0].getX() + 0.5, Y, post[0].getZ() - 3.2, Set.of(), 0.0F, 10.0F, true);
			});
			settle(context, world, sentry[0]);
			context.waitTicks(10);
			check(world.getServer().computeOnServer(server -> sentry[0].dormant()), "one creeping close does not wake it");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.setShiftKeyDown(false);
				// one standing behind it, to see what its slam reaches
				cow[0] = EntityTypes.COW.create(server.overworld(), EntitySpawnReason.EVENT);
				cow[0].snapTo(post[0].getX() + 0.5, Y, post[0].getZ() + 2.8, 0.0F, 0.0F);
				cow[0].setNoAi(true);
				server.overworld().addFreshEntity(cow[0]);
			});
			context.waitTicks(4);
			world.getServer().runOnServer(server -> {
				check(!sentry[0].dormant() && sentry[0].move() == GateSentry.Move.WAKING, "one walking up wakes it, and it rises slowly");
				check(sentry[0].getTarget() == player(server), "and it turns on them");
			});
			until(context, world, server -> (sentry[0].move() == GateSentry.Move.SLAM_WINDUP), GateSentry.WAKE + 40);
			context.waitTicks(GateSentry.SLAM_WINDUP - 4);
			context.takeScreenshot(TestScreenshotOptions.of("sentry-slam").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("sentry")));
			until(context, world, server -> (sentry[0].move() == GateSentry.Move.RECOVER), 40);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(player.getLastHurtByMob() == sentry[0], "close in front, the slam lands");
				check(cow[0].getHealth() == cow[0].getMaxHealth(), "behind it, nothing is struck");
				check(sentry[0].open(), "its fists stick fast after");
				player.teleportTo(server.overworld(), post[0].getX() + 0.5, Y, post[0].getZ() - 2.5, Set.of(), 0.0F, 10.0F, true);
				check(sentry[0].share(hit(server, player)) == 1.0F, "and then its copper turns nothing");
			});
			until(context, world, server -> (sentry[0].move() == GateSentry.Move.NONE), GateSentry.RECOVER + 10);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				cow[0].discard();
				sentry[0].setYRot(180.0F);
				BlockPos p = sentry[0].blockPosition();
				player.teleportTo(level, p.getX() + 0.5, Y, p.getZ() - 2.0, Set.of(), 0.0F, 10.0F, true);
				check(sentry[0].share(hit(server, player)) == GateSentry.FRONT, "its copper front turns most of a blow");
				player.teleportTo(level, p.getX() + 2.5, Y, p.getZ() + 0.5, Set.of(), 0.0F, 10.0F, true);
				check(sentry[0].share(hit(server, player)) == 1.0F, "its side, as it is");
				player.teleportTo(level, p.getX() + 0.5, Y, p.getZ() + 2.5, Set.of(), 0.0F, 10.0F, true);
				check(sentry[0].share(hit(server, player)) == GateSentry.BACK, "the crystal on its back, more");
				float health = sentry[0].getHealth();
				sentry[0].setInvulnerableTime(0);
				sentry[0].hurtServer(level, hit(server, player), 6.0F);
				float back = health - sentry[0].getHealth();
				player.teleportTo(level, p.getX() + 0.5, Y, p.getZ() - 2.0, Set.of(), 0.0F, 10.0F, true);
				health = sentry[0].getHealth();
				sentry[0].setInvulnerableTime(0);
				sentry[0].hurtServer(level, hit(server, player), 6.0F);
				float front = health - sentry[0].getHealth();
				check(back > front * 5.0F, "a blow on its back does far more than one on its front: " + back + " to " + front);
				// kept off, it charges: the hunter far before it, a wall farther on
				sentry[0].setYRot(180.0F);
				sentry[0].setPos(post[0].getX() + 0.5, Y, post[0].getZ() + 0.5);
				for (int x = -3; x <= 3; x++) {
					for (int y = 0; y <= 3; y++) {
						level.setBlock(post[0].offset(x, y, -12), Blocks.STONE.defaultBlockState(), 2);
					}
				}
				player.teleportTo(level, post[0].getX() + 0.5, Y, post[0].getZ() - 8.5, Set.of(), 0.0F, 10.0F, true);
				sentry[0].hasten();
			});
			until(context, world, server -> (sentry[0].move() == GateSentry.Move.RUSH_WINDUP), 60);
			world.getServer().runOnServer(server -> {
				// sidestepped
				ServerPlayer player = player(server);
				player.teleportTo(server.overworld(), post[0].getX() + 7.5, Y, post[0].getZ() - 8.5, Set.of(), 0.0F, 10.0F, true);
			});
			until(context, world, server -> (sentry[0].move() == GateSentry.Move.STUCK), GateSentry.RUSH_WINDUP + GateSentry.RUSH + 10);
			world.getServer().runOnServer(server -> {
				check(sentry[0].getZ() < post[0].getZ() - 9.0 && sentry[0].open(), "sidestepped, it runs on into the wall and is stuck: " + sentry[0].getZ());
				// nothing more to chase
				player(server).setGameMode(GameType.CREATIVE);
				sentry[0].setTarget(null);
				sentry[0].bored();
			});
			try {
				until(context, world, server -> (sentry[0].dormant()), GateSentry.STUCK + 400);
			} catch (AssertionError e) {
				world.getServer().runOnServer(server -> System.out.println("[mistinn sentry] home? at " + sentry[0].position() + " post " + post[0]
					+ " move " + sentry[0].move() + " target " + sentry[0].getTarget() + " heard " + life.shizune.mistinn.enemy.NoiseService.heard(sentry[0])
					+ " nav " + sentry[0].getNavigation().isDone() + " yaw " + sentry[0].getYRot()));
				throw e;
			}
			world.getServer().runOnServer(server -> check(sentry[0].blockPosition().distManhattan(post[0]) <= 1,
				"with nothing to chase, it walks back to its post and kneels: " + sentry[0].blockPosition()));
			// standing, turned three-quarters to the camera
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				sentry[0].setNoAi(true);
				sentry[0].wake(server.overworld());
				player.teleportTo(server.overworld(), post[0].getX() - 3.0, Y + 0.5, post[0].getZ() - 4.5, Set.of(), -35.0F, 12.0F, true);
			});
			context.waitTicks(GateSentry.WAKE + 10);
			context.takeScreenshot(TestScreenshotOptions.of("sentry-standing").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("sentry")));
		}
	}

	/** Waits, a tick at a time, until this holds on the server. */
	private static void until(final ClientGameTestContext context, final TestSingleplayerContext world,
		final java.util.function.Predicate<MinecraftServer> holds, final int ticks) {
		for (int t = 0; t < ticks; t++) {
			if (world.getServer().computeOnServer(holds::test)) {
				return;
			}
			context.waitTick();
		}
		throw new AssertionError("timed out after " + ticks + " ticks");
	}

	/** The hunter set down, not running, nothing heard: the sentry minds the world again. */
	private static void settle(final ClientGameTestContext context, final TestSingleplayerContext world, final GateSentry sentry) {
		context.waitTicks(5);
		context.runOnClient(mc -> mc.player.setSprinting(false));
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			player(server).setSprinting(false);
			life.shizune.mistinn.enemy.NoiseService.forgetAll();
			sentry.setNoAi(false);
		});
	}

	private static DamageSource hit(final MinecraftServer server, final ServerPlayer player) {
		return server.overworld().damageSources().playerAttack(player);
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn sentry] ok: " + what);
	}
}
