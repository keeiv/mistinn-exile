package life.shizune.mistinn.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * The places a story world guarantees, built when the world first starts: an intact drop station
 * (and its radio) a walk from spawn, and a fog-shore lighthouse on the nearest coast.
 */
public record StoryPlaces(BlockPos station, BlockPos radio, BlockPos lighthouse, BlockPos lampRoom) {
	public static final Codec<StoryPlaces> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockPos.CODEC.fieldOf("station").forGetter(StoryPlaces::station),
		BlockPos.CODEC.fieldOf("radio").forGetter(StoryPlaces::radio),
		BlockPos.CODEC.fieldOf("lighthouse").forGetter(StoryPlaces::lighthouse),
		BlockPos.CODEC.fieldOf("lamp_room").forGetter(StoryPlaces::lampRoom)
	).apply(i, StoryPlaces::new));
}
