package life.shizune.mistinn.registry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.gear.Gear;
import life.shizune.mistinn.salt.SaltItem;
import life.shizune.mistinn.tier.Tier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import life.shizune.mistinn.weapon.Weapons;
import life.shizune.mistinn.weapon.SpecialWeapon;
import life.shizune.mistinn.weapon.WeaponItem;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.block.Block;

/** The mod's items: salt, weapons, armour and packs by tier, medicine and repair kits, and the hunter's guide. */
public final class MistinnItems {
	private static final Identifier REACH_ID = Mistinn.id("weapon_reach");
	private static final Identifier KNOCKBACK_ID = Mistinn.id("weapon_knockback");
	/** Salt mends the hunter's gear at an anvil. */
	private static final TagKey<Item> REPAIRS_GEAR = TagKey.create(Registries.ITEM, Mistinn.id("repairs_hunter_gear"));

	/** 鹽: the currency is a number on the player; a salt pouch found in the world adds to it when used. */
	public static final Item SALT = register("salt", SaltItem::new, new Item.Properties());
	/** 燈油: lighthouse lamp oil. Lamp thralls carry it; it lights the golden bell halfway up a lighthouse. */
	public static final Item LAMP_OIL = register("lamp_oil", Item::new, new Item.Properties().stacksTo(16));
	/** 火槍 and 手槍: the pirates' guns; 紙彈 they fire; 炮彈 from a ship's guns. */
	public static final Item FLINTLOCK = register("flintlock", life.shizune.mistinn.naval.FlintlockItem::musket, new Item.Properties()
		.durability(160).rarity(Rarity.UNCOMMON).enchantable(10));
	public static final Item FLINTLOCK_PISTOL = register("flintlock_pistol", life.shizune.mistinn.naval.FlintlockItem::pistol, new Item.Properties()
		.durability(120).rarity(Rarity.RARE).enchantable(10));
	public static final Item PAPER_CARTRIDGE = register("paper_cartridge", Item::new, new Item.Properties());
	public static final Item CANNONBALL = register("cannonball", Item::new, new Item.Properties().stacksTo(16));
	/** 鹽彈, 信號焰, 響鈴誘餌: thrown, they burst, burn or ring where they come down ({@link life.shizune.mistinn.tool.ThrowService}). */
	public static final Item SALT_BOMB = register("salt_bomb", life.shizune.mistinn.tool.ThrowableToolItem::new, new Item.Properties().stacksTo(8));
	public static final Item FLARE = register("flare", life.shizune.mistinn.tool.ThrowableToolItem::new, new Item.Properties().stacksTo(8));
	public static final Item BELL_LURE = register("bell_lure", life.shizune.mistinn.tool.ThrowableToolItem::new, new Item.Properties().stacksTo(4));
	public static final Item RUST_GOD_SALT = register("rust_god_salt", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant());

	/** The named weapons: each a branch's own, with sounds made for it. */
	public static final Item ANCHOR_BLADE = named("anchor_blade", WeaponStyle.SLASH, 6.0, 0.0, 750, MistinnSounds.ANCHOR_BLADE_SWING, MistinnSounds.ANCHOR_BLADE_HIT);
	public static final Item BELL_GREATSWORD = named("bell_greatsword", WeaponStyle.CLEAVE, 11.0, 0.5, 1100, MistinnSounds.BELL_GREATSWORD_SWING, MistinnSounds.BELL_GREATSWORD_HIT);
	public static final Item MIST_SPEAR = named("mist_spear", WeaponStyle.THRUST, 7.0, 1.5, 800, MistinnSounds.MIST_SPEAR_SWING, MistinnSounds.MIST_SPEAR_HIT);
	public static final Item SALT_FANG = named("salt_fang", WeaponStyle.FLURRY, 4.0, -0.4, 600, MistinnSounds.SALT_FANG_SWING, MistinnSounds.SALT_FANG_HIT);

	/** Every branch in every material: wooden_blade ... netherite_scythe. */
	public static final Map<WeaponStyle, Map<WeaponMaterial, Item>> WEAPONS = materialWeapons();

