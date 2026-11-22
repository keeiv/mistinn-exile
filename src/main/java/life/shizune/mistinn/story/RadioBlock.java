package life.shizune.mistinn.story;

import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 投放站無線電: the radio every intact drop station keeps. It comes burned out; redstone mends it,
 * and then it carries Rust Anchor's calls. In the story it is also where the Rust God's salt
 * is handed in, into the recovery pod it controls.
 */
public class RadioBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public RadioBlock(final Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT);
	}

	@Override
	public BlockState getStateForPlacement(final BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level, final BlockPos pos, final Player player, final InteractionHand hand,
		final BlockHitResult hitResult) {
		if (!(player instanceof ServerPlayer hunter)) {
			return InteractionResult.SUCCESS;
		}
		if (!state.getValue(LIT)) {
			if (!stack.is(Items.REDSTONE) || stack.getCount() < StoryStep.RADIO_PARTS) {
				hunter.sendOverlayMessage(Component.translatable("message.mistinn.radio.broken", StoryStep.RADIO_PARTS));
				return InteractionResult.SUCCESS;
			}
			stack.consume(StoryStep.RADIO_PARTS, hunter);
			level.setBlockAndUpdate(pos, state.setValue(LIT, true));
			level.playSound(null, pos, MistinnSounds.RADIO_TUNE, SoundSource.BLOCKS, 0.9F, 1.0F);
			if (!StoryService.radioMended(hunter)) {
				hunter.sendOverlayMessage(Component.translatable("message.mistinn.radio.static"));
			}
			return InteractionResult.SUCCESS;
		}
		if (StoryService.radioMended(hunter)) {
			return InteractionResult.SUCCESS;
		}
		if (stack.is(MistinnItems.RUST_GOD_SALT)
			&& (StoryService.deliver(hunter) || life.shizune.mistinn.training.TrainingService.deliver(hunter, pos))) {
			stack.consume(1, hunter);
			level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.6F);
			return InteractionResult.SUCCESS;
		}
		hunter.sendOverlayMessage(Component.translatable("message.mistinn.radio.static"));
		level.playSound(null, pos, MistinnSounds.RADIO_STATIC, SoundSource.BLOCKS, 0.8F, 0.95F + level.getRandom().nextFloat() * 0.1F);
		return InteractionResult.SUCCESS;
	}
}
