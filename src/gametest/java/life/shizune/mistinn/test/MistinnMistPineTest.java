package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The mist pine's wood is worked as any wood is: stairs, a slab, a fence and its gate, a door, a trapdoor, a button and
 * a pressure plate, each with its recipe and in the wooden tags. And the mod's recipes are learned as vanilla's are:
 * not before the hunter holds the material (iron for an iron blade, netherite for a netherite one, mist pine planks
 * for a mist pine door).
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnMistPineTest implements FabricClientGameTest {
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
			String[] learned = {"iron_blade", "netherite_blade_smithing", "mist_pine_door", "mist_pine_fence"};
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				for (String id : new String[] {"mist_pine_stairs", "mist_pine_slab", "mist_pine_fence", "mist_pine_fence_gate", "mist_pine_door",
					"mist_pine_trapdoor", "mist_pine_button", "mist_pine_pressure_plate"}) {
					check(server.getRecipeManager().byKey(key(id)).isPresent(), id + " has its recipe");
				}
				check(MistinnBlocks.MIST_PINE_DOOR.defaultBlockState().is(BlockTags.WOODEN_DOORS) && MistinnBlocks.MIST_PINE_FENCE.defaultBlockState().is(BlockTags.WOODEN_FENCES)
					&& MistinnBlocks.MIST_PINE_TRAPDOOR.defaultBlockState().is(BlockTags.WOODEN_TRAPDOORS) && new ItemStack(MistinnItems.MIST_PINE_SLAB).is(ItemTags.WOODEN_SLABS),
					"and it is wood as the world knows wood");
				for (String id : learned) {
					check(!player.getRecipeBook().contains(key(id)), id + " is not known before its material is held");
				}
				player.getInventory().add(new ItemStack(Items.IRON_INGOT));
				player.getInventory().add(new ItemStack(Items.NETHERITE_INGOT));
				player.getInventory().add(new ItemStack(MistinnItems.MIST_PINE_PLANKS, 4));
			});
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				for (String id : learned) {
					check(player.getRecipeBook().contains(key(id)), id + " is learned with its material in hand");
				}
				check(!player.getRecipeBook().contains(key("diamond_blade")), "a diamond blade is not, without a diamond");
				// the set, laid out to be seen
				ServerLevel level = server.overworld();
				BlockPos p = player.blockPosition().atY(Y);
				for (int x = -6; x <= 6; x++) {
					for (int z = -2; z <= 8; z++) {
						level.setBlock(p.offset(x, -1, z), MistinnBlocks.MIST_PINE_PLANKS.defaultBlockState(), 2);
						for (int y = 0; y <= 4; y++) {
							level.setBlock(p.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
						}
					}
				}
				BlockPos row = p.offset(-4, 0, 5);
				level.setBlock(row, MistinnBlocks.MIST_PINE_STAIRS.defaultBlockState(), 3);
				level.setBlock(row.east(), MistinnBlocks.MIST_PINE_SLAB.defaultBlockState(), 3);
				level.setBlock(row.east(2), MistinnBlocks.MIST_PINE_FENCE.defaultBlockState(), 3);
				level.setBlock(row.east(3), MistinnBlocks.MIST_PINE_FENCE_GATE.defaultBlockState(), 3);
				level.setBlock(row.east(4), MistinnBlocks.MIST_PINE_FENCE.defaultBlockState(), 3);
				level.setBlock(row.east(5), MistinnBlocks.MIST_PINE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH), 3);
				level.setBlock(row.east(5).above(), MistinnBlocks.MIST_PINE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
					.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
				level.setBlock(row.east(6), MistinnBlocks.MIST_PINE_TRAPDOOR.defaultBlockState(), 3);
				level.setBlock(row.east(7), MistinnBlocks.MIST_PINE_PRESSURE_PLATE.defaultBlockState(), 3);
				level.setBlock(row.east(8).north(-1), MistinnBlocks.MIST_PINE_PLANKS.defaultBlockState(), 3);
				check(level.getBlockState(row.east(5).above()).is(MistinnBlocks.MIST_PINE_DOOR), "a door stands two high");
				// its signs keep their words as any sign does
				BlockPos signs = p.offset(3, 0, 2);
				level.setBlock(signs, MistinnBlocks.MIST_PINE_SIGN.defaultBlockState(), 3);
				check(level.getBlockEntity(signs) instanceof net.minecraft.world.level.block.entity.SignBlockEntity, "a mist pine sign holds words");
				level.setBlock(signs.above(3), MistinnBlocks.MIST_PINE_PLANKS.defaultBlockState(), 3);
				level.setBlock(signs.above(2), MistinnBlocks.MIST_PINE_HANGING_SIGN.defaultBlockState(), 3);
				check(level.getBlockEntity(signs.above(2)) instanceof net.minecraft.world.level.block.entity.HangingSignBlockEntity, "and so does a hanging one");
				// an axe strips its log
				BlockPos log = p.offset(-2, 0, 2);
				level.setBlock(log, MistinnBlocks.MIST_PINE_LOG.defaultBlockState(), 3);
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
				player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
					new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(log), Direction.UP, log, false)));
				check(level.getBlockState(log).is(MistinnBlocks.STRIPPED_MIST_PINE_LOG), "an axe strips the bark off its log: " + level.getBlockState(log));
				for (String id : new String[] {"mist_pine_sign", "mist_pine_hanging_sign", "mist_pine_boat", "mist_pine_chest_boat"}) {
					check(server.getRecipeManager().byKey(key(id)).isPresent(), id + " has its recipe");
				}
				// and its boat floats
				net.minecraft.world.entity.vehicle.boat.Boat boat = life.shizune.mistinn.registry.MistinnEntities.MIST_PINE_BOAT.create(level,
					net.minecraft.world.entity.EntitySpawnReason.EVENT);
				check(boat != null && boat.getPickResult().is(MistinnItems.MIST_PINE_BOAT), "a mist pine boat is its own boat");
				boat.snapTo(p.getX() + 0.5, Y, p.getZ() + 2.5, 30.0F, 0.0F);
				level.addFreshEntity(boat);
				player.teleportTo(level, p.getX() + 0.5, Y, p.getZ() - 0.5, Set.of(), 0.0F, 15.0F, true);
			});
			context.waitTicks(20);
			context.takeScreenshot(TestScreenshotOptions.of("mist-pine").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("wood")));
		}
	}

	private static ResourceKey<Recipe<?>> key(final String id) {
		return ResourceKey.create(Registries.RECIPE, Mistinn.id(id));
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn wood] ok: " + what);
	}
}
