package life.shizune.mistinn.registry;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.shore.LampThrall;
import life.shizune.mistinn.shore.MistShade;
import life.shizune.mistinn.shore.SaltCorpse;
import life.shizune.mistinn.station.StationHand;
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

	/** 鹽屍: a hunter dried in the brine, the salt crusted over them; it gets back up once. */
	public static final EntityType<SaltCorpse> SALT_CORPSE = register("salt_corpse", EntityType.Builder.<SaltCorpse>of(SaltCorpse::new, MobCategory.MONSTER)
		.sized(0.6F, 1.95F)
		.eyeHeight(1.74F)
		.clientTrackingRange(8));

	/** 燈奴: a hooded lamplighter of the dead lighthouses; its lantern flares before it rushes. */
	public static final EntityType<LampThrall> LAMP_THRALL = register("lamp_thrall", EntityType.Builder.<LampThrall>of(LampThrall::new, MobCategory.MONSTER)
		.sized(0.6F, 1.95F)
		.eyeHeight(1.74F)
		.clientTrackingRange(8));

	/** 霧影: the shape a lost hunter leaves in the fog; only ever met in the mist; its touch blinds. */
	public static final EntityType<MistShade> MIST_SHADE = register("mist_shade", EntityType.Builder.<MistShade>of(MistShade::new, MobCategory.MONSTER)
		.sized(0.6F, 1.95F)
		.eyeHeight(1.74F)
		.clientTrackingRange(8));

	/** The people of Rust Anchor, in the player model. */
	public static final EntityType<StationHand> STATION_HAND = register("station_hand", EntityType.Builder.<StationHand>of(StationHand::new, MobCategory.MISC)
		.sized(0.6F, 1.8F)
		.eyeHeight(1.62F)
		.clientTrackingRange(10));

	private MistinnEntities() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(final String name, final EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Mistinn.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(SNUFFER, Snuffer.createAttributes());
		FabricDefaultAttributeRegistry.register(STATION_HAND, StationHand.createAttributes());
		FabricDefaultAttributeRegistry.register(SALT_CORPSE, SaltCorpse.createAttributes());
		FabricDefaultAttributeRegistry.register(LAMP_THRALL, LampThrall.createAttributes());
		FabricDefaultAttributeRegistry.register(MIST_SHADE, MistShade.createAttributes());
	}
}
