package life.shizune.mistinn.enemy;

import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.hardship.Ferocity;
import life.shizune.mistinn.mixin.MobAttackBoxInvoker;
import life.shizune.mistinn.registry.VanillaCreatureSounds;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Creatures hold a melee blow back for a readable windup, then lunge as they release it. The blow is
 * decided here on the server: it lands only if the hunter is still within reach when it is released.
 * In a Rust Anchor world ({@link Ferocity}) the windup is shorter, some windups are feints (the
 * creature stops short and strikes from a quick second windup) and some blows are held a little past
 * the end of the windup, to catch a hunter who dodged on the beat.
 */
public final class WindupService {
	/** Ticks the released state stays visible to clients for the follow-through pose. */
	public static final int FOLLOW_THROUGH = 5;
	/** Share of its own speed a creature creeps forward with while winding up. */
	public static final float CREEP = 0.30F;
	/** How far, in blocks, the lunge chases a hunter who backed away during the windup. */
	public static final float CHASE_CAP = 1.0F;
	/** Vanilla's horizontal melee reach around the attack box, before the lunge is added. */
	private static final double MELEE_REACH = Math.sqrt(2.04) - 0.6;
	/** Converts a lunge distance to a starting speed, given vanilla ground friction. */
	private static final double LUNGE_SPEED = 0.45;

