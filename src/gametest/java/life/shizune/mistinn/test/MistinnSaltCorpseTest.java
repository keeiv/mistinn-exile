package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.enemy.EnemyRig;
import life.shizune.mistinn.enemy.ExecutionMove;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.shore.SaltCorpse;
import life.shizune.mistinn.shore.ShoreSpawner;
import life.shizune.mistinn.world.Builder;
import life.shizune.mistinn.world.Structures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * 鹽屍: the first killing blow drops it like a corpse, and a few breaths later it is on its feet
 * again with half its strength; the next one is the last. Struck while it lies, it stays down.
 * Dormant ones lie about a lighthouse and rise as a hunter comes near; the mist brings more up,
 * a few at a time and only in the mist.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnSaltCorpseTest implements FabricClientGameTest {
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
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("difficulty normal");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			BlockPos arena = arena(world);
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				SaltCorpse corpse = MistinnEntities.SALT_CORPSE.create(server.overworld(), EntitySpawnReason.COMMAND);
				check(EnemyRig.of(MistinnEntities.SALT_CORPSE) == EnemyRig.HUSK, "it fights as a husk does");
				check(ExecutionMove.of(corpse) == ExecutionMove.GRAPPLE_BITE, "and finishes a hunter as one");
			});
			getsUpOnce(context, world, arena);
			struckWhileDown(context, world, arena);
			killedOutright(context, world, arena);
			dormantRises(context, world, arena);
			theMistBringsThemUp(context, world);
		}
	}

	private static void getsUpOnce(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		int id = spawn(world, arena, false);
		context.waitTicks(4);
		world.getServer().runOnServer(server -> {
			SaltCorpse corpse = corpse(server, id);
			strike(server, corpse, 1000.0F);
			check(corpse.isAlive() && corpse.state() == SaltCorpse.FALLEN, "the killing blow drops it like a corpse");
			check(corpse.getHealth() <= 1.0F, "with next to nothing left");
			check(!corpse.doHurtTarget(server.overworld(), player(server)), "lying, it strikes no one");
		});
		context.waitTicks(SaltCorpse.FALL_TICKS + 4);
		context.takeScreenshot("salt-01-fallen");
		context.waitTicks(SaltCorpse.DOWN_TICKS);
		context.takeScreenshot("salt-02-rising");
		context.waitTicks(SaltCorpse.RISE_TICKS + 4);
		world.getServer().runOnServer(server -> {
			SaltCorpse corpse = corpse(server, id);
			check(corpse.state() == SaltCorpse.STANDING && corpse.risen(), "it gets back up");
			check(Math.abs(corpse.getHealth() - corpse.getMaxHealth() * 0.5F) < 0.5F, "with half its strength: " + corpse.getHealth());
			strike(server, corpse, 1000.0F);
			check(!corpse.isAlive(), "the next killing blow is the last");
		});
	}

	private static void struckWhileDown(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		int id = spawn(world, arena, false);
		context.waitTicks(4);
		world.getServer().runOnServer(server -> strike(server, corpse(server, id), 1000.0F));
		context.waitTicks(20);
		world.getServer().runOnServer(server -> {
			SaltCorpse corpse = corpse(server, id);
			check(corpse.state() == SaltCorpse.FALLEN, "still lying");
			strike(server, corpse, 1.0F);
			check(!corpse.isAlive(), "a blow on the body while it lies, however light, finishes it");
		});
	}

	private static void killedOutright(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		int id = spawn(world, arena, false);
		context.waitTicks(4);
		world.getServer().runOnServer(server -> {
			SaltCorpse corpse = corpse(server, id);
			corpse.kill(server.overworld());
			check(!corpse.isAlive(), "a kill that nothing withstands is not survived either");
		});
	}

	private static void dormantRises(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		int id = spawn(world, arena, true);
		world.getServer().runOnServer(server -> player(server).teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() - 12.5,
			Set.of(), 0.0F, 15.0F, true));
		context.waitTicks(40);
		world.getServer().runOnServer(server -> check(corpse(server, id).state() == SaltCorpse.DORMANT, "a dormant corpse lies still while no one is near"));
		context.takeScreenshot("salt-03-dormant");
		world.getServer().runOnServer(server -> player(server).teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() - 1.5,
			Set.of(), 0.0F, 15.0F, true));
		context.waitTicks(SaltCorpse.RISE_TICKS + 10);
		world.getServer().runOnServer(server -> {
			SaltCorpse corpse = corpse(server, id);
			check(corpse.state() == SaltCorpse.STANDING, "a hunter comes close, and it rises");
			check(!corpse.risen(), "and it can still get up once more after a killing blow");
			corpse.discard();
		});
	}

	private static void theMistBringsThemUp(final ClientGameTestContext context, final TestSingleplayerContext world) {
		BlockPos lighthouse = world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			ServerLevel level = server.overworld();
			int x = player.getBlockX() + 300;
			int z = player.getBlockZ();
			level.getChunk(x >> 4, z >> 4);
			BlockPos floor = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
			BlockPos room = Structures.lighthouse(new Builder(level, floor, RandomSource.create(11L)));
			level.getEntitiesOfClass(Snuffer.class, new AABB(floor).inflate(64.0)).forEach(Snuffer::discard);
			List<SaltCorpse> laid = level.getEntitiesOfClass(SaltCorpse.class, new AABB(floor).inflate(16.0));
			check(!laid.isEmpty() && laid.stream().allMatch(c -> c.state() == SaltCorpse.DORMANT), "the dead lie about the lighthouse: " + laid.size());
			laid.forEach(SaltCorpse::discard);
			return room;
		});
		context.waitTicks(4);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			ServerLevel level = server.overworld();
			player.setGameMode(GameType.SURVIVAL);
			player.teleportTo(level, player.getX(), player.getY(), player.getZ(), Set.of(), 0.0F, 0.0F, true);
			check(ShoreSpawner.tryOnce(player) == null, "out of the mist, none come up");
			int x = lighthouse.getX() - 50;
			int z = lighthouse.getZ();
			level.getChunk(x >> 4, z >> 4);
			player.teleportTo(level, x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5, Set.of(), 0.0F, 0.0F, true);
		});
		context.waitTicks(20);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			int brought = 0;
			for (int i = 0; i < 40; i++) {
				SaltCorpse corpse = ShoreSpawner.tryOnce(player);
				if (corpse != null) {
					brought++;
					double distance = Math.hypot(corpse.getX() - player.getX(), corpse.getZ() - player.getZ());
					check(distance >= ShoreSpawner.CLOSEST - 1 && distance <= ShoreSpawner.FARTHEST + 1, "a little way off in the mist: " + distance);
				}
			}
			check(brought > 0, "in the mist, the dead come up");
			int round = server.overworld().getEntitiesOfClass(SaltCorpse.class, player.getBoundingBox().inflate(ShoreSpawner.NEAR)).size();
			check(round <= ShoreSpawner.CAP, "but never more than a few round one hunter: " + round);
			player.setGameMode(GameType.CREATIVE);
		});
	}

	private static BlockPos arena(final TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			player.setGameMode(GameType.CREATIVE);
			BlockPos pos = player.blockPosition().above(12);
			for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-8, -1, -16), pos.offset(8, -1, 8))) {
				player.level().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
			}
			for (BlockPos air : BlockPos.betweenClosed(pos.offset(-8, 0, -16), pos.offset(8, 4, 8))) {
				player.level().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
			}
			player.teleportTo(server.overworld(), pos.getX() + 0.5, pos.getY(), pos.getZ() - 3.5, Set.of(), 0.0F, 15.0F, true);
			return pos;
		});
	}

	private static int spawn(final TestSingleplayerContext world, final BlockPos arena, final boolean dormant) {
		return world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() - 3.5, Set.of(), 0.0F, 15.0F, true);
			server.overworld().getEntitiesOfClass(SaltCorpse.class, new AABB(arena).inflate(24.0)).forEach(SaltCorpse::discard);
			SaltCorpse corpse = MistinnEntities.SALT_CORPSE.create(server.overworld(), EntitySpawnReason.COMMAND);
			corpse.snapTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, 180.0F, 0.0F);
			if (dormant) {
				corpse.lieDormant(true);
			}
			server.overworld().addFreshEntity(corpse);
			return corpse.getId();
		});
	}

	private static void strike(final MinecraftServer server, final SaltCorpse corpse, final float amount) {
		ServerPlayer player = player(server);
		corpse.damageCooldownTime = 0;
		corpse.hurtServer(server.overworld(), player.damageSources().playerAttack(player), amount);
	}

	private static SaltCorpse corpse(final MinecraftServer server, final int id) {
		return (SaltCorpse) server.overworld().getEntity(id);
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
