package life.shizune.mistinn.skill;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The nodes a hunter has unlocked. Immutable: unlocking returns a new value. Ids this version
 * does not know (from a newer save) are kept but count for nothing.
 */
public record Skills(Set<String> unlocked) {
	public static final Skills EMPTY = new Skills(Set.of());

	public static final Codec<Skills> CODEC = Codec.STRING.listOf().xmap(Skills::of, Skills::list);
	public static final StreamCodec<ByteBuf, Skills> STREAM_CODEC = ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(256)).map(Skills::of, Skills::list);

	public Skills {
		unlocked = Collections.unmodifiableSet(new LinkedHashSet<>(unlocked));
	}

	private static Skills of(final List<String> ids) {
		return new Skills(new LinkedHashSet<>(ids));
	}

	private List<String> list() {
		return List.copyOf(this.unlocked);
	}

	public boolean has(final String id) {
		return this.unlocked.contains(id);
	}

	/** The stat's value: one per unlocked minor node of its branch, up to {@link Stat#CAP}. */
	public int stat(final Stat stat) {
		int points = 0;
		for (String id : this.unlocked) {
			SkillTree.Node node = SkillTree.node(id);
			if (node != null && !node.major() && node.stat() == stat) {
				points++;
			}
		}
		return Math.min(points, Stat.CAP);
	}

	/** Minor nodes unlocked so far, which sets the price of the next one. */
	public int minorCount() {
		int count = 0;
		for (String id : this.unlocked) {
			SkillTree.Node node = SkillTree.node(id);
			if (node != null && !node.major()) {
				count++;
			}
		}
		return count;
	}

	public Skills with(final String id) {
		Set<String> next = new LinkedHashSet<>(this.unlocked);
		next.add(id);
		return new Skills(next);
	}
}
