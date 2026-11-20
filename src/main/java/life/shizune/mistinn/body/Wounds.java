package life.shizune.mistinn.body;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wounds on each {@link BodyPart}, in enum order: bleeding, a broken bone, and what has been
 * put on them (a tourniquet since a game time, a splint until one). Immutable.
 */
public record Wounds(List<Wound> parts) {
	/**
	 * One part's wounds. {@code tourniquet} is the game time it was tied on (0 for none; one tied
	 * at time 0 is recorded as 1); {@code splint} the time it loosens (0 for none).
	 */
	public record Wound(boolean bleeding, boolean fractured, long tourniquet, long splint) {
		public static final Wound NONE = new Wound(false, false, 0L, 0L);

		public static final Codec<Wound> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("bleeding", false).forGetter(Wound::bleeding),
			Codec.BOOL.optionalFieldOf("fractured", false).forGetter(Wound::fractured),
			Codec.LONG.optionalFieldOf("tourniquet", 0L).forGetter(Wound::tourniquet),
			Codec.LONG.optionalFieldOf("splint", 0L).forGetter(Wound::splint)
		).apply(i, Wound::new));

		public static final StreamCodec<ByteBuf, Wound> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Wound::bleeding,
			ByteBufCodecs.BOOL, Wound::fractured,
			ByteBufCodecs.VAR_LONG, Wound::tourniquet,
			ByteBufCodecs.VAR_LONG, Wound::splint,
			Wound::new
		);

		public boolean tied() {
			return this.tourniquet != 0L;
		}

		public boolean splinted(final long now) {
			return this.splint > now;
		}

		public boolean any() {
			return this.bleeding || this.fractured || this.tied() || this.splint > 0L;
		}

		public Wound bleeding(final boolean value) {
			return new Wound(value, this.fractured, this.tourniquet, this.splint);
		}

		public Wound fractured(final boolean value) {
			return new Wound(this.bleeding, value, this.tourniquet, this.splint);
		}

		public Wound tourniquet(final long since) {
			return new Wound(this.bleeding, this.fractured, since, this.splint);
		}

		public Wound splint(final long until) {
			return new Wound(this.bleeding, this.fractured, this.tourniquet, until);
		}
	}

	public static final Wounds NONE = of(List.of());

	public static final Codec<Wounds> CODEC = Wound.CODEC.listOf().xmap(Wounds::of, Wounds::parts);
	public static final StreamCodec<ByteBuf, Wounds> STREAM_CODEC = Wound.STREAM_CODEC.apply(ByteBufCodecs.list()).map(Wounds::of, Wounds::parts);

	public Wounds {
		parts = List.copyOf(parts);
	}

	/** Reads a saved list; a missing entry (an older save) is unwounded. */
	private static Wounds of(final List<Wound> saved) {
		List<Wound> values = new ArrayList<>();
		for (int i = 0; i < BodyPart.values().length; i++) {
			values.add(i < saved.size() ? saved.get(i) : Wound.NONE);
		}
		return new Wounds(values);
	}

	public Wound get(final BodyPart part) {
		return this.parts.get(part.ordinal());
	}

	public Wounds with(final BodyPart part, final Wound wound) {
		List<Wound> values = new ArrayList<>(this.parts);
		values.set(part.ordinal(), wound);
		return new Wounds(values);
	}

	public boolean any() {
		return this.parts.stream().anyMatch(Wound::any);
	}
}
