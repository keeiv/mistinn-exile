package life.shizune.mistinn.test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import life.shizune.mistinn.client.ledger.Haunting;
import life.shizune.mistinn.client.path.PathEndScreen;
import life.shizune.mistinn.client.story.StoryCardScreen;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.story.StoryScript;
import life.shizune.mistinn.story.StoryService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The endings' last pages. The inn's goes on into the morning: the soup, the wall of tags, the name asked and carved
 * (the hunter's own), the morning someone calls it up the stairs. The tillers' way, walked with no one lost, ends with
 * all twelve at the water, and its last screen says what they wanted. The anchor's takes the salt they carry, too.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnEpilogueTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		boolean[] reloaded = {false};
		context.runOnClient(mc -> {
			mc.options.languageCode = "zh_tw";
			mc.getLanguageManager().setSelected("zh_tw");
			mc.reloadResourcePacks().thenRun(() -> reloaded[0] = true);
		});
		context.waitFor(mc -> reloaded[0], 2400);

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();

			// the inn's ending goes on, card after card
			context.runOnClient(mc -> mc.gui.setScreen(new StoryCardScreen(StoryScript.INN)));
			List<String> cards = new ArrayList<>();
			for (int i = 0; i < 80; i++) {
				context.waitTicks(30);
				String id = context.computeOnClient(mc -> mc.gui.screen() instanceof StoryCardScreen card ? card.cardId() : null);
				if (id == null) {
					break;
				}
				if (cards.isEmpty() || !cards.getLast().equals(id)) {
					cards.add(id);
					check(context.computeOnClient(mc -> StoryCardScreen.music() != null), "the morning's music plays under " + id);
					context.waitTicks(40);
					shot(context, "epilogue-" + cards.size() + "-" + id);
				}
				context.runOnClient(mc -> ((StoryCardScreen) mc.gui.screen()).advance());
				context.runOnClient(mc -> {
					if (mc.gui.screen() instanceof StoryCardScreen card) {
						card.advance();
					}
				});
			}
			check(cards.equals(List.of("inn", "inn_hearth", "inn_wall", "inn_name", "inn_dawn")), "the inn's ending goes on into the morning: " + cards);
			check(context.computeOnClient(mc -> StoryCardScreen.music() == null), "and once the pages are done, the morning's music stops");
			String name = context.computeOnClient(mc -> mc.getUser().getName());
			check(text(context, () -> Haunting.text("story.mistinn.card.inn_name.2").getString()).contains(name), "the name carved is the hunter's own");
			check(text(context, () -> Haunting.text("story.mistinn.card.inn_dawn.4").getString()).contains(name), "and called up the stairs in the morning");
			check(text(context, () -> Haunting.text("story.mistinn.card.inn_dawn.5").getString()).equals("結局　霧宿"), "the ending's name comes last");

			// the way walked with no one lost: all twelve at the water, then what they wanted
			context.runOnClient(mc -> mc.gui.setScreen(new StoryCardScreen(StoryScript.PATH_SEA, StoryScript.PATH_SEA_ALL)));
			context.waitTicks(20);
			for (int i = 0; i < 12 && !"path_sea_all".equals(text(context, () ->
				mc().gui.screen() instanceof StoryCardScreen card ? card.cardId() : "")); i++) {
				context.runOnClient(mc -> ((StoryCardScreen) mc.gui.screen()).advance());
				context.waitTicks(2);
			}
			check("path_sea_all".equals(text(context, () -> mc().gui.screen() instanceof StoryCardScreen card ? card.cardId() : "")),
				"all twelve at the sea, after the sea");
			context.waitTicks(80);
			shot(context, "epilogue-6-sea-all");
			context.runOnClient(mc -> mc.gui.setScreen(new PathEndScreen(true, List.of(), 3)));
			context.waitTicks(300);
			shot(context, "epilogue-7-sea-end");
			context.runOnClient(mc -> mc.gui.setScreen(new PathEndScreen(true, List.of(0, 1, 2, 5), 6)));
			context.waitTicks(300);
			shot(context, "epilogue-8-sea-end-fallen");
			context.runOnClient(mc -> mc.gui.setScreen(null));

			// the anchor is hungry: the salt they carry goes up with the rest
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.getInventory().setItem(3, new ItemStack(MistinnItems.SALT, 20));
				player.getInventory().setItem(4, new ItemStack(MistinnItems.SALT_BOMB, 3));
				player.getInventory().setItem(5, new ItemStack(MistinnItems.RUST_GOD_SALT, 1));
				int taken = StoryService.confiscate(player);
				check(taken == 24, "every bit of salt is taken: " + taken);
				check(!player.getInventory().hasAnyMatching(stack -> stack.is(MistinnItems.SALT) || stack.is(MistinnItems.SALT_BOMB)),
					"none is left");
			});
			check(StoryScript.sequence(StoryScript.ANCHOR_END).size() == 5, "and the station says so");
		}
	}

	private static net.minecraft.client.Minecraft mc() {
		return net.minecraft.client.Minecraft.getInstance();
	}

	private static String text(final ClientGameTestContext context, final Supplier<String> what) {
		return context.computeOnClient(mc -> what.get());
	}

	private static void shot(final ClientGameTestContext context, final String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(Path.of("photos", "epilogue")));
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn epilogue] ok: " + what);
	}
}
