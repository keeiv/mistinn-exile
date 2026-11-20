package life.shizune.mistinn.block;

import life.shizune.mistinn.residue.ResidueService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Left where a hunter fell. Its salt has turned into a wisp of residue (殘香) that rises from
 * the stone. Use the stone to recover what it holds.
 */
public class GravestoneBlock extends BaseEntityBlock {
	private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 3, 14, 2, 13), Block.box(4, 2, 6, 12, 13, 10));

	public GravestoneBlock(final Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos, final Player player, final BlockHitResult hitResult) {
		if (player instanceof ServerPlayer hunter) {
			ResidueService.open(hunter, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(final BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(final BlockPos worldPosition, final BlockState blockState) {
		return new GravestoneBlockEntity(worldPosition, blockState);
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		// the residue: a thin, slow wisp that curls upward
		double x = pos.getX() + 0.5 + Math.sin(level.getGameTime() * 0.15) * 0.15;
		double z = pos.getZ() + 0.5 + Math.cos(level.getGameTime() * 0.15) * 0.15;
		level.addParticle(ParticleTypes.WHITE_SMOKE, x, pos.getY() + 0.9, z, 0.0, 0.03, 0.0);
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.WHITE_ASH, x, pos.getY() + 1.2 + random.nextDouble(), z, 0.0, 0.0, 0.0);
		}
	}
}
