package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.expedition.Grounds;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.supply.SupplyService;
import life.shizune.mistinn.terra.CrystalDrake;
import life.shizune.mistinn.terra.HomesteadTravel;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.Squall;
import life.shizune.mistinn.terra.TerraService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The grounds of the expeditions: the dam is there in every world, an empty sky with nothing of its own in it (no
 * platform, no stone) until it is built. What belongs to the overworld stays there: the same numbers in the dam are no
 * highland, a drake there is under no squall raging over the highland at those numbers, and neither is a hunter; the way
 * home is not taken from it, and no supply drop finds it.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnExpeditionGroundTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 1000");
			context.waitTicks(20);
			int[] highland = new int[1];
			BlockPos[] centre = new BlockPos[1];
			world.getServer().runOnServer(server -> {
				ServerLevel dam = server.getLevel(Grounds.DAM);
				check(dam != null, "the dam is in every world");
				check(Grounds.is(dam) && !Grounds.is(server.overworld()), "and it is one of the grounds; the overworld is not");
				check(dam.getMinY() == 0 && dam.getHeight() == 192, "its sky runs from 0 to 192: " + dam.getMinY() + ", " + dam.getHeight());
				for (int[] column : new int[][] {{0, 0}, {8, 8}, {-20, 13}, {300, -40}}) {
					for (int y = dam.getMinY(); y < dam.getMinY() + dam.getHeight(); y++) {
						if (!dam.getBlockState(new BlockPos(column[0], y, column[1])).isAir()) {
							throw new AssertionError("failed: the dam has a block of its own at " + column[0] + ", " + y + ", " + column[1]);
						}
					}
				}
				System.out.println("[mistinn grounds] ok: the dam is empty until it is built");

				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(180, 0, 0));
				Domain domain = TerraService.settleAt(level, at, 240);
				Settlements.Settlement built = Settlements.buildNow(level, domain);
				check(Settlements.light(level, built.hub(), player), "a homestead is woken");
				Domain here = DomainService.at(level, at.getX(), at.getZ());
				check(here != null && here.id() == domain.id(), "the highland covers its own ground");
				check(DomainService.at(dam, at.getX(), at.getZ()) == null, "the same numbers in the dam are no highland");
				Squall.gather(level, domain, 1);
				highland[0] = domain.id();
				centre[0] = at;
				// the hunter goes to the dam, held in the air there: nothing to stand on yet
				player.getAbilities().mayfly = true;
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				player.teleportTo(dam, at.getX() + 0.5, 100.0, at.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			});
			context.waitTicks(60);

			world.getServer().runOnServer(server -> {
				ServerLevel dam = server.getLevel(Grounds.DAM);
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				check(player.level() == dam, "the hunter is in the dam");
				Squall.Storm storm = Squall.storm(server, highland[0]);
				check(storm != null && storm.stage(level.getGameTime()) == Squall.Stage.RAGING, "a squall rages over the highland");
				check(drake(level, centre[0]).inSquall(), "a drake over the highland is in it");
				check(!drake(dam, centre[0]).inSquall(), "one at the same numbers in the dam is not");
				check(Squall.sky(player) != Squall.Stage.RAGING, "nor is the hunter there");
				check(HomesteadTravel.ask(player, highland[0]) == HomesteadTravel.Result.ELSEWHERE, "the way home is not taken from the dam");
				SaltService.add(player, 1000);
				check(SupplyService.order(player, "tourniquet") == SupplyService.Result.NOWHERE, "and no supply drop finds it");
			});
		}
	}

	/** A drake at {@code at} (high over it) in {@code level}, never let into the world: only where it is matters. */
	private static CrystalDrake drake(final Level level, final BlockPos at) {
		CrystalDrake drake = MistinnEntities.CRYSTAL_DRAKE.create(level, EntitySpawnReason.COMMAND);
		if (drake == null) {
			throw new AssertionError("failed: no drake");
		}
		drake.setPos(at.getX() + 0.5, 120.0, at.getZ() + 0.5);
		return drake;
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn grounds] ok: " + what);
	}
}
