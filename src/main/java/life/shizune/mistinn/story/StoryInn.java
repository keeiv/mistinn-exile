package life.shizune.mistinn.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/** 霧宿, built when a story world first reaches chapter 5: the ground at its door, and its hearth. */
public record StoryInn(BlockPos inn, BlockPos hearth) {
	public static final Codec<StoryInn> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockPos.CODEC.fieldOf("inn").forGetter(StoryInn::inn),
		BlockPos.CODEC.fieldOf("hearth").forGetter(StoryInn::hearth)
	).apply(i, StoryInn::new));
}
