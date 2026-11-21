package life.shizune.mistinn.enemy;

import java.util.EnumSet;
import life.shizune.mistinn.body.BodyService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * 被注視時靜止: the walking dead come for a hunter from afar only while the hunter is not looking.
 * Watched, they stop where they are and stare back; the moment the hunter turns away they come on,
 * faster than they walk. A stare holds one for {@link #HOLD} ticks at most; after that it comes on
 * anyway, looked at or not, so it cannot be kept off by watching it. Close in, they hunt as before.
 */
public final class StalkGoal extends Goal {
	/** Nearer than this the creature hunts as vanilla does; further than {@link #FAR} it does not stalk. */
	public static final double NEAR = 12.0;
	public static final double FAR = 32.0;
	/** The longest a stare holds the creature, in ticks. */
	public static final int HOLD = 60;
	/** How wide the hunter's gaze is, in degrees either side of where they look. */
	public static final double GAZE = 30.0;
	/** Unwatched, it comes on this much faster than its walk. */
	public static final double HURRY = 1.25;

	private final PathfinderMob mob;
	private int stared;
	private int repath;

	/** The walking dead (zombies, husks, drowned, zombie villagers) stalk; nothing else does. */
	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Zombie mob && NoiseService.listens(mob)
				&& mob.getGoalSelector().getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof StalkGoal)) {
				// above the pack's circling and the attack goals: from afar, the stare decides
				mob.getGoalSelector().addGoal(0, new StalkGoal(mob));
			}
		});
	}

	public StalkGoal(final PathfinderMob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	/** Whether the hunter is looking at the creature: within their gaze, and nothing between. */
	public static boolean watched(final ServerPlayer hunter, final PathfinderMob mob) {
		Vec3 eye = hunter.getEyePosition();
		Vec3 to = mob.getEyePosition().subtract(eye).normalize();
		return hunter.getLookAngle().dot(to) >= Math.cos(Math.toRadians(GAZE)) && hunter.hasLineOfSight(mob);
	}

	private ServerPlayer hunter() {
		return this.mob.getTarget() instanceof ServerPlayer hunter && BodyService.applies(hunter) && hunter.isAlive() ? hunter : null;
	}

	@Override
	public boolean canUse() {
		ServerPlayer hunter = this.hunter();
		if (hunter == null) {
			return false;
		}
		double distance = this.mob.distanceTo(hunter);
		return distance > NEAR && distance <= FAR;
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public void start() {
		this.stared = 0;
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
		ServerPlayer hunter = this.hunter();
		if (hunter == null) {
			return;
		}
		this.mob.getLookControl().setLookAt(hunter, 30.0F, 30.0F);
		if (watched(hunter, this.mob) && this.stared < HOLD) {
			// caught: stand stock still and stare back
			this.stared++;
			this.mob.getNavigation().stop();
			this.repath = 0;
			return;
		}
		if (!watched(hunter, this.mob)) {
			this.stared = 0;
		}
		if (--this.repath <= 0) {
			this.repath = 10;
			this.mob.getNavigation().moveTo(hunter, HURRY);
		}
	}
}
