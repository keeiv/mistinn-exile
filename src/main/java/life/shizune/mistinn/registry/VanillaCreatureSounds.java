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

	/** The cry a creature gives as it winds up a melee blow, by vanilla entity id. */
	private static final java.util.Map<String, SoundEvent> WINDUPS = java.util.stream.Stream.of(
		"zombie", "zombie_villager", "giant", "husk", "drowned", "skeleton", "stray", "bogged", "parched", "wither_skeleton",
		"piglin", "piglin_brute", "zombified_piglin", "vindicator", "enderman", "creaking", "warden", "spider", "cave_spider",
		"silverfish", "endermite", "ravager", "hoglin", "zoglin", "wolf", "polar_bear"
	).collect(java.util.stream.Collectors.toUnmodifiableMap(id -> id, id -> register(id, "windup")));

	/** The sound of a finisher's contact on the hunter, by kind of blow. */
	private static final java.util.Map<life.shizune.mistinn.enemy.ExecutionMove.Hit, SoundEvent> EXECUTION = java.util.Arrays.stream(
		life.shizune.mistinn.enemy.ExecutionMove.Hit.values()).collect(java.util.stream.Collectors.toUnmodifiableMap(hit -> hit, hit -> {
			Identifier id = Mistinn.id("execution." + hit.name().toLowerCase(java.util.Locale.ROOT));
			return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
		}));

	private VanillaCreatureSounds() {
	}

	public static SoundEvent execution(final life.shizune.mistinn.enemy.ExecutionMove.Hit hit) {
		return EXECUTION.get(hit);
	}

	/** Every vanilla creature with a windup cry, by entity id. */
	public static java.util.Set<String> windupCreatures() {
		return WINDUPS.keySet();
	}

	/** The windup cry for a vanilla creature, or null when it has none. */
	public static @org.jspecify.annotations.Nullable SoundEvent windup(final net.minecraft.world.entity.EntityType<?> type) {
		Identifier id = net.minecraft.world.entity.EntityType.getKey(type);
		return id.getNamespace().equals("minecraft") ? WINDUPS.get(id.getPath()) : null;
	}

	private static SoundEvent register(final String mob, final String action) {
		Identifier id = Mistinn.id("entity." + mob + "." + action);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
		// Loading the class registers its static fields.
	}
}
