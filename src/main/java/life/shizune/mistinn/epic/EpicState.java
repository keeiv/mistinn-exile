package life.shizune.mistinn.epic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How far a world's epic has come, shared by everyone in it: the step it is at; the bell towers rung, the great beasts
 * brought down and the great bells rung (one bit each, by their order in {@link life.shizune.mistinn.udasru.UdasruPlaces});
 * the unsent letters found; the name the epic was written under; and what has happened once ({@code flags}).
 */
public record EpicState(int step, int towers, int beasts, int bells, int letters, String name, int flags) {
	/** The rift over the capital has been held. */
	public static final int RIFTED = 1;
	/** The rift over the capital is open now. */
	public static final int RIFT_OPEN = 2;
	/** The anchor's chain lies bare from the shore to the crown. */
	public static final int BRIDGE = 4;
	/** The Helm stands on the crown. */
	public static final int HELM_RISEN = 8;
	/** The capital's great bell has rung. */
	public static final int GREAT_BELL = 16;

	public static final EpicState FRESH = new EpicState(EpicStep.WAKE.ordinal(), 0, 0, 0, 0, "", 0);

	public static final Codec<EpicState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.fieldOf("step").forGetter(EpicState::step),
		Codec.INT.optionalFieldOf("towers", 0).forGetter(EpicState::towers),
		Codec.INT.optionalFieldOf("beasts", 0).forGetter(EpicState::beasts),
		Codec.INT.optionalFieldOf("bells", 0).forGetter(EpicState::bells),
		Codec.INT.optionalFieldOf("letters", 0).forGetter(EpicState::letters),
		Codec.STRING.optionalFieldOf("name", "").forGetter(EpicState::name),
		Codec.INT.optionalFieldOf("flags", 0).forGetter(EpicState::flags)
	).apply(i, EpicState::new));

	public static final StreamCodec<ByteBuf, EpicState> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, EpicState::step,
		ByteBufCodecs.VAR_INT, EpicState::towers,
		ByteBufCodecs.VAR_INT, EpicState::beasts,
		ByteBufCodecs.VAR_INT, EpicState::bells,
		ByteBufCodecs.VAR_INT, EpicState::letters,
		ByteBufCodecs.stringUtf8(64), EpicState::name,
		ByteBufCodecs.VAR_INT, EpicState::flags,
		EpicState::new);

	public EpicStep at() {
		return EpicStep.byOrdinal(this.step);
	}

	public EpicState at(final EpicStep next) {
		return new EpicState(next.ordinal(), this.towers, this.beasts, this.bells, this.letters, this.name, this.flags);
	}

	public EpicState withTower(final int bit) {
		return new EpicState(this.step, this.towers | bit, this.beasts, this.bells, this.letters, this.name, this.flags);
	}

	public EpicState withBeast(final int bit) {
		return new EpicState(this.step, this.towers, this.beasts | bit, this.bells, this.letters, this.name, this.flags);
	}

	public EpicState withBell(final int bit) {
		return new EpicState(this.step, this.towers, this.beasts, this.bells | bit, this.letters, this.name, this.flags);
	}

	public EpicState withLetter(final int bit) {
		return new EpicState(this.step, this.towers, this.beasts, this.bells, this.letters | bit, this.name, this.flags);
	}

	public EpicState named(final String given) {
		return new EpicState(this.step, this.towers, this.beasts, this.bells, this.letters, given, this.flags);
	}

	public EpicState with(final int flag, final boolean on) {
		return new EpicState(this.step, this.towers, this.beasts, this.bells, this.letters, this.name, on ? this.flags | flag : this.flags & ~flag);
	}

	public boolean has(final int flag) {
		return (this.flags & flag) != 0;
	}

	public boolean rungTower(final int bit) {
		return (this.towers & bit) != 0;
	}

	public int bellsRung() {
		return Integer.bitCount(this.bells);
	}
}
