package life.shizune.mistinn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 導線: a crystal tillers' power cable, laid flat on the deck along its axis (or standing, along
 * a wall), clamped in the middle, a crystal line down its back that lights with the hub.
 */
public class ConduitBlock extends RotatedPillarBlock {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	private static final VoxelShape ALONG_X = Block.box(0.0, 0.0, 4.5, 16.0, 3.5, 11.5);
	private static final VoxelShape ALONG_Z = Block.box(4.5, 0.0, 0.0, 11.5, 3.5, 16.0);
	private static final VoxelShape UPRIGHT = Block.box(4.5, 0.0, 4.5, 11.5, 16.0, 11.5);

	public ConduitBlock(final Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(LIT);
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		Direction.Axis axis = state.getValue(AXIS);
		return axis == Direction.Axis.X ? ALONG_X : axis == Direction.Axis.Z ? ALONG_Z : UPRIGHT;
	}
}
