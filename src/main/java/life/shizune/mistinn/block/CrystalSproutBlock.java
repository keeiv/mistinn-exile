package life.shizune.mistinn.block;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 晶苗: the crop the valley people of Terra tend: jade shoots that bead with crystal and, grown, bear
 * a cluster of crystal pods. Four stages; it grows on farmland and on jade turf.
 */
public class CrystalSproutBlock extends CropBlock {
	public static final int MAX = 3;
	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

	public CrystalSproutBlock(final Properties properties) {
		super(properties);
	}

	@Override
	protected IntegerProperty getAgeProperty() {
		return AGE;
	}

	@Override
	public int getMaxAge() {
		return MAX;
	}

	@Override
	protected ItemLike getBaseSeedId() {
		return MistinnItems.CRYSTAL_SPROUT;
	}

	@Override
	protected boolean mayPlaceOn(final BlockState state, final BlockGetter level, final BlockPos pos) {
		return state.is(Blocks.FARMLAND) || state.is(MistinnBlocks.JADE_TURF) || state.is(MistinnBlocks.VEIN_SOIL);
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}
}
