package life.shizune.mistinn.test;

import com.mojang.serialization.JsonOps;
import java.util.LinkedHashSet;
import java.util.Set;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.medical.SurgeryKitItem;
import life.shizune.mistinn.medical.TreatmentService;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.skill.SkillTree;
import life.shizune.mistinn.skill.Skills;
import life.shizune.mistinn.skill.SlideService;
import life.shizune.mistinn.skill.Stat;
import life.shizune.mistinn.client.move.RollClient;
import life.shizune.mistinn.stamina.RollService;
import life.shizune.mistinn.stamina.StaminaService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import life.shizune.mistinn.client.death.DeathScene;
import life.shizune.mistinn.client.death.MissionEndScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The hunter's four stats and the skill tree they come from. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnSkillTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		statsScaleAsWritten();
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty easy");
			world.getServer().runCommand("time set noon");
			context.waitTicks(10);
			strengthHitsHarder(world);
			speedMovesFaster(world);
			constitutionRaisesTheCeiling(context, world);
			intellectHealsFaster(context, world);
			statsSurviveRespawn(context, world);
			majorNodeTakesOneRustSalt(world);
			minorNodesCostSalt(world);
			needsTheNodeBefore(world);
			unlockingTwiceChargesOnce(context, world);
			context.runOnClient(MistinnSkillTest::slidePoseStaysOnTheGround);
			BlockPos lane = world.getServer().computeOnServer(MistinnSkillTest::lane);
			slideNeedsTheSkill(context, world, lane);
			sprintCrouchSlides(context, world, lane);
			slideKicksWhatItMeets(context, world, lane);
			noSlideOnDeadLegs(world);
			slideFrames(context, world, lane);
		}
	}

	// ── The numbers ───────────────────────────────────────────────

	/** Each stat's effect per point, up to the cap of 20, as the design gives it. */
	private static void statsScaleAsWritten() {
		check(near(SkillService.damageScale(5), 1.15), "five strength hit 15% harder");
		check(near(SkillService.moveScale(10), 1.10) && near(SkillService.swingScale(10), 1.15) && near(SkillService.rollScale(10), 1.15), "ten speed: 10% faster on foot, 15% faster swings and rolls");
		check(SkillService.dodgeBonus(4) == 0 && SkillService.dodgeBonus(5) == 1 && SkillService.dodgeBonus(20) == 4, "a tick more dodge for every five speed");
		check(near(SkillService.partCeiling(10), 100.0) && near(SkillService.partCeiling(20), 120.0) && BodyPart.CEILING == 120.0F, "constitution raises each part by 2, to 120 at most");
		check(near(SkillService.staminaCeiling(10), 130.0) && near(SkillService.staminaCeiling(20), 160.0), "and stamina by 3, to 160");
		check(near(SkillService.woundScale(10), 0.7), "ten constitution leave 70% of the chance of a wound");
		check(near(SkillService.treatmentScale(10), 0.75) && near(SkillService.treatmentScale(20), 0.5) && SkillService.treatmentScale(40) >= 0.5, "intellect shortens treatment, never below half");
		check(near(SkillService.healScale(10), 1.30), "and heals 3% more per point");
		check(Stat.STRENGTH.key().equals("stat.mistinn.strength"), "stats have their own names");
	}

	// ── On a hunter ───────────────────────────────────────────────

	private static void strengthHitsHarder(final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			SkillService.set(player, Skills.EMPTY);
			double weak = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
			SkillService.set(player, skills(Stat.STRENGTH, 5));
			check(SkillService.stat(player, Stat.STRENGTH) == 5, "five strength nodes give five strength");
			double strong = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
			check(near(strong / weak, 1.15), "five strength hit 15% harder: " + weak + " -> " + strong);
			SkillService.set(player, Skills.EMPTY);
			check(near(player.getAttributeValue(Attributes.ATTACK_DAMAGE), weak), "and without them as before");
		});
	}

	private static void speedMovesFaster(final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			BodyService.restoreAll(player);
			SkillService.set(player, Skills.EMPTY);
			double move = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
			double swing = player.getAttributeValue(Attributes.ATTACK_SPEED);
			float roll = RollService.rollSpeed(player, BodyService.get(player));
			int dodge = RollService.dodgeTicks(player, BodyService.get(player));
			SkillService.set(player, skills(Stat.SPEED, 5));
			check(near(player.getAttributeValue(Attributes.MOVEMENT_SPEED) / move, 1.05), "five speed: 5% faster on foot");
			check(near(player.getAttributeValue(Attributes.ATTACK_SPEED) / swing, 1.075), "7.5% faster swings");
			check(near(RollService.rollSpeed(player, BodyService.get(player)) / roll, 1.075), "rolls 7.5% faster");
			check(RollService.dodgeTicks(player, BodyService.get(player)) == dodge + 1, "and dodges a tick longer");
			SkillService.set(player, Skills.EMPTY);
		});
	}

	private static void constitutionRaisesTheCeiling(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			SkillService.set(player, skills(Stat.CONSTITUTION, 5));
			check(near(BodyService.max(player), 90.0), "five constitution: each part holds 90");
			BodyService.restoreAll(player);
			for (BodyPart part : BodyPart.values()) {
				check(near(BodyService.get(player).get(part), 90.0), "restoring fills " + part.id + " to 90: " + BodyService.get(player));
			}
			check(near(player.getHealth(), player.getMaxHealth()), "a whole body at the new ceiling is full vanilla health");
			BodyService.set(player, BodyService.get(player).with(BodyPart.LEFT_ARM, 40.0F));
			BodyService.heal(player, 20.0F);
			check(near(BodyService.get(player).get(BodyPart.LEFT_ARM), 90.0), "healing reaches the new ceiling: " + BodyService.get(player));
			check(near(StaminaService.max(player), 115.0), "stamina holds 115");
			check(near(SkillService.woundChance(player, 0.5F), 0.425), "wounds come 15% less often");
			// a saved body at the new ceiling reads back as it was
			Body saved = BodyService.get(player);
			Body read = Body.CODEC.parse(JsonOps.INSTANCE, Body.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow()).getOrThrow();
			check(read.equals(saved), "a body above 80 survives saving: " + read);
		});
		context.waitTicks(60);
		world.getServer().runOnServer(server -> check(StaminaService.get(player(server)) > StaminaService.MAX + 1.0F, "stamina recovers past 100: " + StaminaService.get(player(server))));
		check(context.computeOnClient(mc -> near(BodyService.max(mc.player), 90.0) && near(StaminaService.max(mc.player), 115.0)), "the hunter's own client knows the new ceilings (for the HUD)");
		world.getServer().runOnServer(server -> {
			SkillService.set(player(server), Skills.EMPTY);
			BodyService.restoreAll(player(server));
		});
	}

	private static void intellectHealsFaster(final ClientGameTestContext context, final TestSingleplayerContext world) {
		// a surgery kit takes 160 ticks; five intellect make it 140
		for (boolean clever : new boolean[] {false, true}) {
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				SkillService.set(player, clever ? skills(Stat.INTELLECT, 5) : Skills.EMPTY);
				BodyService.restoreAll(player);
				BodyService.set(player, BodyService.get(player).with(BodyPart.LEFT_ARM, 0.0F));
			});
			world.getServer().runCommand("item replace entity @a weapon.mainhand with mistinn:surgery_kit");
			context.runOnClient(mc -> mc.player.snapTo(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), 60.0F));
			context.waitTicks(5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(2);
			check(world.getServer().computeOnServer(server -> TreatmentService.active(player(server))), "the surgery starts");
			context.waitTicks(148);
			boolean active = world.getServer().computeOnServer(server -> TreatmentService.active(player(server)));
			float arm = world.getServer().computeOnServer(server -> BodyService.get(player(server)).get(BodyPart.LEFT_ARM));
			if (clever) {
				check(!active, "five intellect finish the surgery within 150 ticks");
				check(Math.abs(arm - SurgeryKitItem.REVIVE * 1.15F) < 0.6F, "and bring the arm back 15% stronger: " + arm);
			} else {
				check(active && arm <= 0.0F, "without intellect it is still going at 150 ticks");
				context.waitTicks(20);
			}
		}
		world.getServer().runOnServer(server -> {
			SkillService.set(player(server), Skills.EMPTY);
			BodyService.restoreAll(player(server));
		});
		world.getServer().runCommand("item replace entity @a weapon.mainhand with air");
	}

	private static void statsSurviveRespawn(final ClientGameTestContext context, final TestSingleplayerContext world) {
		Set<String> nodes = new LinkedHashSet<>(skills(Stat.STRENGTH, 5).unlocked());
		nodes.addAll(skills(Stat.CONSTITUTION, 5).unlocked());
		Skills learned = new Skills(nodes);
		check(Skills.CODEC.parse(JsonOps.INSTANCE, Skills.CODEC.encodeStart(JsonOps.INSTANCE, learned).getOrThrow()).getOrThrow().equals(learned), "skills survive saving");
		world.getServer().runOnServer(server -> SkillService.set(player(server), learned));
		world.getServer().runCommand("kill @a");
		context.waitForScreen(DeathScreen.class);
		// the choices answer only once the death scene has played and the screen has come up (real time)
		context.waitFor(mc -> mc.gui.screen() instanceof MissionEndScreen && DeathScene.seconds() > DeathScene.SCREEN + 1.8F, 600);
		returnToMission(context);
		context.waitFor(mc -> mc.player != null && mc.player.isAlive() && !(mc.gui.screen() instanceof MissionEndScreen), 200);
		context.waitTicks(20);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(SkillService.get(player).equals(learned), "the respawned hunter keeps their skills: " + SkillService.get(player));
			AttributeModifier strength = player.getAttribute(Attributes.ATTACK_DAMAGE).getModifier(Mistinn.id("skill_strength"));
			check(strength != null && near(strength.amount(), 0.15), "and their strength is back on: " + strength);
			for (BodyPart part : BodyPart.values()) {
				check(near(BodyService.get(player).get(part), 90.0), "and comes back whole at the higher ceiling: " + BodyService.get(player));
			}
			SkillService.set(player, Skills.EMPTY);
			BodyService.restoreAll(player);
		});
	}

	// ── Unlocking ─────────────────────────────────────────────────

	private static void majorNodeTakesOneRustSalt(final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getInventory().clearContent();
			GridService.set(player, GridInventory.EMPTY);
			SkillService.set(player, Skills.EMPTY);
			check(SkillService.unlock(player, SkillTree.ROOT) == SkillService.Result.NO_RUST_SALT, "the slide needs a rust god salt");
			check(!SkillService.get(player).has(SkillTree.ROOT), "and is not unlocked without one");
			// two rust salts kept in the safe box of the grid (they are 2x2), not on the hotbar
			GridService.set(player, GridInventory.EMPTY.with(Area.SAFE, java.util.List.of(new Placed(0, 0, 0, false, new ItemStack(MistinnItems.RUST_GOD_SALT, 2)))));
			check(rustSalt(player) == 2, "two rust salts in the safe box");
			check(SkillService.unlock(player, SkillTree.ROOT) == SkillService.Result.UNLOCKED, "with one in the grid the slide unlocks");
			check(SkillService.get(player).has(SkillTree.ROOT), "and is learned");
			check(rustSalt(player) == 1, "it takes exactly one rust salt, " + rustSalt(player) + " left");
			check(SkillService.price(player, SkillTree.ROOT) == 0, "a major node costs no salt");
		});
	}

	private static void minorNodesCostSalt(final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			SkillService.set(player, new Skills(Set.of(SkillTree.ROOT)));
			SaltService.set(player, 1000);
			check(SkillService.price(player, "strength_1") == 200, "the first minor node costs 200");
			check(SkillService.unlock(player, "strength_1") == SkillService.Result.UNLOCKED && SaltService.get(player) == 800, "and takes 200 salt, " + SaltService.get(player) + " left");
			check(SkillService.price(player, "speed_1") == 300, "the second costs 300");
			check(SkillService.unlock(player, "speed_1") == SkillService.Result.UNLOCKED && SaltService.get(player) == 500, "and takes 300, " + SaltService.get(player) + " left");
			SaltService.set(player, 100);
			check(SkillService.unlock(player, "constitution_1") == SkillService.Result.NO_SALT, "without enough salt a node stays locked");
			check(SaltService.get(player) == 100 && !SkillService.get(player).has("constitution_1"), "and nothing is taken");
			check(SkillService.stat(player, Stat.STRENGTH) == 1 && SkillService.stat(player, Stat.SPEED) == 1, "each bought node adds a point");
		});
	}

	private static void needsTheNodeBefore(final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			SaltService.set(player, 100_000);
			SkillService.set(player, Skills.EMPTY);
			check(SkillService.unlock(player, "strength_1") == SkillService.Result.LOCKED_PARENT, "nothing before the slide");
			SkillService.set(player, new Skills(Set.of(SkillTree.ROOT)));
			check(SkillService.unlock(player, "strength_2") == SkillService.Result.LOCKED_PARENT, "a branch is bought in order");
			check(SkillService.unlock(player, "no_such_node") == SkillService.Result.UNKNOWN, "an unknown node is refused");
			check(SaltService.get(player) == 100_000, "refusals cost nothing");
			check(SkillService.unlock(player, SkillTree.ROOT) == SkillService.Result.ALREADY, "a learned node is not bought again");
		});
	}

	/** Two requests for the same node in one tick, as a double click sends them: charged once. */
	private static void unlockingTwiceChargesOnce(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			SkillService.set(player, new Skills(Set.of(SkillTree.ROOT)));
			SaltService.set(player, 1000);
		});
		context.waitTicks(5);
		context.runOnClient(mc -> {
			ClientPlayNetworking.send(new SkillService.UnlockPayload("strength_1"));
			ClientPlayNetworking.send(new SkillService.UnlockPayload("strength_1"));
		});
		context.waitTicks(10);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(SkillService.get(player).has("strength_1"), "the request unlocks the node");
			check(SaltService.get(player) == 800, "and charges once: " + SaltService.get(player) + " left");
		});
		check(context.computeOnClient(mc -> SkillService.get(mc.player).has("strength_1")), "the hunter's own client hears about the new skill");
		world.getServer().runOnServer(server -> SkillService.set(player(server), Skills.EMPTY));
	}

	// ── The slide ─────────────────────────────────────────────────

	/**
	 * The slide's pose, in the sandbox, at every step of the clip: no part of the hunter sinks into
	 * the floor, while it slides the hips are low and the feet lead the head.
	 */
	private static void slidePoseStaysOnTheGround(final Minecraft mc) {
		List<String> sunk = new java.util.ArrayList<>();
		for (int i = 0; i <= 28; i++) {
			float t = i / 28.0F;
			List<ClipProbe.Box> boxes = ClipProbe.boxes(mc, mc.player, Vec3.ZERO, 0.0F,
				state -> state.setData(RollClient.KEY, new RollClient.Pose(RollService.Kind.SLIDE, t, 0.0F, 1.0F, 1.0F)), false, null, null);
			float lowest = Float.MAX_VALUE;
			String deepest = "";
			for (ClipProbe.Box box : boxes) {
				if (bottom(box) < lowest) {
					lowest = bottom(box);
					deepest = box.path();
				}
			}
			ClipProbe.Box headBox = part(boxes, "/head");
			System.out.println(String.format("SLIDE t=%.3f lowest %.3f %s head y %.3f z %.3f", t, lowest, deepest, headBox.center().y, headBox.center().z));
			sunk.add(lowest >= -MistinnClipTest.TOLERANCE ? "" : String.format("t=%.2f %.3f (%s)", t, lowest, deepest));
			if (t >= 0.25F && t <= 0.55F) {
				ClipProbe.Box head = part(boxes, "/head");
				ClipProbe.Box rightLeg = part(boxes, "/right_leg");
				ClipProbe.Box leftLeg = part(boxes, "/left_leg");
				ClipProbe.Box body = part(boxes, "/body");
				check(head.center().y < 1.0F, String.format("at t=%.2f the hunter is low: head at %.2f", t, head.center().y));
				check(lowest < 0.12F, String.format("at t=%.2f the hunter is on the floor, lowest %.3f", t, lowest));
				// the hunter faces +z: the feet are ahead, the head leans back behind the hips
				check(rightLeg.center().z > body.center().z + 0.2F && leftLeg.center().z > body.center().z, String.format("at t=%.2f the feet lead", t));
				check(head.center().z < body.center().z, String.format("at t=%.2f the head leans back", t));
			}
		}
		sunk.removeIf(String::isEmpty);
		check(sunk.isEmpty(), "nothing sinks into the floor at any moment of the slide: " + sunk);
	}

	private static float bottom(final ClipProbe.Box box) {
		return box.center().y - (box.half()[0] * Math.abs(box.axes()[0].y) + box.half()[1] * Math.abs(box.axes()[1].y) + box.half()[2] * Math.abs(box.axes()[2].y));
	}

	private static ClipProbe.Box part(final List<ClipProbe.Box> boxes, final String path) {
		return boxes.stream().filter(b -> b.path().equals(path)).findFirst().orElseThrow(() -> new AssertionError("no box " + path));
	}

	/** A stone lane high above the world, 5 wide and 34 long, running towards +z. */
	private static BlockPos lane(final MinecraftServer server) {
		ServerPlayer player = player(server);
		BlockPos pos = player.blockPosition().above(24).immutable();
		for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-2, -1, -3), pos.offset(2, -1, 30))) {
			player.level().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		}
		for (BlockPos air : BlockPos.betweenClosed(pos.offset(-2, 0, -3), pos.offset(2, 3, 30))) {
			player.level().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
		}
		return pos;
	}

	/**
	 * Runs down the lane with the real keys: forward and sprint held, then crouch pressed. Returns how far the
	 * hunter went in the 10 ticks after crouching, and how much stamina those ticks cost.
	 */
	private static double[] sprintThenCrouch(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos lane) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			BodyService.restoreAll(player);
			player.teleportTo(server.overworld(), lane.getX() + 0.5, lane.getY(), lane.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		});
		// rested, so stamina is full
		context.waitTicks(90);
		context.getInput().holdKey(options -> options.keyUp);
		context.getInput().holdKey(options -> options.keySprint);
		context.waitTicks(15);
		check(context.computeOnClient(mc -> mc.player.isSprinting()), "the hunter is running before crouching");
		double from = context.computeOnClient(mc -> mc.player.getZ());
		float before = world.getServer().computeOnServer(server -> StaminaService.get(player(server)));
		context.getInput().holdKey(options -> options.keyShift);
		context.waitTicks(10);
		double to = context.computeOnClient(mc -> mc.player.getZ());
		float after = world.getServer().computeOnServer(server -> StaminaService.get(player(server)));
		context.getInput().releaseKey(options -> options.keyShift);
		context.getInput().releaseKey(options -> options.keySprint);
		context.getInput().releaseKey(options -> options.keyUp);
		context.waitTicks(30);
		return new double[] {to - from, before - after};
	}

	private static void slideNeedsTheSkill(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos lane) {
		world.getServer().runOnServer(server -> {
			SkillService.set(player(server), Skills.EMPTY);
			check(!SlideService.request(player(server), 0.0F), "the server refuses a slide without the skill");
		});
		context.waitTicks(5);
		double[] run = sprintThenCrouch(context, world, lane);
		check(run[0] < 2.0, "without the skill crouching while running only crouches: went " + run[0]);
		check(run[1] < 6.0, "and costs no slide: " + run[1]);
	}

	private static void sprintCrouchSlides(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos lane) {
		world.getServer().runOnServer(server -> SkillService.set(player(server), new Skills(Set.of(SkillTree.ROOT))));
		context.waitTicks(5);
		double[] run = sprintThenCrouch(context, world, lane);
		check(run[0] >= 4.0, "running and crouching slides at least four blocks in half a second: went " + run[0]);
		check(run[0] <= 6.5, "and no further than the slide carries: went " + run[0]);
		check(run[1] >= SlideService.COST - 0.01F && run[1] < SlideService.COST + 6.0F, "the slide costs 18 stamina: " + run[1]);
	}

	private static void slideKicksWhatItMeets(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos lane) {
		int husk = world.getServer().computeOnServer(server -> {
			Husk mob = EntityTypes.HUSK.create(server.overworld(), EntitySpawnReason.COMMAND);
			mob.setNoAi(true);
			mob.snapTo(lane.getX() + 0.5, lane.getY(), lane.getZ() + 7.5, 180.0F, 0.0F);
			server.overworld().addFreshEntity(mob);
			return mob.getId();
		});
		float before = world.getServer().computeOnServer(server -> ((LivingEntity) server.overworld().getEntity(husk)).getHealth());
		sprintThenCrouch(context, world, lane);
		world.getServer().runOnServer(server -> {
			LivingEntity mob = (LivingEntity) server.overworld().getEntity(husk);
			float lost = before - mob.getHealth();
			check(lost > 2.5F && lost < 3.1F, "the slide sweeps the husk once, for about 3: lost " + lost);
			check(mob.getDeltaMovement().y > 0.2, "and lifts it off its feet: " + mob.getDeltaMovement());
			mob.discard();
		});
	}

	private static void noSlideOnDeadLegs(final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			SkillService.set(player, new Skills(Set.of(SkillTree.ROOT)));
			BodyService.set(player, BodyService.get(player).with(BodyPart.LEFT_LEG, 0.0F).with(BodyPart.RIGHT_LEG, 0.0F));
			check(!SlideService.request(player, 0.0F), "no slide on two dead legs");
			BodyService.set(player, BodyService.get(player).with(BodyPart.RIGHT_LEG, 40.0F));
			check(SlideService.request(player, 0.0F), "one leg is enough");
			BodyService.restoreAll(player);
		});
	}

	/** The slide frozen at five moments, seen from the side, for the eye (written to the screenshots folder). */
	private static void slideFrames(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos lane) {
		context.waitTicks(40);
		world.getServer().runOnServer(server -> SkillService.set(player(server), new Skills(Set.of(SkillTree.ROOT))));
		sprintThenCrouch(context, world, lane);
		context.runOnClient(mc -> {
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		// a slide straight ahead, then the camera swings round to its side: the body keeps the slide's heading
		context.waitTicks(30);
		world.getServer().runOnServer(server -> SlideService.request(player(server), 0.0F));
		context.waitTicks(2);
		context.runOnClient(mc -> {
			mc.player.setYRot(90.0F);
			mc.player.setXRot(15.0F);
		});
		for (float t : new float[] {0.1F, 0.25F, 0.45F, 0.7F, 0.9F}) {
			context.runOnClient(mc -> RollClient.preview = t);
			context.waitTicks(2);
			context.takeScreenshot(TestScreenshotOptions.of("slide-" + Math.round(t * 100)).disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("slide")));
		}
		context.runOnClient(mc -> {
			RollClient.preview = -1.0F;
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		world.getServer().runOnServer(server -> SkillService.set(player(server), Skills.EMPTY));
	}

	// ── Helpers ───────────────────────────────────────────────────

	/** Rust god salts the hunter carries, on the hotbar and in the grid. */
	private static int rustSalt(final ServerPlayer player) {
		int count = player.getInventory().countItem(MistinnItems.RUST_GOD_SALT);
		GridInventory grid = GridService.get(player);
		for (Area area : Area.values()) {
			for (Placed placed : grid.items(area)) {
				if (placed.stack().is(MistinnItems.RUST_GOD_SALT)) {
					count += placed.stack().getCount();
				}
			}
		}
		return count;
	}

	/** The root node and the first {@code points} nodes of one stat's branch. */
	private static Skills skills(final Stat stat, final int points) {
		Set<String> nodes = new LinkedHashSet<>();
		nodes.add("slide");
		for (int i = 1; i <= points; i++) {
			nodes.add(stat.id + "_" + i);
		}
		return new Skills(nodes);
	}

	private static void returnToMission(final ClientGameTestContext context) {
		double[] at = context.computeOnClient(mc -> {
			double[] gui = ((MissionEndScreen) mc.gui.screen()).choiceCentre(0);
			double scale = mc.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static boolean near(final double a, final double b) {
		return Math.abs(a - b) < 1.0E-3;
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
