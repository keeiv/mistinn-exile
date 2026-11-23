package life.shizune.mistinn.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * The places of chapters 2 and 3, built when a story world first reaches them: the salt cairns
 * of the road, the fog-bell station they lead to, and the middle of its belfry floor.
 */
public record StoryRoad(List<BlockPos> cairns, BlockPos bellStation, BlockPos belfry) {
	public static final Codec<StoryRoad> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockPos.CODEC.listOf().fieldOf("cairns").forGetter(StoryRoad::cairns),
		BlockPos.CODEC.fieldOf("bell_station").forGetter(StoryRoad::bellStation),
		BlockPos.CODEC.fieldOf("belfry").forGetter(StoryRoad::belfry)
	).apply(i, StoryRoad::new));

	public StoryRoad {
		cairns = List.copyOf(cairns);
	}
}
