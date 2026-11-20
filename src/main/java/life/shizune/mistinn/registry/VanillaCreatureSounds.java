package life.shizune.mistinn.registry;

import life.shizune.mistinn.Mistinn;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Dedicated events for mobs that inherit a sound or have no vanilla event of their own. */
public final class VanillaCreatureSounds {
	public static final SoundEvent CAVE_SPIDER_AMBIENT = register("cave_spider", "ambient");
	public static final SoundEvent CAVE_SPIDER_HURT = register("cave_spider", "hurt");
	public static final SoundEvent CAVE_SPIDER_DEATH = register("cave_spider", "death");
	public static final SoundEvent CAVE_SPIDER_STEP = register("cave_spider", "step");
	public static final SoundEvent GIANT_AMBIENT = register("giant", "ambient");
	public static final SoundEvent GIANT_HURT = register("giant", "hurt");
	public static final SoundEvent GIANT_DEATH = register("giant", "death");
	public static final SoundEvent GIANT_STEP = register("giant", "step");
	public static final SoundEvent BOGGED_SHOOT = register("bogged", "shoot");
	public static final SoundEvent PARCHED_SHOOT = register("parched", "shoot");
	public static final SoundEvent STRAY_SHOOT = register("stray", "shoot");

	private VanillaCreatureSounds() {
	}

	private static SoundEvent register(final String mob, final String action) {
		Identifier id = Mistinn.id("entity." + mob + "." + action);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
		// Loading the class registers its static fields.
	}
}
