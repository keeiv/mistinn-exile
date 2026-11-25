package life.shizune.mistinn.skin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Passes each player's own skin to everyone else in the world. An offline account has no skin
 * server, so the launcher hands the skin to the game and the game sends it here; the server
 * checks it is a 64×64 PNG, keeps it while the player is online and sends it to every player,
 * including newcomers.
 */
public final class SkinSync {
	/** The largest skin file accepted. */
	public static final int MAX_BYTES = 24 * 1024;
	private static final long MIN_GAP_MS = 2000;
	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
	private static final Map<UUID, Skin> SKINS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

	private SkinSync() {
	}

	/** A player's own skin, sent to the server. */
	public record Upload(boolean slim, byte[] png) implements CustomPacketPayload {
		public static final Type<Upload> TYPE = new Type<>(Mistinn.id("skin_upload"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Upload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Upload::slim,
			ByteBufCodecs.byteArray(MAX_BYTES), Upload::png,
			Upload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A player's skin, sent to everyone. */
	public record Skin(UUID player, boolean slim, byte[] png) implements CustomPacketPayload {
		public static final Type<Skin> TYPE = new Type<>(Mistinn.id("skin"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Skin> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, Skin::player,
			ByteBufCodecs.BOOL, Skin::slim,
			ByteBufCodecs.byteArray(MAX_BYTES), Skin::png,
			Skin::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Registers the packets and the server's side. */
	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Upload.TYPE, Upload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Skin.TYPE, Skin.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Upload.TYPE, (payload, context) -> receive(context.server(), context.player(), payload));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			for (Skin skin : SKINS.values()) {
				if (ServerPlayNetworking.canSend(handler.getPlayer(), Skin.TYPE)) {
					ServerPlayNetworking.send(handler.getPlayer(), skin);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SKINS.remove(handler.getPlayer().getUUID());
			LAST.remove(handler.getPlayer().getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SKINS.clear();
			LAST.clear();
		});
	}

	private static void receive(final MinecraftServer server, final ServerPlayer player, final Upload upload) {
		if (!valid(upload.png())) {
			return;
		}
		long now = System.currentTimeMillis();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < MIN_GAP_MS) {
			return;
		}
		LAST.put(player.getUUID(), now);
		Skin skin = new Skin(player.getUUID(), upload.slim(), upload.png());
		SKINS.put(player.getUUID(), skin);
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(other, Skin.TYPE)) {
				ServerPlayNetworking.send(other, skin);
			}
		}
	}

	/** Whether {@code png} is a small PNG of 64×64 pixels, read from its header without decoding it. */
	public static boolean valid(final byte[] png) {
		if (png.length < 33 || png.length > MAX_BYTES) {
			return false;
		}
		for (int i = 0; i < PNG.length; i++) {
			if (png[i] != PNG[i]) {
				return false;
			}
		}
		boolean header = png[12] == 'I' && png[13] == 'H' && png[14] == 'D' && png[15] == 'R';
		return header && readInt(png, 16) == 64 && readInt(png, 20) == 64;
	}

	private static int readInt(final byte[] data, final int at) {
		return (data[at] & 0xFF) << 24 | (data[at + 1] & 0xFF) << 16 | (data[at + 2] & 0xFF) << 8 | data[at + 3] & 0xFF;
	}
}
