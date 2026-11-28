package life.shizune.mistinn.test;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.client.worldmap.ExploredMapScreen;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.terra.Settlements;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The hunter's map the day after: a homestead woken and a highland come upon are still on it once the world has been
 * closed and opened again, and not only after the next settlement is built.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnMapAfterReloadTest implements FabricClientGameTest {
	private static final BlockPos CENTRE = new BlockPos(6000, 0, 6000);
	private static final BlockPos HUB = new BlockPos(6130, 81, 6000);

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		TestWorldSave save;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			save = world.getWorldSave();
			world.getConnection().waitForChunksRender();
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				// every highland already grown out, so nothing more is written about them after the world opens again
				for (Domain domain : DomainService.all(server)) {
					DomainService.update(server, domain.spread(domain.radius() + DomainService.EDGE));
				}
				Domain far = DomainService.add(server, Domain.Kind.TERRA, CENTRE, 260, Domain.Phase.SETTLED, 260 + DomainService.EDGE);
				Settlements.Ledger ledger = Settlements.ledger(server);
				List<Settlements.Settlement> built = new ArrayList<>(ledger.built());
				built.add(new Settlements.Settlement(far.id(), HUB.below(), HUB.getY() - 1, HUB, true, level.getGameTime()));
				level.setAttached(Settlements.LEDGER, new Settlements.Ledger(built, ledger.skipped()));
				level.setAttached(Settlements.VIEW, List.copyOf(built));
			});
			context.waitTicks(20);
			context.runOnClient(mc -> check(home(mc.level.getAttached(Settlements.VIEW)), "before closing, the client knows the homestead"));
		}

		try (TestSingleplayerContext world = save.open()) {
			world.getConnection().waitForChunksRender();
			context.waitTicks(20);
			context.runOnClient(mc -> {
				check(home(mc.level.getAttached(Settlements.VIEW)), "opened again, the homestead is still known: " + mc.level.getAttached(Settlements.VIEW));
				List<Domain> domains = mc.level.getAttached(DomainService.VIEW);
				check(domains != null && domains.stream().anyMatch(d -> d.centre().equals(CENTRE)), "and the highland with it: " + domains);
				ExploredMapScreen map = new ExploredMapScreen();
				mc.gui.setScreen(map);
				double[] at = map.toScreen(HUB.getX() + 0.5, HUB.getZ() + 0.5);
				Settlements.Settlement under = map.homeUnder(at[0], at[1]);
				check(under != null && under.lit() && under.hub().equals(HUB), "the map draws the homestead where it stands, and a click there sets off");
				mc.gui.setScreen(null);
			});
		}
	}

	private static boolean home(final List<Settlements.Settlement> view) {
		return view != null && view.stream().anyMatch(s -> s.hub().equals(HUB) && s.lit());
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn map after reload] ok: " + what);
	}
}
