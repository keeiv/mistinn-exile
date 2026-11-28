package life.shizune.mistinn.client.training;

import java.io.IOException;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.training.TrainingGround;
import life.shizune.mistinn.training.TrainingLesson;
import life.shizune.mistinn.training.TrainingService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FileUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.jspecify.annotations.Nullable;

/** Creates the training save and shows the instructor's objectives and final dispatch. */
public final class TrainingClient {
	private static final int GOLD = 0xFFE0AF63;
	private static final int PAPER = 0xFFECE3D2;
	private static final int ASH = 0xFFB8B0A0;
	private static @Nullable TrainingLesson last;
	private static int radioTicks;
	/** Helm's announcement on screen: which line, how long it has left, and the last one heard in this save. */
	private static final int BROADCAST_TICKS = 160;
	private static int broadcastLine;
	private static int broadcastTicks;
	private static int lastBroadcast = -1;

	private TrainingClient() {
	}

	public static void createWorld(final Minecraft mc, final Hardship hardship) {
		String title = Component.translatable("mode.mistinn.training").getString();
		try {
			String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), title, "");
			var packs = new DataPackConfig(List.of("vanilla", TrainingGround.PACK_ID), List.of());
			var config = new WorldDataConfiguration(packs, WorldDataConfiguration.DEFAULT.enabledFeatures());
			Difficulty difficulty = switch (hardship) {
				case EASY -> Difficulty.EASY;
				case NORMAL -> Difficulty.NORMAL;
				case RUST_ANCHOR -> Difficulty.HARD;
				case HUNTER -> Difficulty.NORMAL;
			};
			var settings = new LevelSettings(title, GameType.SURVIVAL,
				new LevelSettings.DifficultySettings(difficulty, false, false), false, config);
			WorldMode.choose(null);
			// read by the new world's server when it first starts
			Hardship.choose(hardship);
			mc.createWorldOpenFlows().createFreshLevel(folder, settings, WorldOptions.defaultWithRandomSeed(),
				WorldPresets::createNormalWorldDimensions, mc.gui.screen());
		} catch (IOException error) {
			Mistinn.LOGGER.warn("Could not create the hunter training save", error);
			SystemToast.onWorldAccessFailure(mc, title);
		}
	}

	static @Nullable TrainingLesson current(final Minecraft mc) {
		return mc.player == null || !mc.player.level().dimension().equals(TrainingGround.KEY)
			? null : mc.player.getAttached(TrainingService.LESSON);
	}

	/** Helm speaks over the station's speakers: a chime, then the line typed out at the top of the screen. */
	public static void broadcast(final int line) {
		broadcastLine = line;
		broadcastTicks = BROADCAST_TICKS;
		lastBroadcast = line;
		Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
			net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 0.75F, 0.6F));
	}

	/** The drop has begun: the choice closes and the station goes dark over the three bells. */
	public static void drop(final int release) {
		Minecraft.getInstance().gui.setScreen(null);
		DropCinematic.station(release);
	}

	/** Whether the drop is on screen. */
	public static boolean dropping() {
		return DropCinematic.active();
	}

	/** The last announcement heard at this station, or -1. */
	public static int lastBroadcast() {
		return lastBroadcast;
	}

	public static void tick(final Minecraft mc) {
		TrainingLesson current = current(mc);
		DropCinematic.tick(mc);
		StationAmbience.tick(mc, current, dropping());
		if (current == null) {
			lastBroadcast = -1;
			broadcastTicks = 0;
		} else if (broadcastTicks > 0) {
			broadcastTicks--;
		}
		if (current != last) {
			last = current;
			radioTicks = current != null && current.radio ? 120 : 0;
		} else if (radioTicks > 0 && mc.gui.screen() == null) {
			radioTicks--;
		}
		if (mc.gui.screen() instanceof TrainingCompleteScreen && current != TrainingLesson.DEPLOY) {
			mc.gui.setScreen(null);
		}
		if (current == TrainingLesson.DEPLOY && mc.player != null && mc.player.isPassenger() && mc.gui.screen() == null && !dropping()) {
			mc.gui.setScreen(new TrainingCompleteScreen(mode ->
				ClientPlayNetworking.send(new TrainingService.DeployPayload(mode == WorldMode.STORY))));
		}
	}

	public static void draw(final GuiGraphicsExtractor g, final DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (dropping()) {
			// the drop is drawn by its own screen; between its screens (a world loading) it is drawn here
			if (!(mc.gui.screen() instanceof DropScreen)) {
				DropCinematic.draw(g, delta.getGameTimeDeltaPartialTick(false));
			}
			return;
		}
		TrainingLesson lesson = current(mc);
		if (lesson == null || mc.player == null || mc.gui.screen() != null || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		int distance = (int) Math.round(Math.hypot(mc.player.getX() - lesson.target.getX() - 0.5,
			mc.player.getZ() - lesson.target.getZ() - 0.5));
		Component heading = Component.translatable("mode.mistinn.training");
		Component station = Component.translatable("training.mistinn.station", Component.translatable(lesson.key()), distance);
		Component hint = Component.translatable(lesson.key() + ".hint",
			Component.keybind("key.mistinn.roll"), Component.keybind("key.sprint"), Component.keybind("key.inventory"), Component.keybind("key.use"));
		int width = Math.min(220, g.guiWidth() / 2 - 12);
		var rows = mc.font.split(hint, width - 14);
		g.fill(6, 6, 6 + width, 35 + rows.size() * 10, 0xAA080A0E);
		g.fill(6, 6, 8, 35 + rows.size() * 10, GOLD);
		g.text(mc.font, heading, 13, 11, GOLD, true);
		g.text(mc.font, station, 13, 22, PAPER, true);
		int y = 33;
		for (var row : rows) {
			g.text(mc.font, row, 13, y, ASH, true);
			y += 10;
		}
		if (radioTicks > 0) {
			drawRadio(g, mc, lesson);
		}
		if (broadcastTicks > 0) {
			drawBroadcast(g, mc);
		}
		TrainingGuide.drawDirection(g, mc, lesson);
	}

	private static void drawBroadcast(final GuiGraphicsExtractor g, final Minecraft mc) {
		String text = net.minecraft.locale.Language.getInstance().getOrDefault(life.shizune.mistinn.station.StationBroadcast.key(broadcastLine));
		int shown = Math.min(text.length(), (int) ((BROADCAST_TICKS - broadcastTicks) * 1.5F));
		int width = Math.min(g.guiWidth() - 32, 300);
		var rows = mc.font.split(Component.literal(text.substring(0, shown)), width - 14);
		int left = (g.guiWidth() - width) / 2;
		int top = 8;
		g.fill(left, top, left + width, top + 19 + Math.max(1, rows.size()) * 11, 0xC00A0C10);
		g.fill(left, top, left + width, top + 1, GOLD);
		g.text(mc.font, Component.translatable("training.mistinn.broadcast"), left + 7, top + 5, GOLD, true);
		int y = top + 17;
		for (var row : rows) {
			g.text(mc.font, row, left + 7, y, PAPER, true);
			y += 11;
		}
	}

	private static void drawRadio(final GuiGraphicsExtractor g, final Minecraft mc, final TrainingLesson lesson) {
		Component line = Component.translatable(lesson.key() + ".radio");
		int width = Math.min(g.guiWidth() - 32, 350);
		var rows = mc.font.split(line, width - 14);
		int left = (g.guiWidth() - width) / 2;
		int top = g.guiHeight() - 99 - rows.size() * 11;
		g.fill(left, top, left + width, g.guiHeight() - 80, 0xD00A0C10);
		g.text(mc.font, Component.translatable("training.mistinn.instructor"), left + 7, top + 5, GOLD, true);
		int y = top + 17;
		for (var row : rows) {
			g.text(mc.font, row, left + 7, y, PAPER, true);
			y += 11;
		}
	}
}
