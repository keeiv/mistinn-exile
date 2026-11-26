package life.shizune.mistinn.enemy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.body.WoundService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 聽覺與嗅覺: what a hunter does makes noise, and the hostile creatures that hear it come to look.
 * Hearing is not seeing: a creature with nothing to chase walks to where the sound was, looks
 * about, and gives up if it finds nothing; only once it sees the hunter does the vanilla targeting
 * take over. Crouching muffles most of it. A bleeding hunter leaves a scent that carries further.
 */
public final class NoiseService {
	/** The sounds a hunter makes, and how far each carries in blocks. */
	public enum Noise {
		SWING(10.0, true),
		HEAVY(18.0, true),
		ROLL(10.0, true),
		SLIDE(12.0, true),
		SPRINT(12.0, true),
		/** Tearing a kit open, a syringe cap: treatment is never quiet. */
		TREATMENT(16.0, false),
		/** A golden bell calls whatever walks the shore. */
		BELL(24.0, false),
		/** The smell of a bleeding hunter, given off every {@link #SCENT_INTERVAL} ticks. */
		BLOOD(20.0, false);

		public final double radius;
		/** Whether crouching halves how far it carries. */
		public final boolean muffled;

		Noise(final double radius, final boolean muffled) {
			this.radius = radius;
			this.muffled = muffled;
		}
	}

	/** Where a creature heard something, and until when it keeps looking. */
	public record Heard(Vec3 at, long since, long until) {
	}

	/** A hunter makes the same kind of noise at most this often, in ticks. */
	public static final int REPEAT = 10;
	public static final int SCENT_INTERVAL = 40;
	/** Each destroyed part makes the blood smell this much further, in blocks. */
	public static final double SCENT_PER_DEAD_PART = 4.0;
	/** How long a creature keeps looking for what it heard, in ticks. */
	public static final int SEARCH = 160;

	private static final Map<Mob, Heard> HEARD = new WeakHashMap<>();
	private static final Map<UUID, long[]> LAST = new HashMap<>();

	private NoiseService() {
	}

	/** Whether this creature hunts by ear: hostile, not merely provoked, steered by goals, not a boss. */
	public static boolean listens(final Mob mob) {
		return mob instanceof Enemy && !(mob instanceof NeutralMob) && mob instanceof PathfinderMob
			&& !(mob instanceof life.shizune.mistinn.keeper.Keeper) && !(mob instanceof WitherBoss) && !(mob instanceof EnderDragon)
			&& !mob.getGoalSelector().getAvailableGoals().isEmpty();
	}

	/** How far this noise carries from this hunter now. */
	public static double radius(final ServerPlayer hunter, final Noise noise) {
		// a Rust Anchor shore listens harder
		double ears = life.shizune.mistinn.hardship.Ferocity.of(hunter.level()).hearing();
		if (noise == Noise.BLOOD) {
			return (noise.radius + SCENT_PER_DEAD_PART * BodyService.get(hunter).destroyedCount()) * ears;
		}
		return (noise.muffled && hunter.isShiftKeyDown() ? noise.radius / 2.0 : noise.radius) * ears;
	}

	/** The hunter made this noise where they stand. */
	public static void emit(final ServerPlayer hunter, final Noise noise) {
		if (!BodyService.applies(hunter) || hunter.isSpectator() || hunter.isDeadOrDying() || life.shizune.mistinn.team.DownService.isDown(hunter)) {
			return;
		}
		long now = hunter.level().getGameTime();
		long[] last = LAST.computeIfAbsent(hunter.getUUID(), id -> new long[Noise.values().length]);
		if (now - last[noise.ordinal()] < REPEAT && last[noise.ordinal()] != 0L) {
			return;
		}
		last[noise.ordinal()] = now;
		hear(hunter.level(), hunter.position(), radius(hunter, noise));
	}

	/** Every listening creature in range with nothing to chase comes to look. */
	public static int hear(final ServerLevel level, final Vec3 at, final double radius) {
		long now = level.getGameTime();
		int heard = 0;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(radius), m -> m.isAlive() && !m.isNoAi() && listens(m))) {
			if (mob.getTarget() != null || ExecutionService.holdsMob(mob) || mob.position().distanceTo(at) > radius) {
				continue;
			}
			// not exactly the spot: an ear gives a direction and a rough distance
			Vec3 near = at.add((mob.getRandom().nextDouble() - 0.5) * 3.0, 0.0, (mob.getRandom().nextDouble() - 0.5) * 3.0);
			HEARD.put(mob, new Heard(near, now, now + SEARCH));
			heard++;
		}
		return heard;
	}

	/** What this creature is going to look into, or null. */
	public static @Nullable Heard heard(final Mob mob) {
		Heard heard = HEARD.get(mob);
		if (heard != null && mob.level().getGameTime() > heard.until()) {
			HEARD.remove(mob);
			return null;
		}
		return heard;
	}

	public static void forget(final Mob mob) {
		HEARD.remove(mob);
	}

	/** Bleeding hunters give off their scent. */
	public static void tick(final MinecraftServer server) {
		if (server.getTickCount() % SCENT_INTERVAL != 0) {
			return;
		}
		for (ServerPlayer hunter : server.getPlayerList().getPlayers()) {
			if (bleeding(hunter)) {
				hear(hunter.level(), hunter.position(), radius(hunter, Noise.BLOOD));
			}
		}
	}

	public static boolean bleeding(final ServerPlayer hunter) {
		if (!BodyService.applies(hunter) || hunter.isDeadOrDying() || life.shizune.mistinn.team.DownService.isDown(hunter)) {
			return false;
		}
		for (BodyPart part : BodyPart.values()) {
			if (WoundService.drains(hunter, part)) {
				return true;
			}
		}
		return false;
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(NoiseService::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob mob && listens(mob) && mob.getGoalSelector().getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof InvestigateGoal)) {
				// below the attack goals (they run only with a target), above wandering about
				mob.getGoalSelector().addGoal(4, new InvestigateGoal(mob));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LAST.clear();
			HEARD.clear();
		});
	}

	/** Test hook: forgets every noise heard and made. */
	public static void forgetAll() {
		LAST.clear();
		HEARD.clear();
	}
}
