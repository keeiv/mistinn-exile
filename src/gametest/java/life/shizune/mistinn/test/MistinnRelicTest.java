package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.client.terra.CommissionScreen;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.expedition.Cache;
import life.shizune.mistinn.expedition.DamGround;
import life.shizune.mistinn.expedition.Grounds;
import life.shizune.mistinn.expedition.Relics;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.Carried;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.ItemSizes;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.Commissions;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.TerraService;
import life.shizune.mistinn.terra.Tiller;
import life.shizune.mistinn.tier.Tier;
import life.shizune.mistinn.tier.Tiers;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The dam's relic, the gate cassette: a tier V thing of 1x2, in the control room's safe every time the dam is stocked,
 * and like the salvage not to be put in the safe box. Shown to the elder from the board, it is taken for crystal cores;
 * the first time the elder tells what it was and the hunter earns the relic achievement; after that, cores only.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnRelicTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 1000");
			context.waitTicks(20);
			Settlements.Settlement[] home = new Settlements.Settlement[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				ItemStack relic = new ItemStack(MistinnItems.GATE_CASSETTE);
				int[] size = ItemSizes.of(relic);
				check(size[0] == 1 && size[1] == 2 && Tiers.of(relic) == Tier.V && Relics.is(relic), "the gate cassette: tier V, one by two");
				player.containerMenu.setCarried(relic.copy());
				GridService.place(player, Area.SAFE, 0, 0, 0, false);
				check(GridService.get(player).items(Area.SAFE).isEmpty() && !player.containerMenu.getCarried().isEmpty(), "it does not go in the safe box");
				player.containerMenu.setCarried(ItemStack.EMPTY);
				// the dam's safe holds it, every time
				BlockPos far = player.blockPosition().offset(300, 0, 0);
				level.getChunk(far.getX() >> 4, far.getZ() >> 4);
				BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, far);
				level.setBlock(ground, Blocks.BARREL.defaultBlockState(), 3);
				RandomSource random = RandomSource.create(9L);
				for (int roll = 0; roll < 10; roll++) {
					Cache.stock(level, ground, DamGround.STRONGBOX, 4, 4, random);
					check(level.getBlockEntity(ground).getAttached(Cache.CACHE).entries().stream().anyMatch(e -> e.stack().is(MistinnItems.GATE_CASSETTE)),
						"the safe holds the relic (" + roll + ")");
				}
				// a homestead, its elder by the hub, the hunter at the board
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(-200, 0, 0));
				Domain domain = TerraService.settleAt(level, centre, 240);
				Settlements.Settlement built = Settlements.buildNow(level, domain);
				check(Settlements.light(level, built.hub(), player), "a homestead is woken");
				home[0] = Settlements.of(server, domain.id());
				player.getAbilities().mayfly = true;
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
			});
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				check(!Relics.show(player), "with no relic, there is nothing to show");
				Tiller elder = level.getEntitiesOfClass(Tiller.class, new AABB(home[0].hub()).inflate(24.0), t -> t.role() == Tiller.Role.ELDER).getFirst();
				BlockPos hub = home[0].hub();
				elder.setNoAi(true);
				elder.teleportTo(hub.getX() + 3.5, hub.getY() + 1.0, hub.getZ() + 0.5);
				player.teleportTo(level, hub.getX() + 2.5, hub.getY() + 1.0, hub.getZ() + 2.5, Set.of(), 0.0F, 0.0F, true);
				// (no pack on: a one-by-two thing rides in the hotbar)
				player.getInventory().setItem(8, new ItemStack(MistinnItems.GATE_CASSETTE));
				Commissions.open(player, elder);
			});
			context.waitFor(mc -> mc.gui.screen() instanceof CommissionScreen screen && has(screen), 100);
			int cores = world.getServer().computeOnServer(server -> Carried.count(player(server), MistinnItems.CRYSTAL_CORE));
			click(context);
			context.waitFor(mc -> !(mc.gui.screen() instanceof CommissionScreen screen) || !has(screen), 100);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(Carried.count(player, MistinnItems.GATE_CASSETTE) == 0, "shown from the board, the elder takes it");
				check(Carried.count(player, MistinnItems.CRYSTAL_CORE) - cores == Relics.CORES, "and gives crystal cores for it");
				check(Relics.shown(server, Grounds.DAM) && AchievementService.has(player, Achievement.RELIC_SHOWN), "the first time, the elder tells what it was");
				player.getInventory().setItem(8, new ItemStack(MistinnItems.GATE_CASSETTE));
				int before = Carried.count(player, MistinnItems.CRYSTAL_CORE);
				check(Relics.show(player) && Carried.count(player, MistinnItems.CRYSTAL_CORE) - before == Relics.CORES, "another, and more cores");
			});
		}
	}

	private static boolean has(final CommissionScreen screen) {
		try {
			screen.buttonCentre("gui.mistinn.commission.relic", 0);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static void click(final ClientGameTestContext context) {
		double[] at = context.computeOnClient(mc -> {
			double[] gui = ((CommissionScreen) mc.gui.screen()).buttonCentre("gui.mistinn.commission.relic", 0);
			double scale = mc.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(6);
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn relic] ok: " + what);
	}
}
