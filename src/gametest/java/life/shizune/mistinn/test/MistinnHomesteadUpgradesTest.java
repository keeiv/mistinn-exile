package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.HomesteadTravel;
import life.shizune.mistinn.terra.HomesteadUpgrades;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.Siege;
import life.shizune.mistinn.terra.TerraService;
import life.shizune.mistinn.terra.Tiller;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * A homestead grows as its hub is given more crystal cores: too few do nothing; the first step
 * sends grit to the hub each morning, taken from it by hand; the second brings a second warden;
 * the third halves the way home's rest and doubles the thanks for holding it; past the third it
 * grows no more.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnHomesteadUpgradesTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set 1000");
			context.waitTicks(20);
			Settlements.Settlement[] home = new Settlements.Settlement[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(180, 0, 0));
				Domain domain = TerraService.settleAt(level, centre, 240);
				Settlements.Settlement built = Settlements.buildNow(level, domain);
				check(Settlements.light(level, built.hub(), player), "a homestead is woken");
				home[0] = Settlements.of(server, domain.id());
				BlockPos hub = home[0].hub();
				player.teleportTo(level, hub.getX() + 3.5, hub.getY() + 1.0, hub.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			});
			context.waitTicks(20);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				BlockPos hub = home[0].hub();
				check(HomesteadUpgrades.level(server, home[0].domain()) == 0, "a woken homestead has not grown yet");
				check(HomesteadUpgrades.travelCooldown(server, home[0], HomesteadTravel.COOLDOWN) == HomesteadTravel.COOLDOWN, "and its way home rests as long as any");

				// too few cores do nothing
				ItemStack two = new ItemStack(MistinnItems.CRYSTAL_CORE, 2);
				check(!HomesteadUpgrades.offer(level, hub, player, two) && two.getCount() == 2, "two cores are too few to grow it");

				// the first step: grit each morning
				ItemStack cores = new ItemStack(MistinnItems.CRYSTAL_CORE, 20);
				check(HomesteadUpgrades.offer(level, hub, player, cores), "three cores grow it a step");
				check(cores.getCount() == 17 && HomesteadUpgrades.level(server, home[0].domain()) == 1, "taking three: " + cores.getCount());
				// the next morning
				HomesteadUpgrades.Growth growth = HomesteadUpgrades.growth(server, home[0].domain());
				level.setAttached(HomesteadUpgrades.GROWTH, java.util.List.of(new HomesteadUpgrades.Growth(home[0].domain(), 1, 0, growth.day() - 1)));
				HomesteadUpgrades.mornings(server);
				check(HomesteadUpgrades.growth(server, home[0].domain()).tribute() == HomesteadUpgrades.TRIBUTE, "in the morning the fields send grit");
				HomesteadUpgrades.mornings(server);
				check(HomesteadUpgrades.growth(server, home[0].domain()).tribute() == HomesteadUpgrades.TRIBUTE, "once a day");
				check(HomesteadUpgrades.collect(level, hub, player), "the hunter takes it from the hub");
				check(HomesteadUpgrades.growth(server, home[0].domain()).tribute() == 0, "and the hub is empty again");

				// the second: another warden
				long wardens = level.getEntitiesOfClass(Tiller.class, new AABB(hub).inflate(24.0), t -> t.role() == Tiller.Role.WARDEN).size();
				check(HomesteadUpgrades.offer(level, hub, player, cores), "five cores more grow it again");
				long after = level.getEntitiesOfClass(Tiller.class, new AABB(hub).inflate(24.0), t -> t.role() == Tiller.Role.WARDEN).size();
				check(after == wardens + 1, "and a second warden comes: " + wardens + " -> " + after);

				// the third: the way home rests half as long, the thanks are doubled
				check(HomesteadUpgrades.offer(level, hub, player, cores), "eight more grow it a third time");
				check(cores.getCount() == 4, "taking eight: " + cores.getCount());
				check(HomesteadUpgrades.travelCooldown(server, home[0], HomesteadTravel.COOLDOWN) == HomesteadTravel.COOLDOWN / 2, "the way home rests half as long");
				check(HomesteadUpgrades.thanks(server, home[0].domain()) == 2, "the thanks are doubled");
				check(!HomesteadUpgrades.offer(level, hub, player, new ItemStack(MistinnItems.CRYSTAL_CORE, 20)), "and it grows no further");
			});
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				int dust = server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(player(server).blockPosition()).inflate(4.0),
					e -> e.getItem().is(MistinnItems.CRYSTAL_DUST)).stream().mapToInt(e -> e.getItem().getCount()).sum();
				int carried = player(server).getInventory().countItem(MistinnItems.CRYSTAL_DUST);
				check(dust + carried >= HomesteadUpgrades.TRIBUTE, "the grit taken is the hunter's: " + (dust + carried));
				check(Siege.SALT * HomesteadUpgrades.thanks(server, home[0].domain()) == 600, "a held siege of a grown homestead pays 600");
			});
		}
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
	}
}
