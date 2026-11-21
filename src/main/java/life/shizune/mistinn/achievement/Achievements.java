package life.shizune.mistinn.achievement;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 成就紀錄: what a hunter has done that is worth remembering (a keeper brought down, ...), in the
 * order it was earned. Immutable. Ids this version does not know are kept.
 */
public record Achievements(Set<String> earned) {
	public static final Achievements EMPTY = new Achievements(Set.of());

	public static final Codec<Achievements> CODEC = Codec.STRING.listOf().xmap(Achievements::of, Achievements::list);
	public static final StreamCodec<ByteBuf, Achievements> STREAM_CODEC = ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(256)).map(Achievements::of, Achievements::list);

	public Achievements {
		earned = Collections.unmodifiableSet(new LinkedHashSet<>(earned));
	}

	private static Achievements of(final List<String> ids) {
		return new Achievements(new LinkedHashSet<>(ids));
	}

	private List<String> list() {
		return List.copyOf(this.earned);
	}

	public boolean has(final String id) {
		return this.earned.contains(id);
	}

	public Achievements with(final String id) {
		Set<String> next = new LinkedHashSet<>(this.earned);
		next.add(id);
		return new Achievements(next);
	}
}