	/** An attack being wound up: when it began, how long it takes, who it is aimed at, and whether it has been let go. */
	public record Windup(long start, int length, int target, boolean released) {
		public static final StreamCodec<ByteBuf, Windup> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Windup::start,
			ByteBufCodecs.VAR_INT, Windup::length,
			ByteBufCodecs.VAR_INT, Windup::target,
			ByteBufCodecs.BOOL, Windup::released,
			Windup::new
		);
	}

	public static final AttachmentType<Windup> WINDUP = AttachmentRegistry.<Windup>builder()
		.syncWith(Windup.STREAM_CODEC, AttachmentSyncPredicate.all())
		.buildAndRegister(Mistinn.id("windup"));

	/** What a windup turns out to be. */
	public enum Kind {
		/** Released at the end of its windup. */
		PLAIN,
		/** Stops at the end, then a quick second windup carries the real blow. */
		FEINT,
		/** Held a few ticks past the end of its windup. */
		HELD
	}

	private static final Set<Mob> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<Mob, Double> START_DISTANCE = new WeakHashMap<>();
	private static final Set<Mob> FEINTING = Collections.newSetFromMap(new WeakHashMap<>());
	private static boolean releasing;
	private static @Nullable Kind forced;

	private WindupService() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(WindupService::tick);
	}

	/** True while this service lets a wound-up blow through {@code doHurtTarget}. */
	public static boolean releasing() {
		return releasing;
	}

	/** Takes over a melee blow at a hunter: returns true when the blow is now being wound up instead. */
	public static boolean intercept(final Mob mob, final ServerLevel level, final Entity target) {
		if (releasing) {
			return false;
		}
		if (mob.hasAttached(WINDUP)) {
			return true;
		}
		// the one gate for "may this creature strike now": a pack member waiting its turn may not
		if (!PackService.mayStrike(mob)) {
			return true;
		}
		EnemyRig rig = EnemyRig.of(mob.getType());
		if (rig == null || rig.windupTicks() <= 0 || !(target instanceof ServerPlayer hunter) || !BodyService.applies(hunter)
			|| ExecutionService.holdsMob(mob)) {
			return false;
		}
		Ferocity ferocity = Ferocity.of(level);
		shortenBrainCooldown(mob, ferocity);
		int length = ferocity.windupTicks(rig.windupTicks());
		Kind kind = kind(mob, ferocity);
		if (kind == Kind.FEINT) {
			// a feint shows the whole tell as made, so it reads as the real thing
			length = rig.windupTicks();
			FEINTING.add(mob);
		} else if (kind == Kind.HELD) {
			length += 3 + mob.getRandom().nextInt(5);
		}
		mob.setAttached(WINDUP, new Windup(level.getGameTime(), length, hunter.getId(), false));
		START_DISTANCE.put(mob, flatDistance(mob, hunter));
		ACTIVE.add(mob);
		cry(level, mob);
		return true;
	}

	private static Kind kind(final Mob mob, final Ferocity ferocity) {
		Kind kind = forced;
		if (kind != null) {
			return kind;
		}
		if (ferocity.feint() <= 0.0F && ferocity.hold() <= 0.0F) {
			return Kind.PLAIN;
		}
		float roll = mob.getRandom().nextFloat();
		if (roll < ferocity.feint()) {
			return Kind.FEINT;
		}
		return roll < ferocity.feint() + ferocity.hold() ? Kind.HELD : Kind.PLAIN;
	}

	/**
	 * Creatures run by a brain (piglins, hoglins, wardens) set their own wait between blows as they swing, not through
	 * the melee goal: it is cut here, as the blow they began is taken over.
	 */
	private static void shortenBrainCooldown(final Mob mob, final Ferocity ferocity) {
		var brain = mob.getBrain();
		if (ferocity.attackGap() < 1.0F && brain.hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) {
			long left = brain.getTimeUntilExpiry(MemoryModuleType.ATTACK_COOLING_DOWN);
			if (left > 0) {
				brain.setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, Ferocity.gap((int) left, ferocity.attackGap()));
			}
		}
	}

	/** Test hook: every windup from now on is of this kind (null: as the world's ferocity rolls it). */
	public static void force(final @Nullable Kind kind) {
		forced = kind;
	}

	/** Whether this creature's windup is a feint that has not turned into its real blow yet. */
	public static boolean feinting(final Mob mob) {
		return FEINTING.contains(mob);
	}

	private static void cry(final ServerLevel level, final Mob mob) {
		SoundEvent cry = mob instanceof Voiced voiced ? voiced.windupCry() : VanillaCreatureSounds.windup(mob.getType());
		if (cry != null) {
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), cry, SoundSource.HOSTILE, 1.0F, 0.9F + mob.getRandom().nextFloat() * 0.2F);
		}
	}

	/** True while the creature is still winding up (not yet released). */
	public static boolean winding(final Mob mob) {
		Windup windup = mob.getAttached(WINDUP);
		return windup != null && !windup.released();
	}

	/** 打斷: a heavy blow breaks off a blow still being wound up; returns whether one was. */
	public static boolean interrupt(final Mob mob) {
		if (!winding(mob)) {
			return false;
		}
		clear(mob);
		ACTIVE.remove(mob);
		return true;
	}

	/** Runs after the creature's own AI each tick: it plants and creeps, turned toward its target. */
	public static void holdStance(final Mob mob) {
		Windup windup = mob.getAttached(WINDUP);
		if (windup == null || windup.released()) {
			return;
		}
		Entity target = mob.level().getEntity(windup.target());
		mob.getNavigation().stop();
		mob.setXxa(0.0F);
		mob.setZza(CREEP);
		mob.setSpeed((float) mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * CREEP);
		if (target != null) {
			mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
			float yaw = (float) Math.toDegrees(Mth.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX())) - 90.0F;
			mob.setYRot(yaw);
			mob.setYBodyRot(yaw);
			mob.setYHeadRot(yaw);
		}
	}

	private static void tick(final ServerLevel level) {
		for (Iterator<Mob> it = ACTIVE.iterator(); it.hasNext();) {
			Mob mob = it.next();
			if (mob.level() != level) {
				continue;
			}
			Windup windup = mob.getAttached(WINDUP);
			if (windup == null) {
				it.remove();
				continue;
			}
			long elapsed = level.getGameTime() - windup.start();
			if (windup.released()) {
				if (elapsed >= windup.length() + FOLLOW_THROUGH) {
					clear(mob);
					it.remove();
				}
				continue;
			}
			Entity target = level.getEntity(windup.target());
			if (!(target instanceof ServerPlayer hunter) || !valid(mob, hunter)) {
				clear(mob);
				it.remove();
				continue;
			}
			if (elapsed >= windup.length()) {
				if (FEINTING.remove(mob)) {
					// it stops short; the real blow comes from a quick second windup
					EnemyRig rig = EnemyRig.of(mob.getType());
					int quick = Ferocity.of(level).afterFeintTicks(rig == null ? windup.length() : rig.windupTicks());
					mob.setAttached(WINDUP, new Windup(level.getGameTime(), quick, windup.target(), false));
					START_DISTANCE.put(mob, flatDistance(mob, hunter));
					cry(level, mob);
				} else {
					release(level, mob, hunter, windup);
				}
			}
		}
	}

	private static boolean valid(final Mob mob, final ServerPlayer hunter) {
		return mob.isAlive() && !mob.isRemoved() && !mob.isPassenger() && !ExecutionService.holdsMob(mob)
			&& !(mob instanceof Ravager ravager && ravager.getStunnedTick() > 0)
			&& hunter.isAlive() && hunter.level() == mob.level() && BodyService.applies(hunter);
	}

	private static void release(final ServerLevel level, final Mob mob, final ServerPlayer hunter, final Windup windup) {
		EnemyRig rig = EnemyRig.of(mob.getType());
		double retreat = Mth.clamp(flatDistance(mob, hunter) - START_DISTANCE.getOrDefault(mob, 0.0), 0.0, CHASE_CAP * Ferocity.of(level).chase());
		double reach = (rig == null ? 0.0 : rig.lunge()) + retreat;
		Vec3 toward = hunter.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
		Vec3 direction = toward.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(0.0F, mob.getYRot()) : toward.normalize();
		mob.setAttached(WINDUP, new Windup(windup.start(), windup.length(), windup.target(), true));
		mob.swingForAttack(InteractionHand.MAIN_HAND);
		mob.setDeltaMovement(mob.getDeltaMovement().add(direction.scale(reach * LUNGE_SPEED)));
		mob.needsSync = true;
		boolean inReach = ((MobAttackBoxInvoker) mob).mistinn$attackBox(MELEE_REACH + reach).intersects(hunter.getBoundingBox())
			&& EnemyRig.perceives(mob, hunter);
		if (inReach) {
			land(level, mob, hunter);
		}
	}

	/** Lands a melee blow now, as a released windup does: the blow is not held back again. */
	public static boolean land(final ServerLevel level, final Mob mob, final Entity target) {
		releasing = true;
		try {
			return mob.doHurtTarget(level, target);
		} finally {
			releasing = false;
		}
	}

	private static void clear(final Mob mob) {
		mob.removeAttached(WINDUP);
		START_DISTANCE.remove(mob);
		FEINTING.remove(mob);
	}

	private static double flatDistance(final Entity a, final Entity b) {
		return Math.sqrt(Mth.square(a.getX() - b.getX()) + Mth.square(a.getZ() - b.getZ()));
	}
}
