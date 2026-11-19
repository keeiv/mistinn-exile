package life.shizune.mistinn.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One hunter's place in the story: the current objective, where it points (for the minimap),
 * the radio sequences heard so far (for the journal) and whether the opening card was shown.
 */
public record StoryProgress(StoryStep step, Optional<BlockPos> target, List<String> heard, boolean opened) {
	public static final StoryProgress START = new StoryProgress(StoryStep.SALT, Optional.empty(), List.of(), false);

	public static final Codec<StoryProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.xmap(StoryStep::byId, s -> s.id).fieldOf("step").forGetter(StoryProgress::step),
		BlockPos.CODEC.optionalFieldOf("target").forGetter(StoryProgress::target),
		Codec.STRING.listOf().optionalFieldOf("heard", List.of()).forGetter(StoryProgress::heard),
		Codec.BOOL.optionalFieldOf("opened", false).forGetter(StoryProgress::opened)
	).apply(i, StoryProgress::new));

	public static final StreamCodec<ByteBuf, StoryProgress> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8.map(StoryStep::byId, s -> s.id), StoryProgress::step,
		ByteBufCodecs.optional(BlockPos.STREAM_CODEC), StoryProgress::target,
		ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), StoryProgress::heard,
		ByteBufCodecs.BOOL, StoryProgress::opened,
		StoryProgress::new
	);

	public StoryProgress at(final StoryStep next, final Optional<BlockPos> where) {
		return new StoryProgress(next, where, this.heard, this.opened);
	}

	public StoryProgress hear(final String sequence) {
		if (this.heard.contains(sequence)) {
			return this;
		}
		List<String> more = new ArrayList<>(this.heard);
		more.add(sequence);
		return new StoryProgress(this.step, this.target, List.copyOf(more), this.opened);
	}

	public StoryProgress open() {
		return new StoryProgress(this.step, this.target, this.heard, true);
	}
}
