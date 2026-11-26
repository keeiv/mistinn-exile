package life.shizune.mistinn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 紅石息壤: earth that breeds redstone. It ripens by itself over a few minutes, red grains pushing up
 * through it; a hand on the ripe soil gathers the redstone, and the soil starts again.
 */
public class RedstoneSoilBlock extends Block {
	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
	public static final int RIPE = 3;
	/** One random tick in this many moves the soil a stage on: about three to four minutes from bare to ripe. */
	public static final int GROW_CHANCE = 2;
	/** Redstone a harvest gives: this many, and up to {@link #EXTRA} more. */
	public static final int YIELD = 2;
	public static final int EXTRA = 1;

	public RedstoneSoilBlock(final Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}

	@Override
	protected boolean isRandomlyTicking(final BlockState state) {
		return state.getValue(AGE) < RIPE;
	}

	@Override
	protected void randomTick(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
		int age = state.getValue(AGE);
		if (age < RIPE && random.nextInt(GROW_CHANCE) == 0) {
			level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit) {
		if (state.getValue(AGE) < RIPE) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide()) {
			popResource(level, pos.above(), new ItemStack(Items.REDSTONE, YIELD + level.getRandom().nextInt(EXTRA + 1)));
			level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_CLIENTS);
			level.playSound(null, pos, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.3F);
		}
		return InteractionResult.SUCCESS;
	}
}
