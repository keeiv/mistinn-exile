package life.shizune.mistinn.naval;

import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Smoke, fire and spray of the sea fight, drawn by each client from one small message instead of
 * a stream of particle packets: a musket's flash and the line of its shot, a gun's flash and the
 * cloud it throws out, a shot striking the sea or the deck.
 */
public record NavalFx(int kind, Vec3 from, Vec3 to) implements CustomPacketPayload {
	/** A musket or pistol fired: flash and smoke at {@code from}, the shot's line to {@code to}. */
	public static final int GUN = 0;
	/** A gun fired: a great flash and cloud at {@code from}, thrown out toward {@code to}. */
	public static final int CANNON = 1;
	/** A shot falling into the sea at {@code from}. */
	public static final int SPLASH = 2;
	/** A shot striking something solid at {@code from}. */
	public static final int BLAST = 3;

	/** How far off the sea fight's smoke is seen. */
	private static final double SEEN = 160.0;

	public static final Type<NavalFx> TYPE = new Type<>(Mistinn.id("naval_fx"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NavalFx> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, NavalFx::kind,
		Vec3.STREAM_CODEC, NavalFx::from,
		Vec3.STREAM_CODEC, NavalFx::to,
		NavalFx::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	/** Shows the effect to everyone near enough to see it. */
	public static void send(final ServerLevel level, final int kind, final Vec3 from, final Vec3 to) {
		NavalFx fx = new NavalFx(kind, from, to);
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(from) < SEEN * SEEN) {
				ServerPlayNetworking.send(player, fx);
			}
		}
	}
}
