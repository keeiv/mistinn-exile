package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.block.TurretBlock;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.HomesteadDefence;
import life.shizune.mistinn.terra.HomesteadUpgrades;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.Siege;
import life.shizune.mistinn.terra.TerraService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A homestead holds itself in a crystal tide: at its second step two crystal turrets rise on the
 * deck, out to either side of the hub, and in a tide the relay pylons discharge at the tide's
 * creatures and the turrets fire on them.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnHomesteadDefenceTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("difficulty normal");
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
			BlockPos hub = built[0].hub();

			// woken and grown to its second step: the turrets rise
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.teleportTo(server.overworld(), hub.getX() + 2.5, built[0].deck() + 1.0, hub.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.CRYSTAL_CORE, 64));
				check(Settlements.light(server.overworld(), hub, player), "the hub wakes");
				Settlements.Settlement home = Settlements.of(server, id[0]);
				check(HomesteadDefence.spots(home).stream().noneMatch(spot -> server.overworld().getBlockState(spot).is(MistinnBlocks.TURRET)),
					"no turrets before the second step");
				ServerLevel level = server.overworld();
				check(HomesteadUpgrades.offer(level, hub, player, player.getMainHandItem()), "the first step");
				check(HomesteadUpgrades.offer(level, hub, player, player.getMainHandItem()), "the second step");
				for (BlockPos spot : HomesteadDefence.spots(home)) {
					check(level.getBlockState(spot).is(MistinnBlocks.TURRET) && level.getBlockState(spot).getValue(TurretBlock.LIT),
						"a lit turret stands at " + spot.toShortString());
				}
			});

			// a tide: the pylons discharge and the turrets fire
			HomesteadDefence.discharges = 0;
			HomesteadDefence.shots = 0;
			// a hunter the tide's creatures will come for: in survival, unhurt
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runOnServer(server -> {
				player(server).getAbilities().invulnerable = true;
				player(server).onUpdateAbilities();
			});
			world.getServer().runCommand("time set 14000");
			world.getServer().runOnServer(server -> Siege.begin(server.overworld(), Settlements.of(server, id[0]), 20));
			for (int i = 0; i < 20 && world.getServer().computeOnServer(server -> Siege.watch(server, id[0]).foes().size() < 2); i++) {
				context.waitTicks(20);
			}
			// the tide's creatures brought within reach of a pylon and a turret, and held there
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				Settlements.Settlement home = Settlements.of(server, id[0]);
				BlockPos head = null;
				for (BlockPos at : BlockPos.betweenClosed(hub.offset(-Settlements.DECK, 0, -Settlements.DECK), hub.offset(Settlements.DECK, Settlements.PYLON + 1, Settlements.DECK))) {
					if (level.getBlockState(at).is(MistinnBlocks.RELAY_PYLON)
						&& level.getBlockState(at).getValue(life.shizune.mistinn.block.RelayPylonBlock.PART) == life.shizune.mistinn.block.RelayPylonBlock.Part.HEAD) {
						head = at.immutable();
						break;
					}
				}
				BlockPos turret = HomesteadDefence.spots(home).getFirst();
				java.util.List<java.util.UUID> foes = Siege.watch(server, id[0]).foes();
				check(head != null && foes.size() >= 2, "a pylon head and two of the tide's creatures: " + foes.size());
				BlockPos[] near = {new BlockPos(head.getX() + 2, home.deck() + 1, head.getZ()), turret.offset(3, 0, 0)};
				for (int k = 0; k < 2; k++) {
					if (level.getEntity(foes.get(k)) instanceof net.minecraft.world.entity.Mob mob) {
						mob.setNoAi(true);
						mob.teleportTo(near[k].getX() + 0.5, near[k].getY(), near[k].getZ() + 0.5);
					}
				}
			});
			for (int i = 0; i < 10 && (HomesteadDefence.discharges == 0 || HomesteadDefence.shots == 0); i++) {
				context.waitTicks(20);
			}
			world.getServer().runOnServer(server -> {
				Siege.Watch watch = Siege.watch(server, id[0]);
				StringBuilder where = new StringBuilder();
				for (java.util.UUID foe : watch.foes()) {
					var entity = server.overworld().getEntity(foe);
					if (entity != null) {
						where.append(String.format(" %.1f", Math.sqrt(entity.distanceToSqr(hub.getX() + 0.5, entity.getY(), hub.getZ() + 0.5))));
					}
				}
				System.out.println("[mistinn defence] tide: on=" + watch.on() + " wave=" + watch.wave() + " foes from the hub:" + where);
			});
			check(HomesteadDefence.discharges > 0, "the pylons discharge at the tide: " + HomesteadDefence.discharges);
			check(HomesteadDefence.shots > 0, "the turrets fire on it: " + HomesteadDefence.shots);
			world.getServer().runOnServer(server -> {
				BlockPos turret = HomesteadDefence.spots(Settlements.of(server, id[0])).getFirst();
				ServerPlayer player = player(server);
				player.teleportTo(server.overworld(), turret.getX() + 3.5, turret.getY() + 1.5, turret.getZ() + 3.5, Set.of(), 135.0F, 20.0F, true);
			});
			world.getConnection().waitForChunksRender();
			context.waitTicks(30);
			context.takeScreenshot(TestScreenshotOptions.of("turret").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("facilities")));
		}
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn defence] ok: " + what);
	}
}
