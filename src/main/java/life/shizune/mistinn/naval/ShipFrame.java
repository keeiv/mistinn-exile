package life.shizune.mistinn.naval;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where a ship lies in the world: the block its plan's origin stands on and the quarter turn of its
 * bow ({@link Rotation#NONE} east, then clockwise: south, west, north). Turns positions between the
 * ship's frame and the world (about the middle of the origin block, so blocks land on blocks), checks
 * a stretch of sea is open enough for it, and puts it there.
 */
public record ShipFrame(BlockPos origin, Rotation rotation) {
	/** Which blocks the ship may take the place of: sea, air and what grows in the sea. */
	public static boolean open(final BlockState state) {
		if (state.isAir()) {
			return true;
		}
		return state.getFluidState().is(Fluids.WATER) && (state.is(Blocks.WATER) || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)
			|| state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS) || state.is(Blocks.BUBBLE_COLUMN));
	}

	/** A block of the ship's frame, in the world. */
	public BlockPos world(final BlockPos local) {
		return this.origin.offset(local.rotate(this.rotation));
	}

	/** A point of the ship's frame (block {@code (x, z)} spans x to x+1, z to z+1), in the world. */
	public Vec3 world(final double x, final double y, final double z) {
		double cx = x - 0.5;
		double cz = z - 0.5;
		double wx;
		double wz;
		switch (this.rotation) {
			case CLOCKWISE_90 -> {
				wx = -cz;
				wz = cx;
			}
			case CLOCKWISE_180 -> {
				wx = -cx;
				wz = -cz;
			}
			case COUNTERCLOCKWISE_90 -> {
				wx = cz;
				wz = -cx;
			}
			default -> {
				wx = cx;
				wz = cz;
			}
		}
		return new Vec3(this.origin.getX() + 0.5 + wx, this.origin.getY() + y, this.origin.getZ() + 0.5 + wz);
	}

	/** The middle of a block of the ship's frame at its floor, in the world. */
	public Vec3 centre(final BlockPos local) {
		return this.world(local.getX() + 0.5, local.getY(), local.getZ() + 0.5);
	}

	/** A point in the world, in the ship's frame. */
	public Vec3 local(final Vec3 world) {
		double wx = world.x - this.origin.getX() - 0.5;
		double wz = world.z - this.origin.getZ() - 0.5;
		double cx;
		double cz;
		switch (this.rotation) {
			case CLOCKWISE_90 -> {
				cx = wz;
				cz = -wx;
			}
			case CLOCKWISE_180 -> {
				cx = -wx;
				cz = -wz;
			}
			case COUNTERCLOCKWISE_90 -> {
				cx = -wz;
				cz = wx;
			}
			default -> {
				cx = wx;
				cz = wz;
			}
		}
		return new Vec3(cx + 0.5, world.y - this.origin.getY(), cz + 0.5);
	}

	/** Whether a point in the world is aboard: over the deck, within the bulwarks. */
	public boolean aboard(final Vec3 world) {
		Vec3 local = this.local(world);
		return ShipPlan.aboard(local.x, local.y, local.z);
	}

	/** The way a direction of the ship's frame points in the world. */
	public Direction world(final Direction local) {
		return this.rotation.rotate(local);
	}

	/** The world yaw the bow points along (east is -90). */
	public float yaw() {
		return switch (this.rotation) {
			case CLOCKWISE_90 -> 0.0F;
			case CLOCKWISE_180 -> 90.0F;
			case COUNTERCLOCKWISE_90 -> 180.0F;
			default -> -90.0F;
		};
	}

	/** The box the whole ship takes up, in the world. */
	public AABB box() {
		Vec3 a = this.world(ShipPlan.STERN, ShipPlan.BOTTOM, -ShipPlan.BEAM);
		Vec3 b = this.world(ShipPlan.BOWSPRIT + 1, ShipPlan.TOP + 1, ShipPlan.BEAM + 1);
		return new AABB(a, b);
	}

	/** The middle of the main deck, in the world: where the battle is reckoned from. */
	public Vec3 middle() {
		return this.centre(new BlockPos(0, ShipPlan.DECK + 1, 0));
	}

	/**
	 * Whether the ship fits here: every block it takes is sea or air in the loaded world, and there
	 * is water under the keel, so it floats and wrecks nothing.
	 */
	public boolean fits(final Level level) {
		for (BlockPos local : ShipPlan.blocks().keySet()) {
			BlockPos at = this.world(local);
			if (!level.isLoaded(at) || !open(level.getBlockState(at))) {
				return false;
			}
		}
		for (int x = ShipPlan.STERN; x <= ShipPlan.BOW; x++) {
			BlockPos under = this.world(new BlockPos(x, ShipPlan.BOTTOM - 1, 0));
			if (!level.isLoaded(under) || !level.getFluidState(under).is(Fluids.WATER)) {
				return false;
			}
		}
		return true;
	}

	/** Puts the ship in the world, the captain's chest and the hold's barrels filling from the given loot tables. */
	public void place(final ServerLevel level, final ResourceKey<LootTable> chest, final ResourceKey<LootTable> hold) {
		Map<BlockPos, BlockState> plan = ShipPlan.blocks();
		for (Map.Entry<BlockPos, BlockState> entry : plan.entrySet()) {
			BlockPos at = this.world(entry.getKey());
			BlockState state = entry.getValue().rotate(this.rotation);
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				state = state.setValue(BlockStateProperties.WATERLOGGED, entry.getKey().getY() <= 0);
			}
			level.setBlock(at, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		for (Map.Entry<BlockPos, BlockState> entry : plan.entrySet()) {
			BlockState state = entry.getValue();
			if (state.is(Blocks.CHEST) || state.is(Blocks.BARREL)) {
				RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), this.world(entry.getKey()),
					entry.getKey().equals(ShipPlan.CHEST) ? chest : hold);
			}
		}
	}
}
