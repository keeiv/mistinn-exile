package life.shizune.mistinn.test;

import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.client.death.DeathScene;
import life.shizune.mistinn.client.enemy.ExecutionView;
import life.shizune.mistinn.enemy.EnemyRig;
import life.shizune.mistinn.enemy.ExecutionService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Drives real server damage into the execution hook, then observes the attached render state. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnEnemyTest implements FabricClientGameTest {
	private static final Body ONE_LEG = Body.FULL.with(BodyPart.LEFT_LEG, 0.0F);
	private static final Body BOTH_LEGS = ONE_LEG.with(BodyPart.RIGHT_LEG, 0.0F);

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel")) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			runStage(context, world);
		}
	}

	private static void runStage(final ClientGameTestContext context, final TestSingleplayerContext world) {
		for (EntityType<?> entity : BuiltInRegistries.ENTITY_TYPE) {
			if (entity.getCategory() == MobCategory.MONSTER && EntityType.getKey(entity).getNamespace().equals("minecraft")) {
				check(EnemyRig.of(entity) != null, "a vanilla monster has a rig profile: " + EntityType.getKey(entity));
			}
		}
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		BlockPos stage = world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			BlockPos pos = player.blockPosition().above(9);
			for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, -1, 4))) {
				player.level().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
			}
			player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			return pos;
		});
		context.waitTicks(10);

		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			Mob mob = husk(player, stage);
			hit(player, mob, ONE_LEG);
			check(!player.hasAttached(ExecutionService.SEQUENCE), "one necrotic leg does not trigger a finisher");
			hit(player, mob, BOTH_LEGS);
			check(player.isAlive() && player.hasAttached(ExecutionService.SEQUENCE) && ExecutionService.holdsMob(mob), "both necrotic legs trigger a live windup");
			mob.hurtServer(player.level(), player.damageSources().playerAttack(player), 1.0F);
		});
		context.waitTicks(4);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			check(player.isAlive() && !player.hasAttached(ExecutionService.SEQUENCE), "striking the attacker interrupts before contact");
			removeHusks(player.level());
			Mob mob = husk(player, stage);
			hit(player, mob, BOTH_LEGS);
			check(!player.hasAttached(ExecutionService.SEQUENCE), "a normal hit cannot immediately restart the interrupted windup");
			removeHusks(player.level());
		});
		context.waitTicks(ExecutionService.REPEAT_DELAY);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			Mob mob = husk(player, stage);
			hit(player, mob, BOTH_LEGS);
			check(player.hasAttached(ExecutionService.SEQUENCE), "a new attacker can take over after cancellation");
			BodyService.set(player, ONE_LEG);
		});
		context.waitTicks(4);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			check(player.isAlive() && !player.hasAttached(ExecutionService.SEQUENCE), "restoring a leg during windup cancels contact");
			removeHusks(player.level());
		});
		context.waitTicks(ExecutionService.REPEAT_DELAY);
		// A husk walks in and attacks on its own. The seizing blow keeps none of vanilla's knockback or flinch,
		// which used to push the hunter out of reach and cancel the finisher a few ticks in.
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.teleportTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5);
			BodyService.set(player, BOTH_LEGS);
			Mob mob = EntityTypes.HUSK.create(player.level(), EntitySpawnReason.COMMAND);
			mob.snapTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 3.0, 180.0F, 0.0F);
			mob.addTag("mistinn.test_finisher");
			player.level().addFreshEntity(mob);
			mob.setTarget(player);
		});
		context.waitFor(mc -> mc.player != null && ExecutionView.sample(mc.player, 0.0F) != null, 200);
		check(context.computeOnClient(mc -> mc.player.hurtTime == 0), "the seizing blow does not flinch the hunter's view");
		for (int tick = 1; context.computeOnClient(mc -> mc.player.isAlive()); tick++) {
			check(tick <= ExecutionService.IMPACT + 4, "the finisher lands within its windup");
			context.waitTick();
			boolean held = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				return !player.isAlive() || player.hasAttached(ExecutionService.SEQUENCE);
			});
			check(held, "a natural attack keeps the finisher going until contact, tick " + tick);
			check(context.computeOnClient(mc -> !mc.player.isAlive()
				|| (ExecutionView.sample(mc.player, 0.0F) != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK)),
				"the view stays on the finisher without snapping back, tick " + tick);
			if (tick == 7) {
				context.takeScreenshot("enemy-execution-windup");
			}
		}
		context.waitForScreen(DeathScreen.class);
		check(context.computeOnClient(mc -> DeathScene.cause() == DeathScene.Cause.EXECUTION), "a finisher has its own death scene");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			ExecutionService.Sequence sequence = player.getAttached(ExecutionService.SEQUENCE);
			check(player.isDeadOrDying() && sequence != null && sequence.completed(), "the server confirms one fatal contact");
			check(BodyService.get(player).destroyed(BodyPart.TORSO), "contact reaches the body service and destroys the third part");
		});
		context.takeScreenshot("enemy-execution-impact");
		context.waitTicks(10);
		context.takeScreenshot("enemy-execution-settle");
		context.waitTicks(90);
		context.runOnClient(mc -> mc.player.respawn());
		context.waitFor(mc -> mc.player.isAlive() && ExecutionView.sample(mc.player, 0.0F) == null, 200);
		check(context.computeOnClient(mc -> mc.options.getCameraType() == CameraType.FIRST_PERSON), "the camera returns after respawning");
		world.getServer().runOnServer(server -> removeHusks(server.overworld()));
	}

	private static void removeHusks(final ServerLevel level) {
		level.getEntities(EntityTypes.HUSK, mob -> mob.entityTags().contains("mistinn.test_finisher")).forEach(Entity::discard);
	}

	private static Mob husk(final ServerPlayer player, final BlockPos stage) {
		ServerLevel level = player.level();
		player.teleportTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5);
		Mob mob = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
		mob.snapTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 1.38, 180.0F, 0.0F);
		mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(0.0);
		mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0);
		mob.addTag("mistinn.test_finisher");
		level.addFreshEntity(mob);
		return mob;
	}

	private static void hit(final ServerPlayer player, final Mob mob, final Body body) {
		BodyService.set(player, body);
		player.setInvulnerableTime(0);
		player.damageCooldownTime = 0;
		mob.doHurtTarget(player.level(), player);
		player.setDeltaMovement(Vec3.ZERO);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
