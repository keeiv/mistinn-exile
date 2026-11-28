package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.client.gear.DetectorClient;
import life.shizune.mistinn.gear.Detector;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * The detector: worn where a mask is worn (no armour); an empty-handed right click at the open air touches it, and the
 * ores about the hunter show for a while, each kind told apart; then it rests five minutes, and after five touches it is
 * spent. Not worn, it does nothing.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnDetectorTest implements FabricClientGameTest {
	private static final int Y = 200;

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
			world.getServer().runCommand("time set 6000");
			context.waitTicks(20);
			BlockPos[] at = new BlockPos[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				at[0] = player.blockPosition().atY(Y);
				BlockPos p = at[0];
				for (int x = -6; x <= 6; x++) {
					for (int z = -6; z <= 6; z++) {
						level.setBlock(p.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
						for (int y = 0; y <= 3; y++) {
							level.setBlock(p.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
						}
					}
				}
				// ores in a stone wall before the hunter
				for (int x = -4; x <= 4; x++) {
					for (int y = 0; y <= 3; y++) {
						level.setBlock(p.offset(x, y, 5), Blocks.STONE.defaultBlockState(), 2);
					}
				}
				level.setBlock(p.offset(-3, 1, 6), Blocks.IRON_ORE.defaultBlockState(), 2);
				level.setBlock(p.offset(-1, 2, 6), Blocks.GOLD_ORE.defaultBlockState(), 2);
				level.setBlock(p.offset(1, 1, 6), Blocks.DIAMOND_ORE.defaultBlockState(), 2);
				level.setBlock(p.offset(3, 2, 6), Blocks.EMERALD_ORE.defaultBlockState(), 2);
				level.setBlock(p.offset(0, 0, 6), Blocks.COAL_ORE.defaultBlockState(), 2);
				player.teleportTo(level, p.getX() + 0.5, Y, p.getZ() + 0.5, Set.of(), 0.0F, -60.0F, true);
				// an empty hand (the kit the station packed sits in the hotbar)
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				check(!Detector.touch(player), "not worn, it does nothing");
				// worn where a mask is worn
				player.containerMenu.setCarried(new ItemStack(MistinnItems.DETECTOR));
				GridService.maskSlot(player);
				check(Detector.worn(player) && player.containerMenu.getCarried().isEmpty(), "it goes where a mask goes");
				check(life.shizune.mistinn.body.BodyGear.faceArmor(player) == 0.0F, "and gives the face no armour");
				Detector.OresPayload found = Detector.scan(level, player.blockPosition());
				check(found.positions().contains(p.offset(1, 1, 6).asLong()) && found.kinds().get(found.positions().indexOf(p.offset(1, 1, 6).asLong()))
					== Detector.Ore.DIAMOND.ordinal(), "it finds a diamond through the stone, and knows it for one");
				check(!found.positions().contains(p.offset(0, 0, 6).asLong()), "coal is beneath it");
				check(found.positions().size() == 4, "iron, gold, diamond, emerald: " + found.positions().size());
			});
			context.waitTicks(10);
			// an empty hand at the open air touches it
			context.runOnClient(DetectorClient::used);
			boolean shown = false;
			for (int i = 0; i < 40 && !shown; i++) {
				context.waitTick();
				shown = context.computeOnClient(mc -> DetectorClient.shown() == 4);
			}
			check(shown, "an empty hand at the air touches it: the four ores show");
			context.runOnClient(mc -> mc.player.setXRot(10.0F));
			// the hand at the head, a moment after the touch
			context.takeScreenshot(TestScreenshotOptions.of("detector-reach").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("detector")));
			context.waitTicks(25);
			context.takeScreenshot(TestScreenshotOptions.of("detector").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("detector")));
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				ItemStack worn = GridService.get(player).mask();
				check(worn.getDamageValue() == 1, "one of its five touches is spent");
				check(!Detector.touch(player), "it rests before the next");
				// four more, and it is spent
				for (int i = 0; i < Detector.USES - 1; i++) {
					player.setAttached(Detector.READY, 0L);
					check(Detector.touch(player), "touched again (" + (i + 2) + ")");
				}
				check(GridService.get(player).mask().isEmpty(), "after five it is spent and gone");
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
		System.out.println("[mistinn detector] ok: " + what);
	}
}
