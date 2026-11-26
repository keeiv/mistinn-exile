package life.shizune.mistinn.tool;

import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** A salt bomb, a flare or a bell lure in flight; what it does where it comes down is {@link ThrowService}'s. */
public class ThrownTool extends ThrowableItemProjectile {
	public ThrownTool(final EntityType<? extends ThrownTool> type, final Level level) {
		super(type, level);
	}

	public ThrownTool(final Level level, final LivingEntity thrower, final ItemStack stack) {
		super(MistinnEntities.THROWN_TOOL, thrower, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return MistinnItems.SALT_BOMB;
	}

	@Override
	protected void onHit(final HitResult hit) {
		super.onHit(hit);
		if (!(this.level() instanceof ServerLevel level) || this.isRemoved()) {
			return;
		}
		// the open block it came down in: against a block, the one in front of the face it struck
		BlockPos air = hit instanceof BlockHitResult block ? block.getBlockPos().relative(block.getDirection()) : BlockPos.containing(hit.getLocation());
		ThrowService.land(level, ThrowService.Kind.of(this.getItem()), hit.getLocation(), air, this, this.getOwner());
		this.discard();
	}
}
