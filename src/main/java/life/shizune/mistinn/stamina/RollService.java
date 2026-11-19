package life.shizune.mistinn.stamina;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Rolls and dives. A roll gives a few ticks in which blows pass through; how far it goes and how
 * long it protects depends on the legs, how it looks on the arms. With both legs destroyed a
 * hunter cannot roll, only throw themselves forward: the dive protects nothing, and the landing,
 * which the legs can no longer take, is shared out over head, torso and arms.
 * Movement is the client's, as in vanilla: the client moves the hunter and asks; the server
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

	public enum Kind {
		ROLL,
		DIVE
	}

	private static final Map<UUID, Integer> DODGE = new HashMap<>();
	private static final Map<UUID, Integer> LANDING = new HashMap<>();
	private static final Map<UUID, Integer> LAST = new HashMap<>();

	private RollService() {
	}

	// ── Rules shared with the client ─────────────────────────────

	/** Both legs destroyed: no roll, only the dive. */
	public static Kind kind(final Body body) {
		return body.destroyed(BodyPart.LEFT_LEG) && body.destroyed(BodyPart.RIGHT_LEG) ? Kind.DIVE : Kind.ROLL;
	}

	/** How much of the legs is left, 0 to 1. */
	public static float legShare(final Body body) {
		return (body.get(BodyPart.LEFT_LEG) + body.get(BodyPart.RIGHT_LEG)) / (2.0F * BodyPart.MAX);
	}

	/** Ticks of dodge a roll gives: ten on whole legs, four on legs nearly gone. */
	public static int dodgeTicks(final Body body) {
		return Math.round(4.0F + 6.0F * legShare(body));
	}

	/** Horizontal speed the roll starts with. */
	public static float rollSpeed(final Body body) {
		return 0.95F * (0.45F + 0.55F * legShare(body));
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
		Kind kind = kind(body);
		if (!StaminaService.has(player, cost(kind))) {
			return;
		}
		StaminaService.spend(player, cost(kind));
		LAST.put(player.getUUID(), now);
		if (kind == Kind.ROLL) {
			DODGE.put(player.getUUID(), dodgeTicks(body));
		} else {
			LANDING.put(player.getUUID(), DIVE_LANDING);
		}
		player.level().playSound(null, player.blockPosition(), kind == Kind.ROLL ? SoundEvents.ARMOR_EQUIP_LEATHER.value() : SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8F, kind == Kind.ROLL ? 1.2F : 0.7F);
		RollAnimPayload anim = new RollAnimPayload(player.getId(), kind.ordinal(), payload.yaw(),
			body.get(BodyPart.LEFT_ARM) / BodyPart.MAX, body.get(BodyPart.RIGHT_ARM) / BodyPart.MAX);
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(watcher, anim);
		}
		ServerPlayNetworking.send(player, anim);
	}

	public static void tick(final MinecraftServer server) {
		DODGE.replaceAll((id, ticks) -> ticks - 1);
		DODGE.values().removeIf(ticks -> ticks <= 0);
		for (Map.Entry<UUID, Integer> entry : Map.copyOf(LANDING).entrySet()) {
			int left = entry.getValue() - 1;
			if (left > 0) {
				LANDING.put(entry.getKey(), left);
				continue;
			}
			LANDING.remove(entry.getKey());
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null && player.isAlive()) {
				player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_SMALL_FALL, SoundSource.PLAYERS, 1.0F, 0.8F);
				BodyService.strainUpper(player, DIVE_IMPACT);
			}
		}
	}

	/** Test hook: performs a roll or dive request as if the client had sent it. */
	public static void request(final ServerPlayer player, final float yaw) {
		LAST.remove(player.getUUID());
		handle(player, new RollPayload(yaw));
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(RollPayload.TYPE, RollPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SwingPayload.TYPE, SwingPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RollAnimPayload.TYPE, RollAnimPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RollPayload.TYPE, (payload, context) -> handle(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(SwingPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (StaminaService.applies(player) && !player.isDeadOrDying()) {
				StaminaService.spend(player, StaminaService.swingCost(player.getMainHandItem()));
			}
		});
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
