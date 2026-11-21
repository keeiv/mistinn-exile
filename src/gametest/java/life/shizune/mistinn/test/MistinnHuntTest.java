package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.body.WoundService;
import life.shizune.mistinn.enemy.InvestigateGoal;
import life.shizune.mistinn.enemy.NoiseService;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.stamina.StaminaService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** 聽覺與嗅覺: hostile creatures come to what a hunter's noise and blood give away, and only that. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnHuntTest implements FabricClientGameTest {
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
			world.getServer().runOnServer(MistinnHuntTest::whoListens);
			BlockPos arena = world.getServer().computeOnServer(MistinnHuntTest::arena);
			swingIsHeardNotSeen(context, world, arena);
			tooFarHearsNothing(context, world, arena);
			crouchingMuffles(context, world, arena);
			treatmentIsLoud(context, world, arena);
			theRealAttackKeyIsHeard(context, world, arena);
			bloodCarriesFurther(context, world, arena);
			aChaseIsNotInterrupted(context, world, arena);
			theSearchEnds(context, world, arena);
		}
	}

	// ── Who hunts by ear ──────────────────────────────────────────

	/** Hostile creatures do; the provoked neutrals (endermen, zombified piglins, wolves, polar bears), brain-driven ones and keepers do not. */
	private static void whoListens(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (EntityType<?> type : new EntityType<?>[] {EntityTypes.HUSK, EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.CREEPER, EntityTypes.SPIDER, EntityTypes.WITCH, EntityTypes.VINDICATOR}) {
			check(NoiseService.listens((Mob) type.create(level, EntitySpawnReason.COMMAND)), type + " hunts by ear");
		}
		for (EntityType<?> type : new EntityType<?>[] {EntityTypes.ENDERMAN, EntityTypes.ZOMBIFIED_PIGLIN, EntityTypes.WOLF, EntityTypes.POLAR_BEAR, EntityTypes.PIGLIN, EntityTypes.HOGLIN}) {
			check(!NoiseService.listens((Mob) type.create(level, EntitySpawnReason.COMMAND)), type + " does not");
		}
		check(!NoiseService.listens(MistinnEntities.SNUFFER.create(level, EntitySpawnReason.COMMAND)), "nor does the keeper");
	}

	// ── The arena ─────────────────────────────────────────────────

	/**
	 * A stone floor high above the world with a wall across it at z+3 (x -8..8, four high): the
	 * hunter stands at the near side, the creatures beyond the wall, where they cannot see them.
	 */
	private static BlockPos arena(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos pos = server.getPlayerList().getPlayers().getFirst().blockPosition().above(40).immutable();
		for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-12, -1, -24), pos.offset(12, -1, 30))) {
			level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		}
		for (BlockPos air : BlockPos.betweenClosed(pos.offset(-12, 0, -24), pos.offset(12, 5, 30))) {
			level.setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
		}
		for (BlockPos wall : BlockPos.betweenClosed(pos.offset(-12, 0, 3), pos.offset(12, 4, 3))) {
			level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
		}
		return pos;
	}

	private static void stand(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			for (var entity : server.overworld().getEntities((net.minecraft.world.entity.Entity) null, new AABB(arena).inflate(40.0), e -> !(e instanceof ServerPlayer))) {
				entity.discard();
			}
			NoiseService.forgetAll();
			player.setShiftKeyDown(false);
			player.removeAttached(MistinnAttachments.WOUNDS);
			BodyService.restoreAll(player);
			player.setAttached(MistinnAttachments.STAMINA, StaminaService.max(player));
			// facing away from the wall
			player.teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, Set.of(), 180.0F, 0.0F, true);
		});
		context.waitTicks(4);
	}

	/** A husk with its AI, {@code dz} blocks beyond the hunter (past the wall when dz > 3). */
	private static int husk(final TestSingleplayerContext world, final BlockPos arena, final double dz) {
		return world.getServer().computeOnServer(server -> {
			Husk mob = EntityTypes.HUSK.create(server.overworld(), EntitySpawnReason.COMMAND);
			mob.setPersistenceRequired();
			mob.snapTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5 + dz, 0.0F, 0.0F);
			server.overworld().addFreshEntity(mob);
			return mob.getId();
		});
	}

	private static Mob mob(final MinecraftServer server, final int id) {
		return (Mob) server.overworld().getEntity(id);
	}

	private static boolean heard(final TestSingleplayerContext world, final int id) {
		return world.getServer().computeOnServer(server -> NoiseService.heard(mob(server, id)) != null);
	}

	private static double distance(final TestSingleplayerContext world, final int id) {
		return world.getServer().computeOnServer(server -> mob(server, id).position().distanceTo(player(server).position()));
	}

	// ── Noise ─────────────────────────────────────────────────────

	private static void swingIsHeardNotSeen(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int id = husk(world, arena, 8.5);
		context.waitTicks(20);
		check(!heard(world, id), "nothing heard yet");
		double before = distance(world, id);
		world.getServer().runOnServer(server -> NoiseService.emit(player(server), NoiseService.Noise.SWING));
		check(heard(world, id), "a swing carries ten blocks, through the wall");
		check(world.getServer().computeOnServer(server -> mob(server, id).getTarget() == null), "but heard is not seen: the husk has no target");
		context.waitTicks(InvestigateGoal.LISTEN + 60);
		double after = distance(world, id);
		check(before - after > 2.0, "it comes to look: " + before + " -> " + after);
	}

	private static void tooFarHearsNothing(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int id = husk(world, arena, 12.5);
		context.waitTicks(10);
		world.getServer().runOnServer(server -> NoiseService.emit(player(server), NoiseService.Noise.SWING));
		check(!heard(world, id), "twelve blocks off, a swing is not heard");
		world.getServer().runOnServer(server -> NoiseService.emit(player(server), NoiseService.Noise.HEAVY));
		check(heard(world, id), "a heavy blow carries eighteen");
	}

	private static void crouchingMuffles(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int id = husk(world, arena, 8.5);
		context.waitTicks(10);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setShiftKeyDown(true);
			check(NoiseService.radius(player, NoiseService.Noise.SWING) == 5.0, "crouching halves a swing");
			NoiseService.emit(player, NoiseService.Noise.SWING);
			player.setShiftKeyDown(false);
		});
		check(!heard(world, id), "a crouched swing eight blocks off is not heard");
	}

	private static void treatmentIsLoud(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int id = husk(world, arena, 15.0);
		context.waitTicks(10);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setShiftKeyDown(true);
			check(NoiseService.radius(player, NoiseService.Noise.TREATMENT) == 16.0, "treatment is not muffled by crouching");
			NoiseService.emit(player, NoiseService.Noise.TREATMENT);
			player.setShiftKeyDown(false);
		});
		check(heard(world, id), "tearing a kit open is heard fifteen blocks off");
	}

	/** With the real attack key: a swing at the air reaches the server and is heard. */
	private static void theRealAttackKeyIsHeard(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int id = husk(world, arena, 8.5);
		context.waitTicks(20);
		context.getInput().pressKey(options -> options.keyAttack);
		context.waitTicks(4);
		check(heard(world, id), "a real swing is heard");
	}

	// ── Scent ─────────────────────────────────────────────────────

	private static void bloodCarriesFurther(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int id = husk(world, arena, 18.0);
		context.waitTicks(NoiseService.SCENT_INTERVAL + 5);
		check(!heard(world, id), "an unhurt hunter gives off no scent");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			WoundService.inflict(player, BodyPart.LEFT_ARM, false, true);
			check(NoiseService.bleeding(player), "the arm bleeds");
		});
		context.waitTicks(NoiseService.SCENT_INTERVAL + 5);
		check(heard(world, id), "eighteen blocks off, through the wall, the blood is smelt");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			BodyService.set(player, BodyService.get(player).with(BodyPart.RIGHT_ARM, 0.0F));
			check(NoiseService.radius(player, NoiseService.Noise.BLOOD) == 24.0, "a dead part makes it carry four further");
			player.removeAttached(MistinnAttachments.WOUNDS);
			BodyService.restoreAll(player);
		});
	}

	// ── What it does not do ───────────────────────────────────────

	private static void aChaseIsNotInterrupted(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int chaser = husk(world, arena, 8.5);
		int other = husk(world, arena, 9.5);
		world.getServer().runOnServer(server -> mob(server, chaser).setTarget(mob(server, other)));
		context.waitTicks(2);
		world.getServer().runOnServer(server -> NoiseService.emit(player(server), NoiseService.Noise.HEAVY));
		check(!heard(world, chaser), "a creature already chasing something does not turn for a sound");
	}

	private static void theSearchEnds(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int id = husk(world, arena, 8.5);
		context.waitTicks(10);
		world.getServer().runOnServer(server -> NoiseService.emit(player(server), NoiseService.Noise.SWING));
		check(heard(world, id), "heard");
		context.waitTicks(NoiseService.SEARCH + InvestigateGoal.LOOK_ABOUT + 20);
		check(!heard(world, id), "finding nothing, it gives up after a while");
		check(world.getServer().computeOnServer(server -> mob(server, id).getTarget() == null), "and has not found the hunter behind the wall");
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
