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

	/** 3412．鏽錨使徒: the hunter as they are when no one moves them. */
	public static final EntityType<life.shizune.mistinn.path.RustApostle> RUST_APOSTLE = register("rust_apostle",
		EntityType.Builder.<life.shizune.mistinn.path.RustApostle>of(life.shizune.mistinn.path.RustApostle::new, MobCategory.MONSTER)
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
		.sized(0.7F, 2.3F)
		.eyeHeight(2.0F)
		.clientTrackingRange(10));

	/** 裂隙使者: what comes down last out of a sky rift. */
	public static final EntityType<life.shizune.mistinn.rift.RiftHerald> RIFT_HERALD = register("rift_herald",
		EntityType.Builder.<life.shizune.mistinn.rift.RiftHerald>of(life.shizune.mistinn.rift.RiftHerald::new, MobCategory.MONSTER)
		.sized(0.9F, 2.75F)
		.eyeHeight(2.5F)
		.clientTrackingRange(12)
		.fireImmune());

	/** 裂隙獵犬: a gaunt hound lit from inside by a sky rift's light, that hunts in packs. */
	public static final EntityType<life.shizune.mistinn.rift.RiftHound> RIFT_HOUND = register("rift_hound",
		EntityType.Builder.<life.shizune.mistinn.rift.RiftHound>of(life.shizune.mistinn.rift.RiftHound::new, MobCategory.MONSTER)
		.sized(0.9F, 1.15F)
		.eyeHeight(1.0F)
		.clientTrackingRange(10));

	/** 晶蜥: the small one of the lattice, a lizard of vein stone and crystal that keeps to the ground in threes. */
	public static final EntityType<life.shizune.mistinn.terra.CrystalLizard> CRYSTAL_LIZARD = register("crystal_lizard",
		EntityType.Builder.<life.shizune.mistinn.terra.CrystalLizard>of(life.shizune.mistinn.terra.CrystalLizard::new, MobCategory.MONSTER)
		.sized(0.9F, 0.55F)
		.eyeHeight(0.4F)
		.clientTrackingRange(10)
		.fireImmune());

	/** 閘衛機兵: a heavy machine the forebears left to keep the dam, kneeling at its post until woken. */
	public static final EntityType<life.shizune.mistinn.terra.GateSentry> GATE_SENTRY = register("gate_sentry",
		EntityType.Builder.<life.shizune.mistinn.terra.GateSentry>of(life.shizune.mistinn.terra.GateSentry::new, MobCategory.MONSTER)
		.sized(1.5F, 2.4F)
		.eyeHeight(2.1F)
		.clientTrackingRange(10)
		.fireImmune());

	/** 玉角羚: the gentle grazer of Terra's valleys, horned with jade crystal; its hide is leather. */
	public static final EntityType<life.shizune.mistinn.terra.JadeAntelope> JADE_ANTELOPE = register("jade_antelope",
		EntityType.Builder.<life.shizune.mistinn.terra.JadeAntelope>of(life.shizune.mistinn.terra.JadeAntelope::new, MobCategory.CREATURE)
		.sized(0.9F, 1.4F)
		.eyeHeight(1.45F)
		.clientTrackingRange(10));

	/** 爆裂箭、冰凍箭、電箭: arrows with heads of their own. */
	public static final EntityType<life.shizune.mistinn.weapon.ElementArrow> BLAST_ARROW = arrow("blast_arrow", life.shizune.mistinn.weapon.ElementArrow.Kind.BLAST);
	public static final EntityType<life.shizune.mistinn.weapon.ElementArrow> FROST_ARROW = arrow("frost_arrow", life.shizune.mistinn.weapon.ElementArrow.Kind.FROST);
	public static final EntityType<life.shizune.mistinn.weapon.ElementArrow> SHOCK_ARROW = arrow("shock_arrow", life.shizune.mistinn.weapon.ElementArrow.Kind.SHOCK);

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

	/** 晶冠古龍: the ancient drake at the heart of a Terra highland. */
	public static final EntityType<life.shizune.mistinn.terra.CrystalElder> CRYSTAL_ELDER = register("crystal_elder",
		EntityType.Builder.<life.shizune.mistinn.terra.CrystalElder>of(life.shizune.mistinn.terra.CrystalElder::new, MobCategory.MONSTER)
		.sized(5.2F, 3.4F)
		.eyeHeight(2.8F)
		.clientTrackingRange(16)
		.fireImmune());

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

	private static EntityType<life.shizune.mistinn.weapon.ElementArrow> arrow(final String name, final life.shizune.mistinn.weapon.ElementArrow.Kind kind) {
		return register(name, EntityType.Builder.<life.shizune.mistinn.weapon.ElementArrow>of((type, level) -> new life.shizune.mistinn.weapon.ElementArrow(type, level, kind),
			MobCategory.MISC).noLootTable().sized(0.5F, 0.5F).eyeHeight(0.13F).clientTrackingRange(4).updateInterval(20));
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
		FabricDefaultAttributeRegistry.register(RUST_APOSTLE, life.shizune.mistinn.path.RustApostle.createAttributes());
		FabricDefaultAttributeRegistry.register(RIFT_WALKER, life.shizune.mistinn.rift.RiftWalker.createAttributes());
		FabricDefaultAttributeRegistry.register(RIFT_HERALD, life.shizune.mistinn.rift.RiftHerald.createAttributes());
		FabricDefaultAttributeRegistry.register(CRYSTAL_DRAKE, life.shizune.mistinn.terra.CrystalDrake.createAttributes());
		FabricDefaultAttributeRegistry.register(TILLER, life.shizune.mistinn.terra.Tiller.createAttributes());
		FabricDefaultAttributeRegistry.register(CRYSTAL_ELDER, life.shizune.mistinn.terra.CrystalElder.createAttributes());
		FabricDefaultAttributeRegistry.register(RIFT_HOUND, life.shizune.mistinn.rift.RiftHound.createAttributes());
		FabricDefaultAttributeRegistry.register(CRYSTAL_LIZARD, life.shizune.mistinn.terra.CrystalLizard.createAttributes());
		FabricDefaultAttributeRegistry.register(GATE_SENTRY, life.shizune.mistinn.terra.GateSentry.createAttributes());
		FabricDefaultAttributeRegistry.register(JADE_ANTELOPE, life.shizune.mistinn.terra.JadeAntelope.createAttributes());
		// jade-horn antelopes graze the valleys' jade turf in small herds
		life.shizune.mistinn.mixin.SpawnPlacementsInvoker.mistinn$register(JADE_ANTELOPE, net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
			net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, life.shizune.mistinn.terra.JadeAntelope::checkSpawnRules);
		// crystal drakes come of themselves under the open sky of a Terra highland, day or night
		life.shizune.mistinn.mixin.SpawnPlacementsInvoker.mistinn$register(CRYSTAL_DRAKE, net.minecraft.world.entity.SpawnPlacementTypes.NO_RESTRICTIONS,
			net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, life.shizune.mistinn.terra.CrystalDrake::checkSpawnRules);
		// rift walkers come of themselves where a rift has scarred the ground, in the dark like any monster
		life.shizune.mistinn.mixin.SpawnPlacementsInvoker.mistinn$register(RIFT_WALKER, net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
			net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules);
		// rift hounds run the scarred ground in packs, in the dark
		life.shizune.mistinn.mixin.SpawnPlacementsInvoker.mistinn$register(RIFT_HOUND, net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
			net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.minecraft.world.entity.monster.Monster::checkMonsterSpawnRules);
		// crystal lizards keep to the ground of a Terra highland in threes, day or night
		life.shizune.mistinn.mixin.SpawnPlacementsInvoker.mistinn$register(CRYSTAL_LIZARD, net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
			net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, life.shizune.mistinn.terra.CrystalLizard::checkSpawnRules);
	}
}
