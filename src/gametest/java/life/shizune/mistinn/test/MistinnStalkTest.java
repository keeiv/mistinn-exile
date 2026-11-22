package life.shizune.mistinn.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.enemy.NoiseService;
import life.shizune.mistinn.enemy.PackService;
import life.shizune.mistinn.enemy.StalkGoal;
import life.shizune.mistinn.enemy.WindupService;
import life.shizune.mistinn.registry.MistinnAttachments;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** 被注視時靜止: the walking dead keep still while watched from afar and come on the moment the hunter looks away. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnStalkTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty easy");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			context.waitTicks(10);
			BlockPos arena = world.getServer().computeOnServer(MistinnStalkTest::arena);
			stillWhileWatched(context, world, arena);
			aStareHoldsOnlySoLong(context, world, arena);
			closeInItHunts(context, world, arena);
			aPackAtTheEdge(context, world, arena);
			othersDoNotStalk(context, world, arena);
		}
	}

	/** A long flat strip, open air: the hunter at z = 0 and creatures out along +z. */
	private static BlockPos arena(final MinecraftServer server) {
		BlockPos pos = player(server).blockPosition().above(40).immutable();
		for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-6, -1, -6), pos.offset(6, -1, 40))) {
			server.overworld().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		}
		for (BlockPos air : BlockPos.betweenClosed(pos.offset(-6, 0, -6), pos.offset(6, 5, 40))) {
			server.overworld().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
		}
		return pos;
	}

	/** The hunter at the near end facing {@code yaw} (0 looks down the strip at the creatures, 180 away). */
	private static void face(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena, final float yaw) {
		world.getServer().runOnServer(server -> player(server).teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, Set.of(), yaw, 0.0F, true));
		context.waitTicks(2);
	}

	private static int creature(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena, final EntityType<? extends Mob> type, final double dz) {
		return creatures(context, world, arena, type, new double[][] {{0.0, dz}}).getFirst();
	}

	/** Clears the strip and sets creatures hunting the hunter at these (dx, dz) offsets. */
	private static List<Integer> creatures(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena, final EntityType<? extends Mob> type,
		final double[][] offsets) {
		List<Integer> ids = world.getServer().computeOnServer(server -> {
			for (var entity : server.overworld().getEntities((net.minecraft.world.entity.Entity) null, new AABB(arena).inflate(50.0), e -> !(e instanceof ServerPlayer))) {
				entity.discard();
			}
			NoiseService.forgetAll();
			PackService.forgetAll();
			ServerPlayer player = player(server);
			keepWhole(player);
			List<Integer> spawned = new ArrayList<>();
			for (double[] offset : offsets) {
				Mob mob = type.create(server.overworld(), EntitySpawnReason.COMMAND);
				mob.setPersistenceRequired();
				mob.snapTo(arena.getX() + 0.5 + offset[0], arena.getY(), arena.getZ() + 0.5 + offset[1], 180.0F, 0.0F);
				server.overworld().addFreshEntity(mob);
				mob.setTarget(player);
				spawned.add(mob.getId());
			}
			return spawned;
		});
		context.waitTicks(2);
		return ids;
	}

	private static void keepWhole(final ServerPlayer player) {
		BodyService.restoreAll(player);
		player.removeAttached(MistinnAttachments.WOUNDS);
		player.setHealth(player.getMaxHealth());
	}

	/** The nearest and the farthest of these creatures from the hunter. */
	private static double[] spread(final TestSingleplayerContext world, final List<Integer> ids) {
		return world.getServer().computeOnServer(server -> {
			double near = Double.MAX_VALUE;
			double far = 0.0;
			for (int id : ids) {
				Mob mob = (Mob) server.overworld().getEntity(id);
				mob.setTarget(player(server));
				double d = mob.position().distanceTo(player(server).position());
				near = Math.min(near, d);
				far = Math.max(far, d);
			}
			return new double[] {near, far};
		});
	}

	private static double distance(final TestSingleplayerContext world, final int id) {
		return world.getServer().computeOnServer(server -> {
			Mob mob = (Mob) server.overworld().getEntity(id);
			mob.setTarget(player(server));
			return mob.position().distanceTo(player(server).position());
		});
	}

	private static void stillWhileWatched(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		face(context, world, arena, 0.0F);
		int id = creature(context, world, arena, EntityTypes.HUSK, 22.0);
		check(world.getServer().computeOnServer(server -> StalkGoal.watched(player(server), (net.minecraft.world.entity.PathfinderMob) server.overworld().getEntity(id))), "the hunter looks at it");
		double start = distance(world, id);
		context.waitTicks(40);
		double watched = distance(world, id);
		check(start - watched < 0.6, "watched, it does not come on: " + start + " -> " + watched);
		face(context, world, arena, 180.0F);
		context.waitTicks(40);
		double away = distance(world, id);
		check(watched - away > 2.0, "the moment the hunter turns away it comes on: " + watched + " -> " + away);
	}

	private static void aStareHoldsOnlySoLong(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		face(context, world, arena, 0.0F);
		int id = creature(context, world, arena, EntityTypes.HUSK, 24.0);
		context.waitTicks(StalkGoal.HOLD + 5);
		double held = distance(world, id);
		context.waitTicks(40);
		double after = distance(world, id);
		check(held - after > 1.0, "after a long stare it comes on anyway: " + held + " -> " + after);
	}

	private static void closeInItHunts(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		face(context, world, arena, 0.0F);
		int id = creature(context, world, arena, EntityTypes.HUSK, 10.0);
		double start = distance(world, id);
		context.waitTicks(30);
		double after = distance(world, id);
		check(start - after > 1.0, "within twelve blocks it hunts as it always did, watched or not: " + start + " -> " + after);
	}

	/**
	 * Three husks at fourteen blocks: inside the pack's range and outside where the walking dead stop to stare. Watched,
	 * the stare wins and they hold at the edge; once it wears off they close in and the pack takes over, still never
	 * more than two winding up at once.
	 */
	private static void aPackAtTheEdge(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		face(context, world, arena, 0.0F);
		List<Integer> ids = creatures(context, world, arena, EntityTypes.HUSK, new double[][] {{-3.0, 14.0}, {0.0, 14.0}, {3.0, 14.0}});
		double start = spread(world, ids)[0];
		context.waitTicks(30);
		double held = spread(world, ids)[0];
		check(start - held < 0.6, "watched, the pack holds at the edge: " + start + " -> " + held);
		int mostAtOnce = 0;
		for (int tick = 0; tick < 240; tick++) {
			int winding = world.getServer().computeOnServer(server -> {
				keepWhole(player(server));
				int count = 0;
				for (int id : ids) {
					if (server.overworld().getEntity(id) instanceof Mob mob && WindupService.winding(mob)) {
						count++;
					}
				}
				return count;
			});
			mostAtOnce = Math.max(mostAtOnce, winding);
			context.waitTicks(1);
		}
		double far = spread(world, ids)[1];
		check(mostAtOnce <= PackService.STRIKERS, "the pack still sends no more than two at once: " + mostAtOnce);
		check(far < 8.0, "the stare cannot hold them forever: all three close in: " + far);
	}

	private static void othersDoNotStalk(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		face(context, world, arena, 0.0F);
		// within a spider's own follow range (16), beyond where the walking dead would stop to stare
		int id = creature(context, world, arena, EntityTypes.SPIDER, 14.5);
		double start = distance(world, id);
		context.waitTicks(40);
		double after = distance(world, id);
		check(start - after > 2.0, "a spider is no walking dead: it comes on watched: " + start + " -> " + after);
	}

	static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
