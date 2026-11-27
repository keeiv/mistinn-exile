package life.shizune.mistinn.weapon;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.enemy.StaggerService;
import life.shizune.mistinn.stamina.StaminaService;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 格擋: the hunter raises their weapon across the body for a moment. A melee blow from in front,
 * struck at arm's length, that lands in the guard's first few ticks is parried: it is turned aside whole, the attacker
 * rocks back on its heels and some of the stamina comes back. Later in the guard the blow is only
 * blocked, half of it coming through. A great foe's blow is never turned aside whole: a parry
 * takes seven tenths off it and, on a keeper, wears at its balance as a full heavy blow does.
 * The guard wants a weapon in hand, costs stamina and has a cooldown. The client asks; the server
 * keeps the guard's time and shows it to everyone watching.
 */
public final class ParryService {
	/** Ticks the guard is held. */
	public static final int GUARD = 12;
	/** Ticks from raising it in which a blow is parried, not blocked. */
	public static final int PERFECT = 5;
	/** Ticks from raising the guard before it can be raised again. */
	public static final int COOLDOWN = 20;
	/** Stamina the guard costs, and what a parry gives back. */
	public static final float COST = 10.0F;
	public static final float REFUND = 18.0F;
	/** Ticks a parried creature is rocked back. */
	public static final int STAGGER = 30;
	/** Share of a blow that comes through a guard raised too soon, and through a great foe's parry. */
	public static final float BLOCKED = 0.5F;
	public static final float GREAT = 0.3F;
	/** How nearly in front of the hunter the attacker must stand (the cosine of the guard's half-arc). */
	public static final double FRONT = 0.25;
	/** How far apart, body edge to body edge, a blow may be struck from and still be met: arm's length and a lunge. */
	public static final double REACH = 3.0;

	/** What a guard met, for the watchers' animation. */
	public static final int RAISED = 0;
	public static final int PARRIED = 1;
	public static final int BLOCKED_BLOW = 2;

	/** Counts and the last blow met (for tests). */
	public static int perfects;
	public static int blocks;
	public static float lastFull;
	public static float lastTaken;

	/** When each hunter's guard went up, in the server's ticks. */
	private static final Map<UUID, Integer> UP = new HashMap<>();
	/** While a met blow's share is being let through, the guard does not meet it again. */
	private static boolean passing;

	private ParryService() {
	}

	/** Raises the hunter's guard: returns whether it went up. */
	public static boolean raise(final ServerPlayer player) {
		if (!StaminaService.applies(player) || player.isDeadOrDying() || life.shizune.mistinn.team.DownService.isDown(player)
			|| WeaponStyle.of(player.getMainHandItem()) == null) {
			return false;
		}
		if (life.shizune.mistinn.stamina.RollService.dodging(player) || life.shizune.mistinn.skill.SlideService.sliding(player)) {
			return false;
		}
		int now = player.level().getServer().getTickCount();
		Integer up = UP.get(player.getUUID());
		if (up != null && now - up < COOLDOWN || !StaminaService.has(player, COST)) {
			return false;
		}
		StaminaService.spend(player, COST);
		UP.put(player.getUUID(), now);
		// raising the weapon across the body breaks off a heavy blow being charged, and any treatment in hand
		HeavyService.breakCharge(player);
		life.shizune.mistinn.medical.TreatmentService.interrupt(player);
		show(player, RAISED);
		return true;
	}

	/** Whether the hunter's guard is up right now. */
	public static boolean guarding(final Player player) {
		return held(player) >= 0;
	}

	/** Ticks the guard has been up, or -1 when it is down. */
	private static int held(final Player player) {
		Integer up = UP.get(player.getUUID());
		if (up == null || player.level().getServer() == null) {
			return -1;
		}
		int held = player.level().getServer().getTickCount() - up;
		return held < GUARD ? held : -1;
	}

	/** A great foe: one whose blows are never turned aside whole. */
	public static boolean great(final LivingEntity foe) {
		return foe instanceof life.shizune.mistinn.keeper.Keeper || foe instanceof life.shizune.mistinn.rift.RiftHerald
			|| foe instanceof life.shizune.mistinn.terra.CrystalElder || foe instanceof EnderDragon || foe instanceof WitherBoss
			|| foe instanceof Warden || foe instanceof ElderGuardian || foe instanceof life.shizune.mistinn.path.RustApostle;
	}

