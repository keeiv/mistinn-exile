package life.shizune.mistinn.ledger;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 冊子: what a hunter's own game remembers outside any one world. The server only tells; each
 * hunter's client keeps its own ledger, in the game's folder, across every world it has been in.
 * A world has an id of its own, given the first time it is loaded, so each world is one drop in
 * the ledger however often it is joined, and an event that happens once in a world (an ending, a
 * named tiller falling) is counted once for it.
 */
public final class LedgerService {
	/** The world's own id, given once. */
	public static final AttachmentType<String> WORLD = AttachmentRegistry.<String>builder()
		.persistent(Codec.STRING)
		.buildAndRegister(Mistinn.id("ledger_world"));

	/** What each hunter's ledger says of them (its tone bits), as their client told it: only words depend on it. */
	private static final java.util.Map<UUID, Integer> TONES = new java.util.concurrent.ConcurrentHashMap<>();
	/** Hunters who have heard a tiller know them, in this world. */
	private static final java.util.Set<UUID> KNOWN = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private LedgerService() {
	}

	/** The hunter's tone, as their client told it (0 when it has not). */
	public static int tone(final ServerPlayer player) {
		return TONES.getOrDefault(player.getUUID(), 0);
	}

	/** Whether a tiller has already known this hunter in this world; marks it so. */
	public static boolean knownOnce(final ServerPlayer player) {
		return !KNOWN.add(player.getUUID());
	}

	/** Test hook: the hunter's tone, as if their client had told it. */
	public static void setTone(final ServerPlayer player, final int tone) {
		TONES.put(player.getUUID(), tone & 15);
	}

	/** This world's id, given now if it has none. */
	public static String world(final MinecraftServer server) {
		String id = server.overworld().getAttached(WORLD);
		if (id == null) {
			id = UUID.randomUUID().toString();
			server.overworld().setAttached(WORLD, id);
		}
		return id;
	}

	/** Tells the hunter's ledger that this happened to them here; {@code once}: it counts once for this world. */
	public static void tell(final ServerPlayer player, final String event, final boolean once) {
		if (ServerPlayNetworking.canSend(player, EventPayload.TYPE)) {
			ServerPlayNetworking.send(player, new EventPayload(world(player.level().getServer()), event, once));
		}
	}

	/** Tells every hunter in the world. */
	public static void tellAll(final MinecraftServer server, final String event, final boolean once) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tell(player, event, once);
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(WorldPayload.TYPE, WorldPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(EventPayload.TYPE, EventPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TonePayload.TYPE, TonePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TonePayload.TYPE, (payload, context) -> setTone(context.player(), payload.tone()));
		ServerPlayerEvents.LEAVE.register(player -> TONES.remove(player.getUUID()));
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TONES.clear();
			KNOWN.clear();
		});
		// each world a hunter comes into is a drop in their ledger, of the world's kind
		ServerPlayerEvents.JOIN.register(player -> {
			MinecraftServer server = player.level().getServer();
			if (ServerPlayNetworking.canSend(player, WorldPayload.TYPE)) {
				ServerPlayNetworking.send(player, new WorldPayload(world(server), WorldMode.of(server).id));
			}
		});
		// the hunter falling: the tillers' falls are told by the path itself
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && WorldMode.of(player.level().getServer()) != WorldMode.PATH) {
				tell(player, "death", false);
			}
		});
	}

	/** The client's word of its hunter's leaning (tone bits; only words depend on it). */
	public record TonePayload(int tone) implements CustomPacketPayload {
		public static final Type<TonePayload> TYPE = new Type<>(Mistinn.id("ledger_tone"));
		public static final StreamCodec<ByteBuf, TonePayload> CODEC = ByteBufCodecs.VAR_INT.map(TonePayload::new, TonePayload::tone);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The world the hunter is in: its id and its kind. */
	public record WorldPayload(String world, String mode) implements CustomPacketPayload {
		public static final Type<WorldPayload> TYPE = new Type<>(Mistinn.id("ledger_world"));
		public static final StreamCodec<ByteBuf, WorldPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, WorldPayload::world,
			ByteBufCodecs.STRING_UTF8, WorldPayload::mode,
			WorldPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Something that happened to the hunter in that world. */
	public record EventPayload(String world, String event, boolean once) implements CustomPacketPayload {
		public static final Type<EventPayload> TYPE = new Type<>(Mistinn.id("ledger_event"));
		public static final StreamCodec<ByteBuf, EventPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, EventPayload::world,
			ByteBufCodecs.STRING_UTF8, EventPayload::event,
			ByteBufCodecs.BOOL, EventPayload::once,
			EventPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
