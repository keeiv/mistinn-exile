package life.shizune.mistinn.station;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Helm over the station's speakers: a short announcement every minute and a half or two, the first
 * one a few seconds after the hunter wakes. Helm is only ever a voice.
 */
public final class StationBroadcast {
	/** The announcements Helm goes round in the day. */
	public static final int LINES = 7;
	/** The two calls of the drop, made only at the drop. */
	public static final int FAREWELL = 7;
	public static final int YOUR_TURN = 8;
	/** Ticks from waking to the first announcement, and the least and most between the rest. */
	public static final int FIRST = 120;
	private static final int GAP = 1800;
	private static final int GAP_SPREAD = 800;
	private static final Map<UUID, Long> NEXT = new HashMap<>();
	private static final Map<UUID, Integer> SAID = new HashMap<>();

	private StationBroadcast() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
	}

	public static String key(final int line) {
		return "training.mistinn.broadcast." + line;
	}

	/** Called every tick for a hunter at the station. */
	public static void tick(final ServerPlayer hunter) {
		long now = hunter.level().getGameTime();
		UUID id = hunter.getUUID();
		Long next = NEXT.get(id);
		if (next == null) {
			NEXT.put(id, now + FIRST);
			return;
		}
		if (now < next) {
			return;
		}
		int said = SAID.getOrDefault(id, 0);
		// the morning call first, then round the rest
		int line = said == 0 ? 0 : 1 + (said - 1) % (LINES - 1);
		SAID.put(id, said + 1);
		NEXT.put(id, now + GAP + hunter.getRandom().nextInt(GAP_SPREAD));
		ServerPlayNetworking.send(hunter, new Payload(line));
	}

	/** Helm says this line now; the day's round waits a while after it. */
	public static void say(final ServerPlayer hunter, final int line) {
		NEXT.put(hunter.getUUID(), hunter.level().getGameTime() + GAP);
		ServerPlayNetworking.send(hunter, new Payload(line));
	}

	/** Test and world-close hook. */
	public static void forgetAll() {
		NEXT.clear();
		SAID.clear();
	}

	/** Server to client: Helm says this line over the speakers. */
	public record Payload(int line) implements CustomPacketPayload {
		public static final Type<Payload> TYPE = new Type<>(Mistinn.id("station_broadcast"));
		public static final StreamCodec<ByteBuf, Payload> CODEC = ByteBufCodecs.VAR_INT.map(Payload::new, Payload::line);

		@Override
		public Type<Payload> type() {
			return TYPE;
		}
	}
}
