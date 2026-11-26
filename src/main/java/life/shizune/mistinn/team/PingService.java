package life.shizune.mistinn.team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * 標記: a hunter points at something and the whole team sees it marked, in that hunter's colour,
 * for a few seconds: a place, a creature (the mark follows it), or a thing on the ground. The server
 * only checks the mark is near the one who made it and not made too often, and passes it on.
 */
public final class PingService {
	/** How far off a hunter can mark, in blocks. */
	public static final double REACH = 160.0;
	/** Ticks between one hunter's marks, at the least. */
	public static final int GAP = 6;

	public static final int PLACE = 0;
	public static final int ENEMY = 1;
	public static final int ITEM = 2;

	private static final Map<UUID, Long> LAST = new HashMap<>();

	private PingService() {
	}

	/** A hunter marks: the point looked at, and the creature or thing there (-1 for none). */
	public record PingPayload(Vec3 at, int entity) implements CustomPacketPayload {
		public static final Type<PingPayload> TYPE = new Type<>(Mistinn.id("ping"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PingPayload> CODEC = StreamCodec.composite(
			Vec3.STREAM_CODEC, PingPayload::at,
			ByteBufCodecs.VAR_INT, PingPayload::entity,
			PingPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A mark to show: who made it (and their colour), where, what it follows (-1 for none), and what kind it is. */
	public record ShowPayload(UUID from, String name, int colour, Vec3 at, int entity, int kind) implements CustomPacketPayload {
		public static final Type<ShowPayload> TYPE = new Type<>(Mistinn.id("ping_show"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ShowPayload> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, ShowPayload::from,
			ByteBufCodecs.stringUtf8(64), ShowPayload::name,
			ByteBufCodecs.INT, ShowPayload::colour,
			Vec3.STREAM_CODEC, ShowPayload::at,
			ByteBufCodecs.VAR_INT, ShowPayload::entity,
			ByteBufCodecs.VAR_INT, ShowPayload::kind,
			ShowPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(PingPayload.TYPE, PingPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ShowPayload.TYPE, ShowPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PingPayload.TYPE, (payload, context) -> ping(context.player(), payload));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
	}

	/** Checks a hunter's mark and shows it to everyone in their world. Returns whether it was shown. */
	public static boolean ping(final ServerPlayer player, final PingPayload payload) {
		if (player.isSpectator() || !player.isAlive()) {
			return false;
		}
		long now = player.level().getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < GAP) {
			return false;
		}
		Vec3 at = payload.at();
		if (!Double.isFinite(at.x) || !Double.isFinite(at.y) || !Double.isFinite(at.z) || at.distanceTo(player.getEyePosition()) > REACH + 2.0) {
			return false;
		}
		int kind = PLACE;
		int entity = -1;
		if (payload.entity() >= 0) {
			Entity target = player.level().getEntity(payload.entity());
			if (target != null && target != player && target.isAlive() && target.distanceTo(player) <= REACH + 2.0) {
				entity = target.getId();
				at = target.position().add(0.0, target.getBbHeight(), 0.0);
				kind = target instanceof Enemy ? ENEMY : target instanceof ItemEntity ? ITEM : PLACE;
				if (kind == ENEMY && target instanceof net.minecraft.world.entity.LivingEntity quarry) {
					// a hunter's lizards go for what they mark
					life.shizune.mistinn.terra.CrystalLizard.sic(player, quarry);
				}
			}
		}
		LAST.put(player.getUUID(), now);
		ShowPayload show = new ShowPayload(player.getUUID(), player.getScoreboardName(), TeamService.colour(player), at, entity, kind);
		for (ServerPlayer other : player.level().players()) {
			ServerPlayNetworking.send(other, show);
		}
		return true;
	}
}
