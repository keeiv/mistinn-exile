package life.shizune.mistinn.registry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.gear.Gear;
import life.shizune.mistinn.salt.SaltItem;
import life.shizune.mistinn.tier.Tier;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.network.chat.Component;
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

public final class MistinnItems {
	private static final Identifier REACH_ID = Mistinn.id("weapon_reach");
	private static final Identifier KNOCKBACK_ID = Mistinn.id("weapon_knockback");
	/** Salt mends the hunter's gear at an anvil. */
	private static final TagKey<Item> REPAIRS_GEAR = TagKey.create(Registries.ITEM, Mistinn.id("repairs_hunter_gear"));

	/** 鹽: the currency is a number on the player; a salt pouch found in the world adds to it when used. */
	public static final Item SALT = register("salt", SaltItem::new, new Item.Properties());
	/** 鏽神的鹽塊: what the Lighthouse Keepers guard. */
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

	/** 追獵者手冊: the world's story and the rules of the hunt, given to every hunter on arrival. */
	/** Pages of the guide's text (item.mistinn.hunter_guide.page.N), read as spreads in its screen. */
	public static final int GUIDE_PAGES = 15;
	public static final Item HUNTER_GUIDE = register("hunter_guide", life.shizune.mistinn.story.HunterGuideItem::new, new Item.Properties().stacksTo(1));

	public static final Item GOLDEN_BELL = block("golden_bell", MistinnBlocks.GOLDEN_BELL, new Item.Properties().rarity(Rarity.RARE));
	public static final Item ORE_BELL = block("ore_bell", MistinnBlocks.ORE_BELL, new Item.Properties());
	public static final Item LAMP_STAND = block("lamp_stand", MistinnBlocks.LAMP_STAND, new Item.Properties());

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

	/** Helmets, body armour and backpacks, one of each per tier, indexed by {@link Tier#ordinal()}. */
	public static final List<Item> HELMETS = Gear.HELMETS.stream().map(MistinnItems::helmet).toList();
	public static final List<Item> ARMORS = Gear.ARMORS.stream().map(MistinnItems::bodyArmor).toList();
	public static final List<Item> BACKPACKS = Gear.BACKPACKS.stream().map(MistinnItems::backpack).toList();
	/** Chest rigs and masks, one per tier, indexed by {@link Tier#ordinal()}. */
	public static final List<Item> RIGS = Gear.RIGS.stream().map(MistinnItems::rig).toList();
	public static final List<Item> MASKS = Gear.MASKS.stream().map(MistinnItems::mask).toList();
	/** Leggings and boots for tiers N to V, indexed by {@link Tier#ordinal()}. */
	public static final List<Item> LEGGINGS = Gear.LEGGINGS.stream().map(g -> legGear(g, "leggings", EquipmentSlot.LEGS, EquipmentSlotGroup.LEGS)).toList();
	public static final List<Item> BOOTS = Gear.BOOTS.stream().map(g -> legGear(g, "boots", EquipmentSlot.FEET, EquipmentSlotGroup.FEET)).toList();

	private MistinnItems() {
	}


	private static ResourceKey<EquipmentAsset> asset(final Tier tier) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, Mistinn.id("hunter_" + tier.id()));
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
		Identifier id = Mistinn.id("helmet." + gear.tier().id());
		ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
			.add(Attributes.ARMOR, new AttributeModifier(id, gear.armor(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
			.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, gear.toughness(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
			.build();
		return register("helmet_" + gear.tier().id(), Item::new, new Item.Properties()
			.durability(gear.durability())
			.attributes(attributes)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).setEquipSound(equipSound(gear.tier())).setAsset(asset(gear.tier())).build())
			.repairable(REPAIRS_GEAR)
			.enchantable(10));
	}

	private static Item bodyArmor(final Gear.BodyArmor gear) {
		Identifier id = Mistinn.id("armor." + gear.tier().id());
		ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder()
			.add(Attributes.ARMOR, new AttributeModifier(id, gear.armor(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
			.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, gear.toughness(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST);
		if (gear.knockbackResistance() > 0.0F) {
			attributes.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, gear.knockbackResistance(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST);
		}
		if (gear.speedPenalty() > 0.0) {
			attributes.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(id, -gear.speedPenalty(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.CHEST);
		}
		return register("armor_" + gear.tier().id(), Item::new, new Item.Properties()
			.durability(gear.durability())
			.attributes(attributes.build())
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(equipSound(gear.tier())).setAsset(asset(gear.tier())).build())
			.repairable(REPAIRS_GEAR)
			.enchantable(10));
	}

	/** Padded cloth for the lowest tiers, plates from II up. */
	private static Holder<SoundEvent> equipSound(final Tier tier) {
		return tier.ordinal() <= Tier.I.ordinal() ? MistinnSounds.EQUIP_CLOTH : MistinnSounds.EQUIP_PLATE;
	}

	private static Item backpack(final Gear.Backpack gear) {
		return register("backpack_" + gear.tier().id(), Item::new, new Item.Properties().stacksTo(1));
	}

	private static Item rig(final Gear.ChestRig gear) {
		return register("rig_" + gear.tier().id(), Item::new, new Item.Properties().stacksTo(1));
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
