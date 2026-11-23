package life.shizune.mistinn.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/**
 * The places of chapter 4, built when the great bell is rung: the pod the station sent down in answer, on the ground
 * at its middle, and the salt works inland, at the middle of its yard where the wheel stands.
 */
public record StoryWorks(BlockPos pod, BlockPos works) {
	public static final Codec<StoryWorks> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockPos.CODEC.fieldOf("pod").forGetter(StoryWorks::pod),
		BlockPos.CODEC.fieldOf("works").forGetter(StoryWorks::works)
	).apply(i, StoryWorks::new));
}
