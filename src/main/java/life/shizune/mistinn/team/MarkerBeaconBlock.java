package life.shizune.mistinn.team;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 標記信標: a lamp on an iron stake, planted to mark a place. Every hunter sees it on the minimap
 * and in the view, with its name (the item's, given at an anvil) and its colour (the planter's own,
 * or a dye's, right-clicked onto it).
 */
public class MarkerBeaconBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(5.0, 0.0, 5.0, 11.0, 16.0, 11.0);

	public MarkerBeaconBlock(final Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void setPlacedBy(final Level level, final BlockPos pos, final BlockState state, final LivingEntity placer, final ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel server) {
			String name = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : "";
			int colour = placer instanceof Player player ? TeamService.colour(player) : MarkerService.DEFAULT_COLOUR;
			MarkerService.placed(server, pos, name, colour == 0xFFFFFF ? MarkerService.DEFAULT_COLOUR : colour);
		}
	}

	@Override
	protected InteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level, final BlockPos pos, final Player player,
		final InteractionHand hand, final BlockHitResult hit) {
		DyeColor dye = stack.get(DataComponents.DYE);
		if (dye == null) {
			return super.useItemOn(stack, state, level, pos, player, hand, hit);
		}
		if (level instanceof ServerLevel server && MarkerService.recolour(server, pos, dye.getTextColor())) {
			stack.consume(1, player);
			level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void affectNeighborsAfterRemoval(final BlockState state, final ServerLevel level, final BlockPos pos, final boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		MarkerService.removed(level, pos);
	}
}
