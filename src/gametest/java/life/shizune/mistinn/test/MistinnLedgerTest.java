package life.shizune.mistinn.test;

import java.nio.file.Files;
import java.nio.file.Path;
import life.shizune.mistinn.client.ledger.Ledger;
import life.shizune.mistinn.client.ledger.LedgerClient;
import life.shizune.mistinn.ledger.LedgerService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 冊子: the game remembers its hunter outside any one world. Every world come into is one drop, however often it is
 * joined; an ending counts once for its world, a fall every time; a tiller's fall is kept by name; it is all still
 * there when the ledger is read again. As the drops and endings pile up, the story goes further wrong.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnLedgerTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		Path file = FabricLoader.getInstance().getGameDir().resolve("mistinn-test-ledger.json");
		context.runOnClient(mc -> {
			try {
				Files.deleteIfExists(file);
			} catch (java.io.IOException e) {
				throw new AssertionError(e);
			}
			LedgerClient.use(file);
		});
		check(ledger(context).drops() == 0 && ledger(context).stage() == Ledger.Stage.FIRST, "a new ledger has nothing in it");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.waitTicks(10);
			check(ledger(context).drops() == 1, "coming into a world is a drop");
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				LedgerService.tell(player, "ending.anchor", true);
				LedgerService.tell(player, "ending.anchor", true);
				LedgerService.tell(player, "death", false);
				LedgerService.tell(player, "death", false);
				LedgerService.tellAll(server, "path.fallen.3", true);
			});
			context.waitTicks(5);
			check(ledger(context).count("ending.anchor") == 1, "an ending counts once in its world");
			check(ledger(context).count("death") == 2, "a fall counts every time");
			check(ledger(context).fallen(3) == 1, "a tiller's fall is kept by name");
			check(ledger(context).sent() == 2, "one drop and the salt sent up once: two sent down");
			check(ledger(context).stage() == Ledger.Stage.RECORD, "the salt sent up, the record begins to show");
			world.getServer().runCommand("kill @a");
			context.waitTicks(10);
			check(ledger(context).count("death") == 3, "the hunter falling is told: " + ledger(context).count("death"));
		}

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.waitTicks(10);
			check(ledger(context).drops() == 2, "another world, another drop");
		}

		Ledger again = Ledger.load(file);
		check(again.drops() == 2 && again.count("death") == 3 && again.count("ending.anchor") == 1 && again.fallen(3) == 1,
			"read again, the ledger has kept it all");
		again.world("00000000-0000-4000-8000-000000000003", "story");
		check(again.stage() == Ledger.Stage.RECORD, "three drops: still the record");
		again.world("00000000-0000-4000-8000-000000000004", "path");
		check(again.drops() == 3, "a tillers' world is no drop of the hunter's");
		again.world("00000000-0000-4000-8000-000000000005", "training");
		check(again.stage() == Ledger.Stage.WRONG, "four drops and an ending behind them: this time it is wrong");
		// what a server cannot make it write down: worlds that are no world, things that never happen, kinds that do not exist
		again.world("not-a-world", "story");
		again.world("00000000-0000-4000-8000-000000000006", "anything");
		again.event("00000000-0000-4000-8000-000000000003", "x".repeat(5000), false);
		again.event("not-a-world", "death", false);
		check(again.drops() == 4 && again.count("death") == 3 && again.count("x".repeat(5000)) == 0, "only what could happen is written down");
		again.see("scare.snuffer");
		check(again.stage() == Ledger.Stage.AFTER && Ledger.load(file).seen("scare.snuffer"), "once seen, it is over, and remembered");
		// and where it was seen is kept with that world, and no other
		again.firstIn("00000000-0000-4000-8000-000000000005", life.shizune.mistinn.client.ledger.Haunting.WITNESSED);
		again.flush();
		Ledger read = Ledger.load(file);
		check(read.happenedIn("00000000-0000-4000-8000-000000000005", life.shizune.mistinn.client.ledger.Haunting.WITNESSED)
			&& !read.happenedIn("00000000-0000-4000-8000-000000000003", life.shizune.mistinn.client.ledger.Haunting.WITNESSED),
			"the world where it was seen is remembered, and only that one");
	}

	private static Ledger ledger(final ClientGameTestContext context) {
		return context.computeOnClient(mc -> LedgerClient.ledger());
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn ledger] ok: " + what);
	}
}
