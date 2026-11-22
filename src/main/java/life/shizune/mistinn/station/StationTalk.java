package life.shizune.mistinn.station;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.training.TrainingService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Talking with the staff. The server picks what a member opens with (the lesson they lead, or a
 * plain greeting) and which questions a hunter may ask; the client shows the lines, which live in
 * the language files as {@code staff.mistinn.<role>.<topic>.<n>}. A topic that hands something
 * over or moves the course on reports back when the hunter has heard it out.
 */
public final class StationTalk {
	/** How near a hunter must still be for what they heard to count. */
	public static final double REACH = 8.0;
	/** What a member opens with when no lesson of theirs is due. */
	public static final String HELLO = "hello";
	/** The questions each member can always be asked, in menu order. */
	private static final Map<StaffRole, List<String>> TOPICS = Map.ofEntries(
		Map.entry(StaffRole.ROOMMATE, List.of("scared", "before", "came")),
		Map.entry(StaffRole.QUARTERMASTER, List.of("tiers", "cover", "grid", "mask", "station")),
		Map.entry(StaffRole.COOK, List.of("salt", "food", "gossip", "station")),
		Map.entry(StaffRole.SMITH, List.of("branches", "heavy", "special", "scar")),
		Map.entry(StaffRole.INSTRUCTOR, List.of("stamina", "roll", "slide", "windup", "why")),
		Map.entry(StaffRole.SCOUT, List.of("hearing", "smell", "pack", "stare", "outside")),
		Map.entry(StaffRole.DOCTOR, List.of("body", "bleed", "fracture", "meds", "patients")),
		Map.entry(StaffRole.ARCHIVIST, List.of("guide", "stats", "skills", "achievements", "map", "records")),
		Map.entry(StaffRole.BELL_KEEPER, List.of("bell", "death", "wall", "return", "light", "fogbell")),
		Map.entry(StaffRole.RADIO_OPERATOR, List.of("radio", "helm", "static")),
		Map.entry(StaffRole.CRANE_OPERATOR, List.of("pod", "count")));

	private StationTalk() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(OpenPayload.TYPE, OpenPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HeardPayload.TYPE, HeardPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(HeardPayload.TYPE, (payload, context) ->
			heard(context.player(), payload.entity(), payload.topic()));
	}

	public static List<String> topics(final StaffRole role) {
		return TOPICS.getOrDefault(role, List.of());
	}

	/** Opens a talk with this member on the hunter's screen. */
	public static void open(final ServerPlayer hunter, final StationHand hand) {
		TrainingService.greeted(hunter, hand.role());
		String lesson = TrainingService.opening(hunter, hand.role());
		ServerPlayNetworking.send(hunter, new OpenPayload(hand.getId(), hand.role(), lesson == null ? HELLO : lesson, topics(hand.role())));
	}

	/** The hunter heard a topic out: whatever it hands over or unlocks happens now, if it still applies. */
	public static void heard(final ServerPlayer hunter, final int entity, final String topic) {
		if (hunter.level().getEntity(entity) instanceof StationHand hand && hunter.distanceTo(hand) <= REACH) {
			TrainingService.heard(hunter, hand.role(), topic);
		}
	}

	/** Server to client: open a talk with this member. */
	public record OpenPayload(int entity, StaffRole role, String opening, List<String> topics) implements CustomPacketPayload {
		public static final Type<OpenPayload> TYPE = new Type<>(Mistinn.id("staff_talk"));
		public static final StreamCodec<ByteBuf, OpenPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, OpenPayload::entity,
			StaffRole.STREAM_CODEC, OpenPayload::role,
			ByteBufCodecs.STRING_UTF8, OpenPayload::opening,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(16)), OpenPayload::topics,
			OpenPayload::new);

		@Override
		public Type<OpenPayload> type() {
			return TYPE;
		}
	}

	/** Client to server: the hunter heard this topic out. */
	public record HeardPayload(int entity, String topic) implements CustomPacketPayload {
		public static final Type<HeardPayload> TYPE = new Type<>(Mistinn.id("staff_heard"));
		public static final StreamCodec<ByteBuf, HeardPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HeardPayload::entity,
			ByteBufCodecs.stringUtf8(64), HeardPayload::topic,
			HeardPayload::new);

		@Override
		public Type<HeardPayload> type() {
			return TYPE;
		}
	}
}
