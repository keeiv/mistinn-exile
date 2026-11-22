package life.shizune.mistinn.world;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Places a structure's blocks around an origin (the centre of its floor). Coordinates are
 * relative: x east, y up, z south. Used by world generation and by the spawn station alike.
 *
 * <p>A builder can be {@linkplain #rotated turned} a quarter at a time: coordinates and every
 * block's facing turn with it, so one plan stands in four orientations.
 */
public final class Builder {
	private static final int FLAGS = Block.UPDATE_CLIENTS;
	/** How far {@link #surface} looks down through plants and leaves for the ground. */
	private static final int SURFACE_SEARCH = 24;

	private final WorldGenLevel level;
	private final BlockPos origin;
	private final RandomSource random;
	private final Rotation rotation;
	/** Fences, bars, walls and stairs placed so far: {@link #settle} joins them to their neighbours. */
	private final List<BlockPos> joints;

	public Builder(final WorldGenLevel level, final BlockPos origin, final RandomSource random) {
		this(level, origin, random, Rotation.NONE, new ArrayList<>());
	}

	private Builder(final WorldGenLevel level, final BlockPos origin, final RandomSource random, final Rotation rotation, final List<BlockPos> joints) {
		this.level = level;
		this.origin = origin;
		this.random = random;
		this.rotation = rotation;
		this.joints = joints;
	}

	/** The same structure turned by {@code turn} (on top of any turn this builder already has). */
	public Builder rotated(final Rotation turn) {
		return new Builder(this.level, this.origin, this.random, this.rotation.getRotated(turn), this.joints);
	}

	/** A random quarter turn of this builder. */
	public Builder anyTurn() {
		return this.rotated(Rotation.values()[this.random.nextInt(4)]);
	}

	public RandomSource random() {
		return this.random;
	}

	/** True with the given chance. */
	public boolean chance(final float chance) {
		return this.random.nextFloat() < chance;
	}

	/** One of the options, picked at random. */
	@SafeVarargs
	public final <T> T pick(final T... options) {
		return options[this.random.nextInt(options.length)];
	}

	/** Adds an entity at a relative block position (centred on the block), facing {@code yaw}. */
	public void spawn(final net.minecraft.world.entity.EntityType<?> type, final int x, final int y, final int z, final float yaw) {
		net.minecraft.world.entity.Entity entity = type.create(this.level.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
		if (entity == null) {
			return;
		}
		BlockPos pos = this.at(x, y, z);
		entity.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
		this.level.addFreshEntityWithPassengers(entity);
	}

	public BlockPos at(final int x, final int y, final int z) {
		return switch (this.rotation) {
			case NONE -> this.origin.offset(x, y, z);
			case CLOCKWISE_90 -> this.origin.offset(-z, y, x);
			case CLOCKWISE_180 -> this.origin.offset(-x, y, -z);
			case COUNTERCLOCKWISE_90 -> this.origin.offset(z, y, -x);
		};
	}

	public BlockState get(final int x, final int y, final int z) {
		return this.level.getBlockState(this.at(x, y, z));
	}

	public void set(final int x, final int y, final int z, final BlockState state) {
		BlockState turned = state.rotate(this.rotation);
		BlockPos pos = this.at(x, y, z);
		this.level.setBlock(pos, turned, FLAGS);
		Block block = turned.getBlock();
		if (block instanceof CrossCollisionBlock || block instanceof WallBlock || block instanceof StairBlock) {
			this.joints.add(pos);
		}
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
		this.footing(x, 0, z, block, maxDepth);
	}

	/** Props the block at height {@code y} up with a column down to the ground. */
	public void footing(final int x, final int y, final int z, final Block block, final int maxDepth) {
		for (int d = y - 1; d >= y - maxDepth; d--) {
			BlockPos pos = this.at(x, d, z);
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

	/**
	 * A wall sign facing {@code facing}, its front written and waxed so no one can change it. The
	 * words go in only where the sign's block entity exists at once, in a loaded level; in world
	 * generation the sign stays blank.
	 */
	public void sign(final int x, final int y, final int z, final Block wallSign, final Direction facing, final SignText text) {
		this.facing(x, y, z, wallSign, facing);
		BlockPos pos = this.at(x, y, z);
		if (this.level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			sign.setText(text, SignTextSlot.FRONT);
			sign.setWaxed(true);
			sign.setChanged();
			if (this.level instanceof net.minecraft.server.level.ServerLevel server) {
				BlockState state = server.getBlockState(pos);
				server.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
			}
		}
	}

	// ── Shaped blocks ─────────────────────────────────────────────

	/** A stair whose tall back faces {@code facing}; {@code top} hangs it upside down. */
	public void stairs(final int x, final int y, final int z, final Block block, final Direction facing, final boolean top) {
		this.set(x, y, z, block.defaultBlockState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
			.setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM));
	}

	/** A slab in the lower half of the block, or the upper one. */
	public void slab(final int x, final int y, final int z, final Block block, final boolean top) {
		this.set(x, y, z, block.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM));
	}

	/** A log, chain or pillar lying along {@code axis}. */
	public void axis(final int x, final int y, final int z, final Block block, final Direction.Axis axis) {
		this.set(x, y, z, block.defaultBlockState().setValue(BlockStateProperties.AXIS, axis));
	}

	/** A lantern hanging from the block above. */
	public void hanging(final int x, final int y, final int z, final Block lantern) {
		this.set(x, y, z, lantern.defaultBlockState().setValue(BlockStateProperties.HANGING, true));
	}

	/** One to four candles, burnt out unless {@code lit}. */
	public void candles(final int x, final int y, final int z, final Block candle, final int count, final boolean lit) {
		this.set(x, y, z, candle.defaultBlockState()
			.setValue(BlockStateProperties.CANDLES, Math.max(1, Math.min(4, count)))
			.setValue(BlockStateProperties.LIT, lit));
	}

	/** Both halves of a door in the doorway at {@code y}, {@code y + 1}. */
	public void door(final int x, final int y, final int z, final Block door, final Direction facing, final boolean open) {
		BlockState lower = door.defaultBlockState()
			.setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
			.setValue(BlockStateProperties.OPEN, open)
			.setValue(BlockStateProperties.DOOR_HINGE, DoorHingeSide.LEFT);
		this.set(x, y, z, lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		this.set(x, y + 1, z, lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
	}

	/** A bed with its head at the position and its foot one block away from {@code facing}. */
	public void bed(final int x, final int y, final int z, final Block bed, final Direction facing) {
		BlockState state = bed.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
		this.set(x, y, z, state.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
		this.set(x - facing.getStepX(), y, z - facing.getStepZ(), state.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
	}

	/** A barrel or chest that fills itself from a loot table the first time it is opened. */
	public void container(final int x, final int y, final int z, final Block block, final Direction direction, final String lootTable) {
		this.facing(x, y, z, block, direction);
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, Mistinn.id(lootTable));
		RandomizableContainer.setBlockEntityLootTable(this.level, this.random, this.at(x, y, z), key);
	}

	// ── The ground ────────────────────────────────────────────────

	/** Height of the ground's top block at a relative column. */
	public int groundY(final int x, final int z) {
		BlockPos pos = this.at(x, 0, z);
		return this.level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ()) - 1;
	}

	/**
	 * Height of the ground at a relative column, relative to the origin: the top solid block,
	 * looking past grass, flowers, leaves and logs. Water counts as ground (see {@link #wet}).
	 */
	public int surface(final int x, final int z) {
		BlockPos column = this.at(x, 0, z);
		int y = this.groundY(x, z);
		for (int i = 0; i < SURFACE_SEARCH; i++) {
			BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
			BlockState state = this.level.getBlockState(pos);
			if (!state.getFluidState().isEmpty()) {
				break;
			}
			if (state.isFaceSturdy(this.level, pos, Direction.UP) && !(state.getBlock() instanceof LeavesBlock) && !state.is(BlockTags.LOGS)) {
				break;
			}
			y--;
		}
		return y - this.origin.getY();
	}

	/** Whether the ground at a relative column is under water. */
	public boolean wet(final int x, final int z) {
		return !this.get(x, this.surface(x, z), z).getFluidState().isEmpty();
	}

	/**
	 * Swaps the ground's top block at a column for {@code block}, if the ground there is dry and
	 * within {@code reach} of the origin's height. Returns the ground's height, or
	 * {@link Integer#MIN_VALUE} if it was left alone.
	 */
	public int cover(final int x, final int z, final Block block, final int reach) {
		int y = this.surface(x, z);
		if (Math.abs(y) > reach || this.wet(x, z)) {
			return Integer.MIN_VALUE;
		}
		this.set(x, y, z, block);
		return y;
	}

	/**
	 * Stands a block on the dry ground at a column, if the ground is within {@code reach} of the
	 * origin's height and the spot is free. Returns the height it was placed at, or
	 * {@link Integer#MIN_VALUE}.
	 */
	public int onGround(final int x, final int z, final BlockState state, final int reach) {
		int y = this.surface(x, z);
		if (Math.abs(y) > reach || this.wet(x, z)) {
			return Integer.MIN_VALUE;
		}
		BlockState there = this.get(x, y + 1, z);
		if (!there.isAir() && !there.canBeReplaced()) {
			return Integer.MIN_VALUE;
		}
		this.set(x, y + 1, z, state);
		return y + 1;
	}

	/**
	 * Joins every fence, bar, wall and stair placed so far to its neighbours, as if each had been
	 * set down by hand. World generation does not do this by itself.
	 */
	public void settle() {
		for (BlockPos pos : this.joints) {
			BlockState state = this.level.getBlockState(pos);
			BlockState joined = Block.updateFromNeighbourShapes(state, this.level, pos);
			if (joined != state && !joined.isAir()) {
				this.level.setBlock(pos, joined, FLAGS);
			}
		}
		this.joints.clear();
	}
}
