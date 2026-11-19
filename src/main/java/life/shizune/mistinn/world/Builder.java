package life.shizune.mistinn.world;

import life.shizune.mistinn.Mistinn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Places a structure's blocks around an origin (the centre of its floor). Coordinates are
 * relative: x east, y up, z south. Used by world generation and by the spawn station alike.
 */
public final class Builder {
	private static final int FLAGS = Block.UPDATE_CLIENTS;

	private final WorldGenLevel level;
	private final BlockPos origin;
	private final RandomSource random;

	public Builder(final WorldGenLevel level, final BlockPos origin, final RandomSource random) {
		this.level = level;
		this.origin = origin;
		this.random = random;
	}

	public RandomSource random() {
		return this.random;
	}

	public BlockPos at(final int x, final int y, final int z) {
		return this.origin.offset(x, y, z);
	}

	public void set(final int x, final int y, final int z, final BlockState state) {
		this.level.setBlock(this.at(x, y, z), state, FLAGS);
	}

	public void set(final int x, final int y, final int z, final Block block) {
		this.set(x, y, z, block.defaultBlockState());
	}

	/** Sets the block with the given chance; otherwise leaves what is there. */
	public void maybe(final float chance, final int x, final int y, final int z, final Block block) {
		if (this.random.nextFloat() < chance) {
			this.set(x, y, z, block);
		}
	}

	public void fill(final int x0, final int y0, final int z0, final int x1, final int y1, final int z1, final Block block) {
		for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
					this.set(x, y, z, block);
				}
			}
		}
	}

	/** Empties the space, so nothing grows or stands inside the structure. */
	public void clear(final int x0, final int y0, final int z0, final int x1, final int y1, final int z1) {
		this.fill(x0, y0, z0, x1, y1, z1, Blocks.AIR);
	}

	/** Props a floor block up with a column down to the ground, so it never floats. */
	public void footing(final int x, final int z, final Block block, final int maxDepth) {
		for (int y = -1; y >= -maxDepth; y--) {
			BlockPos pos = this.at(x, y, z);
			BlockState there = this.level.getBlockState(pos);
			if (!there.isAir() && !there.canBeReplaced() && there.getFluidState().isEmpty()) {
				return;
			}
			this.level.setBlock(pos, block.defaultBlockState(), FLAGS);
		}
	}

	public void facing(final int x, final int y, final int z, final Block block, final Direction direction) {
		BlockState state = block.defaultBlockState();
		if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
			state = state.setValue(HorizontalDirectionalBlock.FACING, direction);
		} else if (state.hasProperty(BlockStateProperties.FACING)) {
			state = state.setValue(BlockStateProperties.FACING, direction);
		}
		this.set(x, y, z, state);
	}

	/** A barrel or chest that fills itself from a loot table the first time it is opened. */
	public void container(final int x, final int y, final int z, final Block block, final Direction direction, final String lootTable) {
		this.facing(x, y, z, block, direction);
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Mistinn.id(lootTable));
		RandomizableContainer.setBlockEntityLootTable(this.level, this.random, this.at(x, y, z), key);
	}

	/** Height of the ground's top block at a relative column. */
	public int groundY(final int x, final int z) {
		BlockPos pos = this.at(x, 0, z);
		return this.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ()) - 1;
	}
}
