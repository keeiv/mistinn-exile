package life.shizune.mistinn.block;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.rift.RiftTouched;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 血棘: a low thorny bush that grows where a sky rift has seared the ground. It catches at
 * whatever walks through it and tears at anything that moves inside it; the rift's own creatures
 * pass through untouched.
 */
public class BloodThornBlock extends VegetationBlock {
	private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 12.0);

	public BloodThornBlock(final Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean mayPlaceOn(final BlockState state, final BlockGetter level, final BlockPos pos) {
		return state.is(MistinnBlocks.RIFT_TURF) || state.is(MistinnBlocks.RIFT_SOIL) || state.is(MistinnBlocks.CRIMSON_MOSS) || super.mayPlaceOn(state, level, pos);
	}

	@Override
	protected void entityInside(final BlockState state, final Level level, final BlockPos pos, final Entity entity, final InsideBlockEffectApplier effects,
		final boolean intersects) {
		if (!(entity instanceof LivingEntity) || entity instanceof RiftTouched) {
			return;
		}
		entity.makeStuckInBlock(state, new Vec3(0.85, 0.8, 0.85));
		if (level instanceof ServerLevel server) {
			Vec3 moved = entity.isClientAuthoritative() ? entity.getKnownMovement() : entity.oldPosition().subtract(entity.position());
			if (Math.abs(moved.x) >= 0.003 || Math.abs(moved.z) >= 0.003) {
				entity.hurtServer(server, level.damageSources().sweetBerryBush(), 1.0F);
			}
		}
	}
}
