package life.shizune.mistinn.team;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 標記信標: the places the team has marked. The world keeps the list (the beacons themselves may
 * stand in chunks no one has loaded), and every hunter is sent it whole, on arrival and whenever it
 * changes, so the minimap and the view can show each beacon however far it is.
 */
public final class MarkerService {
	/** Beacons a world keeps; the oldest goes when one more is planted. */
	public static final int MAX_MARKERS = 64;
	/** The longest name a beacon shows. */
	public static final int MAX_NAME = 32;
	/** A beacon planted by no hunter in particular: lamp amber. */
	public static final int DEFAULT_COLOUR = 0xF0B040;

	/** One beacon: where it stands, its name (empty for none) and its colour. */
	public record Marker(BlockPos pos, Identifier dimension, String name, int colour) {
		public static final Codec<Marker> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Marker::pos),
			Identifier.CODEC.fieldOf("dimension").forGetter(Marker::dimension),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Marker::name),
			Codec.INT.optionalFieldOf("colour", DEFAULT_COLOUR).forGetter(Marker::colour)
		).apply(i, Marker::new));
		public static final StreamCodec<ByteBuf, Marker> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Marker::pos,
			Identifier.STREAM_CODEC, Marker::dimension,
			ByteBufCodecs.stringUtf8(MAX_NAME * 4), Marker::name,
			ByteBufCodecs.INT, Marker::colour,
			Marker::new);

		boolean at(final ServerLevel level, final BlockPos at) {
			return this.pos.equals(at) && this.dimension.equals(level.dimension().identifier());
		}
	}

	public static final AttachmentType<List<Marker>> MARKERS = AttachmentRegistry.<List<Marker>>builder()
		.persistent(Marker.CODEC.listOf())
		.buildAndRegister(Mistinn.id("markers"));

	private MarkerService() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(MarkersPayload.TYPE, MarkersPayload.CODEC);
		ServerPlayerEvents.JOIN.register(player -> send(player, markers(player.level().getServer())));
	}

	public static List<Marker> markers(final MinecraftServer server) {
		return server.overworld().getAttachedOrElse(MARKERS, List.of());
	}

	/** A beacon planted at {@code pos}. */
	public static void placed(final ServerLevel level, final BlockPos pos, final String name, final int colour) {
		List<Marker> next = new ArrayList<>();
		for (Marker marker : markers(level.getServer())) {
			if (!marker.at(level, pos)) {
				next.add(marker);
			}
		}
		next.add(new Marker(pos.immutable(), level.dimension().identifier(), trim(name), colour & 0xFFFFFF));
		while (next.size() > MAX_MARKERS) {
			next.removeFirst();
		}
		store(level.getServer(), next);
	}

	/** The beacon at {@code pos} is gone. */
	public static void removed(final ServerLevel level, final BlockPos pos) {
		List<Marker> before = markers(level.getServer());
		List<Marker> next = before.stream().filter(marker -> !marker.at(level, pos)).toList();
		if (next.size() != before.size()) {
			store(level.getServer(), next);
		}
	}

	/** The beacon at {@code pos} takes a new colour; false when there is none. */
	public static boolean recolour(final ServerLevel level, final BlockPos pos, final int colour) {
		List<Marker> next = new ArrayList<>();
		boolean found = false;
		for (Marker marker : markers(level.getServer())) {
			if (marker.at(level, pos)) {
				next.add(new Marker(marker.pos(), marker.dimension(), marker.name(), colour & 0xFFFFFF));
				found = true;
			} else {
				next.add(marker);
			}
		}
		if (found) {
			store(level.getServer(), next);
		}
		return found;
	}

	private static String trim(final String name) {
		String clean = name.strip();
		return clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME) : clean;
	}

	private static void store(final MinecraftServer server, final List<Marker> markers) {
		server.overworld().setAttached(MARKERS, List.copyOf(markers));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player, markers);
		}
	}

	private static void send(final ServerPlayer player, final List<Marker> markers) {
		if (ServerPlayNetworking.canSend(player, MarkersPayload.TYPE)) {
			ServerPlayNetworking.send(player, new MarkersPayload(markers));
		}
	}

	/** Every beacon of the world. */
	public record MarkersPayload(List<Marker> markers) implements CustomPacketPayload {
		public static final Type<MarkersPayload> TYPE = new Type<>(Mistinn.id("markers"));
		public static final StreamCodec<ByteBuf, MarkersPayload> CODEC = Marker.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MARKERS))
			.map(MarkersPayload::new, MarkersPayload::markers);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
