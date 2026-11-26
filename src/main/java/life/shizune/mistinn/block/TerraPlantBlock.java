package life.shizune.mistinn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A low plant of the Terra highlands (jade grass, jade fern): it grows on any earth and on jade turf. */
public class TerraPlantBlock extends VegetationBlock {
	private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 13.0);

	public TerraPlantBlock(final Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}
}
