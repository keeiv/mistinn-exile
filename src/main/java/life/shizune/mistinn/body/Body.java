package life.shizune.mistinn.body;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/** Health of each {@link BodyPart}, in enum order. Immutable: every change returns a new value. */
public record Body(List<Float> health) {
	public static final Body FULL = new Body(Arrays.stream(BodyPart.values()).map(p -> BodyPart.MAX).toList());

	public static final Codec<Body> CODEC = Codec.FLOAT.listOf().xmap(Body::of, Body::health);
	public static final StreamCodec<ByteBuf, Body> STREAM_CODEC = ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()).map(Body::of, Body::health);

	public Body {
		health = List.copyOf(health);
	}

	/** Reads a saved list; a missing or extra entry (an older or newer save) falls back to whole. */
	private static Body of(final List<Float> saved) {
		List<Float> values = new ArrayList<>();
		for (int i = 0; i < BodyPart.values().length; i++) {
			values.add(i < saved.size() ? Mth.clamp(saved.get(i), 0.0F, BodyPart.MAX) : BodyPart.MAX);
		}
		return new Body(values);
	}

	public float get(final BodyPart part) {
		return this.health.get(part.ordinal());
	}

	public boolean destroyed(final BodyPart part) {
		return this.get(part) <= 0.0F;
	}

	public Body with(final BodyPart part, final float value) {
		List<Float> values = new ArrayList<>(this.health);
		values.set(part.ordinal(), Mth.clamp(value, 0.0F, BodyPart.MAX));
		return new Body(values);
	}

	public int destroyedCount() {
		int count = 0;
		for (BodyPart part : BodyPart.values()) {
			if (this.destroyed(part)) {
				count++;
			}
		}
		return count;
	}

	public boolean lethal() {
		return this.destroyedCount() >= BodyPart.LETHAL_DESTROYED;
	}

	public float total() {
		float sum = 0.0F;
		for (float value : this.health) {
			sum += value;
		}
		return sum;
	}
}
