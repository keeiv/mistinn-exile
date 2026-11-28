package life.shizune.mistinn.udasru;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/** Udasru's biomes, as drawn: each column takes the biome of its region, the region's number picking from the list. */
public final class UdasruBiomes extends BiomeSource {
	public static final MapCodec<UdasruBiomes> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Biome.CODEC.listOf().fieldOf("biomes").forGetter(source -> source.biomes)
	).apply(i, UdasruBiomes::new));

	private final List<Holder<Biome>> biomes;

	public UdasruBiomes(final List<Holder<Biome>> biomes) {
		this.biomes = List.copyOf(biomes);
	}

	/** The biome of a region (the sea's for one the list does not reach). */
	public Holder<Biome> biome(final int region) {
		return this.biomes.get(region >= 0 && region < this.biomes.size() ? region : UdasruMap.SEA);
	}

	@Override
	protected MapCodec<UdasruBiomes> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return this.biomes.stream().distinct();
	}

	@Override
	public BiomeResolver createResolver(final Climate.Sampler sampler) {
		UdasruMap map = UdasruMap.get();
		return (quartX, quartY, quartZ) -> this.biome(map.region((quartX << 2) + 2, (quartZ << 2) + 2));
	}
}
