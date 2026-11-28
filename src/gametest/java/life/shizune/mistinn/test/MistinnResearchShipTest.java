package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.ResearchShip;
import life.shizune.mistinn.terra.Tiller;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * A research ship of the crystal tillers on a lake: its hull dry inside, its rail joined up, its laboratory chest with a
 * detector in it and its stores with a piece of better gear; its crew of two researchers and two wardens, who leave a
 * hunter in the water beside them alone, and fight one who boards.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnResearchShipTest implements FabricClientGameTest {
	private static final int Y = 200;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 6000");
			context.waitTicks(20);
			BlockPos[] at = new BlockPos[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				BlockPos c = player.blockPosition().atY(Y).offset(0, 0, 40);
				at[0] = c;
				// open water, ten deep, on a floor of stone
				for (int x = -24; x <= 24; x++) {
					for (int z = -12; z <= 12; z++) {
						level.setBlock(c.offset(x, -11, z), Blocks.STONE.defaultBlockState(), 2);
						for (int y = -10; y <= 0; y++) {
							level.setBlock(c.offset(x, y, z), Blocks.WATER.defaultBlockState(), 2);
						}
					}
				}
				ResearchShip.build(level, c, true);
				ResearchShip.watch(level, 99, c, true);
				// look at it, from the water off its side
				player.setGameMode(GameType.CREATIVE);
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				player.teleportTo(level, c.getX() - 2.5, Y + 7, c.getZ() - 21.5, Set.of(), 0.0F, 14.0F, true);
			});
			context.waitTicks(60);
			world.getConnection().waitForChunksRender();
			context.takeScreenshot(TestScreenshotOptions.of("ship").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("ship")));
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				BlockPos c = at[0];
				// the hull: dry inside, under the water's level
				BlockPos amidships = ResearchShip.deck(c, true, 2, 0);
				check(level.getBlockState(amidships.below(2)).isAir() && level.getBlockState(amidships.below(3)).isAir(), "the hold is dry");
				check(level.getBlockState(amidships.below()).is(life.shizune.mistinn.registry.MistinnBlocks.MIST_PINE_PLANKS), "under a deck of mist pine");
				// the rail joined along the side
				var rail = level.getBlockState(ResearchShip.deck(c, true, 4, 4));
				check(rail.getBlock() instanceof FenceBlock && rail.getValue(FenceBlock.EAST) && rail.getValue(FenceBlock.WEST), "the rail runs joined along the side");
				// the laboratory's chest always holds a detector; the stores, a piece of better gear
				check(ResearchShip.lab(level, c, true) instanceof ChestBlockEntity, "a chest in the laboratory");
				ChestBlockEntity lab = (ChestBlockEntity) ResearchShip.lab(level, c, true);
				lab.unpackLootTable(player);
				boolean detector = false;
				for (int i = 0; i < lab.getContainerSize(); i++) {
					detector |= lab.getItem(i).is(MistinnItems.DETECTOR);
				}
				check(detector, "with a detector in it");
				check(ResearchShip.stores(level, c, true) instanceof ChestBlockEntity, "a chest of stores in the hold");
				ChestBlockEntity stores = (ChestBlockEntity) ResearchShip.stores(level, c, true);
				List<Item> better = new java.util.ArrayList<>(MistinnItems.gearOfTier(4));
				better.addAll(MistinnItems.gearOfTier(5));
				boolean gear = false;
				int filled = 0;
				for (int i = 0; i < stores.getContainerSize(); i++) {
					gear |= better.contains(stores.getItem(i).getItem());
					filled += stores.getItem(i).isEmpty() ? 0 : 1;
				}
				check(gear && filled >= 5, "with a piece of the tillers' better gear among " + filled + " stacks");
				// the crew
				List<Tiller> crew = crew(level, c);
				check(crew.size() == 4 && crew.stream().filter(t -> t.role() == Tiller.Role.RESEARCHER).count() == 2, "a crew of two researchers and two wardens");
				check(crew.stream().filter(t -> t.role() == Tiller.Role.SHIP_WARDEN).allMatch(t -> t.getItemBySlot(EquipmentSlot.MAINHAND).is(MistinnItems.MIST_SPEAR)),
					"the wardens hold spears");
				// a hunter in the water beside the ship is left alone
				player.setGameMode(GameType.SURVIVAL);
				player.teleportTo(level, c.getX() + 0.5, Y + 0.2, c.getZ() + 9.5, Set.of(), 0.0F, 0.0F, true);
			});
			context.waitTicks(40);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				check(crew(level, at[0]).stream().allMatch(t -> t.getTarget() == null && !t.holdsAgainst(player)), "a hunter in the water beside it is left alone");
				// one who boards is fought
				BlockPos deck = ResearchShip.deck(at[0], true, 2, 1);
				player.teleportTo(level, deck.getX() + 0.5, deck.getY(), deck.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
				player.setHealth(player.getMaxHealth());
			});
			context.waitTicks(30);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				List<Tiller> crew = crew(level, at[0]);
				check(crew.stream().allMatch(t -> t.holdsAgainst(player)), "boarding turns the whole crew");
				check(crew.stream().filter(t -> t.role() == Tiller.Role.SHIP_WARDEN).allMatch(t -> t.getTarget() == player), "and the wardens come for the boarder");
			});
			boolean hit = false;
			for (int i = 0; i < 200 && !hit; i++) {
				context.waitTick();
				hit = world.getServer().computeOnServer(server -> player(server).getHealth() < player(server).getMaxHealth());
			}
			check(hit, "the crew strike the boarder");
		}
	}

	private static List<Tiller> crew(final ServerLevel level, final BlockPos at) {
		return level.getEntitiesOfClass(Tiller.class, new AABB(at).inflate(24.0), t -> t.role().ship);
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn ship] ok: " + what);
	}
}
