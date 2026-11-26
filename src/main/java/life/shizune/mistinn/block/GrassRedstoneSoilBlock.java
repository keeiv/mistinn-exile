package life.shizune.mistinn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 草紅石息壤: redstone soil grown over with grass, found here and there on the surface among the
 * grass blocks; red grains show between the blades from above. It ripens and is harvested like the
 * dirt kind, and broken without silk touch it gives the dirt kind. Under snow it whitens as grass does.
 */
public class GrassRedstoneSoilBlock extends RedstoneSoilBlock {
	public static final BooleanProperty SNOWY = BlockStateProperties.SNOWY;

	public GrassRedstoneSoilBlock(final Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState().setValue(SNOWY, false));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(SNOWY);
	}

	@Override
	protected BlockState updateShape(final BlockState state, final LevelReader level, final ScheduledTickAccess ticks, final BlockPos pos,
		final Direction direction, final BlockPos neighbourPos, final BlockState neighbour, final RandomSource random) {
		return direction == Direction.UP ? state.setValue(SNOWY, neighbour.is(BlockTags.SNOW))
			: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	public BlockState getStateForPlacement(final BlockPlaceContext context) {
		return this.defaultBlockState().setValue(SNOWY, context.getLevel().getBlockState(context.getClickedPos().above()).is(BlockTags.SNOW));
	}
}
