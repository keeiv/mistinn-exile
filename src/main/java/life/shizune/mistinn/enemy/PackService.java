package life.shizune.mistinn.enemy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import life.shizune.mistinn.body.BodyService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 圍獵: when three or more melee creatures hunt the same hunter, they stop queueing in a line. Two at
 * most go in to strike; the rest spread round the hunter at a distance, watching, and each turn to
 * strike passes to whoever has worked round furthest behind them. Somebody is always coming in, and
 * often from where the hunter is not looking. One or two creatures are left wholly to themselves.
 */
public final class PackService {
	/** At most this many go in to strike at once. */
	public static final int STRIKERS = 2;
	/** How far from the hunter the others keep, in blocks. */
	public static final double RING = 4.5;
	/** Creatures further than this from their hunter are not part of the pack. */
	public static final double RANGE = 16.0;
	/** A striker that has not landed a blow in this many ticks hands its turn on. */
	public static final int TURN = 120;
	/** How often roles are reconsidered, in ticks. */
	public static final int EVERY = 5;

	/** A creature's place in the pack round one hunter; {@code since} is when its turn to strike began. */
	public record Role(UUID hunter, boolean strike, double slot, long since) {
	}

	/**
	 * One creature as the pack sees it: where it stands round the hunter (world angle in radians of
	 * the line from hunter to creature) and how far, whether it is winding up a blow, whether it holds
	 * a turn to strike and whether that turn is spent (a blow released, or the turn ran out).
	 */
	public record Member(int id, double angle, double distance, boolean winding, boolean striking, boolean spent) {
	}

	/** What a member is told: strike, or keep to the ring at this angle. */
	public record Order(int id, boolean strike, double slot) {
	}

	private static final Map<Mob, Role> ROLES = new WeakHashMap<>();

	private PackService() {
	}

	/** Whether this creature may strike its hunter now: true for any creature its pack does not hold back. */
	public static boolean mayStrike(final Mob mob) {
		Role role = ROLES.get(mob);
		return role == null || role.strike();
	}

	public static @Nullable Role role(final Mob mob) {
		return ROLES.get(mob);
	}

	/**
	 * Who strikes and where the rest stand, from where the members are and which way the hunter
	 * faces (pure). Anyone winding up keeps striking; a striker keeps its turn until it is spent;
	 * free turns go to whoever is most behind the hunter, then nearest; a spent striker waits a round.
	 * The rest share the places left on the ring, each taking the free place nearest to it.
	 */
	public static List<Order> assign(final List<Member> members, final double facing) {
		return assign(members, facing, STRIKERS);
	}

	/** {@link #assign(List, double)} with {@code at} strikers at once instead of {@link #STRIKERS}. */
	public static List<Order> assign(final List<Member> members, final double facing, final int at) {
		List<Order> orders = new ArrayList<>();
		if (members.size() <= at) {
			for (Member member : members) {
				orders.add(new Order(member.id(), true, member.angle()));
			}
			return orders;
		}
		Set<Integer> strikers = new HashSet<>();
		for (Member member : members) {
			if (member.winding()) {
				strikers.add(member.id());
			}
		}
		for (Member member : members) {
			if (strikers.size() < at && member.striking() && !member.spent()) {
				strikers.add(member.id());
			}
		}
		List<Member> waiting = new ArrayList<>(members.stream().filter(m -> !strikers.contains(m.id()) && !(m.striking() && m.spent())).toList());
		waiting.sort(Comparator.comparingDouble((Member m) -> -behind(m.angle(), facing)).thenComparingDouble(Member::distance));
		for (Member member : waiting) {
			if (strikers.size() >= at) {
				break;
			}
			strikers.add(member.id());
		}
		List<Double> taken = new ArrayList<>();
		for (Member member : members) {
			if (strikers.contains(member.id())) {
				taken.add(member.angle());
				orders.add(new Order(member.id(), true, member.angle()));
			}
		}
		double step = Mth.TWO_PI / members.size();
		double origin = taken.isEmpty() ? facing : taken.getFirst();
		List<Double> free = new ArrayList<>();
		for (int i = 0; i < members.size(); i++) {
			double place = wrap(origin + i * step);
			boolean near = false;
			for (double angle : taken) {
				near |= Math.abs(wrap(place - angle)) < step / 2.0;
			}
			if (!near) {
				free.add(place);
			}
		}
		for (Member member : members.stream().filter(m -> !strikers.contains(m.id())).sorted(Comparator.comparingDouble(Member::angle)).toList()) {
			double best = member.angle();
			double gap = Double.MAX_VALUE;
			for (double place : free) {
				double d = Math.abs(wrap(place - member.angle()));
				if (d < gap) {
					gap = d;
					best = place;
				}
			}
			free.remove(best);
			orders.add(new Order(member.id(), false, best));
		}
		return orders;
	}

