package life.shizune.mistinn.test;

import java.nio.file.Files;
import java.nio.file.Path;
import life.shizune.mistinn.client.ledger.Haunting;
import life.shizune.mistinn.client.ledger.Ledger;
import life.shizune.mistinn.client.ledger.LedgerClient;
import life.shizune.mistinn.client.ledger.SnufferScare;
import life.shizune.mistinn.client.story.StoryCardScreen;
import life.shizune.mistinn.client.story.StoryClient;
import life.shizune.mistinn.ledger.LedgerService;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.story.StoryScript;
import life.shizune.mistinn.terra.Tiller;
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
 * The ledger answers a hunter who let others live. Their story reads its kind versions and shows its kind pictures;
 * the tillers' way answers how they walked it; when the Snuffer looks back at them it is to thank them, without a
 * fright, Esc still working; and a tiller in a hunter's world knows the one who walked as a tiller, once.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnKindTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		Path file = FabricLoader.getInstance().getGameDir().resolve("mistinn-kind-ledger.json");
		boolean[] reloaded = {false};
		context.runOnClient(mc -> {
			try {
				Files.deleteIfExists(file);
			} catch (java.io.IOException e) {
				throw new AssertionError(e);
			}
			LedgerClient.use(file);
			// a hunter who has been down many times, and burned the salt in the inn
			Ledger ledger = LedgerClient.ledger();
			for (int i = 1; i <= 4; i++) {
				ledger.world("00000000-0000-4000-8000-00000000000" + i, "story");
			}
			ledger.event("00000000-0000-4000-8000-000000000002", "ending.inn", true);
			ledger.event("00000000-0000-4000-8000-000000000003", "path.sea.whole", true);
			mc.options.languageCode = "zh_tw";
			mc.getLanguageManager().setSelected("zh_tw");
			mc.reloadResourcePacks().thenRun(() -> reloaded[0] = true);
		});
		context.waitFor(mc -> reloaded[0], 2400);
		check(context.computeOnClient(mc -> LedgerClient.ledger().kindStory() && LedgerClient.ledger().kindPath()), "the ledger knows a kind hunter");
		check(context.computeOnClient(mc -> Haunting.key("story.mistinn.card.drop.4")).endsWith(".kind"), "their story reads its kind version");
		check(context.computeOnClient(mc -> Haunting.picture("drop").getPath().endsWith("drop_kind.png")), "and shows its kind picture");
		check(context.computeOnClient(mc -> Haunting.picture("path_fields").getPath().endsWith("path_fields_kind.png")), "the tillers' way answers how they walked it");
		check(context.computeOnClient(mc -> LedgerClient.ledger().tone()) == 3, "and the server may hear which way they lean");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 12500");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule spawn_mobs false");
			context.waitTicks(10);
			context.runOnClient(mc -> mc.gui.setScreen(new StoryCardScreen(StoryScript.DROP)));
			context.waitTicks(30);
			shot(context, "kind-0-drop");
			context.runOnClient(mc -> mc.gui.setScreen(null));
			int[] ids = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.setGameMode(GameType.CREATIVE);
				BlockPos floor = player.blockPosition().above(14);
				for (BlockPos stone : BlockPos.betweenClosed(floor.offset(-8, -1, -8), floor.offset(8, -1, 12))) {
					level.setBlockAndUpdate(stone, net.minecraft.world.level.block.Blocks.DEEPSLATE_TILES.defaultBlockState());
				}
				player.teleportTo(level, floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5, java.util.Set.of(), 0.0F, 0.0F, true);
				Mob keeper = MistinnEntities.SNUFFER.create(level, EntitySpawnReason.COMMAND);
				keeper.setNoAi(true);
				keeper.setPermanentlyInvulnerable(true);
				BlockPos at = floor.offset(0, 0, 4);
				keeper.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180.0F, 0.0F);
				level.addFreshEntity(keeper);
				Tiller tiller = MistinnEntities.TILLER.create(level, EntitySpawnReason.COMMAND);
				tiller.setNoAi(true);
				tiller.snapTo(floor.getX() + 3.5, floor.getY(), floor.getZ() - 2.5, 0.0F, 0.0F);
				level.addFreshEntity(tiller);
				return new int[] {keeper.getId(), tiller.getId()};
			});
			context.waitTicks(20);

			// a tiller knows the one who walked as a tiller, once in this world
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				Tiller tiller = (Tiller) server.overworld().getEntity(ids[1]);
				LedgerService.setTone(player, 2);
				check(tiller.line(player).equals("message.mistinn.tiller.known.kind"), "a tiller knows them: " + tiller.line(player));
				check(!tiller.line(player).startsWith("message.mistinn.tiller.known"), "once");
			});

			// nothing goes around the scares setting: with it off, not even the thanks comes
			context.runOnClient(mc -> life.shizune.mistinn.client.ContentLevel.set(life.shizune.mistinn.client.ContentLevel.NORMAL));
			check(context.computeOnClient(mc -> !SnufferScare.due() && SnufferScare.wordsDue()),
				"below full content the Snuffer does not look back, even to thank them; its words alone come");
			context.runOnClient(mc -> life.shizune.mistinn.client.ContentLevel.set(life.shizune.mistinn.client.ContentLevel.FULL));
			check(context.computeOnClient(mc -> SnufferScare.due()), "with scares on it does");

			// the Snuffer looks back to thank them
			context.runOnClient(mc -> StoryClient.play(StoryScript.SNUFFER_END, ids[0], ""));
			check(context.computeOnClient(mc -> StoryClient.playing().lines().size()) == StoryScript.sequence(StoryScript.SNUFFER_END).size() + 3,
				"its last words go on: the silence, the lamp kept, the thanks");
			check(context.computeOnClient(mc -> !SnufferScare.holding(5)), "Esc still works: it is not holding them");
			context.waitFor(mc -> StoryClient.lineIndex() >= 5, 4000);
			context.waitFor(mc -> SnufferScare.phase() == SnufferScare.Phase.STILL, 600);
			context.waitTicks(10);
			shot(context, "kind-1-looks");
			context.waitFor(mc -> StoryClient.lineIndex() >= 6, 200);
			context.waitTicks(16);
			shot(context, "kind-2-thanks");
			check(context.computeOnClient(mc -> SnufferScare.phase()) == SnufferScare.Phase.NONE, "no face, no dark");
			context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen, 400);
			context.waitTicks(80);
			shot(context, "kind-3-lamp");
			check(context.computeOnClient(mc -> LedgerClient.ledger().seen("scare.snuffer")), "it is remembered, as the other is");
			context.runOnClient(mc -> mc.gui.setScreen(null));
		}
	}

	private static void shot(final ClientGameTestContext context, final String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(Path.of("photos", "kind")));
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn kind] ok: " + what);
	}
}
