package life.shizune.mistinn.test;

import java.util.List;
import life.shizune.mistinn.block.GrassRedstoneSoilBlock;
import life.shizune.mistinn.block.RedstoneSoilBlock;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Redstone soil in the wild: grassy patches on the surface and veins in the dirt beneath, ripe when
 * found, common enough to come across but not everywhere.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnSoilTest implements FabricClientGameTest {
	/** Chunks a side of the square counted round the spawn. */
	private static final int SPAN = 12;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> s.setSeed("20261126")).create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			context.waitTicks(10);
			world.getServer().runOnServer(MistinnSoilTest::foundAsOftenAsMeant);
			lookAtAPatch(context, world);
			world.getServer().runOnServer(MistinnSoilTest::grassySoilBehavesLikeGrass);
		}
	}

	/** The first grassy patch found, for the picture. */
	private static BlockPos found;

	/** A photo of a grassy patch as it was generated, from a few blocks off. */
	private static void lookAtAPatch(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.teleportTo(server.overworld(), found.getX() + 0.5, found.getY() + 5.0, found.getZ() - 1.5, java.util.Set.of(), 0.0F, 70.0F, true);
			player.getAbilities().flying = true;
			player.getAbilities().mayfly = true;
			player.onUpdateAbilities();
		});
		context.waitTicks(40);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot("mistinn-redstone-soil-patch");
	}

	private static void foundAsOftenAsMeant(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos spawn = level.getRespawnData().pos();
		int grassSurface = 0;
		int grassSoil = 0;
		int dirtSoil = 0;
		int dirtBlocks = 0;
		int unripe = 0;
		int chunksWithGrassSoil = 0;
		for (int cx = -SPAN / 2; cx < SPAN / 2; cx++) {
			for (int cz = -SPAN / 2; cz < SPAN / 2; cz++) {
				LevelChunk chunk = level.getChunk((spawn.getX() >> 4) + cx, (spawn.getZ() >> 4) + cz);
				boolean here = false;
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
						for (int y = top - 8; y <= top; y++) {
							BlockState state = chunk.getBlockState(new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z));
							if (state.is(Blocks.GRASS_BLOCK)) {
								grassSurface++;
							} else if (state.is(MistinnBlocks.GRASS_REDSTONE_SOIL)) {
								grassSoil++;
								if (found == null) {
									found = new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
								}
								grassSurface++;
								here = true;
								unripe += state.getValue(RedstoneSoilBlock.AGE) == RedstoneSoilBlock.RIPE ? 0 : 1;
							} else if (state.is(MistinnBlocks.REDSTONE_SOIL)) {
								dirtSoil++;
								unripe += state.getValue(RedstoneSoilBlock.AGE) == RedstoneSoilBlock.RIPE ? 0 : 1;
							} else if (state.is(Blocks.DIRT)) {
								dirtBlocks++;
							}
						}
					}
				}
				chunksWithGrassSoil += here ? 1 : 0;
			}
		}
		int chunks = SPAN * SPAN;
		String counts = "grass surface " + grassSurface + ", grassy soil " + grassSoil + " in " + chunksWithGrassSoil + " chunks, dirt " + dirtBlocks
			+ ", dirt soil " + dirtSoil + ", over " + chunks + " chunks";
		life.shizune.mistinn.Mistinn.LOGGER.info("Redstone soil found: {}", counts);
		check(grassSurface > 2000 && dirtBlocks > 2000, "the counted ground has grass and dirt enough to judge by: " + counts);
		check(unripe == 0, "soil is ripe when found: " + unripe + " not");
		check(grassSoil > 0 && dirtSoil > 0, "both kinds are found: " + counts);
		// often: a patch every few chunks of grassland; not too many: a few blocks in a thousand of grass and dirt
		double grassShare = grassSoil / (double) grassSurface;
		double dirtShare = dirtSoil / (double) dirtBlocks;
		check(chunksWithGrassSoil >= grassSurface / 256.0 / 8.0, "a grassy patch every few chunks of grassland: " + counts);
		check(grassShare < 0.01, "but no more than one grass block in a hundred: " + grassShare);
		check(dirtShare > 0.001 && dirtShare < 0.02, "and in the dirt a few blocks in a thousand: " + dirtShare + " (" + counts + ")");
	}

	private static void grassySoilBehavesLikeGrass(final MinecraftServer server) {
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		ServerLevel level = server.overworld();
		BlockPos pos = player.blockPosition().above(40);
		level.setBlockAndUpdate(pos, MistinnBlocks.GRASS_REDSTONE_SOIL.defaultBlockState().setValue(RedstoneSoilBlock.AGE, RedstoneSoilBlock.RIPE));
		level.setBlockAndUpdate(pos.above(), Blocks.SNOW.defaultBlockState());
		check(level.getBlockState(pos).getValue(GrassRedstoneSoilBlock.SNOWY), "snow on it whitens its sides, as on grass");
		List<ItemStack> plain = Block.getDrops(level.getBlockState(pos), level, pos, null);
		check(plain.size() == 1 && plain.getFirst().is(MistinnItems.REDSTONE_SOIL), "dug, it gives the dirt kind: " + plain);
		ItemStack silk = new ItemStack(Items.DIAMOND_SHOVEL);
		silk.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
		List<ItemStack> kept = Block.getDrops(level.getBlockState(pos), level, pos, null, player, silk);
		check(kept.size() == 1 && kept.getFirst().is(MistinnItems.GRASS_REDSTONE_SOIL), "with silk touch, itself: " + kept);
		level.getBlockState(pos).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		BlockState after = level.getBlockState(pos);
		check(after.getValue(RedstoneSoilBlock.AGE) == 0 && after.getValue(GrassRedstoneSoilBlock.SNOWY), "harvested, it starts again, still snowy");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
