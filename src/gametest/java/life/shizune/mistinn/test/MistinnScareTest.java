package life.shizune.mistinn.test;

import java.nio.file.Files;
import java.nio.file.Path;
import life.shizune.mistinn.client.ledger.Haunting;
import life.shizune.mistinn.client.ledger.Ledger;
import life.shizune.mistinn.client.ledger.LedgerClient;
import life.shizune.mistinn.client.ledger.SnufferScare;
import life.shizune.mistinn.client.story.SceneScreen;
import life.shizune.mistinn.client.story.StoryCardScreen;
import life.shizune.mistinn.client.story.StoryClient;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.story.StoryScript;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;

/**
 * The one time it looks back. A ledger that has gone wrong (many drops, an ending behind them) changes the cards'
 * pictures and words, and the Snuffer's last words do not end: a silence in which Esc does nothing, the count of those
 * sent down, its head turned to the view, its face, dark, and the lamp room lit and empty. It is remembered, and does
 * not happen again; with scares set off it never happens at all. Each moment is photographed (in photos/scare).
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnScareTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		Path file = FabricLoader.getInstance().getGameDir().resolve("mistinn-scare-ledger.json");
		boolean[] reloaded = {false};
		context.runOnClient(mc -> {
			try {
				Files.deleteIfExists(file);
			} catch (java.io.IOException e) {
				throw new AssertionError(e);
			}
			LedgerClient.use(file);
			// a hunter who has been down many times, and once sent the salt up
			Ledger ledger = LedgerClient.ledger();
			for (int i = 1; i <= 4; i++) {
				ledger.world("00000000-0000-4000-8000-00000000000" + i, "story");
			}
			ledger.event("00000000-0000-4000-8000-000000000001", "ending.anchor", true);
			mc.options.languageCode = "zh_tw";
			mc.getLanguageManager().setSelected("zh_tw");
			mc.reloadResourcePacks().thenRun(() -> reloaded[0] = true);
		});
		context.waitFor(mc -> reloaded[0], 2400);
		check(context.computeOnClient(mc -> LedgerClient.ledger().stage()) == Ledger.Stage.WRONG, "the ledger has gone wrong");
		check(context.computeOnClient(mc -> Haunting.picture("drop").getPath().endsWith("drop_wrong.png")), "the drop card's picture is not the same");
		check(context.computeOnClient(mc -> Haunting.key("story.mistinn.card.drop.4")).endsWith(".again"), "nor its last words: again");
		check(context.computeOnClient(mc -> Haunting.text("story.mistinn.radio.first_call.2").getString()).contains("4"), "the record counts the drops");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 12500");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule spawn_mobs false");
			context.waitTicks(10);
			// the cards, as they look now
			context.runOnClient(mc -> mc.gui.setScreen(new StoryCardScreen(StoryScript.LIGHTHOUSE)));
			context.waitTicks(30);
			shot(context, "scare-0-lighthouse");
			context.runOnClient(mc -> mc.gui.setScreen(null));
			int snuffer = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.setGameMode(GameType.CREATIVE);
				// a stone floor in the open, high enough that the view circles clear of the ground
				BlockPos floor = player.blockPosition().above(14);
				for (BlockPos stone : BlockPos.betweenClosed(floor.offset(-8, -1, -8), floor.offset(8, -1, 12))) {
					level.setBlockAndUpdate(stone, net.minecraft.world.level.block.Blocks.DEEPSLATE_TILES.defaultBlockState());
				}
				player.teleportTo(level, floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5, java.util.Set.of(), 0.0F, 0.0F, true);
				BlockPos at = floor.offset(0, 0, 4);
				Mob keeper = MistinnEntities.SNUFFER.create(level, EntitySpawnReason.COMMAND);
				keeper.setNoAi(true);
				keeper.setPermanentlyInvulnerable(true);
				keeper.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180.0F, 0.0F);
				level.addFreshEntity(keeper);
				return keeper.getId();
			});
			context.waitTicks(20);
			check(world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst().level().getEntity(snuffer) != null), "the Snuffer stands there");

			context.runOnClient(mc -> StoryClient.play(StoryScript.SNUFFER_END, snuffer, ""));
			check(context.computeOnClient(mc -> StoryClient.playing().lines().size()) == StoryScript.sequence(StoryScript.SNUFFER_END).size() + 2,
				"its last words do not end there");
			context.waitFor(mc -> StoryClient.lineIndex() >= 4, 4000);
			context.waitTicks(40);
			shot(context, "scare-1-silence");
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
			context.waitTicks(5);
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof SceneScreen), "in the silence, Esc does nothing");
			context.waitFor(mc -> StoryClient.lineIndex() >= 5, 400);
			context.waitTicks(70);
			shot(context, "scare-2-count");
			context.waitFor(mc -> SnufferScare.phase() == SnufferScare.Phase.STILL, 400);
			context.waitTicks(10);
			shot(context, "scare-3-turned");
			context.waitFor(mc -> SnufferScare.phase() == SnufferScare.Phase.FACE, 200);
			context.waitTicks(2);
			shot(context, "scare-4-face");
			check(context.computeOnClient(mc -> LedgerClient.ledger().seen("scare.snuffer")), "it is remembered the moment it is seen");
			context.waitFor(mc -> SnufferScare.phase() == SnufferScare.Phase.DARK, 100);
			context.waitTicks(10);
			shot(context, "scare-5-dark");
			context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen, 200);
			context.waitTicks(60);
			shot(context, "scare-6-lamp");
			context.runOnClient(mc -> mc.gui.setScreen(null));
			context.waitTicks(5);

			// it does not happen again: the ledger is past it, and everything is as it was
			check(context.computeOnClient(mc -> LedgerClient.ledger().stage()) == Ledger.Stage.AFTER, "after it, the ledger is past the wrong");
			context.runOnClient(mc -> StoryClient.play(StoryScript.SNUFFER_END, snuffer, ""));
			check(context.computeOnClient(mc -> StoryClient.playing().lines().size()) == StoryScript.sequence(StoryScript.SNUFFER_END).size(),
				"the next time, its last words are only its last words");
			check(context.computeOnClient(mc -> Haunting.picture("drop").getPath().endsWith("drop.png")), "and the drop card is as it was");
			check(context.computeOnClient(mc -> Haunting.key("story.mistinn.card.drop.3")).endsWith(".after"), "but for one more notch on the tag");
			context.runOnClient(mc -> StoryClient.skip());
		}
	}

	private static void shot(final ClientGameTestContext context, final String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(Path.of("photos", "scare")));
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn scare] ok: " + what);
	}
}
