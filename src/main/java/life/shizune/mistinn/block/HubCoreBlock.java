package life.shizune.mistinn.block;

import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.Settlements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 樞紐核心: the heart of a crystal tillers' settlement, a machine around a sleeping crystal. Given
 * crystal cores enough, the crystal wakes and the settlement becomes the hunters' homestead.
 * Like everything the tillers build, it cannot be broken or made.
 */
public class HubCoreBlock extends Block {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public HubCoreBlock(final Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	protected InteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level, final BlockPos pos, final Player player,
		final InteractionHand hand, final BlockHitResult hit) {
		if (!stack.is(MistinnItems.CRYSTAL_CORE)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (state.getValue(LIT)) {
			// a homestead's hub given more cores grows a step
			if (level instanceof ServerLevel server && player instanceof ServerPlayer hunter) {
				return life.shizune.mistinn.terra.HomesteadUpgrades.offer(server, pos, hunter, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
			}
			return InteractionResult.SUCCESS;
		}
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer hunter)) {
			return InteractionResult.SUCCESS;
		}
		if (stack.getCount() < Settlements.CORES && !player.isCreative()) {
			hunter.sendSystemMessage(Component.translatable("message.mistinn.hub.cores", Settlements.CORES), true);
			return InteractionResult.FAIL;
		}
		if (!Settlements.light(server, pos, hunter)) {
			return InteractionResult.FAIL;
		}
		if (!player.isCreative()) {
			stack.shrink(Settlements.CORES);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer hunter) {
			if (state.getValue(LIT)) {
				// the grit the fields have sent, if any; else what it is
				if (!life.shizune.mistinn.terra.HomesteadUpgrades.collect(server, pos, hunter)) {
					hunter.sendSystemMessage(Component.translatable("message.mistinn.hub.home").append(" ")
						.append(life.shizune.mistinn.terra.HomesteadUpgrades.describe(server.getServer(), pos)).withStyle(ChatFormatting.GRAY));
				}
			} else {
				hunter.sendSystemMessage(Component.translatable("message.mistinn.hub.asleep", Settlements.CORES).withStyle(ChatFormatting.GRAY));
			}
		}
		return InteractionResult.SUCCESS;
	}
}
