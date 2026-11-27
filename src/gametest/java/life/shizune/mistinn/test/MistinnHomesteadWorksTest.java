package life.shizune.mistinn.test;

import life.shizune.mistinn.block.WorkbenchBlock;
import life.shizune.mistinn.grid.Carried;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.HomesteadWorks;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.TerraService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The homestead at work: a sleeping homestead's refinery and bench do nothing; once the hub wakes
 * the refinery takes crystal grit and presses eight of it into each crystal core, handed over when
 * a hunter comes empty-handed, and the workshop's bench mends worn gear whole for grit by how worn
 * it is.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnHomesteadWorksTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
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
			BlockPos refinery = world.getServer().computeOnServer(server -> find(server.overworld(), hub, MistinnBlocks.REFINERY));
			BlockPos bench = world.getServer().computeOnServer(server -> find(server.overworld(), hub, MistinnBlocks.WORKBENCH));
			check(refinery != null && bench != null, "a refinery on the deck and a bench in the workshop");
			world.getServer().runOnServer(server -> check(server.overworld().getBlockState(bench).getValue(WorkbenchBlock.FACING)
				!= null, "the bench faces its doorway"));

			// asleep, the refinery takes nothing
			world.getServer().runOnServer(server -> {
				ItemStack grit = new ItemStack(MistinnItems.CRYSTAL_DUST, 20);
				HomesteadWorks.useRefinery(server.overworld(), refinery, player(server), grit);
				check(grit.getCount() == 20 && HomesteadWorks.refinery(server, id[0]) == null, "a sleeping homestead's refinery takes no grit");
			});

			// woken: grit in, cores out
			HomesteadWorks.refining = 40;
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.CRYSTAL_CORE, Settlements.CORES));
				check(Settlements.light(server.overworld(), hub, player), "the hub wakes");
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				ItemStack grit = new ItemStack(MistinnItems.CRYSTAL_DUST, 40);
				HomesteadWorks.useRefinery(server.overworld(), refinery, player, grit);
				check(grit.getCount() == 40 - HomesteadWorks.HOLDS && HomesteadWorks.refinery(server, id[0]).grit() == HomesteadWorks.HOLDS,
					"the refinery takes grit up to what it holds: " + grit.getCount());
			});
			context.waitTicks(140);
			world.getServer().runOnServer(server -> {
				HomesteadWorks.Refinery state = HomesteadWorks.refinery(server, id[0]);
				check(state.cores() >= 2 && state.grit() <= HomesteadWorks.HOLDS - 2 * HomesteadWorks.GRIT_PER_CORE,
					"eight grit to a core, pressed in time: " + state);
				ServerPlayer player = player(server);
				int before = Carried.count(player, MistinnItems.CRYSTAL_CORE);
				HomesteadWorks.useRefinery(server.overworld(), refinery, player, ItemStack.EMPTY);
				check(Carried.count(player, MistinnItems.CRYSTAL_CORE) - before == state.cores(), "the cores are handed over");
				check(HomesteadWorks.refinery(server, id[0]).cores() == 0, "and the refinery keeps none back");
			});
			HomesteadWorks.refining = 2400;

			// the bench mends worn gear for grit
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.getInventory().clearContent();
				ItemStack sword = new ItemStack(Items.IRON_SWORD);
				sword.setDamageValue(200);
				int cost = HomesteadWorks.mendCost(sword);
				check(cost == 5, "a sword four fifths worn costs five grit: " + cost);
				// the sword in hand first: the grit must not land in the slot the hand holds
				player.setItemInHand(InteractionHand.MAIN_HAND, sword);
				player.getInventory().add(new ItemStack(MistinnItems.CRYSTAL_DUST, 3));
				HomesteadWorks.useBench(server.overworld(), bench, player, player.getMainHandItem());
				check(player.getMainHandItem().getDamageValue() == 200, "not mended without the grit for it");
				player.getInventory().add(new ItemStack(MistinnItems.CRYSTAL_DUST, 4));
				HomesteadWorks.useBench(server.overworld(), bench, player, player.getMainHandItem());
				check(player.getMainHandItem().getDamageValue() == 0, "mended whole");
				check(Carried.count(player, MistinnItems.CRYSTAL_DUST) == 2, "for five of the seven grit: " + Carried.count(player, MistinnItems.CRYSTAL_DUST));
				check(server.overworld().getBlockState(bench).getValue(WorkbenchBlock.LIT), "and the bench's lamp is lit with the hub");
			});
		}
	}

	private static BlockPos find(final ServerLevel level, final BlockPos hub, final Block block) {
		for (BlockPos at : BlockPos.betweenClosed(hub.offset(-Settlements.CLEAR, -1, -Settlements.CLEAR), hub.offset(Settlements.CLEAR, 4, Settlements.CLEAR))) {
			if (level.getBlockState(at).is(block)) {
				return at.immutable();
			}
		}
		return null;
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn works] ok: " + what);
	}
}
