package life.shizune.mistinn.registry;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.keeper.Snuffer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** The Lighthouse Keepers and other creatures of the forbidden zone. */
public final class MistinnEntities {
	/** 熄燈人: tall, thin, an unlit lantern for a head. */
	public static final EntityType<Snuffer> SNUFFER = register("snuffer", EntityType.Builder.<Snuffer>of(Snuffer::new, MobCategory.MONSTER)
		.sized(0.8F, 2.7F)
		.eyeHeight(2.4F)
		.clientTrackingRange(10)
		.fireImmune());

	private MistinnEntities() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(final String name, final EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Mistinn.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(SNUFFER, Snuffer.createAttributes());
	}
}
