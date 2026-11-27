package life.shizune.mistinn.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.client.ledger.LedgerClient;
import life.shizune.mistinn.client.path.Thoughts;
import life.shizune.mistinn.path.PathService;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.terra.CrystalElder;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;

/**
 * 念頭. On the tillers' way a walker now and then thinks something that is not quite theirs. A new walker feels their
 * body is not their own; with half the twelve gone they count it; near the crown elder they think they might win, and
 * wonder why. A hunter who has let the settlement fall in other worlds hears the mover plainly and never wonders; one
 * who brought everyone to the sea hears a tiller being careful for the others.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnThoughtsTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		Path file = FabricLoader.getInstance().getGameDir().resolve("mistinn-thoughts-ledger.json");
		boolean[] reloaded = {false};
		context.runOnClient(mc -> {
			try {
				Files.deleteIfExists(file);
			} catch (java.io.IOException e) {
				throw new AssertionError(e);
			}
			LedgerClient.use(file);
			mc.options.languageCode = "zh_tw";
			mc.getLanguageManager().setSelected("zh_tw");
			mc.reloadResourcePacks().thenRun(() -> reloaded[0] = true);
		});
		context.waitFor(mc -> reloaded[0], 2400);

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.setGameMode(GameType.SURVIVAL);
				player.setPermanentlyInvulnerable(true);
				// the crown elder, some way off and asleep
				ServerLevel level = server.overworld();
				CrystalElder elder = MistinnEntities.CRYSTAL_ELDER.create(level, EntitySpawnReason.COMMAND);
				elder.setNoAi(true);
				elder.snapTo(player.getX() + 4.0, player.getY() + 6.0, player.getZ() + 18.0, 180.0F, 0.0F);
				level.addFreshEntity(elder);
			});
			context.waitTicks(10);

			// before any of that, in the zone as No. 3412: their own voice, as sure of themself as the hardship lets them be
			context.waitFor(mc -> LedgerClient.world() != null, 400);
			String mode = context.computeOnClient(mc -> LedgerClient.ledger().mode(LedgerClient.world()));
			context.waitFor(mc -> !life.shizune.mistinn.client.path.InnerVoice.SAID.isEmpty() || !"story".equals(mode) && !"free".equals(mode), 400);
			context.waitTicks(30);
			shotThought(context, "thoughts-voice");
			List<String> voice = context.computeOnClient(mc -> List.copyOf(life.shizune.mistinn.client.path.InnerVoice.SAID));
			System.out.println("[mistinn thoughts] in a " + mode + " world, No. 3412 thought: " + voice);
			if ("story".equals(mode) || "free".equals(mode)) {
				check(voice.equals(List.of("voice.mistinn.landing.normal.0")), "No. 3412 tells themself it is just like training");
				context.waitTicks(200);
				check(context.computeOnClient(mc -> life.shizune.mistinn.client.path.InnerVoice.SAID.size()) == 1, "and only once in a world");
			}

			// as the way finds them: a new walker, half the twelve gone, the elder near
			List<String> plain = walk(context, world, 3, "thoughts-plain");
			check(plain.contains("……身體好像不是自己的。") && plain.contains("大概是太累了吧。"), "a new walker feels their body is not their own: " + plain);
			check(plain.contains("已經走了一半的人了。"), "they count the gone");
			int might = plain.indexOf("我或許能打過。");
			check(might >= 0 && plain.indexOf("……我為什麼會這樣想？") == might + 1, "near the elder they think they might win, and wonder why");

			// one who let the settlement fall in another world: the mover, plainly
			context.runOnClient(mc -> LedgerClient.ledger().event("00000000-0000-4000-8000-000000000001", "path.cut", true));
			List<String> cruel = walk(context, world, 4, "thoughts-cruel");
			check(cruel.contains("換我了。") && cruel.contains("反正還有人。") && cruel.contains("我或許能打過。"), "the mover, plainly: " + cruel);
			check(!cruel.contains("大概是太累了吧。") && !cruel.contains("……我為什麼會這樣想？"), "and never wondering");

			// one who brought everyone to the sea: a tiller careful for the others
			context.runOnClient(mc -> {
				LedgerClient.ledger().event("00000000-0000-4000-8000-000000000002", "path.sea.whole", true);
				LedgerClient.ledger().event("00000000-0000-4000-8000-000000000003", "path.sea.whole", true);
			});
			List<String> kind = walk(context, world, 5, "thoughts-kind");
			check(kind.contains("……我會小心的。為了走在前面的人。") && kind.contains("不會再少任何一個了。") && kind.contains("不要逞強。我們要一起到海邊。"),
				"a tiller careful for the others: " + kind);
		}
	}

	/** The hunter walks as {@code walker} with half the twelve gone, near the elder; what comes into their head, in order. */
	private static List<String> walk(final ClientGameTestContext context, final TestSingleplayerContext world, final int walker, final String photo) {
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setAttached(PathService.VIEW,
			new PathService.View(PathService.Step.ELDER.ordinal(), PathService.PEOPLE / 2, walker, 0, 0, -1)));
		List<String> seen = new ArrayList<>();
		boolean shot = false;
		for (int t = 0; t < 1400; t += 2) {
			context.waitTicks(2);
			String now = context.computeOnClient(mc -> {
				Component line = Thoughts.showing();
				return line == null ? null : line.getString();
			});
			if (now != null && (seen.isEmpty() || !seen.getLast().equals(now))) {
				seen.add(now);
				if (!shot) {
					shot = true;
					context.waitTicks(30);
					context.takeScreenshot(TestScreenshotOptions.of(photo).disableCounterPrefix().withDestinationDir(Path.of("photos", "thoughts")));
				}
			}
			if (seen.size() >= 4 && now == null && t > 900) {
				break;
			}
		}
		System.out.println("[mistinn thoughts] walker " + walker + " thought: " + seen);
		return seen;
	}

	private static void shotThought(final ClientGameTestContext context, final String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(Path.of("photos", "thoughts")));
	}

	/** What comes into the hunter's head over the next {@code ticks}, in order. */
	private static List<String> listen(final ClientGameTestContext context, final int ticks) {
		List<String> heard = new ArrayList<>();
		for (int t = 0; t < ticks; t += 2) {
			context.waitTicks(2);
			String now = context.computeOnClient(mc -> {
				Component line = Thoughts.showing();
				return line == null ? null : line.getString();
			});
			if (now != null && (heard.isEmpty() || !heard.getLast().equals(now))) {
				heard.add(now);
			}
		}
		return heard;
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn thoughts] ok: " + what);
	}
}