	/**
	 * A blow reaches a hunter: when their guard meets it, returns what the hurt comes to (the share
	 * that comes through, dealt here); null lets it through as it is.
	 */
	public static @Nullable Boolean meet(final ServerPlayer player, final ServerLevel level, final DamageSource source, final float damage) {
		if (passing || damage <= 0.0F) {
			return null;
		}
		int held = held(player);
		if (held < 0 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !melee(source)) {
			return null;
		}
		// struck by the one who means it, at arm's length: a keeper's wave or charge from across the floor is no blow to meet
		if (!(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker) {
			return null;
		}
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		Vec3 toward = attacker.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		if (toward.length() - (attacker.getBbWidth() + player.getBbWidth()) / 2.0 > REACH) {
			return null;
		}
		if (look.lengthSqr() < 1.0E-6 || toward.lengthSqr() < 1.0E-6 || look.normalize().dot(toward.normalize()) < FRONT) {
			return null;
		}
		boolean perfect = held < PERFECT;
		boolean great = great(attacker);
		float share = perfect ? (great ? GREAT : 0.0F) : BLOCKED;
		lastFull = damage;
		lastTaken = damage * share;
		Vec3 at = player.getEyePosition().add(toward.normalize().scale(0.7)).add(0.0, -0.35, 0.0);
		if (perfect) {
			perfects++;
			life.shizune.mistinn.achievement.AchievementService.grant(player, life.shizune.mistinn.achievement.Achievement.PARRY_PERFECT);
			StaminaService.restore(player, REFUND);
			if (attacker instanceof life.shizune.mistinn.keeper.Keeper keeper) {
				keeper.takeHeavyBlow(level);
			} else if (!great && attacker instanceof Mob mob) {
				life.shizune.mistinn.enemy.WindupService.interrupt(mob);
				StaggerService.stagger(mob, STAGGER, toward.normalize());
			}
			if (!great) {
				HeavyService.shove(attacker, player.getX() - attacker.getX(), player.getZ() - attacker.getZ(), 0.45);
			}
			level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 1.5F);
			level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.9F);
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.15, 0.15, 0.15, 0.35);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.2);
			show(player, PARRIED);
		} else {
			blocks++;
			level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.9F, 0.85F);
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 5, 0.1, 0.1, 0.1, 0.15);
			show(player, BLOCKED_BLOW);
		}
		if (share <= 0.0F) {
			return false;
		}
		passing = true;
		try {
			return player.hurtServer(level, source, damage * share);
		} finally {
			passing = false;
		}
	}

	/** A blow of hand or weapon: a creature's or a hunter's attack, not thorns, a beam, a shot or a blast. */
	private static boolean melee(final DamageSource source) {
		return source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.PLAYER_ATTACK);
	}

	/** Test hook: the hunter's guard went up this many ticks ago. */
	public static void age(final ServerPlayer player, final int ticks) {
		UP.computeIfPresent(player.getUUID(), (id, up) -> up - ticks);
	}

	/** Lowers and forgets the hunter's guard and its cooldown. */
	public static void forget(final ServerPlayer player) {
		UP.remove(player.getUUID());
	}

	private static void show(final ServerPlayer player, final int kind) {
		GuardPayload payload = new GuardPayload(player.getId(), kind);
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(watcher, payload);
		}
		ServerPlayNetworking.send(player, payload);
	}

	public static void register() {
		ServerPlayerEvents.JOIN.register(ParryService::forget);
		ServerPlayerEvents.LEAVE.register(ParryService::forget);
		PayloadTypeRegistry.serverboundPlay().register(RaisePayload.TYPE, RaisePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GuardPayload.TYPE, GuardPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RaisePayload.TYPE, (payload, context) -> raise(context.player()));
	}

	// ── Payloads ──────────────────────────────────────────────────

	/** The client raised its guard. */
	public record RaisePayload() implements CustomPacketPayload {
		public static final Type<RaisePayload> TYPE = new Type<>(Mistinn.id("parry"));
		public static final StreamCodec<ByteBuf, RaisePayload> CODEC = StreamCodec.unit(new RaisePayload());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Shows a hunter's guard to everyone watching: raised, a parry, or a blow blocked. */
	public record GuardPayload(int entity, int kind) implements CustomPacketPayload {
		public static final Type<GuardPayload> TYPE = new Type<>(Mistinn.id("guard"));
		public static final StreamCodec<ByteBuf, GuardPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GuardPayload::entity,
			ByteBufCodecs.VAR_INT, GuardPayload::kind,
			GuardPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
