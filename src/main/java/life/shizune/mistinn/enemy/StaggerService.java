package life.shizune.mistinn.enemy;

import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

/**
 * 踉蹌: a fully charged heavy blow rocks an ordinary creature back on its heels. For a moment it
 * cannot strike or start winding up a blow, and it stands where it was knocked; the hunter has
 * earned that moment with a long, costly charge. Keepers keep their feet (they have their own
 * balance to break), and so do the great bosses.
 */
public final class StaggerService {
	/** How long a creature is rocked back: 0.8 seconds. */
	public static final int TICKS = 16;

	/**
	 * A stagger: when it began, how long it lasts, and which way the blow rocked the creature, from
	 * -1 (toward its left) to 1 (toward its right).
	 */
	public record Stagger(long start, int length, float side) {
		public static final StreamCodec<ByteBuf, Stagger> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Stagger::start,
			ByteBufCodecs.VAR_INT, Stagger::length,
			ByteBufCodecs.FLOAT, Stagger::side,
			Stagger::new
		);
	}

	public static final AttachmentType<Stagger> STAGGER = AttachmentRegistry.<Stagger>builder()
		.syncWith(Stagger.STREAM_CODEC, AttachmentSyncPredicate.all())
		.buildAndRegister(Mistinn.id("stagger"));

	private static final Set<Mob> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

	private StaggerService() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(StaggerService::tick);
	}

	/** Whether a heavy blow can rock this creature: one with a rig, that is no keeper or great boss. */
	public static boolean staggers(final Mob mob) {
		return EnemyRig.of(mob.getType()) != null && !(mob instanceof life.shizune.mistinn.keeper.Snuffer)
			&& !(mob instanceof EnderDragon) && !(mob instanceof WitherBoss);
	}

	/**
	 * Rocks the creature back for {@code ticks}, away from a blow travelling along {@code blow}
	 * (flat): leaning toward whichever of its sides the blow pushes it. A creature already rocked
	 * is rocked afresh.
	 */
	public static void stagger(final Mob mob, final int ticks, final Vec3 blow) {
		if (!staggers(mob) || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		float yaw = mob.getYRot() * Mth.DEG_TO_RAD;
		// the creature's right, as it faces: yaw 0 faces south, and its right is to the west
		double side = Mth.clamp(blow.x * -Mth.cos(yaw) + blow.z * -Mth.sin(yaw), -1.0, 1.0);
		mob.setAttached(STAGGER, new Stagger(level.getGameTime(), ticks, (float) side));
		mob.getNavigation().stop();
		ACTIVE.add(mob);
	}

	/** Whether the creature is rocked back right now. */
	public static boolean staggered(final Mob mob) {
		return remaining(mob) > 0;
	}

	/** Ticks of the stagger still to come, or 0. */
	public static int remaining(final Mob mob) {
		Stagger stagger = mob.getAttached(STAGGER);
		if (stagger == null) {
			return 0;
		}
		return (int) Math.max(0L, stagger.start() + stagger.length() - mob.level().getGameTime());
	}

	/** Runs after the creature's own AI each tick: a rocked creature goes nowhere. */
	public static void holdStance(final Mob mob) {
		if (!staggered(mob)) {
			return;
		}
		mob.getNavigation().stop();
		mob.setXxa(0.0F);
		mob.setZza(0.0F);
		mob.setSpeed(0.0F);
		mob.setJumping(false);
	}

	private static void tick(final ServerLevel level) {
		for (Iterator<Mob> it = ACTIVE.iterator(); it.hasNext();) {
			Mob mob = it.next();
			if (mob.level() != level) {
				continue;
			}
			if (mob.isRemoved() || !staggered(mob)) {
				mob.removeAttached(STAGGER);
				it.remove();
			}
		}
	}
}
