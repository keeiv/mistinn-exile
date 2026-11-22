package life.shizune.mistinn.block;

import java.util.List;
import life.shizune.mistinn.bell.BellBlockEntity;
import life.shizune.mistinn.bell.BellService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 金鐘 and 一次性金鐘: rest here to be brought back here when you fall. A golden bell halfway up a
 * lighthouse has let its little lamp go out: it will not take a hunter until lamp oil lights it again.
 */
public class GoldenBellBlock extends BaseEntityBlock {
	/** Whether the bell's lamp burns: an unlit bell takes no one until oil is brought to it. */
	public static final BooleanProperty OILED = BooleanProperty.create("oiled");

	private static final VoxelShape SHAPE = Shapes.or(
		Block.box(2, 0, 2, 14, 2, 14),
		Block.box(4, 3, 4, 12, 5, 12),
		Block.box(5, 5, 5, 11, 11, 11),
		Block.box(6, 11, 6, 10, 15, 10)
	);

	private final boolean oneTime;

	public GoldenBellBlock(final Properties properties, final boolean oneTime) {
		super(properties);
		this.oneTime = oneTime;
		this.registerDefaultState(this.stateDefinition.any().setValue(OILED, true));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(OILED);
	}

	public boolean isOneTime() {
		return this.oneTime;
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	/** Lamp oil brought to an unlit bell lights it, and the hunter who lit it rests there. */
	@Override
	protected InteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level, final BlockPos pos, final Player player,
		final net.minecraft.world.InteractionHand hand, final BlockHitResult hitResult) {
		if (state.getValue(OILED) || !stack.is(life.shizune.mistinn.registry.MistinnItems.LAMP_OIL)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (player instanceof ServerPlayer hunter && level instanceof net.minecraft.server.level.ServerLevel server) {
			stack.consume(1, hunter);
			server.setBlockAndUpdate(pos, state.setValue(OILED, true));
			server.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.2F);
			server.playSound(null, pos, net.minecraft.sounds.SoundEvents.BELL_RESONATE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
			server.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 12, 0.2, 0.2, 0.2, 0.01);
			BellService.rest(hunter, pos);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
		if (!state.getValue(OILED)) {
			if (player instanceof ServerPlayer hunter) {
				hunter.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.mistinn.bell.unlit"), true);
			}
			return InteractionResult.CONSUME;
		}
		if (player instanceof ServerPlayer serverPlayer && !BellService.rest(serverPlayer, pos)) {
			return InteractionResult.CONSUME;
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	protected RenderShape getRenderShape(final BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public BellBlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new BellBlockEntity(pos, state);
	}

	@Override
	protected List<ItemStack> getDrops(final BlockState state, final LootParams.Builder params) {
		if (this.oneTime && params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof BellBlockEntity bell && bell.claimed()) {
			return List.of();
		}
		return super.getDrops(state, params);
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		if (!state.getValue(OILED)) {
			// a thread of smoke from the dead lamp
			if (random.nextInt(6) == 0) {
				level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 0.0, 0.02, 0.0);
			}
			return;
		}
		if (random.nextInt(this.oneTime ? 18 : 3) == 0) {
			double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
			double y = pos.getY() + 0.4 + random.nextDouble() * 0.6;
			double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
			level.addParticle(this.oneTime ? ParticleTypes.WAX_ON : ParticleTypes.END_ROD, x, y, z, 0.0, 0.01, 0.0);
		}
	}
}
