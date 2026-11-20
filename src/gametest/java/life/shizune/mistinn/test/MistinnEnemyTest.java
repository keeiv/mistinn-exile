package life.shizune.mistinn.test;

import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.body.WoundService;
import life.shizune.mistinn.body.Wounds;
import life.shizune.mistinn.client.death.DeathScene;
import life.shizune.mistinn.client.enemy.ExecutionView;
import life.shizune.mistinn.enemy.EnemyRig;
import life.shizune.mistinn.enemy.ExecutionMove;
import life.shizune.mistinn.enemy.ExecutionService;
import life.shizune.mistinn.enemy.WindupService;
import net.fabricmc.fabric.api.entity.FakePlayer;
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
		if (TestFilter.skip(getClass())) {
			return;
		}
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
			// clear the air above the stage: tall creatures' eyes would sit in nearby tree crowns
			for (BlockPos air : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 5, 4))) {
				player.level().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
			}
			// a roof keeps the undead out of the sun: their flames would cover the shots
			for (BlockPos roof : BlockPos.betweenClosed(pos.offset(-7, 9, -7), pos.offset(7, 9, 7))) {
				player.level().setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
			}
			player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			return pos;
		});
		context.waitTicks(10);
		world.getServer().runOnServer(server -> inflictMarksOnlyRequestedWound(server.getPlayerList().getPlayers().getFirst()));
		world.getServer().runOnServer(server -> everyFinisherMobHasAMove(server.overworld()));

		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			Mob mob = husk(player, stage);
			hit(player, mob, ONE_LEG);
			check(!player.hasAttached(ExecutionService.SEQUENCE), "one necrotic leg does not trigger a finisher");
			hit(player, mob, BOTH_LEGS);
			check(player.isAlive() && player.hasAttached(ExecutionService.SEQUENCE) && ExecutionService.holdsMob(mob), "both necrotic legs trigger a live windup");
			FakePlayer ally = FakePlayer.get(player.level());
			mob.hurtServer(player.level(), ally.damageSources().playerAttack(ally), 1.0F);
		});
		context.waitTicks(4);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			check(player.isAlive() && !player.hasAttached(ExecutionService.SEQUENCE), "a teammate striking the attacker interrupts before contact");
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
		executionHoldsAndTeammateRescues(context, world, stage);
		context.waitTicks(ExecutionService.REPEAT_DELAY);
		blockedStandingSpotFallsBackToNormalHit(context, world, stage);
		context.waitTicks(ExecutionService.REPEAT_DELAY);
		secondAttackerCannotStartSecondExecution(context, world, stage);
		context.waitTicks(ExecutionService.REPEAT_DELAY);
		movesRunTheirCourse(context, world, stage);
		headshots(context, world, stage);
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
			check(tick <= ExecutionMove.GRAPPLE_BITE.kill() + 4, "the finisher lands within its windup");
			context.waitTick();
			boolean held = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				return !player.isAlive() || player.hasAttached(ExecutionService.SEQUENCE);
			});
			check(held, "a natural attack keeps the finisher going until contact, tick " + tick);
			if (tick == 20) {
				// the hunter's own blow does not break the hold
				world.getServer().runOnServer(server -> {
					ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
					for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(4), m -> m.entityTags().contains("mistinn.test_finisher"))) {
						mob.hurtServer(player.level(), player.damageSources().playerAttack(player), 1.0F);
					}
				});
			}
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

	private static ServerPlayer hunter(final net.minecraft.server.MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Mob finisher(final ServerPlayer player) {
		return player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(6),
			m -> m.entityTags().contains("mistinn.test_finisher") && ExecutionService.holdsMob(m)).stream().findFirst().orElse(null);
	}

	private static long elapsed(final ServerPlayer player) {
		ExecutionService.Sequence sequence = player.getAttached(ExecutionService.SEQUENCE);
		return sequence == null ? -1 : player.level().getGameTime() - sequence.start();
	}

	private static void executionHoldsAndTeammateRescues(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = hunter(server);
			WoundService.clearAll(player);
			hit(player, husk(player, stage), BOTH_LEGS);
			check(player.hasAttached(ExecutionService.SEQUENCE), "the seizing blow starts the grapple");
		});
		context.waitTicks(4);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = hunter(server);
			Mob mob = finisher(player);
			double flat = Math.hypot(mob.getX() - player.getX(), mob.getZ() - player.getZ());
			check(Math.abs(flat - ExecutionMove.GRAPPLE_BITE.spacing(mob)) < 0.05, "the striker stands at its move's spacing, " + flat);
			check(player.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.0, "the hunter is held in place");
		});
		while (world.getServer().computeOnServer(server -> elapsed(hunter(server))) < 50) {
			context.waitTick();
		}
		world.getServer().runOnServer(server -> {
			ServerPlayer player = hunter(server);
			FakePlayer ally = FakePlayer.get(player.level());
			finisher(player).hurtServer(player.level(), ally.damageSources().playerAttack(ally), 1.0F);
		});
		context.waitTicks(2);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = hunter(server);
			Wounds wounds = WoundService.get(player);
			check(player.isAlive() && !player.hasAttached(ExecutionService.SEQUENCE), "a teammate's blow breaks the hold");
			check(wounds.get(BodyPart.RIGHT_ARM).fractured() && wounds.get(BodyPart.HEAD).bleeding(), "the twisted arm and the bite stay: " + wounds);
			check(player.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.0, "the hold is gone with the move");
			removeHusks(player.level());
			WoundService.clearAll(player);
		});
	}

	private static void blockedStandingSpotFallsBackToNormalHit(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = hunter(server);
			ServerLevel level = player.level();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx != 0 || dz != 0) {
						level.setBlockAndUpdate(stage.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
					}
				}
			}
			player.teleportTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5);
			Mob mob = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
			mob.snapTo(stage.getX() + 1.5, stage.getY() + 1, stage.getZ() + 0.5, 90.0F, 0.0F);
			mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(0.0);
			mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
			mob.addTag("mistinn.test_finisher");
			level.addFreshEntity(mob);
			BodyService.set(player, BOTH_LEGS);
			float before = BodyService.get(player).total();
			player.setInvulnerableTime(0);
			WindupService.land(level, mob, player);
			check(!player.hasAttached(ExecutionService.SEQUENCE), "no finisher starts where the striker cannot stand");
			check(BodyService.get(player).total() < before, "the blow lands as an ordinary hit instead");
			removeHusks(level);
			for (BlockPos wall : BlockPos.betweenClosed(stage.offset(-1, 0, -1), stage.offset(1, 0, 1))) {
				level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
			}
			WoundService.clearAll(player);
		});
	}

	private static void secondAttackerCannotStartSecondExecution(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = hunter(server);
			Mob first = husk(player, stage);
			hit(player, first, BOTH_LEGS);
			Mob second = EntityTypes.HUSK.create(player.level(), EntitySpawnReason.COMMAND);
			second.snapTo(stage.getX() + 0.5, stage.getY(), stage.getZ() - 0.38, 0.0F, 0.0F);
			second.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(0.0);
			second.addTag("mistinn.test_finisher");
			player.level().addFreshEntity(second);
			player.setInvulnerableTime(0);
			WindupService.land(player.level(), second, player);
			long strikers = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(6),
				m -> m.hasAttached(ExecutionService.SEQUENCE)).size();
			check(strikers == 1, "only one creature can hold the hunter, found " + strikers);
			// the hold ends cleanly when the hunter leaves
			ExecutionService.release(player);
			check(player.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.0 && !first.hasAttached(ExecutionService.SEQUENCE)
				&& !player.hasAttached(ExecutionService.SEQUENCE), "leaving releases the hold on both sides");
			removeHusks(player.level());
			WoundService.clearAll(player);
		});
	}

	/** Every melee finisher, start to kill: the striker's spacing, the wounds at each contact, and four frames. */
	private static void movesRunTheirCourse(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		java.util.List<EntityType<? extends Mob>> strikers = java.util.List.of(EntityTypes.ZOMBIE, EntityTypes.SPIDER, EntityTypes.SLIME,
			EntityTypes.WARDEN, EntityTypes.RAVAGER, EntityTypes.VINDICATOR, EntityTypes.WITHER_SKELETON, EntityTypes.ENDERMAN);
		for (EntityType<? extends Mob> type : strikers) {
			ExecutionMove move = world.getServer().computeOnServer(server -> {
				ServerPlayer player = hunter(server);
				ServerLevel level = player.level();
				// right after a respawn the stage's chunks briefly drop out, so the last round's striker and
				// gravestone are cleared here, once they are loaded again
				removeHusks(level);
				for (BlockPos air : BlockPos.betweenClosed(stage.offset(-4, 0, -4), stage.offset(4, 5, 4))) {
					level.setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
				}
				player.teleportTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5);
				player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
				BodyService.set(player, BOTH_LEGS);
				WoundService.clearAll(player);
				player.setInvulnerableTime(0);
				Mob mob = type.create(level, EntitySpawnReason.COMMAND);
				if (mob instanceof net.minecraft.world.entity.monster.cubemob.Slime slime) {
					slime.setSize(3, true);
				}
				if (type == EntityTypes.VINDICATOR) {
					mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE));
				}
				if (type == EntityTypes.WITHER_SKELETON) {
					mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_SWORD));
				}
				ExecutionMove expected = ExecutionMove.of(mob);
				// it strikes from where a blow can land; the move then slides it out to its own spacing
				double reach = Math.min(expected.spacing(mob) + 0.3, 0.3 + mob.getBbWidth() / 2.0 + 0.45);
				mob.snapTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5 + reach, 180.0F, 0.0F);
				mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(0.0);
				mob.addTag("mistinn.test_finisher");
				level.addFreshEntity(mob);
				float before = BodyService.get(player).total();
				String facts = "range=" + mob.isWithinMeleeAttackRange(player) + " sight=" + mob.hasLineOfSight(player)
					+ " hurt=" + mob.hurtTime + " canAttack=" + mob.canAttack(player) + " allied=" + mob.isAlliedTo(player)
					+ " ray=" + level.clip(new net.minecraft.world.level.ClipContext(mob.getEyePosition(), player.getEyePosition(),
						net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mob)).getType()
					+ " eye=" + mob.getEyePosition() + " target=" + player.getEyePosition()
					+ (mob instanceof net.minecraft.world.entity.monster.Ravager r ? " stun=" + r.getStunnedTick() + " roar=" + r.getRoarTick() : "");
				boolean landed = WindupService.land(level, mob, player);
				ExecutionService.Sequence sequence = player.getAttached(ExecutionService.SEQUENCE);
				check(sequence != null && sequence.finisher() == expected, EntityType.getKey(type) + " starts " + expected
					+ " [" + facts + " landed=" + landed + " body " + before + "->" + BodyService.get(player).total() + "]");
				return expected;
			});
			int shot = 1;
			while (context.computeOnClient(mc -> mc.player.isAlive())) {
				context.waitTick();
				long elapsed = world.getServer().computeOnServer(server -> elapsed(hunter(server)));
				if (elapsed < 0) {
					break;
				}
				if (shot <= 3 && elapsed >= Math.round(move.kill() * shot / 4.0F)) {
					context.takeScreenshot("execution-" + move.id() + "-" + shot);
					shot++;
				}
				for (ExecutionMove.Beat beat : move.beats()) {
					if (beat.part() != null && elapsed > beat.tick() && elapsed < move.kill()) {
						boolean wounded = world.getServer().computeOnServer(server -> {
							Wounds.Wound wound = WoundService.get(hunter(server)).get(beat.part());
							return (!beat.fracture() || wound.fractured()) && (!beat.bleed() || wound.bleeding());
						});
						check(wounded, move.id() + " leaves its wound on " + beat.part() + " at tick " + beat.tick());
					}
				}
			}
			context.waitForScreen(DeathScreen.class);
			check(context.computeOnClient(mc -> DeathScene.cause() == DeathScene.Cause.EXECUTION), move.id() + " ends in an execution death");
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof life.shizune.mistinn.client.death.MissionEndScreen screen
				&& crimsonExecuted(screen.causeLine())), move.id() + ": the cause line says 處決 in crimson");
			check(context.computeOnClient(mc -> {
				ExecutionView.Pose pose = ExecutionView.sample(mc.player, 0.0F);
				return pose != null && pose.move() == move && !DeathScene.hides(mc.level.getEntity(pose.striker()));
			}), move.id() + ": the striker stays in the death scene's first moments");
			context.takeScreenshot("execution-" + move.id() + "-4");
			context.waitTicks(20);
			check(context.computeOnClient(mc -> {
				ExecutionView.Pose pose = ExecutionView.sample(mc.player, 0.0F);
				net.minecraft.world.entity.Entity striker = pose == null ? null : mc.level.getEntity(pose.striker());
				return striker == null || DeathScene.hides(striker);
			}), move.id() + ": the striker is gone from the death scene after a moment");
			context.waitTicks(40);
			if (move == ExecutionMove.GRAPPLE_BITE) {
				// the mission-end screen itself, once it has come up over the scene
				context.waitTicks(60);
				context.takeScreenshot("mission-end-execution");
			}
			context.runOnClient(mc -> mc.player.respawn());
			context.waitFor(mc -> mc.player != null && mc.player.isAlive() && ExecutionView.sample(mc.player, 0.0F) == null, 200);
			world.getServer().runOnServer(server -> {
				removeHusks(server.overworld());
				// each death leaves a gravestone where the hunter fell; they would stack up in the next creature's sight
				for (BlockPos air : BlockPos.betweenClosed(stage.offset(-4, 0, -4), stage.offset(4, 5, 4))) {
					server.overworld().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
				}
			});
			// let the death scene's smoke clear before the next move is filmed
			context.waitTicks(60);
		}
		world.getServer().runOnServer(server -> {
			removeHusks(server.overworld());
			for (BlockPos air : BlockPos.betweenClosed(stage.offset(-4, 0, -4), stage.offset(4, 5, 4))) {
				server.overworld().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
			}
		});
	}

	/** A skeleton with a bow, out of melee reach, aiming at a hunter whose legs are gone. */
	private static void placeArcher(final ServerPlayer player, final BlockPos stage, final double distance) {
		ServerLevel level = player.level();
		removeHusks(level);
		for (BlockPos air : BlockPos.betweenClosed(stage.offset(-4, 0, -4), stage.offset(4, 5, 4))) {
			level.setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
		}
		// floor out to the archer, under the roof (the stage itself only reaches four blocks out)
		for (BlockPos floor : BlockPos.betweenClosed(stage.offset(-2, -1, 0), stage.offset(2, -1, (int) distance + 2))) {
			level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		}
		player.teleportTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5);
		player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		BodyService.set(player, BOTH_LEGS);
		WoundService.clearAll(player);
		player.setInvulnerableTime(0);
		Mob archer = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		archer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
		archer.snapTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5 + distance, 180.0F, 0.0F);
		archer.addTag("mistinn.test_finisher");
		level.addFreshEntity(archer);
		archer.setTarget(player);
	}

	private static void awaitHeadshot(final ClientGameTestContext context, final TestSingleplayerContext world) {
		for (int tick = 0; tick < 400; tick++) {
			boolean aiming = world.getServer().computeOnServer(server -> {
				ExecutionService.Sequence sequence = hunter(server).getAttached(ExecutionService.SEQUENCE);
				return sequence != null && sequence.finisher() == ExecutionMove.HEADSHOT;
			});
			if (aiming) {
				return;
			}
			context.waitTick();
		}
		throw new AssertionError("the archer never took aim at the downed hunter");
	}

	private static void awaitElapsed(final ClientGameTestContext context, final TestSingleplayerContext world, final long ticks) {
		while (world.getServer().computeOnServer(server -> elapsed(hunter(server))) >= 0
			&& world.getServer().computeOnServer(server -> elapsed(hunter(server))) < ticks) {
			context.waitTick();
		}
	}

	private static void headshots(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		// a teammate's blow on the archer during the aim saves the hunter
		world.getServer().runOnServer(server -> placeArcher(hunter(server), stage, 6.0));
		awaitHeadshot(context, world);
		awaitElapsed(context, world, 12);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = hunter(server);
			FakePlayer ally = FakePlayer.get(player.level());
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(12), m -> m.entityTags().contains("mistinn.test_finisher"))) {
				mob.hurtServer(player.level(), ally.damageSources().playerAttack(ally), 1.0F);
			}
		});
		context.waitTicks(2);
		world.getServer().runOnServer(server -> check(hunter(server).isAlive() && !hunter(server).hasAttached(ExecutionService.SEQUENCE),
			"striking the archer before the release saves the hunter"));
		world.getServer().runOnServer(server -> removeHusks(server.overworld()));
		context.waitTicks(ExecutionService.REPEAT_DELAY);

		// something in the arrow's path takes the shot instead
		world.getServer().runOnServer(server -> placeArcher(hunter(server), stage, 6.0));
		awaitHeadshot(context, world);
		awaitElapsed(context, world, 24);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = hunter(server);
			net.minecraft.world.entity.decoration.ArmorStand shield = EntityTypes.ARMOR_STAND.create(player.level(), EntitySpawnReason.COMMAND);
			shield.snapTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 3.5, 0.0F, 0.0F);
			shield.addTag("mistinn.test_finisher");
			player.level().addFreshEntity(shield);
		});
		context.waitTicks(ExecutionMove.HEADSHOT.kill() + 20);
		world.getServer().runOnServer(server -> check(hunter(server).isAlive() && !hunter(server).hasAttached(ExecutionService.SEQUENCE),
			"a body in the arrow's path saves the hunter"));
		world.getServer().runOnServer(server -> removeHusks(server.overworld()));
		context.waitTicks(ExecutionService.REPEAT_DELAY);

		// the shot lands
		world.getServer().runOnServer(server -> placeArcher(hunter(server), stage, 6.0));
		awaitHeadshot(context, world);
		float before = world.getServer().computeOnServer(server -> BodyService.get(hunter(server)).total());
		awaitElapsed(context, world, 15);
		context.takeScreenshot("execution-headshot-1");
		check(world.getServer().computeOnServer(server -> BodyService.get(hunter(server)).total()) == before, "the hunter is untouched while the archer aims");
		awaitElapsed(context, world, ExecutionMove.HEADSHOT.kill() + 1);
		context.takeScreenshot("execution-headshot-2");
		for (int tick = 0; tick < 30 && context.computeOnClient(mc -> mc.player.isAlive()); tick++) {
			context.waitTick();
		}
		context.waitForScreen(DeathScreen.class);
		check(context.computeOnClient(mc -> DeathScene.cause() == DeathScene.Cause.EXECUTION), "the headshot is an execution death");
		check(context.computeOnClient(mc -> {
			ExecutionView.Pose pose = ExecutionView.sample(mc.player, 0.0F);
			return pose != null && pose.move() == ExecutionMove.HEADSHOT && pose.done();
		}), "the fallen hunter keeps the arrow in the head");
		context.waitTicks(8);
		context.takeScreenshot("execution-headshot-3");
		context.waitTicks(10);
		context.takeScreenshot("execution-headshot-4");
		context.waitTicks(60);
		context.runOnClient(mc -> mc.player.respawn());
		context.waitFor(mc -> mc.player != null && mc.player.isAlive() && ExecutionView.sample(mc.player, 0.0F) == null, 200);
		context.waitTicks(20);
		world.getServer().runOnServer(server -> {
			removeHusks(server.overworld());
			for (BlockPos air : BlockPos.betweenClosed(stage.offset(-4, 0, -4), stage.offset(4, 5, 4))) {
				server.overworld().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
			}
		});
	}

	/** Whether a cause line carries the word for "executed" in its own crimson. */
	static boolean crimsonExecuted(final net.minecraft.network.chat.@org.jspecify.annotations.Nullable Component cause) {
		if (cause == null) {
			return false;
		}
		String word = net.minecraft.network.chat.Component.translatable("death.mistinn.executed").getString();
		return cause.visit((style, text) -> style.getColor() != null && style.getColor().getValue() == 0xB3121F && text.contains(word)
			? java.util.Optional.of(Boolean.TRUE) : java.util.Optional.empty(), net.minecraft.network.chat.Style.EMPTY).orElse(false);
	}

	private static void everyFinisherMobHasAMove(final ServerLevel level) {
		java.util.Map<String, ExecutionMove> expected = new java.util.LinkedHashMap<>();
		for (String id : new String[] {"zombie", "zombie_villager", "giant", "husk", "drowned", "zombified_piglin"}) {
			expected.put(id, ExecutionMove.GRAPPLE_BITE);
		}
		for (String id : new String[] {"spider", "cave_spider", "wolf", "silverfish", "endermite", "phantom"}) {
			expected.put(id, ExecutionMove.NECK_POUNCE);
		}
		for (String id : new String[] {"slime", "magma_cube", "sulfur_cube"}) {
			expected.put(id, ExecutionMove.LEAP_SLAM);
		}
		expected.put("warden", ExecutionMove.FIST_SLAM);
		expected.put("creaking", ExecutionMove.FIST_SLAM);
		for (String id : new String[] {"ravager", "hoglin", "zoglin", "polar_bear"}) {
			expected.put(id, ExecutionMove.CHARGE_MAUL);
		}
		for (String id : new String[] {"vindicator", "piglin", "piglin_brute", "vex"}) {
			expected.put(id, ExecutionMove.STOMP_STAB);
		}
		expected.put("wither_skeleton", ExecutionMove.SLASH);
		expected.put("enderman", ExecutionMove.NECK_TWIST);
		expected.put("creeper", null);
		for (var entry : expected.entrySet()) {
			Mob mob = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(entry.getKey()))
				.create(level, EntitySpawnReason.COMMAND);
			check(ExecutionMove.of(mob) == entry.getValue(), entry.getKey() + " finishes with " + entry.getValue() + ", got " + ExecutionMove.of(mob));
		}
		for (String id : new String[] {"zombie", "husk", "drowned", "zombie_villager", "zombified_piglin", "hoglin", "zoglin"}) {
			Mob young = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id))
				.create(level, EntitySpawnReason.COMMAND);
			young.setBaby(true);
			check(ExecutionMove.of(young) == ExecutionMove.NECK_POUNCE, "a young " + id + " goes for the throat, got " + ExecutionMove.of(young));
		}
		for (String id : new String[] {"skeleton", "stray", "bogged", "parched"}) {
			Mob archer = (Mob) BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(id))
				.create(level, EntitySpawnReason.COMMAND);
			archer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
			check(ExecutionMove.of(archer) == ExecutionMove.HEADSHOT, id + " with a bow takes a headshot");
			archer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
			check(ExecutionMove.of(archer) == ExecutionMove.SLASH, id + " without a bow slashes");
		}
		check(ExecutionMove.GRAPPLE_BITE.kill() == 58 && ExecutionMove.GRAPPLE_BITE.length() == 70, "the grapple bite kills at tick 58 of 70");
	}

	private static void inflictMarksOnlyRequestedWound(final ServerPlayer player) {
		WoundService.clearAll(player);
		WoundService.inflict(player, BodyPart.RIGHT_ARM, true, false);
		Wounds wounds = WoundService.get(player);
		check(wounds.get(BodyPart.RIGHT_ARM).fractured() && !wounds.get(BodyPart.RIGHT_ARM).bleeding(), "inflict breaks the arm without opening it");
		for (BodyPart part : BodyPart.values()) {
			check(part == BodyPart.RIGHT_ARM || !wounds.get(part).any(), "no other part is wounded: " + part);
		}
		WoundService.inflict(player, BodyPart.RIGHT_ARM, false, true);
		check(WoundService.get(player).get(BodyPart.RIGHT_ARM).fractured() && WoundService.get(player).get(BodyPart.RIGHT_ARM).bleeding(),
			"a second wound adds to the first");
		WoundService.clearAll(player);
	}

	private static void removeHusks(final ServerLevel level) {
		// every creature a test placed, whatever its kind
		java.util.List<Entity> placed = new java.util.ArrayList<>();
		level.getAllEntities().forEach(entity -> {
			if (entity.entityTags().contains("mistinn.test_finisher")) {
				placed.add(entity);
			}
		});
		placed.forEach(Entity::discard);
		// an armour stand an arrow broke leaves itself behind as an item
		level.getAllEntities().forEach(entity -> {
			if (entity instanceof net.minecraft.world.entity.item.ItemEntity item && item.getItem().is(net.minecraft.world.item.Items.ARMOR_STAND)) {
				placed.add(entity);
			}
		});
		placed.forEach(Entity::discard);
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
		// the blow a released windup lands
		life.shizune.mistinn.enemy.WindupService.land(player.level(), mob, player);
		player.setDeltaMovement(Vec3.ZERO);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
