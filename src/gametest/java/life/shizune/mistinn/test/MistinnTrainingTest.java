package life.shizune.mistinn.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Set;
import java.util.function.Consumer;
import life.shizune.mistinn.client.death.MissionEndScreen;
import life.shizune.mistinn.client.story.StoryCardScreen;
import life.shizune.mistinn.client.title.ModeSelectScreen;
import life.shizune.mistinn.client.training.TrainingClient;
import life.shizune.mistinn.client.training.TrainingCompleteScreen;
import life.shizune.mistinn.client.training.TrainingGuide;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.residue.DeathMark;
import life.shizune.mistinn.salt.SaltService;
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
		context.runOnClient(TrainingClient::createWorld);
		waitForCourse(context);
		try {
			// The full course above proves how the last objective is earned. This world checks
			// the other branch from the same final objective.
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
		waitForCourse(context);
	}

	private static void waitForCourse(final ClientGameTestContext context) {
		context.waitFor(mc -> mc.player != null, 1000);
		context.waitTicks(20);
		waitLesson(context, TrainingLesson.ARRIVAL);
		server(context, player -> {
			check(WorldMode.of(player.level().getServer()) == WorldMode.TRAINING, "the third card created a training save");
			check(player.level().dimension().equals(TrainingGround.KEY), "the owner arrived at the course");
			check(player.gameMode() == GameType.SURVIVAL, "the course starts in survival");
		});
	}

	private static void playCourse(final ClientGameTestContext context) {
		context.waitTicks(40);
		context.takeScreenshot("training-02-arrival");
		overview(context);
		context.runOnClient(mc -> ClientPlayNetworking.send(new TrainingService.DeployPayload(true)));
		context.waitTicks(10);
		waitLesson(context, TrainingLesson.ARRIVAL);

		go(context, TrainingLesson.ARRIVAL);
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
		waitLesson(context, TrainingLesson.SPRINT);
		go(context, TrainingLesson.SPRINT);
		server(context, player -> player.setSprinting(true));
		waitLesson(context, TrainingLesson.ROLL);
		context.waitFor(mc -> mc.player != null && mc.player.onGround(), 200);
		context.getInput().pressKey(options -> java.util.Arrays.stream(options.keyMappings)
			.filter(key -> key.getName().equals("key.mistinn.roll")).findFirst().orElseThrow());
		waitLesson(context, TrainingLesson.STRIKE);
		go(context, TrainingLesson.STRIKE);
		defeat(context, TrainingLesson.STRIKE, "mistinn.training_target");
		waitLesson(context, TrainingLesson.SALT);
		holdAndUse(context, MistinnItems.SALT, 1);
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
		server(context, player -> {
			check(player.level().getBlockState(new BlockPos(4, 65, -50)).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE),
				"the lesson provides a crafting station");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.ORE_BELL));
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
			check(player.distanceToSqr(0.5, 65, -63.5) < 12.0, "the bell brought the hunter back");
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
			for (int x : new int[] {-4, 4}) {
				for (int z : new int[] {-103, -113}) {
					useBlock(player, new BlockPos(x, 65, z), ItemStack.EMPTY);
				}
			}
		});
		waitLesson(context, TrainingLesson.KEEPER);
		context.waitTicks(12);
		context.takeScreenshot("training-05-lamp-room");
		defeat(context, TrainingLesson.KEEPER, "mistinn.training_keeper");
		waitLesson(context, TrainingLesson.REPORT);
		go(context, TrainingLesson.REPORT);
		server(context, player -> useBlock(player, TrainingLesson.REPORT.target, new ItemStack(MistinnItems.RUST_GOD_SALT)));
		waitLesson(context, TrainingLesson.BOARD);
		board(context);
		context.waitTicks(24);
		context.takeScreenshot("training-06-pod-heart");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof TrainingCompleteScreen), "the destination prompt stays open");
	}

	private static void board(final ClientGameTestContext context) {
		go(context, TrainingLesson.BOARD);
		server(context, player -> {
			BlockPos button = new BlockPos(0, 66, -131);
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
		context.waitFor(mc -> mc.player != null && mc.player.level().dimension().equals(Level.OVERWORLD), 1000);
		server(context, player -> {
			var world = player.level().getServer();
			check(WorldMode.of(world) == mode, "the chosen destination is now the world's mode");
			check(!player.hasAttached(TrainingService.LESSON) && !player.isPassenger(), "the training progress and seat were cleared");
			check(!player.hasAttached(MistinnAttachments.BELL) && SaltService.get(player) == 0, "course resources stay at the range");
			check(player.gameMode() == GameType.SURVIVAL, "normal play starts in survival");
			check((StoryService.get(player) != null) == (mode == WorldMode.STORY), "only Story starts the prologue");
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

	private static void overview(final ClientGameTestContext context) {
		server(context, player -> {
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			player.teleportTo(player.level(), 22.5, 88.0, -1.5, Set.of(), 155.0F, 24.0F, true);
		});
		context.runOnClient(mc -> mc.gui.hud.toggle());
		try {
			context.waitTicks(30);
			context.takeScreenshot("training-map-overview");
		} finally {
			context.runOnClient(mc -> mc.gui.hud.toggle());
			server(context, player -> {
				move(player, new BlockPos(0, 65, 10));
				player.getAbilities().mayfly = false;
				player.getAbilities().flying = false;
				player.onUpdateAbilities();
			});
		}
	}

	private static void go(final ClientGameTestContext context, final TrainingLesson step) {
		go(context, step, 2);
	}

	private static void go(final ClientGameTestContext context, final TrainingLesson step, final int south) {
		server(context, player -> move(player, step.target.south(south)));
		context.waitTicks(12);
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
