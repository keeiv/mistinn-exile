package life.shizune.mistinn.udasru;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * The drawn land as the terrain reads it, column by column: in place of the overworld's continents and splines, the
 * height Udasru was drawn at ({@code offset}), how closely the ground keeps to it ({@code factor}), and whether a
 * column is land or sea ({@code continents}). The rest of the overworld's machinery (caves, ore veins, water under
 * ground, the surface) is left as it is and reads these.
 */
public record UdasruField(Channel channel) implements DensityFunction {
	public static final MapCodec<UdasruField> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Channel.CODEC.fieldOf("channel").forGetter(UdasruField::channel)
	).apply(i, UdasruField::new));

	/** The overworld's depth gradient crosses zero at this height; the offset moves that crossing to the drawn height. */
	private static final float MIDDLE = 128.0F;

	/** What a column of the drawing is read for. */
	public enum Channel implements StringRepresentable {
		OFFSET("offset", -1.2F, 1.2F),
		FACTOR("factor", 1.0F, 8.0F),
		JAGGEDNESS("jaggedness", 0.0F, 1.0F),
		CONTINENTS("continents", -1.1F, 1.0F);

		public static final Codec<Channel> CODEC = StringRepresentable.fromEnum(Channel::values);
		private final String id;
		final float lowest;
		final float highest;

		Channel(final String id, final float lowest, final float highest) {
			this.id = id;
			this.lowest = lowest;
			this.highest = highest;
		}

		@Override
		public String getSerializedName() {
			return this.id;
		}
	}

	/** The value at a column. */
	public static float value(final Channel channel, final int x, final int z) {
		UdasruMap map = UdasruMap.get();
		return switch (channel) {
			case OFFSET -> clamp(channel, (map.height(x, z) - MIDDLE) / MIDDLE);
			case FACTOR -> UdasruRegion.byId(map.region(x, z)).factor;
			case JAGGEDNESS -> UdasruRegion.byId(map.region(x, z)).jaggedness;
			case CONTINENTS -> map.height(x, z) < 62.0F ? -0.6F : 0.3F;
		};
	}

	private static float clamp(final Channel channel, final float value) {
		return Math.max(channel.lowest, Math.min(channel.highest, value));
	}

	@Override
	public DensitySampler compileSampler(final CompileContext context) {
		Channel channel = this.channel;
		return new DensitySampler() {
			@Override
			public void sampleVolume(final SamplerContext context, final DensityBuffer buffer, final DensityVolume volume) {
				DensitySampler.sampleVolumeNaive(context, buffer, volume, this);
			}

			@Override
			public float sampleValue(final SamplerContext context, final int x, final int y, final int z) {
				return value(channel, x, z);
			}
		};
	}

	@Override
	public DensityFunction rewriteChildren(final DfRewriteRule rule) {
		return this;
	}

	@Override
	public Interval range() {
		return Interval.of(this.channel.lowest, this.channel.highest);
	}

	@Override
	public int domainAxes() {
		return AXIS_X | AXIS_Z;
	}

	@Override
	public MapCodec<UdasruField> codec() {
		return CODEC;
	}
}
