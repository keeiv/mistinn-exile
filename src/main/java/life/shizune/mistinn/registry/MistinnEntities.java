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

	/** 沉鐘: a drowned bell-ringer, his head and shoulders sealed in a cracked bronze bell. */
	public static final EntityType<life.shizune.mistinn.keeper.SunkenBell> SUNKEN_BELL = register("sunken_bell",
		EntityType.Builder.<life.shizune.mistinn.keeper.SunkenBell>of(life.shizune.mistinn.keeper.SunkenBell::new, MobCategory.MONSTER)
		.sized(1.3F, 2.9F)
		.eyeHeight(2.3F)
		.clientTrackingRange(10)
		.fireImmune());

	/** 鏽輪: the great brine wheel of a dead salt works, a man chained across the yoke at its back. */
	public static final EntityType<life.shizune.mistinn.keeper.RustWheel> RUST_WHEEL = register("rust_wheel",
		EntityType.Builder.<life.shizune.mistinn.keeper.RustWheel>of(life.shizune.mistinn.keeper.RustWheel::new, MobCategory.MONSTER)
		.sized(2.6F, 3.0F)
		.eyeHeight(1.6F)
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

	/** 海盜: a sailor of the fog sea, in the player model. */
	public static final EntityType<life.shizune.mistinn.naval.Pirate> PIRATE = register("pirate",
		EntityType.Builder.<life.shizune.mistinn.naval.Pirate>of(life.shizune.mistinn.naval.Pirate::new, MobCategory.MONSTER)
		.sized(0.6F, 1.8F)
		.eyeHeight(1.62F)
		.clientTrackingRange(10));

	/** 裂隙行者: a husk held together by a sky rift's light. */
	public static final EntityType<life.shizune.mistinn.rift.RiftWalker> RIFT_WALKER = register("rift_walker",
		EntityType.Builder.<life.shizune.mistinn.rift.RiftWalker>of(life.shizune.mistinn.rift.RiftWalker::new, MobCategory.MONSTER)
		.sized(0.6F, 2.1F)
		.eyeHeight(1.9F)
		.clientTrackingRange(10));

	/** 裂隙使者: what comes down last out of a sky rift. */
	public static final EntityType<life.shizune.mistinn.rift.RiftHerald> RIFT_HERALD = register("rift_herald",
		EntityType.Builder.<life.shizune.mistinn.rift.RiftHerald>of(life.shizune.mistinn.rift.RiftHerald::new, MobCategory.MONSTER)
		.sized(0.8F, 2.6F)
		.eyeHeight(2.3F)
		.clientTrackingRange(12)
		.fireImmune());

	/** 晶龍: a wyvern of living crystal that keeps the skies of a Terra highland. */
	public static final EntityType<life.shizune.mistinn.terra.CrystalDrake> CRYSTAL_DRAKE = register("crystal_drake",
		EntityType.Builder.<life.shizune.mistinn.terra.CrystalDrake>of(life.shizune.mistinn.terra.CrystalDrake::new, MobCategory.MONSTER)
		.sized(2.4F, 1.6F)
		.eyeHeight(1.3F)
		.clientTrackingRange(12)
		.fireImmune());

	/** 晶耕族: the people of the Terra valleys, in the player model. */
	public static final EntityType<life.shizune.mistinn.terra.Tiller> TILLER = register("tiller",
		EntityType.Builder.<life.shizune.mistinn.terra.Tiller>of(life.shizune.mistinn.terra.Tiller::new, MobCategory.MISC)
		.sized(0.6F, 1.8F)
		.eyeHeight(1.62F)
		.clientTrackingRange(10));

	/** A salt bomb, a flare or a bell lure in flight. */
	public static final EntityType<life.shizune.mistinn.tool.ThrownTool> THROWN_TOOL = register("thrown_tool",
		EntityType.Builder.<life.shizune.mistinn.tool.ThrownTool>of(life.shizune.mistinn.tool.ThrownTool::new, MobCategory.MISC)
		.sized(0.25F, 0.25F)
		.clientTrackingRange(4)
		.updateInterval(10));

	/** A round shot from a ship's gun. */
	public static final EntityType<life.shizune.mistinn.naval.CannonShot> CANNON_SHOT = register("cannon_shot",
		EntityType.Builder.<life.shizune.mistinn.naval.CannonShot>of(life.shizune.mistinn.naval.CannonShot::new, MobCategory.MISC)
		.sized(0.45F, 0.45F)
		.clientTrackingRange(10)
		.updateInterval(5));

	/** A pirate ship under sail, drawn whole, coming in out of the fog. */
	public static final EntityType<life.shizune.mistinn.naval.SailingShip> SAILING_SHIP = register("sailing_ship",
		EntityType.Builder.<life.shizune.mistinn.naval.SailingShip>of(life.shizune.mistinn.naval.SailingShip::new, MobCategory.MISC)
		.sized(1.0F, 1.0F)
		.clientTrackingRange(16)
		.updateInterval(1)
		.noSave());

	private MistinnEntities() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(final String name, final EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Mistinn.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(SNUFFER, Snuffer.createAttributes());
		FabricDefaultAttributeRegistry.register(SUNKEN_BELL, life.shizune.mistinn.keeper.SunkenBell.createAttributes());
		FabricDefaultAttributeRegistry.register(RUST_WHEEL, life.shizune.mistinn.keeper.RustWheel.createAttributes());
		FabricDefaultAttributeRegistry.register(STATION_HAND, StationHand.createAttributes());
		FabricDefaultAttributeRegistry.register(SALT_CORPSE, SaltCorpse.createAttributes());
		FabricDefaultAttributeRegistry.register(LAMP_THRALL, LampThrall.createAttributes());
		FabricDefaultAttributeRegistry.register(MIST_SHADE, MistShade.createAttributes());
		FabricDefaultAttributeRegistry.register(PIRATE, life.shizune.mistinn.naval.Pirate.createAttributes());
		FabricDefaultAttributeRegistry.register(RIFT_WALKER, life.shizune.mistinn.rift.RiftWalker.createAttributes());
		FabricDefaultAttributeRegistry.register(RIFT_HERALD, life.shizune.mistinn.rift.RiftHerald.createAttributes());
		FabricDefaultAttributeRegistry.register(CRYSTAL_DRAKE, life.shizune.mistinn.terra.CrystalDrake.createAttributes());
		FabricDefaultAttributeRegistry.register(TILLER, life.shizune.mistinn.terra.Tiller.createAttributes());
		// crystal drakes come of themselves under the open sky of a Terra highland, day or night
		life.shizune.mistinn.mixin.SpawnPlacementsInvoker.mistinn$register(CRYSTAL_DRAKE, net.minecraft.world.entity.SpawnPlacementTypes.NO_RESTRICTIONS,
			net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, life.shizune.mistinn.terra.CrystalDrake::checkSpawnRules);
		// rift walkers come of themselves where a rift has scarred the ground, in the dark like any monster
		life.shizune.mistinn.mixin.SpawnPlacementsInvoker.mistinn$register(RIFT_WALKER, net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
			net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules);
	}
}
