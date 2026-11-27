package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.enemy.CryService;
import life.shizune.mistinn.enemy.NoiseService;
import life.shizune.mistinn.enemy.RetreatGoal;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 狡猾: creatures that call and creatures that break off.
 * <ul>
 * <li>A creature setting on a hunter cries out: the creatures near it with nothing to chase hear it and come to look,
 * those far off do not, and it does not cry again for a while; one that came to a cry does not cry in turn.</li>
 * <li>A creature with its wits about it (a vindicator), badly hurt, backs away from the hunter and waits a moment
 * before going back in; once in its life. The walking dead never break off.</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnCunningTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("difficulty normal");
			BlockPos arena = arena(world);
			context.waitTicks(10);
			cry(context, world, arena);
			retreat(context, world, arena);
			theDeadDoNotBreakOff(context, world, arena);
		}
	}

	private static void cry(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		int[] ids = world.getServer().computeOnServer(server -> {
			ServerPlayer player = stand(server, arena);
			// unseen, so the others come by ear, not by eye
			player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 2400, 0, false, false));
			return new int[] {spawn(server, arena, EntityTypes.ZOMBIE, 4), spawn(server, arena, EntityTypes.ZOMBIE, 16), spawn(server, arena, EntityTypes.ZOMBIE, 44)};
		});
		context.waitTicks(5);
		double[] before = new double[1];
		Vec3[] cried = new Vec3[1];
		world.getServer().runOnServer(server -> {
			Mob caller = mob(server, ids[0]);
			Mob near = mob(server, ids[1]);
			Mob far = mob(server, ids[2]);
			int cries = CryService.cries;
			caller.setTarget(player(server));
			check(CryService.cries == cries + 1, "a creature setting on a hunter cries out");
			check(NoiseService.heard(near) != null, "one near enough with nothing to chase hears it");
			check(NoiseService.heard(far) == null, "one far off does not");
			cried[0] = caller.position();
			before[0] = near.position().distanceTo(cried[0]);
		});
		context.waitTicks(100);
		world.getServer().runOnServer(server -> {
			Mob caller = mob(server, ids[0]);
			Mob near = mob(server, ids[1]);
			double now = near.position().distanceTo(cried[0]);
			check(now < before[0] - 3.0, "and comes to look: " + before[0] + " -> " + now);
			int cries = CryService.cries;
			caller.setTarget(null);
			caller.setTarget(player(server));
			check(CryService.cries == cries, "a creature does not cry again so soon");
			near.setTarget(player(server));
			check(CryService.cries == cries, "nor does one that came to a cry");
			player(server).removeEffect(MobEffects.INVISIBILITY);
		});
	}

	private static void retreat(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		int id = world.getServer().computeOnServer(server -> {
			stand(server, arena);
			int vindicator = spawn(server, arena, EntityTypes.VINDICATOR, 3);
			Mob mob = mob(server, vindicator);
			check(RetreatGoal.minded(mob), "a vindicator has its wits about it");
			mob.setTarget(player(server));
			return vindicator;
		});
		context.waitTicks(10);
		double[] start = new double[1];
		int[] retreats = new int[1];
		world.getServer().runOnServer(server -> {
			Mob mob = mob(server, id);
			check(!RetreatGoal.retreating(mob), "whole, it does not break off");
			retreats[0] = RetreatGoal.retreats;
			mob.setHealth(mob.getMaxHealth() * (RetreatGoal.BREAK - 0.1F));
			start[0] = mob.position().distanceTo(player(server).position());
		});
		until(context, world, server -> RetreatGoal.retreating(mob(server, id)), 20, "badly hurt, it breaks off");
		context.waitTicks(30);
		world.getServer().runOnServer(server -> {
			Mob mob = mob(server, id);
			double away = mob.position().distanceTo(player(server).position());
			check(away > start[0] + 3.0, "and backs away from the hunter: " + start[0] + " -> " + away);
			check(RetreatGoal.retreats == retreats[0] + 1, "once");
		});
		until(context, world, server -> !RetreatGoal.retreating(mob(server, id)), RetreatGoal.FLEE + RetreatGoal.WAIT + 20, "then it goes back in");
		context.waitTicks(20);
		world.getServer().runOnServer(server -> {
			Mob mob = mob(server, id);
			check(!RetreatGoal.retreating(mob) && RetreatGoal.retreats == retreats[0] + 1, "and it does not break off a second time");
		});
	}

	private static void theDeadDoNotBreakOff(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		int id = world.getServer().computeOnServer(server -> {
			stand(server, arena);
			int zombie = spawn(server, arena, EntityTypes.ZOMBIE, 3);
			Mob mob = mob(server, zombie);
			check(!RetreatGoal.minded(mob), "a zombie has no wits to break off with");
			mob.setTarget(player(server));
			mob.setHealth(mob.getMaxHealth() * 0.1F);
			return zombie;
		});
		context.waitTicks(30);
		world.getServer().runOnServer(server -> check(!RetreatGoal.retreating(mob(server, id)), "the walking dead come on to the end"));
	}

	private static BlockPos arena(final TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			player.setGameMode(GameType.SURVIVAL);
			BlockPos pos = player.blockPosition().above(20);
			for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-20, -1, -12), pos.offset(20, -1, 50))) {
				player.level().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
			}
			for (BlockPos air : BlockPos.betweenClosed(pos.offset(-20, 0, -12), pos.offset(20, 4, 50))) {
				player.level().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
			}
			return pos;
		});
	}

	/** The hunter faces south down the arena, whole; nothing else is about. */
	private static ServerPlayer stand(final MinecraftServer server, final BlockPos arena) {
		ServerPlayer player = player(server);
		for (var entity : server.overworld().getEntities((net.minecraft.world.entity.Entity) null, new AABB(arena).inflate(60.0), e -> !(e instanceof ServerPlayer))) {
			entity.discard();
		}
		player.teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		BodyService.restoreAll(player);
		player.setHealth(player.getMaxHealth());
		return player;
	}

	private static int spawn(final MinecraftServer server, final BlockPos arena, final EntityType<? extends Mob> type, final double dz) {
		ServerLevel level = server.overworld();
		Mob mob = type.create(level, EntitySpawnReason.COMMAND);
		mob.snapTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5 + dz, 180.0F, 0.0F);
		level.addFreshEntity(mob);
		return mob.getId();
	}

	private static void until(final ClientGameTestContext context, final TestSingleplayerContext world,
		final java.util.function.Predicate<MinecraftServer> condition, final int ticks, final String what) {
		for (int i = 0; i < ticks; i++) {
			if (world.getServer().computeOnServer(condition::test)) {
				check(true, what);
				return;
			}
			context.waitTicks(1);
		}
		throw new AssertionError("failed: timed out waiting until " + what);
	}

	private static Mob mob(final MinecraftServer server, final int id) {
		return (Mob) server.overworld().getEntity(id);
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError("failed: " + message);
		}
		System.out.println("[mistinn cunning] ok: " + message);
	}
}
