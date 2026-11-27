package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.Squall;
import life.shizune.mistinn.terra.TerraService;
import life.shizune.mistinn.terra.Tiller;
import life.shizune.mistinn.terra.TillerRoutine;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 作息: a settlement keeps the day. By day the tillers work the crystal field, the warden walks
 * the deck and the keeper of trade keeps by the hub; at dusk they gather round the hub; at night
 * the tillers and the keeper go in to the workshop while the warden watches at the hub; and in a
 * crystal squall every one of them takes cover in the workshop.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnTillerRoutineTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		check(TillerRoutine.phase(1000L, false, false) == TillerRoutine.Phase.WORK, "morning is for work");
		check(TillerRoutine.phase(12000L, false, false) == TillerRoutine.Phase.GATHER, "dusk is for gathering");
		check(TillerRoutine.phase(18000L, false, false) == TillerRoutine.Phase.REST, "night is for rest");
		check(TillerRoutine.phase(23500L, false, false) == TillerRoutine.Phase.WORK, "and before sunrise they are back at work");
		check(TillerRoutine.phase(6000L + 24000L * 5, false, false) == TillerRoutine.Phase.WORK, "every day the same");
		check(TillerRoutine.phase(6000L, true, false) == TillerRoutine.Phase.SHELTER, "a squall sends them in");
		check(TillerRoutine.phase(18000L, false, true) == TillerRoutine.Phase.SHELTER, "so does a crystal tide");
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 1000");
			context.waitTicks(20);
			int[] id = new int[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player(server).blockPosition().offset(30, 0, 0));
				id[0] = TerraService.settleAt(level, centre, 60).id();
			});
			Settlements.Settlement[] built = new Settlements.Settlement[1];
			for (int i = 0; i < 60 && built[0] == null; i++) {
				context.waitTicks(20);
				built[0] = world.getServer().computeOnServer(server -> Settlements.of(server, id[0]));
			}
			check(built[0] != null, "the settlement is built");
			Settlements.Settlement home = built[0];
			BlockPos hub = home.hub();
			world.getServer().runOnServer(server -> player(server).teleportTo(server.overworld(), hub.getX() + 0.5, home.deck() + 6.0, hub.getZ() + 14.5,
				Set.of(), 180.0F, 30.0F, true));
			BlockPos workshop = TillerRoutine.workshop(home);
			BlockPos field = TillerRoutine.field(home);

			// by day: the tillers at the field, the keeper of trade by the hub, each at their own place
			until(context, world, people -> people.stream().allMatch(t -> near(t, TillerRoutine.spot(t), 2.5)), 50, "by day, everyone at their work");
			world.getServer().runOnServer(server -> {
				for (Tiller tiller : people(server, hub)) {
					BlockPos spot = TillerRoutine.spot(tiller);
					switch (tiller.role()) {
						case TILLER_1, TILLER_2 -> check(flat(spot, field) <= 4.5, tiller.role() + " works beside the field: " + flat(spot, field));
						case ELDER -> check(flat(spot, hub) <= 3.5, "the keeper of trade keeps by the hub");
						case WARDEN -> check(flat(spot, hub) >= 6.0 && flat(spot, hub) <= Settlements.DECK, "the warden walks the deck: " + flat(spot, hub));
					}
				}
			});

			// at dusk: round the hub
			world.getServer().runCommand("time set 12000");
			until(context, world, people -> people.stream().allMatch(t -> flat(t.blockPosition(), hub) <= 5.0), 50, "at dusk, all gathered round the hub");

			// at night: the tillers and the keeper in the workshop, the warden watching at the hub
			world.getServer().runCommand("time set 18000");
			until(context, world, people -> people.stream().allMatch(t -> t.role() == Tiller.Role.WARDEN
				? flat(t.blockPosition(), hub) <= 4.0 : inside(t, workshop, home)), 60, "at night, in the workshop, and the warden at the hub");

			// a squall by day: every one of them takes cover, the warden too
			world.getServer().runCommand("time set 6000");
			world.getServer().runOnServer(server -> Squall.gather(server.overworld(), life.shizune.mistinn.domain.DomainService.at(server, hub.getX(), hub.getZ()), 600));
			until(context, world, people -> people.stream().allMatch(t -> inside(t, workshop, home)), 60, "in a squall, all of them in the workshop");
			world.getServer().runOnServer(server -> Squall.stop(server.overworld(), life.shizune.mistinn.domain.DomainService.at(server, hub.getX(), hub.getZ())));
			until(context, world, people -> people.stream().filter(t -> t.role() == Tiller.Role.TILLER_1 || t.role() == Tiller.Role.TILLER_2)
				.allMatch(t -> near(t, TillerRoutine.spot(t), 2.5)), 60, "and when it passes, back to the field");
		}
	}

	private static boolean inside(final Tiller tiller, final BlockPos workshop, final Settlements.Settlement home) {
		return flat(tiller.blockPosition(), workshop) <= 1.6 && tiller.blockPosition().getY() == home.deck() + 1;
	}

	private static boolean near(final Tiller tiller, final BlockPos spot, final double reach) {
		return spot != null && tiller.position().distanceTo(Vec3.atBottomCenterOf(spot)) <= reach;
	}

	private static double flat(final BlockPos a, final BlockPos b) {
		return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
	}

	private static List<Tiller> people(final MinecraftServer server, final BlockPos hub) {
		return server.overworld().getEntitiesOfClass(Tiller.class, new AABB(hub).inflate(24.0));
	}

	private static void until(final ClientGameTestContext context, final TestSingleplayerContext world, final Predicate<List<Tiller>> condition,
		final int seconds, final String what) {
		for (int i = 0; i < seconds * 4; i++) {
			boolean done = world.getServer().computeOnServer(server -> {
				Settlements.Settlement home = Settlements.ledger(server).built().getFirst();
				List<Tiller> people = people(server, home.hub());
				return people.size() == Tiller.Role.values().length && condition.test(people);
			});
			if (done) {
				check(true, what);
				return;
			}
			context.waitTicks(5);
		}
		String where = world.getServer().computeOnServer(server -> {
			StringBuilder out = new StringBuilder();
			for (Tiller t : people(server, Settlements.ledger(server).built().getFirst().hub())) {
				out.append(' ').append(t.role()).append(t.blockPosition().toShortString()).append("->").append(TillerRoutine.spot(t) == null ? "none" : TillerRoutine.spot(t).toShortString());
			}
			return out.toString();
		});
		throw new AssertionError("failed: " + what + ":" + where);
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn routine] ok: " + what);
	}
}
