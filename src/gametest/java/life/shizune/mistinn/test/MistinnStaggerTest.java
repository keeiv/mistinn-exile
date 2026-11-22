package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.client.enemy.EnemyMotion;
import life.shizune.mistinn.stamina.StaminaService;
import life.shizune.mistinn.enemy.StaggerService;
import life.shizune.mistinn.enemy.WindupService;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.weapon.HeavyService;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 踉蹌: a fully charged heavy blow rocks an ordinary creature back on its heels. For a moment it
 * cannot strike or start winding up a blow, and stands where it was knocked; a lighter charge
 * does not do it, and a keeper keeps its feet. Every rig leans back without sinking into the ground.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnStaggerTest implements FabricClientGameTest {
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
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			BlockPos arena = arena(world);
			context.waitTicks(10);
			fullChargeRocks(context, world, arena);
			lighterChargeDoesNot(context, world, arena);
			keeperKeepsItsFeet(context, world, arena);
			leansAwayFromTheBlow(context, world, arena);
			sideView(context, world, arena);
			everyRigLeansAboveTheGround(context);
		}
	}

	private static void fullChargeRocks(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int husk = spawn(world, arena, EntityTypes.HUSK, 180.0F);
		context.waitTicks(2);
		release(world, 1.0F);
		until(context, world, server -> StaggerService.staggered(mob(server, husk)), 20, "the full charge rocks the husk");
		context.takeScreenshot("stagger-01-rocked");
		world.getServer().runOnServer(server -> {
			Mob mob = mob(server, husk);
			check(StaggerService.remaining(mob) <= StaggerService.TICKS && StaggerService.remaining(mob) > 0, "for a moment");
			check(!mob.doHurtTarget(server.overworld(), player(server)), "rocked back, it cannot strike");
			check(!mob.hasAttached(WindupService.WINDUP), "nor start winding up a blow");
		});
		until(context, world, server -> !StaggerService.staggered(mob(server, husk)), StaggerService.TICKS + 4, "it finds its feet again");
		world.getServer().runOnServer(server -> {
			Mob mob = mob(server, husk);
			check(!mob.hasAttached(StaggerService.STAGGER), "the stagger is cleared once over");
			mob.doHurtTarget(server.overworld(), player(server));
			check(mob.hasAttached(WindupService.WINDUP), "on its feet, it winds up again");
		});
	}

	private static void lighterChargeDoesNot(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int husk = spawn(world, arena, EntityTypes.HUSK, 180.0F);
		context.waitTicks(2);
		release(world, 0.6F);
		context.waitTicks(12);
		world.getServer().runOnServer(server -> {
			Mob mob = mob(server, husk);
			check(mob.getHealth() < mob.getMaxHealth(), "a sixty-percent blow lands");
			check(!StaggerService.staggered(mob), "but does not rock it");
		});
	}

	private static void keeperKeepsItsFeet(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int keeper = spawn(world, arena, MistinnEntities.SNUFFER, 180.0F);
		context.waitTicks(2);
		release(world, 1.0F);
		context.waitTicks(12);
		world.getServer().runOnServer(server -> {
			Mob mob = mob(server, keeper);
			check(mob.getHealth() < mob.getMaxHealth(), "the full blow lands on the keeper");
			check(!StaggerService.staggered(mob), "a keeper keeps its feet: it has its own balance to break");
			mob.discard();
		});
	}

	/** Struck from its right, it is rocked toward its left. */
	private static void leansAwayFromTheBlow(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		// facing west: the hunter's blow, going south, comes from its right
		int husk = spawn(world, arena, EntityTypes.HUSK, 90.0F);
		context.waitTicks(2);
		release(world, 1.0F);
		until(context, world, server -> StaggerService.staggered(mob(server, husk)), 20, "the side blow rocks the husk");
		world.getServer().runOnServer(server -> {
			StaggerService.Stagger stagger = mob(server, husk).getAttached(StaggerService.STAGGER);
			check(stagger.side() < -0.5F, "toward its left, away from the blow: " + stagger.side());
		});
	}

	/** A still of the lean from the side: a long stagger, caught at its furthest. */
	private static void sideView(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int husk = spawn(world, arena, EntityTypes.HUSK, 180.0F);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), arena.getX() + 3.5, arena.getY(), arena.getZ() + 2.5, Set.of(), 90.0F, 5.0F, true);
		});
		context.waitTicks(6);
		int length = 200;
		world.getServer().runOnServer(server -> mob(server, husk).setAttached(StaggerService.STAGGER,
			new StaggerService.Stagger(server.overworld().getGameTime(), length, 0.0F)));
		// the furthest of the lean comes a fifth of the way in
		context.waitTicks(length / 5);
		context.takeScreenshot("stagger-02-side");
		world.getServer().runOnServer(server -> mob(server, husk).discard());
	}

	/** At the furthest of the lean, no rig's model reaches into the ground it stands on. */
	private static void everyRigLeansAboveTheGround(final ClientGameTestContext context) {
		for (EntityType<? extends LivingEntity> type : List.<EntityType<? extends LivingEntity>>of(
			EntityTypes.HUSK, EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.SPIDER, EntityTypes.WOLF)) {
			for (float side : new float[] {-1.0F, 0.0F, 1.0F}) {
				float[] low = context.computeOnClient(mc -> {
					LivingEntity entity = type.create(mc.level, EntitySpawnReason.COMMAND);
					// a model only, never added to the world: it still needs an id to be drawn
					entity.setId(-4242);
					float rest = lowest(ClipProbe.boxes(mc, entity, Vec3.ZERO, 0.0F, state -> { }, true, null, null));
					float rocked = lowest(ClipProbe.boxes(mc, entity, Vec3.ZERO, 0.0F, state -> {
						EnemyMotion.Sample sample = state.getData(EnemyMotion.KEY);
						state.setData(EnemyMotion.KEY, sample.rocked(1.0F, side));
					}, true, null, null));
					return new float[] {rest, rocked};
				});
				check(low[1] > -0.06F && low[1] > low[0] - 0.05F,
					EntityType.getKey(type) + " leaning back (side " + side + ") reaches into the ground: lowest " + low[1] + ", at rest " + low[0]);
			}
		}
	}

	private static float lowest(final List<ClipProbe.Box> boxes) {
		float lowest = Float.MAX_VALUE;
		for (ClipProbe.Box box : boxes) {
			float reach = 0.0F;
			for (int i = 0; i < 3; i++) {
				reach += box.half()[i] * Math.abs(box.axes()[i].y);
			}
			lowest = Math.min(lowest, box.center().y - reach);
		}
		return lowest;
	}

	private static BlockPos arena(final TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			player.setGameMode(GameType.SURVIVAL);
			BlockPos pos = player.blockPosition().above(12);
			for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-6, -1, -6), pos.offset(6, -1, 8))) {
				player.level().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
			}
			for (BlockPos air : BlockPos.betweenClosed(pos.offset(-6, 0, -6), pos.offset(6, 4, 8))) {
				player.level().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
			}
			return pos;
		});
	}

	private static void stand(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			for (var entity : server.overworld().getEntities((net.minecraft.world.entity.Entity) null, new AABB(arena).inflate(20.0), e -> !(e instanceof ServerPlayer))) {
				entity.discard();
			}
			HeavyService.forgetAll();
			player.teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			player.getInventory().setSelectedSlot(0);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.IRON)));
			BodyService.restoreAll(player);
			player.setAttached(MistinnAttachments.STAMINA, StaminaService.max(player));
		});
		context.waitTicks(4);
	}

	private static int spawn(final TestSingleplayerContext world, final BlockPos arena, final EntityType<? extends Mob> type, final float yaw) {
		return world.getServer().computeOnServer(server -> {
			Mob mob = type.create(server.overworld(), EntitySpawnReason.COMMAND);
			mob.setNoAi(true);
			mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
			mob.setHealth(500.0F);
			mob.snapTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 2.5, yaw, 0.0F);
			mob.setYHeadRot(yaw);
			mob.setYBodyRot(yaw);
			server.overworld().addFreshEntity(mob);
			return mob.getId();
		});
	}

	/** Lets go a heavy slash charged to this share of full. */
	private static void release(final TestSingleplayerContext world, final float charge) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setAttached(MistinnAttachments.STAMINA, StaminaService.max(player));
			int ticks = Math.round(HeavyService.chargeTicks(player, WeaponStyle.SLASH) * charge);
			check(HeavyService.release(player, player.getMainHandItem(), ticks), "the heavy blow is let go at " + charge);
		});
	}

	private static void until(final ClientGameTestContext context, final TestSingleplayerContext world,
		final java.util.function.Predicate<MinecraftServer> condition, final int ticks, final String what) {
		for (int i = 0; i < ticks; i++) {
			if (world.getServer().computeOnServer(condition::test)) {
				return;
			}
			context.waitTicks(1);
		}
		throw new AssertionError("timed out waiting until " + what);
	}

	private static Mob mob(final MinecraftServer server, final int id) {
		return (Mob) server.overworld().getEntity(id);
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