	/** The special weapons: each answers only a hunter who meets its requirements. */
	public static final Item SNUFFER_HOOK = special(SpecialWeapon.SNUFFER_HOOK, WeaponStyle.REAP, 7.0, 1.0, 900, MistinnSounds.SCYTHE_SWING, MistinnSounds.HIT_IRON);
	public static final Item LANTERN_STAFF = special(SpecialWeapon.LANTERN_STAFF, WeaponStyle.THRUST, 5.0, 1.5, 700, MistinnSounds.MIST_SPEAR_SWING, MistinnSounds.MIST_SPEAR_HIT);
	public static final Item TIDE_MAUL = special(SpecialWeapon.TIDE_MAUL, WeaponStyle.SMASH, 12.0, 0.3, 1400, MistinnSounds.HAMMER_SWING, MistinnSounds.BELL_GREATSWORD_HIT);
	public static final Item BELL_CLAPPER = special(SpecialWeapon.BELL_CLAPPER, WeaponStyle.SMASH, 10.0, 0.5, 1200, MistinnSounds.HAMMER_SWING, MistinnSounds.BELL_GREATSWORD_HIT);
	public static final Item RUST_RIM = special(SpecialWeapon.RUST_RIM, WeaponStyle.CLEAVE, 11.0, 0.5, 1300, MistinnSounds.BELL_GREATSWORD_SWING, MistinnSounds.HIT_IRON);

