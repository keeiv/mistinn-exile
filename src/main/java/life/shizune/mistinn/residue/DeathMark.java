package life.shizune.mistinn.residue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Where a hunter fell. {@code serial} matches the gravestone placed there, so an older stone
 * left behind by an earlier death cannot be opened any more.
 */
public record DeathMark(GlobalPos pos, int serial, int salt) {
	public static final Codec<DeathMark> CODEC = RecordCodecBuilder.create(
		i -> i.group(
				GlobalPos.CODEC.fieldOf("pos").forGetter(DeathMark::pos),
				Codec.INT.fieldOf("serial").forGetter(DeathMark::serial),
				Codec.INT.optionalFieldOf("salt", 0).forGetter(DeathMark::salt)
			)
			.apply(i, DeathMark::new)
	);
	public static final StreamCodec<ByteBuf, DeathMark> STREAM_CODEC = StreamCodec.composite(
		GlobalPos.STREAM_CODEC, DeathMark::pos, ByteBufCodecs.VAR_INT, DeathMark::serial, ByteBufCodecs.VAR_INT, DeathMark::salt, DeathMark::new
	);
}