	/** How far round behind the hunter an angle is: 0 straight ahead, 1 straight behind. */
	public static double behind(final double angle, final double facing) {
		return Math.abs(wrap(angle - facing)) / Math.PI;
	}

	static double wrap(final double angle) {
		double a = angle % Mth.TWO_PI;
		if (a > Math.PI) {
			a -= Mth.TWO_PI;
		} else if (a < -Math.PI) {
			a += Mth.TWO_PI;
		}
		return a;
	}

	/** World angle (radians) of the line from the hunter to a point. */
	public static double angle(final Vec3 hunter, final Vec3 point) {
		return Math.atan2(point.z - hunter.z, point.x - hunter.x);
	}

	/** World angle (radians) of the way a hunter faces, on the same scale as {@link #angle}. */
	public static double facing(final float yaw) {
		double rad = Math.toRadians(yaw);
		return Math.atan2(Math.cos(rad), -Math.sin(rad));
	}

	public static void tick(final MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer hunter : server.getPlayerList().getPlayers()) {
			UUID id = hunter.getUUID();
			// a downed hunter is left to whoever can finish them: no turns, no holding back
			if (!BodyService.applies(hunter) || hunter.isDeadOrDying() || ExecutionService.downed(BodyService.get(hunter))) {
				ROLES.entrySet().removeIf(e -> e.getValue().hunter().equals(id));
				continue;
			}
			long now = hunter.level().getGameTime();
			List<Mob> pack = hunter.level().getEntitiesOfClass(Mob.class, hunter.getBoundingBox().inflate(RANGE), m -> belongs(m, hunter));
			ROLES.entrySet().removeIf(e -> e.getValue().hunter().equals(id) && !pack.contains(e.getKey()));
			Vec3 at = hunter.position();
			List<Member> members = new ArrayList<>();
			for (Mob mob : pack) {
				Role role = ROLES.get(mob);
				boolean striking = role != null && role.strike();
				WindupService.Windup windup = mob.getAttached(WindupService.WINDUP);
				boolean released = windup != null && windup.released() && windup.start() >= (role == null ? now : role.since());
				boolean spent = striking && (released || now - role.since() > TURN) && !WindupService.winding(mob);
				members.add(new Member(mob.getId(), angle(at, mob.position()), mob.position().distanceTo(at), WindupService.winding(mob), striking, spent));
			}
			int strikers = life.shizune.mistinn.hardship.Ferocity.of(hunter.level()).strikers();
			for (Order order : assign(members, facing(hunter.getYRot()), strikers)) {
				for (Mob mob : pack) {
					if (mob.getId() == order.id()) {
						Role was = ROLES.get(mob);
						long since = order.strike() && was != null && was.strike() ? was.since() : now;
						ROLES.put(mob, new Role(id, order.strike(), order.slot(), since));
					}
				}
			}
		}
	}

	/** A melee creature hunting this hunter from near enough, not held by a finisher. */
	static boolean belongs(final Mob mob, final ServerPlayer hunter) {
		EnemyRig rig = EnemyRig.of(mob.getType());
		return mob.isAlive() && !mob.isNoAi() && mob.getTarget() == hunter && rig != null && rig.windupTicks() > 0
			&& mob instanceof PathfinderMob && NoiseService.listens(mob) && !ExecutionService.holdsMob(mob)
			&& mob.position().distanceTo(hunter.position()) <= RANGE;
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PackService::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob mob && NoiseService.listens(mob) && EnemyRig.of(mob.getType()) != null
				&& EnemyRig.of(mob.getType()).windupTicks() > 0
				&& mob.getGoalSelector().getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof CircleGoal)) {
				// above the attack goals, which it holds off while the creature waits its turn
				mob.getGoalSelector().addGoal(1, new CircleGoal(mob));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ROLES.clear());
	}

	/** Test hook. */
	public static void forgetAll() {
		ROLES.clear();
	}
}
