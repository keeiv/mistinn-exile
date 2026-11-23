package life.shizune.mistinn.achievement;

import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 成就紀錄: how achievements were kept before they became advancements: the ids earned, in order. Old saves still
 * hold one; it is read once and moved over. Immutable.
 */
public record Achievements(Set<String> earned) {
	public static final Achievements EMPTY = new Achievements(Set.of());

	public static final Codec<Achievements> CODEC = Codec.STRING.listOf().xmap(Achievements::of, Achievements::list);

	public Achievements {
		earned = Collections.unmodifiableSet(new LinkedHashSet<>(earned));
	}

	private static Achievements of(final List<String> ids) {
		return new Achievements(new LinkedHashSet<>(ids));
	}

	private List<String> list() {
		return List.copyOf(this.earned);
	}
}
