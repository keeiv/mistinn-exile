package life.shizune.mistinn.enemy;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.WeakHashMap;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.registry.MistinnEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 退避: a creature with its wits about it (a spider, a vindicator, a rift walker), badly hurt by a
 * hunter, breaks off. It backs away crying out, then waits, watching, for others to come before
 * it goes back in, or goes back alone when none come. Once in its life; never when it is winding
 * up a blow, rocked back, held by a finisher, swimming or riding, and never when there is nowhere
 * to go: cornered, it fights. Creatures that keep their distance anyway (archers, crossbowmen)
 * and those that fight by their own rules (the hound pack, the lizards, the pirates on their
 * deck) are not among them; nor are the walking dead.
 */
public final class RetreatGoal extends Goal {
	/** Below this share of its health a minded creature breaks off. */
	public static final float BREAK = 0.35F;
	/** Ticks it spends getting away, and the most it then waits for others. */
	public static final int FLEE = 60;
	public static final int WAIT = 100;
	/** How near another of the hunt must come for it to go back in. */
	public static final double TOGETHER = 6.0;
	/** Breaks-off so far (for tests). */
	public static int retreats;

	private static final Set<EntityType<?>> MINDED = Set.of(EntityTypes.SPIDER, EntityTypes.CAVE_SPIDER, EntityTypes.VINDICATOR, MistinnEntities.RIFT_WALKER);
	private static final Set<Mob> RETREATING = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Set<Mob> SPENT = Collections.newSetFromMap(new WeakHashMap<>());

	private final PathfinderMob mob;
	private @Nullable ServerPlayer from;
	private @Nullable Vec3 away;
	private int ticks;
	private boolean waiting;

	public RetreatGoal(final PathfinderMob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.MOVE));
	}

	/** Whether this kind of creature breaks off when badly hurt. */
	public static boolean minded(final Mob mob) {
		return MINDED.contains(mob.getType());
	}

	/** Whether the creature is breaking off now. */
	public static boolean retreating(final Mob mob) {
		return RETREATING.contains(mob);
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob mob && minded(mob)
				&& mob.getGoalSelector().getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof RetreatGoal)) {
				// above the attack goals and the pack's circling: breaking off outranks pressing in
				mob.getGoalSelector().addGoal(0, new RetreatGoal(mob));
			}
		});
	}

	@Override
	public boolean canUse() {
		if (SPENT.contains(this.mob) || !(this.mob.getTarget() instanceof ServerPlayer hunter) || !BodyService.applies(hunter) || !hunter.isAlive()) {
			return false;
		}
		if (this.mob.getHealth() > this.mob.getMaxHealth() * BREAK || this.mob.isPassenger() || this.mob.isInWater() || WindupService.winding(this.mob)
			|| StaggerService.staggered(this.mob) || ExecutionService.holdsMob(this.mob)) {
			return false;
		}
		Vec3 to = DefaultRandomPos.getPosAway(this.mob, 12, 6, hunter.position());
		if (to == null || to.distanceTo(hunter.position()) <= this.mob.position().distanceTo(hunter.position())) {
			// nowhere further from it: cornered, it fights
			return false;
		}
		this.from = hunter;
		this.away = to;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return this.ticks < FLEE + WAIT && this.from != null && this.from.isAlive() && this.mob.getTarget() == this.from;
	}

	@Override
	public void start() {
		SPENT.add(this.mob);
		RETREATING.add(this.mob);
		retreats++;
		this.ticks = 0;
		this.waiting = false;
		this.mob.getNavigation().moveTo(this.away.x, this.away.y, this.away.z, 1.3);
		// a cry for help as it goes
		CryService.cry(this.mob);
	}

	@Override
	public void stop() {
		RETREATING.remove(this.mob);
		this.mob.getNavigation().stop();
		this.from = null;
		this.away = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		this.ticks++;
		if (this.from == null) {
			return;
		}
		if (!this.waiting) {
			if (this.mob.getNavigation().isDone() || this.ticks >= FLEE) {
				this.waiting = true;
				this.mob.getNavigation().stop();
				this.ticks = Math.max(this.ticks, FLEE);
			}
			return;
		}
		this.mob.getLookControl().setLookAt(this.from, 30.0F, 30.0F);
		// once another of the hunt has come up beside it, they go in together
		boolean joined = !this.mob.level().getEntitiesOfClass(Mob.class, new AABB(this.mob.blockPosition()).inflate(TOGETHER),
			other -> other != this.mob && other.isAlive() && other.getTarget() == this.from).isEmpty();
		if (joined) {
			this.ticks = FLEE + WAIT;
		}
	}
}
