package life.shizune.mistinn.enemy;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.medical.TreatmentService;
import life.shizune.mistinn.stamina.RollService;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/** A close-range finisher has one server-owned contact, after a visible opportunity to escape. */
public final class ExecutionService {
	public static final int IMPACT = 18;
	public static final int DURATION = 30;
	public static final int REPEAT_DELAY = 40;
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Mistinn.id("execution"));

	/** A level clock is shared by tracking clients, including clients that start tracking mid-move. */
	public record Sequence(int counterpart, long start, float yaw, boolean striker, boolean completed) {
		public static final StreamCodec<ByteBuf, Sequence> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Sequence::counterpart,
			ByteBufCodecs.VAR_LONG, Sequence::start,
			ByteBufCodecs.FLOAT, Sequence::yaw,
			ByteBufCodecs.BOOL, Sequence::striker,
			ByteBufCodecs.BOOL, Sequence::completed,
			Sequence::new
		);
	}

	public static final AttachmentType<Sequence> SEQUENCE = AttachmentRegistry.<Sequence>builder()
		.syncWith(Sequence.CODEC, AttachmentSyncPredicate.all())
		.buildAndRegister(Mistinn.id("execution_pose"));
	private static final AttachmentType<Long> NEXT_ATTEMPT = AttachmentRegistry.<Long>builder()
		.buildAndRegister(Mistinn.id("execution_next_attempt"));
	/** The blow a finisher took over, between the body service and the end of vanilla's damage step. */
	private static @Nullable ServerPlayer seizedHunter;
	private static @Nullable DamageSource seizedBlow;

	private ExecutionService() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(ExecutionService::tick);
		ServerPlayerEvents.LEAVE.register(player -> clear(player, player.getAttached(SEQUENCE)));
	}

	public static boolean begins(final ServerPlayer player, final DamageSource source) {
		if (!(source.getDirectEntity() instanceof Mob attacker) || source.getEntity() != attacker || !contact(source)) {
			return false;
		}
		long now = player.level().getGameTime();
		EnemyRig rig = EnemyRig.of(attacker.getType());
		if (rig == null || rig.finisher() == null || player.hasAttached(SEQUENCE) || holdsMob(attacker)
			|| now < player.getAttachedOrElse(NEXT_ATTEMPT, Long.MIN_VALUE) || !eligible(player, attacker)) {
			return false;
		}
		double dx = player.getX() - attacker.getX();
		double dz = player.getZ() - attacker.getZ();
		float yaw = dx * dx + dz * dz < 1.0E-5 ? attacker.getYRot() : (float) Math.toDegrees(Mth.atan2(dz, dx)) - 90.0F;
		player.setAttached(SEQUENCE, new Sequence(attacker.getId(), now, Mth.wrapDegrees(yaw + 180.0F), false, false));
		attacker.setAttached(SEQUENCE, new Sequence(player.getId(), now, yaw, true, false));
		player.setAttached(NEXT_ATTEMPT, now + REPEAT_DELAY);
		attacker.getNavigation().stop();
		TreatmentService.interrupt(player);
		seize(player, source);
		return true;
	}

	/** The striker's own blows during the move are held back; contact comes from the move itself. */
	public static boolean holdsBlow(final ServerPlayer player, final DamageSource source) {
		if (source.getDirectEntity() instanceof Mob mob && source.getEntity() == mob && contact(source) && holdsMob(mob)) {
			seize(player, source);
			return true;
		}
		return false;
	}

	private static void seize(final ServerPlayer player, final DamageSource source) {
		seizedHunter = player;
		seizedBlow = source;
	}

	/**
	 * Whether this blow was taken over by a finisher. Vanilla would still flinch the hunter and knock them back
	 * after the damage step, which pushed them out of reach and cancelled the move a few ticks in.
	 */
	public static boolean takeSeized(final ServerPlayer player, final DamageSource source) {
		boolean seized = seizedHunter == player && seizedBlow == source;
		seizedHunter = null;
		seizedBlow = null;
		return seized;
	}

	private static boolean contact(final DamageSource source) {
		return source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
	}

	public static boolean holdsMob(final Mob mob) {
		Sequence sequence = mob.getAttached(SEQUENCE);
		if (sequence == null || !sequence.striker()) {
			return false;
		}
		if (mob.level().getGameTime() - sequence.start() >= DURATION) {
			mob.removeAttached(SEQUENCE);
			return false;
		}
		return true;
	}

	public static boolean finalBlow(final ServerPlayer player, final DamageSource source) {
		Sequence sequence = player.getAttached(SEQUENCE);
		return source.is(DAMAGE) && source.getDirectEntity() instanceof Mob mob && sequence != null
			&& sequence.counterpart() == mob.getId() && downed(BodyService.get(player))
			&& player.level().getGameTime() - sequence.start() == IMPACT;
	}

	private static boolean downed(final Body body) {
		return body.destroyed(BodyPart.LEFT_LEG) && body.destroyed(BodyPart.RIGHT_LEG);
	}

	private static boolean eligible(final ServerPlayer player, final Mob mob) {
		return BodyService.applies(player) && player.isAlive() && !player.isSleeping() && !player.isPassenger()
			&& !player.isVehicle() && !RollService.dodging(player) && player.level() == mob.level()
			&& player.level().getDifficulty() != Difficulty.PEACEFUL && downed(BodyService.get(player))
			&& mob.isAlive() && !mob.isNoAi() && !mob.isPassenger() && !mob.isVehicle() && mob.hurtTime <= 0
			&& mob.canAttack(player) && !mob.isAlliedTo(player) && mob.isWithinMeleeAttackRange(player) && mob.hasLineOfSight(player);
	}

	private static boolean inFront(final ServerPlayer player, final Mob mob, final Sequence sequence) {
		double dx = player.getX() - mob.getX();
		double dz = player.getZ() - mob.getZ();
		float facing = (sequence.yaw() + 180.0F) * Mth.DEG_TO_RAD;
		return dx * dx + dz * dz < 0.16 || -Mth.sin(facing) * dx + Mth.cos(facing) * dz > 0.25 * Math.hypot(dx, dz);
	}

	private static void tick(final ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			Sequence sequence = player.getAttached(SEQUENCE);
			if (sequence == null) {
				continue;
			}
			long elapsed = level.getGameTime() - sequence.start();
			Entity entity = level.getEntity(sequence.counterpart());
			if (player.isDeadOrDying() && sequence.completed()) {
				// Keep the victim's final pose through the mission screen. The attachment is not copied on respawn.
				if (elapsed >= DURATION && entity instanceof Mob mob) {
					clearMob(mob, player.getId(), sequence.start());
				}
				continue;
			}
			if (elapsed > IMPACT || !(entity instanceof Mob mob) || !matching(mob, player.getId(), sequence.start())
				|| !eligible(player, mob) || !inFront(player, mob, sequence)) {
				clear(player, sequence);
				continue;
			}
			if (elapsed == IMPACT) {
				strike(level, player, mob, sequence);
			}
		}
	}

	public static boolean wasExecuted(final @Nullable DamageSource source) {
		return source != null && source.is(DAMAGE);
	}

	private static void strike(final ServerLevel level, final ServerPlayer player, final Mob mob, final Sequence sequence) {
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE), mob);
		if (player.hurtServer(level, source, 128.0F)) {
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 0.66F);
			level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.44, player.getZ(), 9, 0.28, 0.15, 0.28, 0.08);
		}
		if (!player.isAlive() && wasExecuted(player.getLastDamageSource())) {
			player.setAttached(SEQUENCE, new Sequence(sequence.counterpart(), sequence.start(), sequence.yaw(), false, true));
		} else {
			clear(player, sequence);
		}
	}

	private static void clear(final ServerPlayer player, final @Nullable Sequence sequence) {
		if (sequence == null) {
			return;
		}
		if (player.level().getEntity(sequence.counterpart()) instanceof Mob mob) {
			clearMob(mob, player.getId(), sequence.start());
		}
		player.removeAttached(SEQUENCE);
	}

	private static boolean matching(final Mob mob, final int playerId, final long start) {
		Sequence pose = mob.getAttached(SEQUENCE);
		return pose != null && pose.striker() && pose.counterpart() == playerId && pose.start() == start;
	}

	private static void clearMob(final Mob mob, final int playerId, final long start) {
		if (matching(mob, playerId, start)) {
			mob.removeAttached(SEQUENCE);
		}
	}
}
