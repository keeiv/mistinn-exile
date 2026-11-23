package life.shizune.mistinn.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Set;
import java.util.function.Consumer;
import life.shizune.mistinn.client.death.MissionEndScreen;
import life.shizune.mistinn.client.station.StationTalkScreen;
import life.shizune.mistinn.client.story.StoryCardScreen;
import life.shizune.mistinn.client.title.ModeSelectScreen;
import life.shizune.mistinn.client.training.StationAmbience;
import life.shizune.mistinn.client.training.TrainingClient;
import life.shizune.mistinn.client.training.TrainingCompleteScreen;
import life.shizune.mistinn.client.training.TrainingGuide;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.enemy.NoiseService;
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.skill.SkillTree;
import life.shizune.mistinn.skill.SlideService;
import life.shizune.mistinn.skill.Stat;
import life.shizune.mistinn.stamina.RollService;
import life.shizune.mistinn.stamina.StaminaService;
import life.shizune.mistinn.weapon.HeavyService;
import life.shizune.mistinn.weapon.WeaponStyle;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.residue.DeathMark;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.station.StaffRole;
import life.shizune.mistinn.station.StationHand;
import life.shizune.mistinn.station.StationBroadcast;
import life.shizune.mistinn.story.StoryService;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.training.TrainingGround;
import life.shizune.mistinn.training.TrainingLesson;
import life.shizune.mistinn.training.TrainingService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer.MultiplayerScope;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Plays the training route through the real singleplayer client and verifies both deployments. */
public class MistinnTrainingTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet") || Boolean.getBoolean("mistinn.medreel")) {
			return;
		}
		context.runOnClient(mc -> {
			mc.options.guiScale().set(4);
			mc.options.pauseOnLostFocus = false;
			mc.options.renderDistance().set(12);
			mc.getWindow().setWindowed(1920, 1080);
		});
		context.runOnClient(mc -> {
			mc.options.languageCode = "zh_tw";
			mc.getLanguageManager().setSelected("zh_tw");
		});
		var reload = context.computeOnClient(Minecraft::reloadResourcePacks);
		context.waitFor(mc -> reload.isDone(), 1000);
		openCourseFromWorldList(context);
		try {
			context.runOnClient(mc -> {
				var server = mc.getSingleplayerServer();
				// World Options calls this same vanilla method. Port zero lets the OS choose a free port.
				boolean opened = server.publishServer(MultiplayerScope.LAN, 0);
				if (opened) {
					server.unpublishServer();
				}
				check(!opened, "vanilla world options cannot publish a training save");
			});
			playCourse(context);
			deploy(context, WorldMode.STORY);
		} finally {
			closeWorld(context);
		}
		// World creation through the mode card was exercised above. Reuse its creation action
		// here to isolate the second destination from the previous save's world-list reload.
		// the second save is a Rust Anchor one: the course runs the same, with a 3x3 safe box
		context.runOnClient(mc -> TrainingClient.createWorld(mc, life.shizune.mistinn.hardship.Hardship.RUST_ANCHOR));
		waitForCourse(context, life.shizune.mistinn.hardship.Hardship.RUST_ANCHOR);
		server(context, player -> check(life.shizune.mistinn.grid.GridService.get(player).safeSize() == 3, "a Rust Anchor hunter keeps a 3x3 safe box"));
		try {
			// The full course above proves how the last objective is earned. This world checks
			// the other branch from the same final objective.
			// a save built before the station: the course starts over on the new map
			server(context, player -> {
				player.setAttached(TrainingService.LESSON, TrainingLesson.RADIO);
				player.setAttached(MistinnAttachments.BELL, net.minecraft.core.GlobalPos.of(player.level().dimension(), new BlockPos(0, 65, -64)));
				TrainingGround.markUpgraded(player.level());
			});
			context.waitTicks(10);
			server(context, player -> {
				check(player.getAttached(TrainingService.LESSON) == TrainingLesson.ARRIVAL, "an upgraded save starts the course over");
				check(!player.hasAttached(MistinnAttachments.BELL), "with no bell from the old quays");
				check(player.distanceToSqr(Vec3.atBottomCenterOf(TrainingGround.ENTRY)) < 4.0, "back in the barracks");
			});
			server(context, player -> player.setAttached(TrainingService.LESSON, TrainingLesson.BOARD));
			board(context);
			deploy(context, WorldMode.FREE);
		} finally {
			closeWorld(context);
		}
	}

	private static void openCourseFromWorldList(final ClientGameTestContext context) {
		context.setScreen(() -> new SelectWorldScreen(new TitleScreen()));
		context.waitTicks(4);
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof SelectWorldScreen list) {
				CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(list));
			}
		});
		context.waitForScreen(ModeSelectScreen.class);
		context.takeScreenshot("training-01-mode-select");
		context.runOnClient(mc -> ((ModeSelectScreen) mc.gui.screen()).pick(WorldMode.TRAINING));
		// training asks how hard the world is; normal is already lit, and Escape goes back to the modes
		context.waitForScreen(life.shizune.mistinn.client.title.HardshipScreen.class);
		check(context.computeOnClient(mc -> ((life.shizune.mistinn.client.title.HardshipScreen) mc.gui.screen()).lit(life.shizune.mistinn.hardship.Hardship.NORMAL)),
			"normal is the hardship already chosen");
		context.waitTicks(10);
		context.takeScreenshot("training-01b-hardship");
		context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		context.waitForScreen(ModeSelectScreen.class);
		context.runOnClient(mc -> ((ModeSelectScreen) mc.gui.screen()).pick(WorldMode.TRAINING));
		context.waitForScreen(life.shizune.mistinn.client.title.HardshipScreen.class);
		context.runOnClient(mc -> ((life.shizune.mistinn.client.title.HardshipScreen) mc.gui.screen()).pick(life.shizune.mistinn.hardship.Hardship.NORMAL));
		waitForCourse(context, life.shizune.mistinn.hardship.Hardship.NORMAL);
		server(context, player -> check(life.shizune.mistinn.grid.GridService.get(player).safeSize() == 2, "a normal hunter keeps a 2x2 safe box"));
	}

	private static void waitForCourse(final ClientGameTestContext context, final life.shizune.mistinn.hardship.Hardship hardship) {
		context.waitFor(mc -> mc.player != null, 1000);
		context.waitTicks(20);
		waitLesson(context, TrainingLesson.ARRIVAL);
		server(context, player -> {
			check(WorldMode.of(player.level().getServer()) == WorldMode.TRAINING, "the third card created a training save");
			check(life.shizune.mistinn.hardship.Hardship.of(player.level().getServer()) == hardship, "the save is as hard as chosen: " + hardship.id);
			check(player.level().dimension().equals(TrainingGround.KEY), "the owner arrived at the course");
			check(player.gameMode() == GameType.SURVIVAL, "the course starts in survival");
		});
	}

	private static void playCourse(final ClientGameTestContext context) {
		context.waitTicks(40);
		context.takeScreenshot("training-02-arrival");
		overview(context);
		crewAtWork(context);
		context.runOnClient(mc -> ClientPlayNetworking.send(new TrainingService.DeployPayload(true)));
		context.waitTicks(10);
		waitLesson(context, TrainingLesson.ARRIVAL);

		go(context, TrainingLesson.ARRIVAL);
		waitLesson(context, TrainingLesson.QUARTERMASTER);
		check(context.computeOnClient(mc -> TrainingLesson.QUARTERMASTER.target.equals(TrainingGuide.target(mc, TrainingLesson.QUARTERMASTER))),
			"the arrow points at the quartermaster");
		talk(context, StaffRole.QUARTERMASTER, "kit");
		waitLesson(context, TrainingLesson.GEAR);
		check(context.computeOnClient(mc -> TrainingLesson.KIT.equals(TrainingGuide.target(mc, TrainingLesson.GEAR))),
			"the guide points at the gear chest");
		go(context, TrainingLesson.GEAR);
		server(context, player -> {
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(MistinnItems.ARMORS.get(3)));
			GridService.set(player, GridInventory.EMPTY
				.withBackpack(new ItemStack(MistinnItems.BACKPACKS.get(2)))
				.withRig(new ItemStack(MistinnItems.RIGS.getFirst()))
				.withMask(new ItemStack(MistinnItems.MASKS.getFirst())));
		});
		waitLesson(context, TrainingLesson.PACK);
		server(context, player -> GridService.set(player, GridService.get(player).with(Area.BACKPACK,
			java.util.List.of(new Placed(0, 0, 0, true, new ItemStack(MistinnItems.SPLINT))))));
		waitLesson(context, TrainingLesson.HEAVY);
		go(context, TrainingLesson.HEAVY);
		context.waitTicks(10);
		server(context, player -> {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.ANCHOR_BLADE));
			rested(player);
			int ticks = HeavyService.chargeTicks(player, WeaponStyle.SLASH);
			check(HeavyService.release(player, player.getMainHandItem(), ticks), "a full heavy blow is let go into the forge's dummies");
		});
		waitLesson(context, TrainingLesson.SPRINT);
		go(context, TrainingLesson.SPRINT);
		server(context, player -> player.setSprinting(true));
		waitLesson(context, TrainingLesson.ROLL);
		context.waitFor(mc -> mc.player != null && mc.player.onGround(), 200);
		context.getInput().pressKey(options -> java.util.Arrays.stream(options.keyMappings)
			.filter(key -> key.getName().equals("key.mistinn.roll")).findFirst().orElseThrow());
		waitLesson(context, TrainingLesson.SLIDE);
		go(context, TrainingLesson.SLIDE);
		server(context, player -> {
			check(SkillService.get(player).has(SkillTree.ROOT), "the drill yard lends the slide for the lesson");
			rested(player);
			player.setSprinting(true);
			check(SlideService.request(player, 180.0F), "a trial slide goes");
		});
		waitLesson(context, TrainingLesson.STRIKE);
		go(context, TrainingLesson.STRIKE);
		defeat(context, TrainingLesson.STRIKE, "mistinn.training_target");
		waitLesson(context, TrainingLesson.SALT);
		holdAndUse(context, MistinnItems.SALT, 1);
		waitLesson(context, TrainingLesson.WINDUP);
		go(context, TrainingLesson.WINDUP);
		context.waitTicks(10);
		// the sparring husk comes on and winds up on its own; the hunter rolls as it lets go
		boolean[] winds = {false};
		for (int i = 0; i < 400 && !winds[0]; i++) {
			server(context, player -> {
				KeepWhole.of(player);
				winds[0] = sparringWinds(player);
			});
			context.waitTicks(1);
		}
		check(winds[0], "the sparring husk comes on and winds up a blow");
		for (int i = 0; i < 30 && context.computeOnClient(mc -> mc.player.getAttached(TrainingService.LESSON)) == TrainingLesson.WINDUP; i++) {
			server(context, player -> {
				rested(player);
				KeepWhole.of(player);
				RollService.request(player, 0.0F);
			});
			context.waitTicks(3);
		}
		waitLesson(context, TrainingLesson.SNEAK);
		server(context, player -> {
			// walking, not running: the listeners only hear the noise made on purpose below
			player.setSprinting(false);
			move(player, new BlockPos(9, 77, -66));
		});
		context.waitTicks(10);
		server(context, player -> {
			check(player.level().getEntitiesOfClass(Mob.class, new AABB(TrainingLesson.SNEAK.target).inflate(16),
				mob -> mob.entityTags().contains("mistinn.training_caged")).size() == 3, "three listeners wait in the cages");
			NoiseService.emit(player, NoiseService.Noise.SPRINT);
		});
		context.waitTicks(60);
		server(context, player -> {
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, new AABB(TrainingLesson.SNEAK.target).inflate(16),
				m -> m.entityTags().contains("mistinn.training_caged"))) {
				check(mob.getZ() < -70.0 && mob.getZ() > -75.0 && mob.getY() >= 76.0, "a listener drawn by the noise stays in its cage: " + mob.position());
			}
			check(player.getAttached(TrainingService.LESSON) == TrainingLesson.SNEAK, "heard, the lesson starts over");
			check(player.distanceToSqr(Vec3.atBottomCenterOf(TrainingGround.SNEAK_START)) < 4.0, "and the hunter is sent back to the pen's door");
		});
		go(context, TrainingLesson.SNEAK, 1);
		server(context, player -> useBlock(player, TrainingLesson.SNEAK.target, ItemStack.EMPTY));
		waitLesson(context, TrainingLesson.TOURNIQUET);
		go(context, TrainingLesson.TOURNIQUET);
		context.waitTicks(20);
		context.takeScreenshot("training-03-infirmary");
		holdAndUse(context, MistinnItems.TOURNIQUET, 1);
		waitLesson(context, TrainingLesson.SPLINT);
		holdAndUse(context, MistinnItems.SPLINT, 1);
		waitLesson(context, TrainingLesson.PAINKILLER);
		holdAndUse(context, MistinnItems.PAINKILLERS, 1);
		waitLesson(context, TrainingLesson.SURGERY);
		holdAndUse(context, MistinnItems.SURGERY_KIT, 1);
		waitLesson(context, TrainingLesson.INJECTOR);
		holdAndUse(context, MistinnItems.POTENT_INJECTOR, 1);
		waitLesson(context, TrainingLesson.REPAIR);
		holdAndUse(context, MistinnItems.ARMOR_REPAIR_KITS.getFirst(), 1);
		waitLesson(context, TrainingLesson.CRAFT);
		go(context, TrainingLesson.CRAFT);
		server(context, player -> {
			check(player.level().getBlockState(TrainingLesson.CRAFTING_TABLE).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE),
				"the lesson provides a crafting station");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.ORE_BELL));
		});
		waitLesson(context, TrainingLesson.SKILLS);
		go(context, TrainingLesson.SKILLS);
		server(context, player -> {
			check(SaltService.get(player) >= SkillService.MINOR_PRICE, "the archive hands out salt to practise with: " + SaltService.get(player));
			check(has(player, MistinnItems.HUNTER_GUIDE), "and the guide");
			check(SkillService.unlock(player, Stat.STRENGTH.id + "_1") == SkillService.Result.UNLOCKED, "a practice point buys a stat");
		});
		waitLesson(context, TrainingLesson.BELL);
		go(context, TrainingLesson.BELL);
		server(context, player -> useBlock(player, TrainingLesson.BELL.target, ItemStack.EMPTY));
		waitLesson(context, TrainingLesson.FALL);
		context.takeScreenshot("training-04-golden-bell");
		go(context, TrainingLesson.FALL, 0);
		context.waitForScreen(MissionEndScreen.class);
		context.waitTicks(140);
		double[] respawn = context.computeOnClient(mc -> ((MissionEndScreen) mc.gui.screen()).choiceCentre(0));
		click(context, respawn[0], respawn[1]);
		context.waitFor(mc -> mc.player != null && mc.player.isAlive(), 400);
		waitLesson(context, TrainingLesson.RECOVER);
		context.waitTicks(12);
		server(context, player -> {
			DeathMark mark = player.getAttached(MistinnAttachments.DEATH_MARK);
			check(mark != null && mark.salt() >= 20, "the failed exercise made a real residue mark");
			check(player.distanceToSqr(Vec3.atBottomCenterOf(TrainingLesson.BELL.target)) < 12.0, "the bell brought the hunter back");
			player.setGameMode(GameType.ADVENTURE);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 3));
			move(player, mark.pos().pos().south(2));
		});
		BlockPos grave = context.computeOnClient(mc -> mc.player.getAttached(MistinnAttachments.DEATH_MARK).pos().pos());
		check(context.computeOnClient(mc -> grave.equals(TrainingGuide.target(mc, TrainingLesson.RECOVER))),
			"the guide follows the actual gravestone");
		context.waitFor(mc -> mc.level != null && mc.level.getBlockState(grave).is(MistinnBlocks.GRAVESTONE), 200);
		context.getInput().lookAt(grave);
		context.waitFor(mc -> mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(grave), 100);
		context.getInput().pressKey(options -> options.keyAttack);
		context.waitTicks(4);
		check(context.computeOnClient(mc -> mc.player.hasAttached(MistinnAttachments.DEATH_MARK)), "attacking the grave does not take the residue");
		context.getInput().pressKey(options -> options.keyUse);
		waitLesson(context, TrainingLesson.RADIO);
		server(context, player -> {
			check(player.gameMode() == GameType.SURVIVAL, "a saved Adventure mode is restored to Survival during training");
			check(SaltService.get(player) >= 20 && player.level().getBlockState(grave).isAir(), "using the grave returned its salt");
			check(player.getMainHandItem().is(Items.COBBLESTONE) && player.getMainHandItem().getCount() == 3,
				"using the grave did not place the held block");
		});
		go(context, TrainingLesson.RADIO);
		server(context, player -> useBlock(player, TrainingLesson.RADIO.target, new ItemStack(Items.REDSTONE, 4)));
		waitLesson(context, TrainingLesson.LAMPS);
		go(context, TrainingLesson.LAMPS);
		check(context.computeOnClient(mc -> TrainingLesson.LAMP_POSITIONS.contains(TrainingGuide.target(mc, TrainingLesson.LAMPS))),
			"the guide points at a lamp while the lamp lesson is active");
		server(context, player -> {
			for (BlockPos lamp : TrainingLesson.LAMP_POSITIONS) {
				useBlock(player, lamp, ItemStack.EMPTY);
			}
		});
		waitLesson(context, TrainingLesson.KEEPER);
		context.waitTicks(12);
		context.takeScreenshot("training-05-lamp-room");
		defeat(context, TrainingLesson.KEEPER, "mistinn.training_keeper");
		waitLesson(context, TrainingLesson.REPORT);
		go(context, TrainingLesson.REPORT);
		server(context, player -> {
			// as if their part of the station were not loaded: the drop must still bring them up
			player.level().getEntitiesOfClass(StationHand.class, player.getBoundingBox().inflate(256.0),
				h -> h.role() == StaffRole.ROOMMATE || h.role() == StaffRole.COOK).forEach(StationHand::discard);
			useBlock(player, TrainingLesson.REPORT.target, new ItemStack(MistinnItems.RUST_GOD_SALT));
		});
		waitLesson(context, TrainingLesson.BOARD);
		farewell(context);
		board(context);
		context.waitTicks(24);
		context.takeScreenshot("training-06-pod-heart");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof TrainingCompleteScreen), "the destination prompt stays open");
	}

	/**
	 * The station sees 3411 off first: everyone gathers under the crane, Helm calls the drop over the
	 * speakers, and 3411 goes down with the pod.
	 */
	private static void farewell(final ClientGameTestContext context) {
		context.waitFor(mc -> mc.level.getEntitiesOfClass(StationHand.class, new AABB(TrainingLesson.BOARD.target).inflate(24.0),
			h -> h.role() == StaffRole.COOK || h.role() == StaffRole.BELL_KEEPER).size() == 2, 200);
		context.takeScreenshot("training-07-farewell");
		context.waitFor(mc -> !mc.level.getEntitiesOfClass(StationHand.class, new AABB(TrainingLesson.BOARD.target).inflate(8.0),
			h -> h.role() == StaffRole.ROOMMATE).isEmpty(), 200);
		context.waitFor(mc -> TrainingClient.lastBroadcast() == StationBroadcast.FAREWELL, 400);
		context.waitFor(mc -> mc.level.getEntitiesOfClass(StationHand.class, mc.player.getBoundingBox().inflate(256.0),
			h -> h.role() == StaffRole.ROOMMATE).isEmpty(), 200);
	}

	private static void board(final ClientGameTestContext context) {
		go(context, TrainingLesson.BOARD);
		// the seat waits until the one before has gone
		context.waitFor(mc -> TrainingClient.lastBroadcast() == StationBroadcast.YOUR_TURN, 600);
		server(context, player -> {
			BlockPos button = TrainingLesson.BOARD_BUTTON;
			var hit = new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false);
			UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, hit);
			check(player.isPassenger(), "the hunter physically boards the pod");
		});
		context.waitForScreen(TrainingCompleteScreen.class);
	}

	private static void deploy(final ClientGameTestContext context, final WorldMode mode) {
		double[] destination = context.computeOnClient(mc -> {
			AbstractButton choice = mc.gui.screen().children().stream()
				.filter(AbstractButton.class::isInstance)
				.map(AbstractButton.class::cast)
				.toList().get(mode == WorldMode.STORY ? 0 : 1);
			return new double[] {choice.getX() + choice.getWidth() / 2.0, choice.getY() + choice.getHeight() / 2.0};
		});
		click(context, destination[0], destination[1]);
		// three bells, the station falls quiet and dark, then the drop
		context.waitFor(mc -> TrainingClient.dropping(), 100);
		int sounds = context.computeOnClient(mc -> StationAmbience.played());
		context.waitTicks(110);
		check(context.computeOnClient(mc -> StationAmbience.played()) == sounds, "the station falls silent for the drop");
		server(context, player -> check(player.level().getEntitiesOfClass(StationHand.class, player.getBoundingBox().inflate(256.0),
			h -> h.role() != StaffRole.CRANE_OPERATOR).isEmpty(), "by the third bell no one is left but the crane operator"));
		context.waitFor(mc -> mc.player != null && mc.player.level().dimension().equals(Level.OVERWORLD), 1000);
		// the hunter falls over the new world onto the pad, and comes to before anything else
		context.waitFor(mc -> !mc.player.isPassenger() && !TrainingClient.dropping(), 900);
		context.waitTicks(30);
		server(context, player -> {
			var world = player.level().getServer();
			check(!life.shizune.mistinn.training.StationArrival.arriving(player), "the arrival is over");
			check(WorldMode.of(world) == mode, "the chosen destination is now the world's mode");
			check(!player.hasAttached(TrainingService.LESSON) && !player.isPassenger(), "the training progress and seat were cleared");
			check(!player.hasAttached(MistinnAttachments.BELL) && SaltService.get(player) == 0, "course resources stay at the range");
			check(player.gameMode() == GameType.SURVIVAL, "normal play starts in survival");
			check((StoryService.get(player) != null) == (mode == WorldMode.STORY), "only Story starts the prologue");
			check(SkillService.get(player).unlocked().isEmpty(), "what was learned for practice is taken back at the drop");
			check(AchievementService.earned(player) == 0, "achievements earned in training do not count");
		});
		if (mode == WorldMode.STORY) {
			context.waitForScreen(StoryCardScreen.class);
		}
	}

	private static void click(final ClientGameTestContext context, final double x, final double y) {
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		context.getInput().setCursorPos(x * scale, y * scale);
		context.waitTicks(2);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static void waitLesson(final ClientGameTestContext context, final TrainingLesson step) {
		try {
			context.waitFor(mc -> mc.player != null && mc.player.getAttached(TrainingService.LESSON) == step, 400);
		} catch (AssertionError failure) {
			throw new AssertionError("Did not reach the training lesson " + step.id, failure);
		}
	}

	/** The station from the places it is meant to be seen from, HUD hidden, then back to the barracks. */
	private static void overview(final ClientGameTestContext context) {
		double[][] views = {
			// camera x, y, z, then the point it looks at
			{0.5, 66.62, 26.5, -3.5, 68, 8.5},
			{25.5, 73.5, -34.5, 5.5, 81, -34.5},
			{-16, 80, -36, 6, 96, -34},
			{-58.5, 78.62, -88.5, -47.5, 79, -80.5},
			{-38.5, 92.62, -114.5, -26.5, 101.5, -129.5},
			{73.5, 139, 48.5, -4, 93, -44},
			{15.5, 66.62, -12.5, 6, 66, -38},
			{-5.5, 66.62, -6.5, 6, 64, -36},
		};
		// held in the air for the shots: no gravity, so the hunter neither falls nor lands in the sea
		server(context, player -> player.setNoGravity(true));
		context.runOnClient(mc -> mc.gui.hud.toggle());
		try {
			for (int i = 0; i < views.length; i++) {
				double[] v = views[i];
				double dx = v[3] - v[0];
				double dy = v[4] - v[1];
				double dz = v[5] - v[2];
				float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
				float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
				server(context, player -> {
					player.teleportTo(player.level(), v[0], v[1] - player.getEyeHeight(), v[2], Set.of(), yaw, pitch, true);
					player.setDeltaMovement(Vec3.ZERO);
				});
				context.waitTicks(i == 0 ? 60 : 30);
				context.takeScreenshot("training-station-" + (i + 1));
			}
		} finally {
			context.runOnClient(mc -> mc.gui.hud.toggle());
			server(context, player -> {
				player.setNoGravity(false);
				move(player, TrainingGround.ENTRY);
			});
		}
	}

	/**
	 * The station is busy: everyone is at their post, Helm's first announcement has come over the
	 * speakers, the cook calls out to a hunter who comes near, and the dock hands are on the move.
	 */
	private static void crewAtWork(final ClientGameTestContext context) {
		java.util.Set<StaffRole> posted = new java.util.HashSet<>();
		for (TrainingGround.Post post : TrainingGround.POSTS) {
			posted.add(post.role());
		}
		context.waitFor(mc -> mc.level.getEntitiesOfClass(StationHand.class, mc.player.getBoundingBox().inflate(256.0))
			.stream().map(StationHand::role).collect(java.util.stream.Collectors.toSet()).containsAll(posted), 400);
		context.waitFor(mc -> TrainingClient.lastBroadcast() == 0, 600);
		double[] start = new double[3];
		server(context, player -> {
			StationHand hand = player.level().getEntitiesOfClass(StationHand.class, player.getBoundingBox().inflate(256.0),
				h -> h.role() == StaffRole.DOCK_WORKER_1).getFirst();
			start[0] = hand.getX();
			start[1] = hand.getZ();
			StationHand cook = player.level().getEntitiesOfClass(StationHand.class, player.getBoundingBox().inflate(256.0),
				h -> h.role() == StaffRole.COOK).getFirst();
			move(player, cook.blockPosition().relative(cook.getDirection(), 3));
		});
		context.waitFor(mc -> mc.level.getEntitiesOfClass(StationHand.class, mc.player.getBoundingBox().inflate(8.0),
			h -> h.role() == StaffRole.COOK && h.isCustomNameVisible() && h.getCustomName() != null
				&& h.getCustomName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
				&& t.getKey().startsWith(StaffRole.COOK.key() + ".bark.")).size() == 1, 200);
		context.takeScreenshot("training-cook-bark");
		server(context, player -> {
			StationHand cook = player.level().getEntitiesOfClass(StationHand.class, player.getBoundingBox().inflate(8.0),
				h -> h.role() == StaffRole.COOK).getFirst();
			cook.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
			check(has(player, life.shizune.mistinn.registry.MistinnItems.FOODS.get(life.shizune.mistinn.food.StationFood.OFFAL_SOUP)), "the cook hands over the bowl of fish soup the greeting offers");
		});
		context.waitFor(mc -> mc.gui.screen() instanceof StationTalkScreen, 100);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		int heard = context.computeOnClient(mc -> StationAmbience.played());
		context.waitTicks(200);
		check(context.computeOnClient(mc -> StationAmbience.played()) > heard, "by the mess the kitchen is heard at work");
		server(context, player -> {
			StationHand hand = player.level().getEntitiesOfClass(StationHand.class, player.getBoundingBox().inflate(256.0),
				h -> h.role() == StaffRole.DOCK_WORKER_1).getFirst();
			check(Math.hypot(hand.getX() - start[0], hand.getZ() - start[1]) > 3.0, "the dock hand carries crates along the quay");
			move(player, TrainingGround.ENTRY);
		});
	}

	private static void go(final ClientGameTestContext context, final TrainingLesson step) {
		go(context, step, 2);
	}

	private static void go(final ClientGameTestContext context, final TrainingLesson step, final int south) {
		server(context, player -> move(player, step.target.south(south)));
		context.waitTicks(12);
	}

	/** Walks up to a member of the staff, right-clicks them and hears out what they open with. */
	private static void talk(final ClientGameTestContext context, final StaffRole role, final String opening) {
		context.waitFor(mc -> !mc.level.getEntitiesOfClass(StationHand.class, mc.player.getBoundingBox().inflate(64.0), hand -> hand.role() == role).isEmpty(), 200);
		server(context, player -> {
			StationHand hand = player.level().getEntitiesOfClass(StationHand.class, player.getBoundingBox().inflate(64.0), h -> h.role() == role).getFirst();
			move(player, hand.blockPosition().relative(hand.getDirection(), 3));
			hand.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
		});
		context.waitFor(mc -> mc.gui.screen() instanceof StationTalkScreen talk && opening.equals(talk.topic()), 100);
		context.waitTicks(30);
		context.takeScreenshot("training-talk-" + role.id);
		for (int i = 0; i < 16 && context.computeOnClient(mc -> mc.gui.screen() instanceof StationTalkScreen talk && talk.topic() != null); i++) {
			context.runOnClient(mc -> ((StationTalkScreen) mc.gui.screen()).advance());
		}
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	private static boolean sparringWinds(final ServerPlayer player) {
		return player.level().getEntitiesOfClass(Mob.class, new AABB(TrainingLesson.WINDUP.target).inflate(16),
			mob -> mob.entityTags().contains("mistinn.training_sparring")).stream().anyMatch(life.shizune.mistinn.enemy.WindupService::winding);
	}

	/** Keeps the hunter from being worn down by the sparring husk while the test waits for its windup. */
	private static final class KeepWhole {
		static void of(final ServerPlayer player) {
			life.shizune.mistinn.body.BodyService.restoreAll(player);
			player.removeAttached(MistinnAttachments.WOUNDS);
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void rested(final ServerPlayer player) {
		player.setAttached(MistinnAttachments.STAMINA, StaminaService.max(player));
	}

	private static boolean has(final ServerPlayer player, final Item item) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).is(item)) {
				return true;
			}
		}
		GridInventory grid = GridService.get(player);
		for (Area area : Area.values()) {
			for (Placed placed : grid.items(area)) {
				if (placed.stack().is(item)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void move(final ServerPlayer player, final BlockPos pos) {
		player.teleportTo(player.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), 180.0F, 0.0F, true);
	}

	private static void defeat(final ClientGameTestContext context, final TrainingLesson lesson, final String tag) {
		server(context, player -> {
			var level = player.level();
			Mob mob = level.getEntitiesOfClass(Mob.class, new AABB(lesson.target).inflate(12), entity ->
				entity.entityTags().contains(tag)).getFirst();
			mob.hurtServer(level, level.damageSources().playerAttack(player), 200.0F);
			check(!mob.isAlive(), "the training target was defeated");
		});
	}

	private static void holdAndUse(final ClientGameTestContext context, final Item item, final int count) {
		server(context, player -> {
			ItemStack stack = new ItemStack(item, count);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			item.use(player.level(), player, InteractionHand.MAIN_HAND);
		});
	}

	private static void useBlock(final ServerPlayer player, final BlockPos pos, final ItemStack stack) {
		var level = player.level();
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
		var state = level.getBlockState(pos);
		check(!state.isAir(), "the lesson has its interactive block");
		player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
	}

	private static void server(final ClientGameTestContext context, final Consumer<ServerPlayer> action) {
		var server = context.computeOnClient(Minecraft::getSingleplayerServer);
		var done = server.submit(() -> action.accept(server.getPlayerList().getPlayers().getFirst()));
		context.waitFor(mc -> done.isDone(), 400);
		done.join();
	}

	private static void closeWorld(final ClientGameTestContext context) {
		var previous = context.computeOnClient(Minecraft::getSingleplayerServer);
		context.runOnClient(mc -> {
			if (mc.level != null) {
				mc.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
				mc.disconnectWithSavingScreen();
			}
		});
		context.waitFor(mc -> mc.level == null && (previous == null || !previous.getRunningThread().isAlive()), 1200);
		context.setScreen(TitleScreen::new);
	}

	private static void check(final boolean condition, final String description) {
		if (!condition) {
			throw new AssertionError(description);
		}
	}
}
