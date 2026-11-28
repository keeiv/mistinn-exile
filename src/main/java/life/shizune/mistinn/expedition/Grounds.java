package life.shizune.mistinn.expedition;

import java.util.List;
import life.shizune.mistinn.Mistinn;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The grounds of Terra's expeditions: the places the tillers' forebears built and left, each one its own dimension with
 * its own sky, fog and light. They ship with the mod's own data, so every world has them from the first day (an old save
 * too, the first time it is opened with them), empty until a hunter is first sent there.
 */
public final class Grounds {
	/** 泰拉水壩: the dam that held the highland's water back and watered the valley below. */
	public static final ResourceKey<Level> DAM = ResourceKey.create(Registries.DIMENSION, Mistinn.id("expedition_dam"));
	/** Every ground, in the order they open. */
	public static final List<ResourceKey<Level>> ALL = List.of(DAM);

	private Grounds() {
	}

	/** Whether {@code level} is one of the grounds. */
	public static boolean is(final Level level) {
		return ALL.contains(level.dimension());
	}
}