	/** Rust Anchor Station's food, one item each. */
	public static final Map<life.shizune.mistinn.food.StationFood, Item> FOODS = stationFoods();
	/** 受潮的食物: ordinary food in a hunter's hands in a Rust Anchor world. Eating it shrinks the stomach for good. */
	public static final Item DAMP_FOOD = register("damp_food", life.shizune.mistinn.food.DampFoodItem::new, new Item.Properties()
		.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(0.0F).build(),
			net.minecraft.world.item.component.Consumables.defaultFood()
				.onConsume(new net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect(
					new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NAUSEA, 200, 0), 0.4F))
				.build()));

	/** 舊編號牌: a hunter's tag found under a cairn of the salt road; its number and last words are on the stack. */
	public static final Item HUNTER_TAG = register("hunter_tag", Item::new, new Item.Properties().stacksTo(1));

	/** Pages of the guide's text (item.mistinn.hunter_guide.page.N), read as spreads in its screen. */
	public static final int GUIDE_PAGES = 46;
	public static final Item HUNTER_GUIDE = register("hunter_guide", life.shizune.mistinn.story.HunterGuideItem::new, new Item.Properties().stacksTo(1));

	public static final Item GOLDEN_BELL = block("golden_bell", MistinnBlocks.GOLDEN_BELL, new Item.Properties().rarity(Rarity.RARE));
	public static final Item ORE_BELL = block("ore_bell", MistinnBlocks.ORE_BELL, new Item.Properties());
	public static final Item LAMP_STAND = block("lamp_stand", MistinnBlocks.LAMP_STAND, new Item.Properties());
	public static final Item MARKER_BEACON = block("marker_beacon", MistinnBlocks.MARKER_BEACON, new Item.Properties().stacksTo(16));
	public static final Item REDSTONE_SOIL = block("redstone_soil", MistinnBlocks.REDSTONE_SOIL, new Item.Properties());
	public static final Item GRASS_REDSTONE_SOIL = block("grass_redstone_soil", MistinnBlocks.GRASS_REDSTONE_SOIL, new Item.Properties());
	public static final Item RIFT_TURF = block("rift_turf", MistinnBlocks.RIFT_TURF, new Item.Properties());
	public static final Item RIFT_SOIL = block("rift_soil", MistinnBlocks.RIFT_SOIL, new Item.Properties());
	public static final Item SCORCHED_STONE = block("scorched_stone", MistinnBlocks.SCORCHED_STONE, new Item.Properties());
	public static final Item CRIMSON_MOSS = block("crimson_moss", MistinnBlocks.CRIMSON_MOSS, new Item.Properties());
	public static final Item CRIMSON_MOSS_CARPET = block("crimson_moss_carpet", MistinnBlocks.CRIMSON_MOSS_CARPET, new Item.Properties());
	public static final Item BLOOD_THORN = block("blood_thorn", MistinnBlocks.BLOOD_THORN, new Item.Properties());
	public static final Item RIFT_CRYSTAL_BUD = block("rift_crystal_bud", MistinnBlocks.RIFT_CRYSTAL_BUD, new Item.Properties());
	public static final Item RIFT_CRYSTAL_CLUSTER = block("rift_crystal_cluster", MistinnBlocks.RIFT_CRYSTAL_CLUSTER, new Item.Properties());
	public static final Item RIFT_CRYSTAL_BLOCK = block("rift_crystal_block", MistinnBlocks.RIFT_CRYSTAL_BLOCK, new Item.Properties());
	public static final Item CHARRED_LOG = block("charred_log", MistinnBlocks.CHARRED_LOG, new Item.Properties());
	public static final Item JADE_TURF = block("jade_turf", MistinnBlocks.JADE_TURF, new Item.Properties());
	public static final Item VEIN_SOIL = block("vein_soil", MistinnBlocks.VEIN_SOIL, new Item.Properties());
	public static final Item VEIN_STONE = block("vein_stone", MistinnBlocks.VEIN_STONE, new Item.Properties());
	public static final Item VEIN_STONE_POLISHED = block("vein_stone_polished", MistinnBlocks.VEIN_STONE_POLISHED, new Item.Properties());
	public static final Item VEIN_STONE_BRICKS = block("vein_stone_bricks", MistinnBlocks.VEIN_STONE_BRICKS, new Item.Properties());
	public static final Item VEIN_CRYSTAL_ORE = block("vein_crystal_ore", MistinnBlocks.VEIN_CRYSTAL_ORE, new Item.Properties());
	public static final Item MIST_PINE_LOG = block("mist_pine_log", MistinnBlocks.MIST_PINE_LOG, new Item.Properties());
	public static final Item MIST_PINE_PLANKS = block("mist_pine_planks", MistinnBlocks.MIST_PINE_PLANKS, new Item.Properties());
	public static final Item MIST_PINE_LEAVES = block("mist_pine_leaves", MistinnBlocks.MIST_PINE_LEAVES, new Item.Properties());
	public static final Item JADE_GRASS = block("jade_grass", MistinnBlocks.JADE_GRASS, new Item.Properties());
	public static final Item JADE_FERN = block("jade_fern", MistinnBlocks.JADE_FERN, new Item.Properties());
	public static final Item MIST_REED = block("mist_reed", MistinnBlocks.MIST_REED, new Item.Properties());
	public static final Item CRYSTAL_SPROUT = block("crystal_sprout", MistinnBlocks.CRYSTAL_SPROUT, new Item.Properties());
	public static final Item TERRA_CRYSTAL_SMALL_BUD = block("terra_crystal_small_bud", MistinnBlocks.TERRA_CRYSTAL_SMALL_BUD, new Item.Properties());
	public static final Item TERRA_CRYSTAL_MEDIUM_BUD = block("terra_crystal_medium_bud", MistinnBlocks.TERRA_CRYSTAL_MEDIUM_BUD, new Item.Properties());
	public static final Item TERRA_CRYSTAL_LARGE_BUD = block("terra_crystal_large_bud", MistinnBlocks.TERRA_CRYSTAL_LARGE_BUD, new Item.Properties());
	public static final Item TERRA_CRYSTAL_CLUSTER = block("terra_crystal_cluster", MistinnBlocks.TERRA_CRYSTAL_CLUSTER, new Item.Properties());
	public static final Item TERRA_CRYSTAL_BLOCK = block("terra_crystal_block", MistinnBlocks.TERRA_CRYSTAL_BLOCK, new Item.Properties());
	public static final Item LATTICE_CRYSTAL = block("lattice_crystal", MistinnBlocks.LATTICE_CRYSTAL, new Item.Properties());
	public static final Item LATTICE_CRYSTAL_DIM = block("lattice_crystal_dim", MistinnBlocks.LATTICE_CRYSTAL_DIM, new Item.Properties());
	// the facilities' items exist only for those who build worlds by command; no one can get them in play
	public static final Item FACILITY_DECK = block("facility_deck", MistinnBlocks.FACILITY_DECK, new Item.Properties());
	public static final Item FACILITY_DECK_EDGE = block("facility_deck_edge", MistinnBlocks.FACILITY_DECK_EDGE, new Item.Properties());
	public static final Item FACILITY_DECK_SLAB = block("facility_deck_slab", MistinnBlocks.FACILITY_DECK_SLAB, new Item.Properties());
	public static final Item FACILITY_PANEL = block("facility_panel", MistinnBlocks.FACILITY_PANEL, new Item.Properties());
	public static final Item FACILITY_FRAME = block("facility_frame", MistinnBlocks.FACILITY_FRAME, new Item.Properties());
	public static final Item FACILITY_CONDUIT = block("facility_conduit", MistinnBlocks.FACILITY_CONDUIT, new Item.Properties());
	public static final Item FACILITY_LAMP = block("facility_lamp", MistinnBlocks.FACILITY_LAMP, new Item.Properties());
	public static final Item FACILITY_CRATE = block("facility_crate", MistinnBlocks.FACILITY_CRATE, new Item.Properties());
	public static final Item FACILITY_GLASS = block("facility_glass", MistinnBlocks.FACILITY_GLASS, new Item.Properties());
	public static final Item HAZARD_STRIPE = block("hazard_stripe", MistinnBlocks.HAZARD_STRIPE, new Item.Properties());
	public static final Item HUB_CORE = block("hub_core", MistinnBlocks.HUB_CORE, new Item.Properties());
	/** 晶屑: crystal grit, from the veins, the crop and the crystal dragons. */
	public static final Item CRYSTAL_DUST = register("crystal_dust", Item::new, new Item.Properties());
	/** 晶核: the heart of a crystal dragon. */
	public static final Item CRYSTAL_CORE = register("crystal_core", Item::new, new Item.Properties().rarity(Rarity.RARE));
	/** 歸途晶片: a wafer of cut crystal in a frame, the valley people's key. */
	public static final Item LATTICE_CHIP = register("lattice_chip", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item CRYSTAL_WHETSTONE = register("crystal_whetstone", life.shizune.mistinn.terra.TillerTools.Whetstone::new,
		new Item.Properties().stacksTo(16));
	public static final Item RESONANCE_FORK = register("resonance_fork", life.shizune.mistinn.terra.TillerTools.Fork::new,
		new Item.Properties().durability(64).rarity(Rarity.UNCOMMON));
	/** 裂晶: a shard of the sky rift's crystal, from its clusters and its creatures. */
	public static final Item RIFT_SHARD = register("rift_shard", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON));

	/** 護甲維修包 / 頭盔維修包 for tiers III to VI, indexed by {@code tier.ordinal() - 3}. */
	public static final List<Item> ARMOR_REPAIR_KITS = repairKits("armor_repair_kit", EquipmentSlot.CHEST);
	public static final List<Item> HELMET_REPAIR_KITS = repairKits("helmet_repair_kit", EquipmentSlot.HEAD);
	/** 強效注射器: one dose. */
	public static final Item POTENT_INJECTOR = register("potent_injector", life.shizune.mistinn.medical.InjectorItem::new, new Item.Properties()
		.stacksTo(3)
		.rarity(Rarity.UNCOMMON));
	/** 手術包: three operations. */
	public static final Item SURGERY_KIT = register("surgery_kit", life.shizune.mistinn.medical.SurgeryKitItem::new, new Item.Properties()
		.durability(life.shizune.mistinn.medical.SurgeryKitItem.USES)
		.rarity(Rarity.RARE));
	/** 止疼片: a bottle of four doses. */
	public static final Item PAINKILLERS = register("painkillers", life.shizune.mistinn.medical.PainkillerItem::new, new Item.Properties()
		.durability(life.shizune.mistinn.medical.PainkillerItem.DOSES));

	/** 止血帶: stops one part bleeding. */
	public static final Item TOURNIQUET = register("tourniquet", life.shizune.mistinn.medical.TourniquetItem::new, new Item.Properties()
		.stacksTo(4));
	/** 夾板: holds one broken limb for a while. */
	public static final Item SPLINT = register("splint", life.shizune.mistinn.medical.SplintItem::new, new Item.Properties()
		.stacksTo(4));

	/**
	 * Helmets, body armour and backpacks in the order of their lists in {@link Gear}: each tier's
	 * standard issue by {@link Tier#ordinal()}, then the light line and the heavy line, N to VI.
	 */
	public static final List<Item> HELMETS = Gear.HELMETS.stream().map(MistinnItems::helmet).toList();
	public static final List<Item> ARMORS = Gear.ARMORS.stream().map(MistinnItems::bodyArmor).toList();
	public static final List<Item> BACKPACKS = Gear.BACKPACKS.stream().map(MistinnItems::backpack).toList();
	/** Chest rigs as {@link Gear#RIGS} has them (the standard issue by tier, then the heavy vests); masks, one per tier. */
	public static final List<Item> RIGS = Gear.RIGS.stream().map(MistinnItems::rig).toList();
	public static final List<Item> MASKS = Gear.MASKS.stream().map(MistinnItems::mask).toList();
	/** Leggings and boots for tiers N to V, indexed by {@link Tier#ordinal()}. */
	public static final List<Item> LEGGINGS = Gear.LEGGINGS.stream().map(g -> legGear(g, "leggings", EquipmentSlot.LEGS, EquipmentSlotGroup.LEGS)).toList();
	public static final List<Item> BOOTS = Gear.BOOTS.stream().map(g -> legGear(g, "boots", EquipmentSlot.FEET, EquipmentSlotGroup.FEET)).toList();

	private MistinnItems() {
	}

	/**
	 * Every piece of a tier a keeper may leave behind: its helmets and body armour of all three lines,
	 * its mask, its rigs (standard and heavy), and its leggings and boots where the tier has them.
	 */
	public static List<Item> gearOfTier(final int tier) {
		List<Item> pieces = new java.util.ArrayList<>();
		for (int i = 0; i < Gear.HELMETS.size(); i++) {
			if (Gear.HELMETS.get(i).tier().ordinal() == tier) {
				pieces.add(HELMETS.get(i));
			}
			if (Gear.ARMORS.get(i).tier().ordinal() == tier) {
				pieces.add(ARMORS.get(i));
			}
		}
		for (int i = 0; i < Gear.RIGS.size(); i++) {
			if (Gear.RIGS.get(i).tier().ordinal() == tier) {
				pieces.add(RIGS.get(i));
			}
		}
		pieces.add(MASKS.get(tier));
		if (tier < LEGGINGS.size()) {
			pieces.add(LEGGINGS.get(tier));
			pieces.add(BOOTS.get(tier));
		}
		return List.copyOf(pieces);
	}

	private static ResourceKey<EquipmentAsset> asset(final Tier tier) {
		return asset(Gear.Line.STANDARD, tier);
	}

	/** The worn look a line's helmet and body armour share in a tier: {@code hunter_3}, {@code hunter_heavy_3}. */
	private static ResourceKey<EquipmentAsset> asset(final Gear.Line line, final Tier tier) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, Mistinn.id(line.item("hunter", tier)));
	}

	/** The attribute modifiers' id for a piece: the standard issue keeps the ones it always had. */
	private static Identifier modifierId(final String kind, final Gear.Line line, final Tier tier) {
		return Mistinn.id(line == Gear.Line.STANDARD ? kind + "." + tier.id() : kind + "." + line.id + "." + tier.id());
	}

	private static List<Item> repairKits(final String name, final EquipmentSlot slot) {
		List<Item> kits = new java.util.ArrayList<>();
		for (Tier tier : new Tier[] {Tier.III, Tier.IV, Tier.V, Tier.VI}) {
			kits.add(register(name + "_" + tier.id(), p -> new life.shizune.mistinn.medical.RepairKitItem(p, tier, slot), new Item.Properties()
				.stacksTo(1)
				.rarity(tier == Tier.VI ? Rarity.EPIC : tier == Tier.V ? Rarity.RARE : Rarity.UNCOMMON)));
		}
		return List.copyOf(kits);
	}

	private static Item helmet(final Gear.Helmet gear) {
		Identifier id = modifierId("helmet", gear.line(), gear.tier());
		ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder()
			.add(Attributes.ARMOR, new AttributeModifier(id, gear.armor(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
			.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, gear.toughness(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD);
		if (gear.speedPenalty() > 0.0) {
			attributes.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(id, -gear.speedPenalty(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.HEAD);
		}
		if (gear.burden().swing() > 0.0F) {
			attributes.add(Attributes.ATTACK_SPEED, new AttributeModifier(id, -gear.burden().swing(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.HEAD);
		}
		Holder<SoundEvent> sound = gear.line() == Gear.Line.LIGHT ? MistinnSounds.EQUIP_CLOTH : gear.line() == Gear.Line.HEAVY ? MistinnSounds.EQUIP_HELMET
			: helmetSound(gear.tier());
		return register(gear.line().item("helmet", gear.tier()), Item::new, new Item.Properties()
			.durability(gear.durability())
			.attributes(attributes.build())
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).setEquipSound(sound).setAsset(asset(gear.line(), gear.tier())).build())
			.repairable(REPAIRS_GEAR)
			.enchantable(10));
	}

	private static Item bodyArmor(final Gear.BodyArmor gear) {
		Identifier id = modifierId("armor", gear.line(), gear.tier());
		ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder()
			.add(Attributes.ARMOR, new AttributeModifier(id, gear.armor(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
			.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, gear.toughness(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST);
		if (gear.knockbackResistance() > 0.0F) {
			attributes.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, gear.knockbackResistance(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST);
		}
		if (gear.speedPenalty() > 0.0) {
			attributes.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(id, -gear.speedPenalty(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.CHEST);
		}
		if (gear.burden().swing() > 0.0F) {
			attributes.add(Attributes.ATTACK_SPEED, new AttributeModifier(id, -gear.burden().swing(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.CHEST);
		}
		Holder<SoundEvent> sound = gear.line() == Gear.Line.LIGHT ? MistinnSounds.EQUIP_CLOTH : gear.line() == Gear.Line.HEAVY ? MistinnSounds.EQUIP_PLATE
			: equipSound(gear.tier());
		return register(gear.line().item("armor", gear.tier()), Item::new, new Item.Properties()
			.durability(gear.durability())
			.attributes(attributes.build())
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(sound).setAsset(asset(gear.line(), gear.tier())).build())
			.repairable(REPAIRS_GEAR)
			.enchantable(10));
	}

	/** A cloth cap for the lowest tiers, a hard shell knocked on and buckled from II up. */
	private static Holder<SoundEvent> helmetSound(final Tier tier) {
		return tier.ordinal() <= Tier.I.ordinal() ? MistinnSounds.EQUIP_CLOTH : MistinnSounds.EQUIP_HELMET;
	}

	/** Padded cloth for the lowest tiers, plates from II up. */
	private static Holder<SoundEvent> equipSound(final Tier tier) {
		return tier.ordinal() <= Tier.I.ordinal() ? MistinnSounds.EQUIP_CLOTH : MistinnSounds.EQUIP_PLATE;
	}

	private static Item backpack(final Gear.Backpack gear) {
		return register(gear.line().item("backpack", gear.tier()), Item::new, new Item.Properties().stacksTo(1));
	}

	private static Item rig(final Gear.ChestRig gear) {
		return register(gear.line().item("rig", gear.tier()), Item::new, new Item.Properties().stacksTo(1));
	}

	/** Masks are worn in the grid's mask slot, not a vanilla one: no equippable component. */
	private static Item mask(final Gear.Mask gear) {
		return register("mask_" + gear.tier().id(), Item::new, new Item.Properties()
			.durability(gear.durability())
			.repairable(REPAIRS_GEAR));
	}

	private static Item legGear(final Gear.LegGear gear, final String kind, final EquipmentSlot slot, final EquipmentSlotGroup group) {
		Identifier id = Mistinn.id(kind + "." + gear.tier().id());
		ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder()
			.add(Attributes.ARMOR, new AttributeModifier(id, gear.armor(), AttributeModifier.Operation.ADD_VALUE), group)
			.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, gear.toughness(), AttributeModifier.Operation.ADD_VALUE), group);
		if (gear.speedPenalty() > 0.0) {
			attributes.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(id, -gear.speedPenalty(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), group);
		}
		return register(kind + "_" + gear.tier().id(), Item::new, new Item.Properties()
			.durability(gear.durability())
			.attributes(attributes.build())
			.component(DataComponents.EQUIPPABLE, Equippable.builder(slot).setEquipSound(equipSound(gear.tier())).setAsset(asset(gear.tier())).build())
			.repairable(REPAIRS_GEAR)
			.enchantable(10));
	}

	private static Map<WeaponStyle, Map<WeaponMaterial, Item>> materialWeapons() {
		Map<WeaponStyle, Map<WeaponMaterial, Item>> all = new EnumMap<>(WeaponStyle.class);
		for (WeaponStyle style : WeaponStyle.values()) {
			Map<WeaponMaterial, Item> byMaterial = new EnumMap<>(WeaponMaterial.class);
			for (WeaponMaterial material : WeaponMaterial.values()) {
				Item.Properties properties = weaponProperties(style, style.baseDamage + material.bonus, style.reach, material.durability)
					.repairable(material.repair);
				if (material == WeaponMaterial.NETHERITE) {
					properties.fireResistant();
				}
				Item item = register(material.prefix + "_" + style.id, WeaponItem::new, properties);
				Weapons.add(item, new Weapons.Profile(style, material, style.swingSound(), material.hitSound(), material.pitch));
				byMaterial.put(material, item);
			}
			all.put(style, Map.copyOf(byMaterial));
		}
		return all;
	}

	private static Map<life.shizune.mistinn.food.StationFood, Item> stationFoods() {
		Map<life.shizune.mistinn.food.StationFood, Item> foods = new EnumMap<>(life.shizune.mistinn.food.StationFood.class);
		for (life.shizune.mistinn.food.StationFood food : life.shizune.mistinn.food.StationFood.values()) {
			foods.put(food, register(food.id, Item::new, food.properties()));
		}
		return Map.copyOf(foods);
	}

	private static Item named(final String name, final WeaponStyle style, final double damage, final double reach, final int durability, final SoundEvent swing, final SoundEvent hit) {
		Item item = register(name, WeaponItem::new, weaponProperties(style, damage, reach, durability));
		Weapons.add(item, new Weapons.Profile(style, null, swing, hit, 1.0F));
		return item;
	}

	private static Item special(final SpecialWeapon special, final WeaponStyle style, final double damage, final double reach, final int durability, final SoundEvent swing, final SoundEvent hit) {
		Item item = register(special.id, WeaponItem::new, weaponProperties(style, damage, reach, durability).rarity(Rarity.RARE));
		Weapons.add(item, new Weapons.Profile(style, null, swing, hit, 1.0F));
		special.bind(item);
		return item;
	}

	private static Item.Properties weaponProperties(final WeaponStyle style, final double damage, final double reach, final int durability) {
		ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder()
			.add(
				Attributes.ATTACK_DAMAGE,
				new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND
			)
			.add(
				Attributes.ATTACK_SPEED,
				new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, style.attacksPerSecond - 4.0, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND
			);
		if (reach != 0.0) {
			attributes.add(
				Attributes.ENTITY_INTERACTION_RANGE,
				new AttributeModifier(REACH_ID, reach, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND
			);
		}
		if (style == WeaponStyle.SMASH) {
			// the hammer's weight is in the knockback, not just the damage
			attributes.add(
				Attributes.ATTACK_KNOCKBACK,
				new AttributeModifier(KNOCKBACK_ID, 1.0, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND
			);
		}
		// the client mixins draw each branch's own swing; WHACK only sets the shared timing
		return new Item.Properties()
			.durability(durability)
			.attributes(attributes.build())
			.component(DataComponents.WEAPON, new Weapon(1, style == WeaponStyle.CLEAVE || style == WeaponStyle.SMASH ? 3.0F : 0.0F))
			.component(DataComponents.ATTACK_ANIMATION, new SwingAnimation(SwingAnimationType.WHACK, style.swingTicks))
			// charging a heavy blow: no sprinting, a slow step
			.component(DataComponents.USE_EFFECTS, new UseEffects(false, true, 0.45F))
			.enchantable(14);
	}

	private static Item block(final String name, final Block block, final Item.Properties properties) {
		return register(name, p -> new BlockItem(block, p), properties.useBlockDescriptionPrefix());
	}

	private static Item register(final String name, final Function<Item.Properties, Item> factory, final Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Mistinn.id(name));
		Item item = factory.apply(properties.setId(key));
		if (item instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, item);
		}
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void init() {
		// Loading the class registers the fields above.
	}
}
