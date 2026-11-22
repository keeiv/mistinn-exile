package life.shizune.mistinn.stamina;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.medical.TreatmentService;
import life.shizune.mistinn.registry.MistinnSounds;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Rolls and dives. A roll gives a few ticks in which blows pass through; how far it goes and how
 * long it protects depends on the legs, how it looks on the arms. With both legs destroyed a
 * hunter cannot roll, only throw themselves forward: the dive protects nothing, and the landing,
 * which the legs can no longer take, is shared out over head, torso and arms.
 * Under painkillers the dead legs are not felt: the hunter rolls on them anyway, and while any
 * part is destroyed each roll or dive lands {@link TreatmentService#NUMB_STRAIN} on the whole
 * body instead, split evenly. Movement is the client's, as in vanilla: the client moves the hunter and asks; the server
 * charges stamina, grants the dodge and shows the move to everyone watching.
 */
public final class RollService {
	public static final int ROLL_TICKS = 12;
	public static final int DIVE_TICKS = 16;
	public static final int COOLDOWN = 14;
	/** Part health the dive's landing costs, shared over the upper body. */
	public static final float DIVE_IMPACT = 12.0F;
	/** Ticks from the dive to its landing. */
	public static final int DIVE_LANDING = 8;
	/** Ticks from the roll to the moment its weight comes down on the body. */
	public static final int ROLL_LANDING = 6;

	/** The ways a hunter throws themself aside. */
	public enum Kind {
		ROLL,
		DIVE,
		/** 滑鏟: a skill of its own ({@code SlideService}); shares the roll's motion and drawing on the client. */
		SLIDE
	}

	private static final Map<UUID, Integer> DODGE = new HashMap<>();
	/** 落地: ticks left to a landing that costs part health, and how it is shared. */
	private record Landing(int ticks, float amount, boolean whole) {
	}

	private static final Map<UUID, Landing> LANDING = new HashMap<>();
	private static final Map<UUID, Integer> LAST = new HashMap<>();

	private RollService() {
	}

	// ── Rules shared with the client ─────────────────────────────

	/** Both legs destroyed: no roll, only the dive. */
	public static Kind kind(final Body body) {
		return kind(body, false);
	}

	/** As {@link #kind(Body)}; a numb hunter (painkillers) rolls even on two dead legs. */
	public static Kind kind(final Body body, final boolean numb) {
		return !numb && body.destroyed(BodyPart.LEFT_LEG) && body.destroyed(BodyPart.RIGHT_LEG) ? Kind.DIVE : Kind.ROLL;
	}

	/** The hunter's own roll kind, painkillers counted; a broken leg not held by a splint only dives. */
	public static Kind kind(final Player player) {
		if (life.shizune.mistinn.body.WoundService.legBroken(player)) {
			return Kind.DIVE;
		}
		return kind(BodyService.get(player), TreatmentService.numb(player));
	}

	/** How much of the legs is left, 0 to 1. */
	public static float legShare(final Player player, final Body body) {
		return Math.min(1.0F, (body.get(BodyPart.LEFT_LEG) + body.get(BodyPart.RIGHT_LEG)) / (2.0F * BodyService.max(player)));
	}

	/** Ticks of dodge a roll gives: ten on whole legs, four on legs nearly gone; speed adds to it. */
	public static int dodgeTicks(final Player player, final Body body) {
		return Math.round(4.0F + 6.0F * legShare(player, body)) + life.shizune.mistinn.skill.SkillService.rollDodgeBonus(player);
	}

	/** Horizontal speed the roll starts with; speed adds to it. */
	public static float rollSpeed(final Player player, final Body body) {
		return 0.95F * (0.45F + 0.55F * legShare(player, body)) * life.shizune.mistinn.skill.SkillService.rollSpeedScale(player);
	}

	public static float cost(final Kind kind) {
		return kind == Kind.DIVE ? StaminaService.DIVE : StaminaService.ROLL;
	}

	// ── Server ────────────────────────────────────────────────────

	public static boolean dodging(final Player player) {
		return DODGE.getOrDefault(player.getUUID(), 0) > 0;
	}

	private static void handle(final ServerPlayer player, final RollPayload payload) {
		if (!StaminaService.applies(player) || player.isDeadOrDying()) {
			return;
		}
		// the server's clock, not the player's: a respawned player is a new entity counting from zero
		int now = player.level().getServer().getTickCount();
		if (now - LAST.getOrDefault(player.getUUID(), -COOLDOWN) < COOLDOWN) {
			return;
		}
		Body body = BodyService.get(player);
		boolean numb = TreatmentService.numb(player);
		Kind kind = kind(player);
		if (!StaminaService.has(player, cost(kind))) {
			return;
		}
		StaminaService.spend(player, cost(kind));
		LAST.put(player.getUUID(), now);
		// a roll breaks off any treatment in hand, and a heavy blow being charged
		TreatmentService.interrupt(player);
		life.shizune.mistinn.weapon.HeavyService.breakCharge(player);
		life.shizune.mistinn.enemy.NoiseService.emit(player, life.shizune.mistinn.enemy.NoiseService.Noise.ROLL);
		if (kind == Kind.ROLL) {
			DODGE.put(player.getUUID(), dodgeTicks(player, body));
		}
		if (numb && body.destroyedCount() > 0) {
			// the body pays for what it cannot feel, all over and heavier
			LANDING.put(player.getUUID(), new Landing(kind == Kind.ROLL ? ROLL_LANDING : DIVE_LANDING, TreatmentService.NUMB_STRAIN, true));
		} else if (kind == Kind.DIVE) {
			LANDING.put(player.getUUID(), new Landing(DIVE_LANDING, DIVE_IMPACT, false));
		}
		player.level().playSound(null, player.blockPosition(), kind == Kind.ROLL ? MistinnSounds.ROLL : MistinnSounds.DIVE, SoundSource.PLAYERS, 0.8F, 0.95F + player.getRandom().nextFloat() * 0.1F);
		life.shizune.mistinn.voice.Voice.effort(player);
		// a broken arm hangs like a dead one
		RollAnimPayload anim = new RollAnimPayload(player.getId(), kind.ordinal(), payload.yaw(),
			life.shizune.mistinn.body.WoundService.broken(player, BodyPart.LEFT_ARM) ? 0.0F : Math.min(1.0F, body.get(BodyPart.LEFT_ARM) / BodyService.max(player)),
			life.shizune.mistinn.body.WoundService.broken(player, BodyPart.RIGHT_ARM) ? 0.0F : Math.min(1.0F, body.get(BodyPart.RIGHT_ARM) / BodyService.max(player)));
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(watcher, anim);
		}
		ServerPlayNetworking.send(player, anim);
	}

	public static void tick(final MinecraftServer server) {
		DODGE.replaceAll((id, ticks) -> ticks - 1);
		DODGE.values().removeIf(ticks -> ticks <= 0);
		for (Map.Entry<UUID, Landing> entry : Map.copyOf(LANDING).entrySet()) {
			Landing landing = entry.getValue();
			if (landing.ticks() > 1) {
				LANDING.put(entry.getKey(), new Landing(landing.ticks() - 1, landing.amount(), landing.whole()));
				continue;
			}
			LANDING.remove(entry.getKey());
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null && player.isAlive()) {
				player.level().playSound(null, player.blockPosition(), MistinnSounds.LAND, SoundSource.PLAYERS, 0.9F, 0.95F + player.getRandom().nextFloat() * 0.1F);
				if (landing.whole()) {
					BodyService.strainWhole(player, landing.amount());
				} else {
					BodyService.strainUpper(player, landing.amount());
				}
			}
		}
	}

	/** Test hook: performs a roll or dive request as if the client had sent it. */
	public static void request(final ServerPlayer player, final float yaw) {
		LAST.remove(player.getUUID());
		handle(player, new RollPayload(yaw));
	}

	public static void register() {
		// The cooldown is measured in this server's ticks. A new session has a different clock.
		ServerPlayerEvents.JOIN.register(RollService::forget);
		ServerPlayerEvents.LEAVE.register(RollService::forget);
		PayloadTypeRegistry.serverboundPlay().register(RollPayload.TYPE, RollPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SwingPayload.TYPE, SwingPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RollAnimPayload.TYPE, RollAnimPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RollPayload.TYPE, (payload, context) -> handle(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(SwingPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (StaminaService.applies(player) && !player.isDeadOrDying()) {
				StaminaService.spend(player, StaminaService.swingCost(player.getMainHandItem()));
				life.shizune.mistinn.enemy.NoiseService.emit(player, life.shizune.mistinn.enemy.NoiseService.Noise.SWING);
			}
		});
	}

	private static void forget(final ServerPlayer player) {
		UUID id = player.getUUID();
		LAST.remove(id);
		DODGE.remove(id);
		LANDING.remove(id);
	}

	// ── Payloads ──────────────────────────────────────────────────

	/** The client rolled (or dived) towards {@code yaw}. */
	public record RollPayload(float yaw) implements CustomPacketPayload {
		public static final Type<RollPayload> TYPE = new Type<>(Mistinn.id("roll"));
		public static final StreamCodec<ByteBuf, RollPayload> CODEC = ByteBufCodecs.FLOAT.map(RollPayload::new, RollPayload::yaw);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The client swung: pay for it, hit or miss. */
	public record SwingPayload() implements CustomPacketPayload {
		public static final Type<SwingPayload> TYPE = new Type<>(Mistinn.id("swing"));
		public static final StreamCodec<ByteBuf, SwingPayload> CODEC = StreamCodec.unit(new SwingPayload());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Shows a roll or dive to everyone watching, with how whole the roller's arms are. */
	public record RollAnimPayload(int entity, int kind, float yaw, float leftArm, float rightArm) implements CustomPacketPayload {
		public static final Type<RollAnimPayload> TYPE = new Type<>(Mistinn.id("roll_anim"));
		public static final StreamCodec<ByteBuf, RollAnimPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, RollAnimPayload::entity,
			ByteBufCodecs.VAR_INT, RollAnimPayload::kind,
			ByteBufCodecs.FLOAT, RollAnimPayload::yaw,
			ByteBufCodecs.FLOAT, RollAnimPayload::leftArm,
			ByteBufCodecs.FLOAT, RollAnimPayload::rightArm,
			RollAnimPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
