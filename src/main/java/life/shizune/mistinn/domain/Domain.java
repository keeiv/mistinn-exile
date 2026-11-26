package life.shizune.mistinn.domain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A place in the world that has become something else: a sky rift and the ground it scars, or a
 * Terra highland. It has a centre, a radius, how far the change has spread from the centre so
 * far, and a phase.
 *
 * @param id        the world's number for it, never reused
 * @param kind      what it is
 * @param centre    its middle, on the ground
 * @param radius    how far it reaches
 * @param phase     where it stands
 * @param since     the game tick it entered that phase
 * @param spread    how far from the centre the land has been changed so far
 */
public record Domain(int id, Kind kind, BlockPos centre, int radius, Phase phase, long since, int spread) {
	/** What a domain is. */
	public enum Kind {
		RIFT, TERRA;

		static final Codec<Kind> CODEC = Codec.STRING.xmap(Kind::valueOf, Kind::name);
	}

	/** Where a domain stands: a rift hangs open, is being fought under, or has been sealed into a scar; a Terra simply is. */
	public enum Phase {
		OPEN, BATTLE, SCAR, SETTLED;

		static final Codec<Phase> CODEC = Codec.STRING.xmap(Phase::valueOf, Phase::name);
	}

	public static final Codec<Domain> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("id").forGetter(Domain::id),
		Kind.CODEC.fieldOf("kind").forGetter(Domain::kind),
		BlockPos.CODEC.fieldOf("centre").forGetter(Domain::centre),
		Codec.INT.fieldOf("radius").forGetter(Domain::radius),
		Phase.CODEC.fieldOf("phase").forGetter(Domain::phase),
		Codec.LONG.fieldOf("since").forGetter(Domain::since),
		Codec.INT.fieldOf("spread").forGetter(Domain::spread)
	).apply(i, Domain::new));

	public static final StreamCodec<ByteBuf, Domain> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public Domain decode(final ByteBuf buf) {
			int id = ByteBufCodecs.VAR_INT.decode(buf);
			Kind kind = Kind.values()[ByteBufCodecs.VAR_INT.decode(buf)];
			BlockPos centre = BlockPos.STREAM_CODEC.decode(buf);
			int radius = ByteBufCodecs.VAR_INT.decode(buf);
			Phase phase = Phase.values()[ByteBufCodecs.VAR_INT.decode(buf)];
			long since = ByteBufCodecs.VAR_LONG.decode(buf);
			int spread = ByteBufCodecs.VAR_INT.decode(buf);
			return new Domain(id, kind, centre, radius, phase, since, spread);
		}

		@Override
		public void encode(final ByteBuf buf, final Domain domain) {
			ByteBufCodecs.VAR_INT.encode(buf, domain.id());
			ByteBufCodecs.VAR_INT.encode(buf, domain.kind().ordinal());
			BlockPos.STREAM_CODEC.encode(buf, domain.centre());
			ByteBufCodecs.VAR_INT.encode(buf, domain.radius());
			ByteBufCodecs.VAR_INT.encode(buf, domain.phase().ordinal());
			ByteBufCodecs.VAR_LONG.encode(buf, domain.since());
			ByteBufCodecs.VAR_INT.encode(buf, domain.spread());
		}
	};

	/** How far a column is from the centre, across the ground. */
	public double distance(final double x, final double z) {
		return Math.hypot(x - (this.centre.getX() + 0.5), z - (this.centre.getZ() + 0.5));
	}

	/** Whether a column lies within the domain's reach, give or take {@code margin}. */
	public boolean covers(final double x, final double z, final double margin) {
		return this.distance(x, z) <= this.radius + margin;
	}

	public Domain phase(final Phase next, final long now) {
		return new Domain(this.id, this.kind, this.centre, this.radius, next, now, this.spread);
	}

	public Domain spread(final int reached) {
		return new Domain(this.id, this.kind, this.centre, this.radius, this.phase, this.since, reached);
	}

	public Domain radius(final int reach) {
		return new Domain(this.id, this.kind, this.centre, reach, this.phase, this.since, this.spread);
	}

	public boolean open() {
		return this.kind == Kind.RIFT && (this.phase == Phase.OPEN || this.phase == Phase.BATTLE);
	}
}
