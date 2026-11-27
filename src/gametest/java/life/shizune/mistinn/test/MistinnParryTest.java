package life.shizune.mistinn.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Set;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.client.combat.ParryClient;
import life.shizune.mistinn.enemy.StaggerService;
import life.shizune.mistinn.enemy.WindupService;
import life.shizune.mistinn.keeper.Keeper;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.stamina.StaminaService;
import life.shizune.mistinn.weapon.HeavyService;
import life.shizune.mistinn.weapon.ParryService;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * 格擋: a guard raised just as a blow lands turns it aside whole. The hunter takes nothing, the
 * creature rocks back on its heels and some stamina comes back. A guard raised a moment too soon
 * takes only half off the blow; one turned the wrong way, or gone down again, takes nothing off.
 * A keeper's blow is never turned aside whole, but each perfect parry wears at its balance. The
 * guard wants a weapon in hand, costs stamina and cannot go straight back up.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnParryTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		context.runOnClient(mc -> {
			KeyMapping parry = null;
			for (KeyMapping key : mc.options.keyMappings) {
				if (key.getName().equals("key.mistinn.parry")) {
					parry = key;
				}
			}
			check(parry != null, "there is a guard key");
			check(parry.getDefaultKey().getValue() == InputConstants.KEY_Z, "on Z by default");
			StringBuilder shared = new StringBuilder();
			for (KeyMapping key : mc.options.keyMappings) {
				if (key != parry && key.getDefaultKey().equals(parry.getDefaultKey())) {
					shared.append(' ').append(key.getName());
				}
			}
			check(shared.isEmpty(), "no other key starts on Z:" + shared);
		});
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("gamerule spawn_mobs false");
			BlockPos arena = arena(world);
			context.waitTicks(10);
			perfect(context, world, arena);
			tooSoon(context, world, arena);
			fromBehind(context, world, arena);
			goneDown(context, world, arena);
			bareHanded(context, world, arena);
			keeper(context, world, arena);
			onlyWithinReach(context, world, arena);
			pictures(context, world, arena);
		}
	}

	/** Just as the blow lands: nothing taken, the zombie rocked back, stamina back. */
	private static void perfect(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int zombie = spawn(world, arena, EntityTypes.ZOMBIE, 2.5, 180.0F);
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Mob mob = mob(server, zombie);
			StaminaService.spend(player, StaminaService.max(player) / 2.0F);
			float before = StaminaService.get(player);
			check(ParryService.raise(player), "the guard goes up");
			check(ParryService.guarding(player), "and holds");
			check(StaminaService.get(player) == before - ParryService.COST, "for a little stamina: " + StaminaService.get(player));
			check(!ParryService.raise(player), "it cannot go straight back up");
			float health = player.getHealth();
			float body = BodyService.get(player).total();
			int perfects = ParryService.perfects;
			WindupService.land((ServerLevel) mob.level(), mob, player);
			check(ParryService.perfects == perfects + 1, "the blow is turned aside");
			check(player.getHealth() == health && BodyService.get(player).total() == body, "and nothing of it is taken");
			check(StaggerService.staggered(mob), "the zombie rocks back on its heels");
			check(StaminaService.get(player) == Math.min(StaminaService.max(player), before - ParryService.COST + ParryService.REFUND),
				"and some stamina comes back: " + StaminaService.get(player));
		});
	}

	/** Raised a moment too soon: half the blow comes through, and the zombie keeps its feet. */
	private static void tooSoon(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int zombie = spawn(world, arena, EntityTypes.ZOMBIE, 2.5, 180.0F);
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Mob mob = mob(server, zombie);
			check(ParryService.raise(player), "the guard goes up");
			ParryService.age(player, ParryService.PERFECT + 1);
			float body = BodyService.get(player).total();
			int blocks = ParryService.blocks;
			int perfects = ParryService.perfects;
			WindupService.land((ServerLevel) mob.level(), mob, player);
			check(ParryService.blocks == blocks + 1 && ParryService.perfects == perfects, "the blow meets the guard, not a parry");
			check(ParryService.lastFull > 0.0F && Math.abs(ParryService.lastTaken - ParryService.lastFull * ParryService.BLOCKED) < 1.0E-4F,
				"half of it comes through: " + ParryService.lastTaken + " of " + ParryService.lastFull);
			check(BodyService.get(player).total() < body, "and is taken");
			check(!StaggerService.staggered(mob), "the zombie keeps its feet");
		});
	}

	/** A blow from behind the hunter meets no guard. */
	private static void fromBehind(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int zombie = spawn(world, arena, EntityTypes.ZOMBIE, -2.5, 0.0F);
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Mob mob = mob(server, zombie);
			check(ParryService.raise(player), "the guard goes up, facing away");
			float body = BodyService.get(player).total();
			int met = ParryService.perfects + ParryService.blocks;
			WindupService.land((ServerLevel) mob.level(), mob, player);
			check(ParryService.perfects + ParryService.blocks == met, "a blow from behind meets no guard");
			check(BodyService.get(player).total() < body, "and lands whole");
		});
	}

	/** A guard held past its time is down again. */
	private static void goneDown(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int zombie = spawn(world, arena, EntityTypes.ZOMBIE, 2.5, 180.0F);
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Mob mob = mob(server, zombie);
			check(ParryService.raise(player), "the guard goes up");
			ParryService.age(player, ParryService.GUARD);
			check(!ParryService.guarding(player), "and comes down at its time");
			int met = ParryService.perfects + ParryService.blocks;
			WindupService.land((ServerLevel) mob.level(), mob, player);
			check(ParryService.perfects + ParryService.blocks == met, "a blow after it meets nothing");
		});
	}

	/** No weapon in hand, no guard. */
	private static void bareHanded(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			float before = StaminaService.get(player);
			check(!ParryService.raise(player), "no guard without a weapon");
			check(StaminaService.get(player) == before, "and nothing spent");
		});
	}

	/** A keeper's blow is never turned aside whole: seven tenths off, no stagger, but its balance wears. */
	private static void keeper(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int snuffer = spawn(world, arena, MistinnEntities.SNUFFER, 2.5, 180.0F);
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Keeper keeper = (Keeper) mob(server, snuffer);
			check(ParryService.great(keeper), "a keeper is a great foe");
			check(!ParryService.great(mob(server, spawnOn(server, arena, EntityTypes.ZOMBIE, 4.0, 180.0F))), "a zombie is not");
			int poise = keeper.poise();
			check(ParryService.raise(player), "the guard goes up");
			int perfects = ParryService.perfects;
			WindupService.land((ServerLevel) keeper.level(), keeper, player);
			check(ParryService.perfects == perfects + 1, "the keeper's blow is parried");
			check(ParryService.lastFull > 0.0F && Math.abs(ParryService.lastTaken - ParryService.lastFull * ParryService.GREAT) < 1.0E-4F,
				"but some of it still comes through: " + ParryService.lastTaken + " of " + ParryService.lastFull);
			check(!keeper.staggered(), "the keeper keeps its feet");
			check(keeper.poise() == poise + 1, "but its balance wears: " + keeper.poise());
		});
	}

	/** The guard as it is drawn: in one's own view, and seen from in front. */
	private static void pictures(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		context.runOnClient(mc -> {
			ParryClient.preview = 0.0F;
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
		context.waitTicks(5);
		context.takeScreenshot(TestScreenshotOptions.of("guard-first").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("parry")));
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(5);
		context.takeScreenshot(TestScreenshotOptions.of("guard-front").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("parry")));
		context.runOnClient(mc -> {
			ParryClient.preview = -1.0F;
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
	}

	/** Only a blow struck at arm's length meets the guard: not a keeper's blow from across the floor, nor thorns. */
	private static void onlyWithinReach(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena);
		int far = spawn(world, arena, MistinnEntities.SNUFFER, 8.0, 180.0F);
		int near = spawn(world, arena, EntityTypes.ZOMBIE, 2.0, 180.0F);
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			ServerLevel level = server.overworld();
			Mob keeper = mob(server, far);
			Mob zombie = mob(server, near);
			check(ParryService.raise(player), "the guard goes up");
			int met = ParryService.perfects + ParryService.blocks;
			int poise = ((Keeper) keeper).poise();
			player.hurtServer(level, keeper.damageSources().mobAttack(keeper), 4.0F);
			check(ParryService.perfects + ParryService.blocks == met, "a keeper's blow from across the floor meets no guard");
			check(((Keeper) keeper).poise() == poise, "and wears nothing of its balance");
			player.setInvulnerableTime(0);
			player.hurtServer(level, zombie.damageSources().thorns(zombie), 2.0F);
			check(ParryService.perfects + ParryService.blocks == met, "nor do thorns");
		});
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

	/** The hunter faces south (toward +z) with a blade in hand, whole, rested and ready to guard again. */
	private static void stand(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			for (var entity : server.overworld().getEntities((net.minecraft.world.entity.Entity) null, new AABB(arena).inflate(20.0), e -> !(e instanceof ServerPlayer))) {
				entity.discard();
			}
			HeavyService.forgetAll();
			ParryService.forget(player);
			player.teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			player.getInventory().setSelectedSlot(0);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.IRON)));
			BodyService.restoreAll(player);
			player.setAttached(MistinnAttachments.STAMINA, StaminaService.max(player));
			player.setInvulnerableTime(0);
		});
		context.waitTicks(4);
	}

	private static int spawn(final TestSingleplayerContext world, final BlockPos arena, final EntityType<? extends Mob> type, final double dz, final float yaw) {
		return world.getServer().computeOnServer(server -> spawnOn(server, arena, type, dz, yaw));
	}

	private static int spawnOn(final MinecraftServer server, final BlockPos arena, final EntityType<? extends Mob> type, final double dz, final float yaw) {
		Mob mob = type.create(server.overworld(), EntitySpawnReason.COMMAND);
		mob.setNoAi(true);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		mob.setHealth(500.0F);
		mob.snapTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5 + dz, yaw, 0.0F);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		server.overworld().addFreshEntity(mob);
		return mob.getId();
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
		System.out.println("[mistinn parry] ok: " + message);
	}
}
