package life.shizune.mistinn.test;

import java.util.function.Consumer;
import java.util.function.Predicate;
import com.mojang.blaze3d.platform.InputConstants;
import life.shizune.mistinn.client.training.DropCinematic;
import life.shizune.mistinn.client.training.DropScreen;
import life.shizune.mistinn.client.training.TrainingClient;
import life.shizune.mistinn.client.training.TrainingCompleteScreen;
import life.shizune.mistinn.client.story.StoryCardScreen;
import life.shizune.mistinn.station.StationBroadcast;
import life.shizune.mistinn.story.StoryService;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.training.StationArrival;
import life.shizune.mistinn.training.StationCeremony;
import life.shizune.mistinn.training.TrainingGround;
import life.shizune.mistinn.training.TrainingLesson;
import life.shizune.mistinn.training.TrainingService;
import life.shizune.mistinn.world.Builder;
import life.shizune.mistinn.world.MistinnWorld;
import life.shizune.mistinn.world.PodPlate;
import life.shizune.mistinn.world.Structures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The drop, played through: the seat falls from the crane into the sea and goes under, and the
 * hunter comes out of the dark falling through the sky of the new world, looking up at the clouds
 * going away, then down at the station they land beside. The pod at that station is never a real
 * one, and whichever it is, broken or a copy, it carries the hunter's own number.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnDropTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		context.runOnClient(mc -> TrainingClient.createWorld(mc, life.shizune.mistinn.hardship.Hardship.NORMAL));
		context.waitFor(mc -> mc.player != null && mc.player.level().dimension().equals(TrainingGround.KEY), 2000);
		context.waitTicks(40);
		try {
			plates(context);
			board(context);
			// the crane's pod carries the hunter's number once 3411 has gone
			server(context, player -> {
				SignText plate = sign(player.level(), StationCeremony.PLATE);
				check(plate != null && "3412".equals(number(plate)), "the pod at the crane is numbered 3412 once 3411 has gone");
				check(PodPlate.STATION_KEY.equals(station(plate)), "and it is the station's own plate");
			});
			choose(context, WorldMode.STORY);
			fall(context);
			arrive(context);
		} finally {
			closeWorld(context);
		}
	}

	/** Both kinds of pod a spawn can have, over several seeds: each carries 3412, the copy misspelled. */
	private static void plates(final ClientGameTestContext context) {
		server(context, player -> {
			ServerLevel level = player.level();
			int i = 0;
			for (Structures.Station kind : new Structures.Station[] {Structures.Station.BROKEN, Structures.Station.COUNTERFEIT}) {
				for (long seed = 1; seed <= 4; seed++) {
					BlockPos floor = new BlockPos(160 + 16 * i++, 70, 160);
					Builder builder = new Builder(level, floor, RandomSource.create(seed));
					BlockPos stand = floor.offset(Structures.station(builder, kind));
					Structures.plate(builder, kind);
					BlockPos at = MistinnWorld.plate(stand);
					SignText plate = sign(level, at);
					check(plate != null, kind + " pod (seed " + seed + ") has a plate by its hatch");
					check(level.getBlockState(at).getValue(WallSignBlock.FACING) == Direction.NORTH, "facing the pad");
					check("3412".equals(number(plate)), kind + " pod plate reads 3412, not " + number(plate));
					String expected = kind == Structures.Station.COUNTERFEIT ? PodPlate.COPY_KEY : PodPlate.STATION_KEY;
					check(expected.equals(station(plate)), kind + " pod plate names the station as " + expected + ", not " + station(plate));
					check(((SignBlockEntity) level.getBlockEntity(at)).isWaxed(), "the plate cannot be rewritten");
					// a world from before the plates: it is put up when the hunter comes down
					level.removeBlock(at, false);
					MistinnWorld.ensurePlate(level, stand);
					SignText again = sign(level, at);
					check(again != null && "3412".equals(number(again)) && expected.equals(station(again)),
						kind + " pod gets the same plate in an older world");
				}
			}
		});
	}

	private static void board(final ClientGameTestContext context) {
		server(context, player -> {
			player.setAttached(TrainingService.LESSON, TrainingLesson.BOARD);
			BlockPos to = TrainingLesson.BOARD.target.south(2);
			player.teleportTo(player.level(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, java.util.Set.of(), 180.0F, 0.0F, true);
		});
		context.waitFor(mc -> TrainingClient.lastBroadcast() == StationBroadcast.YOUR_TURN, 700);
		server(context, player -> {
			BlockPos button = TrainingLesson.BOARD_BUTTON;
			var hit = new BlockHitResult(Vec3.atCenterOf(button), Direction.SOUTH, button, false);
			UseBlockCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, hit);
			check(player.isPassenger(), "the hunter boards the pod");
		});
		context.waitForScreen(TrainingCompleteScreen.class);
	}

	private static void choose(final ClientGameTestContext context, final WorldMode mode) {
		double[] at = context.computeOnClient(mc -> {
			AbstractButton choice = mc.gui.screen().children().stream()
				.filter(AbstractButton.class::isInstance)
				.map(AbstractButton.class::cast)
				.toList().get(mode == WorldMode.STORY ? 0 : 1);
			return new double[] {choice.getX() + choice.getWidth() / 2.0, choice.getY() + choice.getHeight() / 2.0};
		});
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		context.getInput().setCursorPos(at[0] * scale, at[1] * scale);
		context.waitTicks(2);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	/** The bells, the dark, the seat let go, the sea, and under it. */
	private static void fall(final ClientGameTestContext context) {
		context.waitFor(mc -> TrainingClient.dropping(), 100);
		context.waitFor(mc -> mc.gui.screen() instanceof DropScreen && mc.gui.hud.isHidden(), 40);
		double seated = context.computeOnClient(mc -> mc.player.getY());
		// the seat is let go after the third bell, and falls
		context.waitFor(mc -> mc.player.level().dimension().equals(TrainingGround.KEY) && mc.player.getY() < seated - 8.0, 400);
		context.waitFor(mc -> mc.gameRenderer.mainCamera().xRot() > 35.0F, 40);
		context.takeScreenshot("drop-01-falling");
		// into the sea, still in the seat, and under
		context.waitFor(mc -> mc.player.level().dimension().equals(TrainingGround.KEY) && mc.player.isUnderWater(), 200);
		server(context, player -> {
			check(player.level().dimension().equals(TrainingGround.KEY), "the hunter is still at the station when the sea closes over");
			check(player.getVehicle() instanceof ArmorStand, "still in the seat under water");
			check(player.getVehicle().getY() < 62.0, "the seat has gone under the waterline, at " + player.getVehicle().getY());
			check(StationCeremony.dropping(player), "and the drop is still going");
		});
		context.waitTicks(12);
		context.takeScreenshot("drop-02-under");
		// the seat goes down but never into the sea floor
		until(context, player -> !player.level().dimension().equals(TrainingGround.KEY) || player.getEyeY() < 57.0, 200, "the hunter sinks");
		server(context, player -> check(!player.level().dimension().equals(TrainingGround.KEY) || player.getEyeY() > 52.5,
			"the hunter's head stays above the sea floor"));
	}

	/** Out of the dark, high over the new world: up at the sky, then down at the station, then the ground. */
	private static void arrive(final ClientGameTestContext context) {
		context.waitFor(mc -> mc.player != null && mc.player.level().dimension().equals(Level.OVERWORLD), 600);
		BlockPos stand = context.computeOnClient(mc -> mc.getSingleplayerServer().overworld().getRespawnData().pos());
		server(context, player -> {
			check(player.isPassenger() && player.getVehicle().entityTags().contains(StationArrival.SEAT_TAG), "the hunter arrives on the fall's seat");
			check(player.getY() > stand.getY() + 60.0, "high above the spawn, at " + player.getY());
			check(Math.abs(player.getX() - stand.getX() - 0.5) < 1.0 && Math.abs(player.getZ() - stand.getZ() - 0.5) < 1.0, "right over the pad");
			check(StationArrival.arriving(player), "the arrival is under way");
		});
		context.waitFor(mc -> mc.gui.screen() instanceof DropScreen && mc.gui.hud.isHidden(), 200);
		// looking up while falling: the sky goes away from the hunter
		context.waitFor(mc -> DropCinematic.shown() > 0.99F && mc.gameRenderer.mainCamera().xRot() < -50.0F, 400);
		context.waitTicks(20);
		context.takeScreenshot("drop-03-sky");
		report(context, "drop-03-sky", stand);
		// then turned over, looking down at the station coming up
		context.waitFor(mc -> mc.gameRenderer.mainCamera().xRot() > 50.0F && mc.player.isPassenger(), 300);
		context.takeScreenshot("drop-04-station");
		report(context, "drop-04-station", stand);
		context.waitFor(mc -> !mc.player.isPassenger() || mc.player.getY() < stand.getY() + 40.0, 300);
		context.takeScreenshot("drop-05-pod");
		int drawn = report(context, "drop-05-pod", stand);
		check(drawn > 40, "looking down from high up, the ground is drawn: " + drawn + " sections");
		context.waitFor(mc -> !mc.player.isPassenger() || mc.player.getY() < stand.getY() + 16.0, 300);
		context.takeScreenshot("drop-06-pod-close");
		report(context, "drop-06-pod-close", stand);
		context.waitFor(mc -> !mc.player.isPassenger(), 200);
		server(context, player -> {
			check(player.distanceToSqr(Vec3.atBottomCenterOf(stand)) < 1.0, "the hunter comes down on the pad");
			check(player.getHealth() >= player.getMaxHealth(), "unhurt by the fall");
			check(player.level().getEntitiesOfClass(ArmorStand.class, new AABB(stand).inflate(256.0, 400.0, 256.0),
				seat -> seat.entityTags().contains(StationArrival.SEAT_TAG)).isEmpty(), "the fall's seat is gone");
			SignText plate = sign(player.level(), MistinnWorld.plate(stand));
			check(plate != null && "3412".equals(number(plate)), "the pod beside the pad carries the hunter's number");
			check(StoryService.get(player) == null, "the story waits until the hunter has come to");
		});
		context.waitFor(mc -> !DropCinematic.active() && !(mc.gui.screen() instanceof DropScreen), 200);
		context.takeScreenshot("drop-07-landed");
		context.waitForScreen(StoryCardScreen.class);
		server(context, player -> {
			check(StoryService.get(player) != null, "the story begins once the hunter is on the ground");
			check(!StationArrival.arriving(player), "the arrival is over");
		});
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(4);
		context.runOnClient(mc -> check(!mc.gui.hud.isHidden(), "the HUD is back after the drop"));
	}

	/** What the view had in it at a shot: how high over the pad, which way, and how much of the world was built. */
	private static int report(final ClientGameTestContext context, final String shot, final BlockPos stand) {
		String line = context.computeOnClient(mc -> String.format("%s: %.1f over the pad, pitch %.1f, %d sections visible, %d chunks loaded",
			shot, mc.player.getY() - stand.getY(), mc.gameRenderer.mainCamera().xRot(),
			mc.levelRenderer.visibleSections().size(), mc.level.getChunkSource().getLoadedChunksCount()));
		System.out.println("DROP VIEW " + line);
		return context.computeOnClient(mc -> mc.levelRenderer.visibleSections().size());
	}

	private static SignText sign(final ServerLevel level, final BlockPos pos) {
		return level.getBlockEntity(pos) instanceof SignBlockEntity sign ? sign.getText(SignTextSlot.FRONT) : null;
	}

	/** The number on a plate, from its "No. %s" line, or null. */
	private static String number(final SignText text) {
		for (Component line : text.getMessages(false)) {
			if (line.getContents() instanceof TranslatableContents t && t.getKey().equals("sign.mistinn.pod.number") && t.getArgs().length == 1) {
				return String.valueOf(t.getArgs()[0]);
			}
		}
		return null;
	}

	/** The key of the plate's station line, or null. */
	private static String station(final SignText text) {
		return text.getMessages(false).stream()
			.map(Component::getContents)
			.filter(TranslatableContents.class::isInstance)
			.map(c -> ((TranslatableContents) c).getKey())
			.filter(key -> key.startsWith("sign.mistinn.pod.station"))
			.findFirst().orElse(null);
	}

	private static void until(final ClientGameTestContext context, final Predicate<ServerPlayer> condition, final int ticks, final String what) {
		for (int i = 0; i < ticks; i++) {
			boolean[] met = {false};
			server(context, player -> met[0] = condition.test(player));
			if (met[0]) {
				return;
			}
			context.waitTicks(1);
		}
		throw new AssertionError("timed out waiting until " + what);
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
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
		// saving a world of generated land can take a while on a busy machine
		context.waitFor(mc -> mc.level == null && (previous == null || !previous.getRunningThread().isAlive()), 2400);
		context.setScreen(TitleScreen::new);
	}
}
