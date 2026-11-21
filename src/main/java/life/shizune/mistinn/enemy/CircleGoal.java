package life.shizune.mistinn.enemy;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * A pack member waiting its turn: it keeps to its place on the ring round the hunter, facing them,
 * close enough to be seen and heard, never quite out of mind. Its attack goal is held off while
 * this runs (both want to move and look).
 */
public final class CircleGoal extends Goal {
	private final PathfinderMob mob;
	private int repath;

	public CircleGoal(final PathfinderMob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		PackService.Role role = PackService.role(this.mob);
		return role != null && !role.strike() && this.mob.getTarget() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public void start() {
		this.repath = 0;
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity hunter = this.mob.getTarget();
		PackService.Role role = PackService.role(this.mob);
		if (hunter == null || role == null) {
			return;
		}
		this.mob.getLookControl().setLookAt(hunter, 30.0F, 30.0F);
		Vec3 place = hunter.position().add(Math.cos(role.slot()) * PackService.RING, 0.0, Math.sin(role.slot()) * PackService.RING);
		double off = this.mob.position().distanceTo(place);
		if (off < 1.0) {
			this.mob.getNavigation().stop();
			return;
		}
		if (--this.repath <= 0) {
			this.repath = 10;
			// hurry round to a far place, sidle into a near one
			this.mob.getNavigation().moveTo(place.x, place.y, place.z, off > 3.0 ? 1.2 : 0.8);
		}
	}
}
