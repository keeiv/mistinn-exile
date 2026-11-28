package life.shizune.mistinn.udasru;

import life.shizune.mistinn.Mistinn;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

/**
 * 烏達斯魯: the land behind the fog wall, which Rust Anchor calls the forbidden zone. A world of the epic is Udasru: its
 * overworld is drawn by the map ({@link UdasruMap}) instead of the overworld's noise.
 */
public final class Udasru {
	/** The world preset a world of the epic is made with. */
	public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, Mistinn.id("udasru"));

	private Udasru() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Mistinn.id("udasru_field"), UdasruField.CODEC);
		Registry.register(BuiltInRegistries.BIOME_SOURCE, Mistinn.id("udasru"), UdasruBiomes.CODEC);
	}
}
