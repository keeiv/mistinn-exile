package life.shizune.mistinn.test;

import life.shizune.mistinn.client.epic.EpicOpeningScreen;
import life.shizune.mistinn.client.title.HardshipScreen;
import life.shizune.mistinn.client.title.ModeSelectScreen;
import life.shizune.mistinn.client.title.StorySelectScreen;
import life.shizune.mistinn.epic.EpicService;
import life.shizune.mistinn.epic.EpicState;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.udasru.UdasruBiomes;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.impl.client.gametest.threading.ThreadingImpl;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * The way a player comes to the epic: the world list's "Create New World", the story card, the epic's road, its one
 * hardship, the heart in the dark, and a world that opens as Udasru at the Hunter's hardship with 3412 in it.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnEpicEntryTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		context.runOnClient(mc -> mc.gui.setScreen(new SelectWorldScreen(new TitleScreen())));
		context.waitTicks(20);
		// with no world yet the list asks by itself; with worlds, the player presses "Create New World"
		context.runOnClient(mc -> {
			if (!(mc.gui.screen() instanceof ModeSelectScreen)) {
				CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen()));
			}
		});
		context.waitForScreen(ModeSelectScreen.class);
		context.runOnClient(mc -> ((ModeSelectScreen) mc.gui.screen()).pick(WorldMode.STORY));
		context.waitForScreen(StorySelectScreen.class);
		context.runOnClient(mc -> ((StorySelectScreen) mc.gui.screen()).pick(WorldMode.EPIC));
		context.waitForScreen(HardshipScreen.class);
		context.runOnClient(mc -> ((HardshipScreen) mc.gui.screen()).pick(Hardship.HUNTER));
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof EpicOpeningScreen), "the Hunter chosen, the heart beats in the dark");
		// the opening plays out by itself, then the world is made and opened
		context.waitFor(mc -> mc.level != null && mc.player != null && mc.getSingleplayerServer() != null && ThreadingImpl.isServerRunning, 6000);
		context.waitFor(mc -> mc.level.getAttached(EpicService.STATE) != null, 1200);
		context.waitTicks(40);
		context.runOnClient(mc -> {
			MinecraftServer server = mc.getSingleplayerServer();
			check(WorldMode.of(server) == WorldMode.EPIC, "the world is the epic's: " + WorldMode.of(server));
			check(Hardship.of(server) == Hardship.HUNTER, "at the Hunter's hardship: " + Hardship.of(server));
			check(server.overworld().getChunkSource().getGenerator().getBiomeSource() instanceof UdasruBiomes, "and its land is Udasru");
			check(!server.overworld().getAttachedOrElse(life.shizune.mistinn.world.MistinnWorld.SPAWN_STATION, false), "with no drop station built over the capital");
			EpicState state = mc.level.getAttached(EpicService.STATE);
			check(state != null && state.at() == life.shizune.mistinn.epic.EpicStep.WAKE, "the epic begins at waking: " + state);
			check(!mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(), "3412 wakes dressed");
		});
		// leave the world the way Fabric's own close does, so the next test starts from the title
		context.runOnClient(mc -> {
			mc.level.disconnect(Component.translatable("menu.savingLevel"));
			mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")), false);
		});
		context.waitFor(mc -> !ThreadingImpl.isServerRunning && mc.level == null, 1200);
		context.waitTicks(2);
		context.setScreen(TitleScreen::new);
		WorldMode.choose(null);
		Hardship.choose(null);
	}

	private static void check(final boolean ok, final String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
		life.shizune.mistinn.Mistinn.LOGGER.info("[mistinn epic entry] ok: {}", what);
	}
}
