package life.shizune.mistinn.team;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Where the other hunters are, sent to each hunter every {@link TeamService#REPORT_TICKS} ticks. */
public record TeamPayload(List<Member> members) implements CustomPacketPayload {
	public static final Type<TeamPayload> TYPE = new Type<>(Mistinn.id("team"));

	/** One other hunter: who, their colour, and where they stand and face. */
	public record Member(UUID id, String name, int colour, Identifier dimension, double x, double z, float yaw, boolean downed) {
		public static final StreamCodec<ByteBuf, Member> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, Member::id,
			ByteBufCodecs.stringUtf8(16), Member::name,
			ByteBufCodecs.INT, Member::colour,
			Identifier.STREAM_CODEC, Member::dimension,
			ByteBufCodecs.DOUBLE, Member::x,
			ByteBufCodecs.DOUBLE, Member::z,
			ByteBufCodecs.FLOAT, Member::yaw,
			ByteBufCodecs.BOOL, Member::downed,
			Member::new);
	}

	public static final StreamCodec<ByteBuf, TeamPayload> CODEC = Member.CODEC.apply(ByteBufCodecs.list(16))
		.map(TeamPayload::new, TeamPayload::members);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
