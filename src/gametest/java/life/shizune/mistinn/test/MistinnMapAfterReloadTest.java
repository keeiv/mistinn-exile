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
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * The hunter's map the day after: a homestead woken and a highland come upon are still on it once the world has been
 * closed and opened again, and not only after the next settlement is built; and after a journey to another world (an
 * expedition, the nether) and back, or a death there and the walk home from the overworld's spawn.
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

			// to the nether and back: the client builds a new world each way, and the map must still know the homestead
			travel(context, world, Level.NETHER);
			travel(context, world, Level.OVERWORLD);
			known(context, "back from the nether");

			// dying in the nether and waking in the overworld
			travel(context, world, Level.NETHER);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.kill(player.level());
			});
			context.waitTicks(60);
			context.runOnClient(mc -> mc.player.respawn());
			context.waitFor(mc -> mc.player != null && mc.player.isAlive() && mc.level != null && mc.level.dimension() == Level.OVERWORLD, 400);
			context.waitTicks(20);
			known(context, "after dying in the nether and waking in the overworld");
		}
	}

	/** Sends the hunter to a world, onto a stone floor, and waits until their client is there. */
	private static void travel(final ClientGameTestContext context, final TestSingleplayerContext world, final ResourceKey<Level> to) {
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.getLevel(to);
			BlockPos at = to == Level.OVERWORLD ? level.getRespawnData().pos() : new BlockPos(0, 100, 0);
			if (to != Level.OVERWORLD) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						level.setBlockAndUpdate(at.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
						level.setBlockAndUpdate(at.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
						level.setBlockAndUpdate(at.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
					}
				}
			}
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		});
		context.waitFor(mc -> mc.level != null && mc.level.dimension() == to && mc.player != null, 400);
		context.waitTicks(20);
	}

	/** Whether the overworld's client still knows the homestead and the highland. */
	private static void known(final ClientGameTestContext context, final String when) {
		context.runOnClient(mc -> {
			check(home(mc.level.getAttached(Settlements.VIEW)), when + ", the homestead is still known: " + mc.level.getAttached(Settlements.VIEW));
			List<Domain> domains = mc.level.getAttached(DomainService.VIEW);
			check(domains != null && domains.stream().anyMatch(d -> d.centre().equals(CENTRE)), when + ", and the highland with it");
		});
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
