package life.shizune.mistinn.naval;

import io.netty.buffer.ByteBuf;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A pirate ship is coming for this hunter: the client plays its entrance over the ship sailing in
 * ({@code entity}), naming it ({@code name}, an index into the ships' names and captains), for as long
 * as it takes to come ({@code ticks}).
 */
public record NavalIntroPayload(int entity, int name, int ticks) implements CustomPacketPayload {
	public static final Type<NavalIntroPayload> TYPE = new Type<>(Mistinn.id("naval_intro"));
	public static final StreamCodec<ByteBuf, NavalIntroPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, NavalIntroPayload::entity,
		ByteBufCodecs.VAR_INT, NavalIntroPayload::name,
		ByteBufCodecs.VAR_INT, NavalIntroPayload::ticks,
		NavalIntroPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}
}
