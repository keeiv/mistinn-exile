package life.shizune.mistinn.block;

import life.shizune.mistinn.bell.BellService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 金鐘 and 一次性金鐘: rest here to be brought back here when you fall. */
public class GoldenBellBlock extends Block {
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
	}

	public boolean isOneTime() {
		return this.oneTime;
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
		if (player instanceof ServerPlayer serverPlayer) {
			BellService.rest(serverPlayer, pos, this.oneTime);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		if (random.nextInt(this.oneTime ? 6 : 3) == 0) {
			double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
			double y = pos.getY() + 0.4 + random.nextDouble() * 0.6;
			double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
			level.addParticle(this.oneTime ? ParticleTypes.WAX_ON : ParticleTypes.END_ROD, x, y, z, 0.0, 0.01, 0.0);
		}
	}
}
