package life.shizune.mistinn.world;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;

/**
 * 霧岸: the mist that closes in around the dead lighthouses. Every lighthouse is remembered where
 * it is built (by world generation or for the story), and each hunter in the overworld is told of
 * the ones near them; the client thickens the mist from {@link #REACH} blocks out, until a hunter
 * at the tower's foot sees little further than a stone's throw, and their map less.
 */
public final class ShoreMist {
	/** How far from a lighthouse the mist begins, and how close in it is at its thickest, in blocks. */
	public static final double REACH = 96.0;
	public static final double THICKEST = 40.0;
	/** How far a hunter is told of lighthouses: past the mist's reach, so it is known before it is entered. */
	private static final double TOLD = 192.0;
	private static final int EVERY = 40;

	/** The lighthouses of a world: the centre of each lamp room's floor. */
	public static final AttachmentType<List<BlockPos>> LIGHTHOUSES = AttachmentRegistry.<List<BlockPos>>builder()
		.persistent(BlockPos.CODEC.listOf().xmap(ArrayList::new, list -> list))
		.buildAndRegister(Mistinn.id("lighthouses"));

	private static final Map<UUID, List<BlockPos>> TOLD_TO = new HashMap<>();

	private ShoreMist() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(MistPayload.TYPE, MistPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(ShoreMist::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TOLD_TO.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> TOLD_TO.clear());
	}

	/**
	 * Remembers a lighthouse built at {@code centre} of the overworld. Built during world generation
	 * off the server thread, it is written down on the server thread.
	 */
	public static void remember(final WorldGenLevel level, final BlockPos centre) {
		ServerLevel server = level.getLevel();
		if (!server.dimension().equals(Level.OVERWORLD)) {
			return;
		}
		BlockPos at = centre.immutable();
		server.getServer().execute(() -> {
			List<BlockPos> known = new ArrayList<>(server.getAttachedOrElse(LIGHTHOUSES, List.of()));
			if (!known.contains(at)) {
				known.add(at);
				server.setAttached(LIGHTHOUSES, known);
			}
		});
	}

	/** Every lighthouse the world knows of. */
	public static List<BlockPos> lighthouses(final ServerLevel overworld) {
		return overworld.getAttachedOrElse(LIGHTHOUSES, List.of());
	}

	private static void tick(final MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		List<BlockPos> all = lighthouses(overworld);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			List<BlockPos> near = new ArrayList<>();
			if (player.level() == overworld) {
				for (BlockPos lighthouse : all) {
					double dx = lighthouse.getX() + 0.5 - player.getX();
					double dz = lighthouse.getZ() + 0.5 - player.getZ();
					if (dx * dx + dz * dz <= TOLD * TOLD) {
						near.add(lighthouse);
					}
				}
			}
			if (!near.equals(TOLD_TO.get(player.getUUID()))) {
				TOLD_TO.put(player.getUUID(), near);
				ServerPlayNetworking.send(player, new MistPayload(near));
			}
		}
	}

	/** Server to client: the lighthouses near the hunter (none, to clear the mist). */
	public record MistPayload(List<BlockPos> lighthouses) implements CustomPacketPayload {
		public static final Type<MistPayload> TYPE = new Type<>(Mistinn.id("shore_mist"));
		public static final StreamCodec<ByteBuf, MistPayload> CODEC = BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list())
			.map(MistPayload::new, MistPayload::lighthouses);

		@Override
		public Type<MistPayload> type() {
			return TYPE;
		}
	}
}
