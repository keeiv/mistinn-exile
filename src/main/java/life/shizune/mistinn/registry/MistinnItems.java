package life.shizune.mistinn.registry;

import java.util.function.Function;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.weapon.WeaponStyle;
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

	/** 鹽: the currency. Kept as a number on the player; the item is what a pouch of it looks like. */
	public static final Item SALT = register("salt", Item::new, new Item.Properties());
	/** 鏽神的鹽塊: what the Lighthouse Keepers guard. */
	public static final Item RUST_GOD_SALT = register("rust_god_salt", Item::new, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant());

	public static final Item ANCHOR_BLADE = weapon("anchor_blade", WeaponStyle.SLASH, 750);
	public static final Item BELL_GREATSWORD = weapon("bell_greatsword", WeaponStyle.CLEAVE, 1100);
	public static final Item MIST_SPEAR = weapon("mist_spear", WeaponStyle.THRUST, 800);
	public static final Item SALT_FANG = weapon("salt_fang", WeaponStyle.FLURRY, 600);

	public static final Item GOLDEN_BELL = block("golden_bell", MistinnBlocks.GOLDEN_BELL, new Item.Properties().rarity(Rarity.RARE));
	public static final Item ORE_BELL = block("ore_bell", MistinnBlocks.ORE_BELL, new Item.Properties());

	private MistinnItems() {
	}

	private static Item weapon(final String name, final WeaponStyle style, final int durability) {
		ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder()
			.add(
				Attributes.ATTACK_DAMAGE,
				new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, style.damage, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND
			)
			.add(
				Attributes.ATTACK_SPEED,
				new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, style.attacksPerSecond - 4.0, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND
			);
		if (style.reach != 0.0) {
			attributes.add(
				Attributes.ENTITY_INTERACTION_RANGE,
				new AttributeModifier(REACH_ID, style.reach, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND
			);
		}
		Item.Properties properties = new Item.Properties()
			.durability(durability)
			.attributes(attributes.build())
			.component(DataComponents.WEAPON, new Weapon(1, style == WeaponStyle.CLEAVE ? 3.0F : 0.0F))
			.component(DataComponents.ATTACK_ANIMATION, new SwingAnimation(style.animation, style.swingTicks))
			.enchantable(14);
		return register(name, Item::new, properties);
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
