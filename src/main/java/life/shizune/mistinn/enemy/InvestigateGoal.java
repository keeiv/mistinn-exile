package life.shizune.mistinn.enemy;

import java.util.EnumSet;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A creature with nothing to chase goes to look into something it heard: it freezes and turns
 * its head toward the sound, then walks there and looks about. Seeing the hunter on the way hands
 * it to the vanilla targeting, which ends this goal.
 */
public final class InvestigateGoal extends Goal {
	/** Ticks the creature stands still, head turned to the sound, before it moves. */
	public static final int LISTEN = 10;
	/** Ticks it looks about once it gets there. */
	public static final int LOOK_ABOUT = 40;
	private static final double ARRIVED = 1.6;

	private final PathfinderMob mob;
	private NoiseService.@Nullable Heard heard;
	private int listen;
	private int lookAbout;
	private int repath;

	public InvestigateGoal(final PathfinderMob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return this.mob.getTarget() == null && NoiseService.heard(this.mob) != null;
	}

	@Override
	public boolean canContinueToUse() {
		return this.mob.getTarget() == null && NoiseService.heard(this.mob) != null && this.lookAbout < LOOK_ABOUT;
	}

	@Override
	public void start() {
		this.heard = NoiseService.heard(this.mob);
		this.listen = LISTEN;
		this.lookAbout = 0;
		this.repath = 0;
		this.mob.getNavigation().stop();
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		NoiseService.forget(this.mob);
		this.heard = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		NoiseService.Heard now = NoiseService.heard(this.mob);
		if (now != null && now != this.heard) {
			// a newer sound: listen again, then go there instead
			this.heard = now;
			this.listen = LISTEN / 2;
			this.lookAbout = 0;
		}
		if (this.heard == null) {
			return;
		}
		Vec3 at = this.heard.at();
		if (this.listen > 0) {
			this.listen--;
			this.mob.getNavigation().stop();
			this.mob.getLookControl().setLookAt(at.x, at.y + 1.0, at.z);
			return;
		}
		if (this.mob.position().distanceTo(at) > ARRIVED) {
			if (--this.repath <= 0) {
				this.repath = 20;
				this.mob.getNavigation().moveTo(at.x, at.y, at.z, 1.0);
			}
			this.mob.getLookControl().setLookAt(at.x, this.mob.getEyeY(), at.z);
			return;
		}
		// there: nothing in sight; turn about slowly, then give up
		this.mob.getNavigation().stop();
		this.lookAbout++;
		float yaw = this.mob.getYRot() + 4.0F * (this.lookAbout < LOOK_ABOUT / 2 ? 1 : -1);
		Vec3 way = Vec3.directionFromRotation(0.0F, yaw);
		this.mob.getLookControl().setLookAt(this.mob.getX() + way.x * 4.0, this.mob.getEyeY(), this.mob.getZ() + way.z * 4.0);
	}
}
