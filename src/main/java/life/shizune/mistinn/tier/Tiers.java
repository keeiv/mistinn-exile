package life.shizune.mistinn.tier;

import java.util.IdentityHashMap;
import java.util.Map;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

/**
 * The tier of any item. The mod's own items have fixed tiers; vanilla items follow their
 * material (iron I, gold II, emerald III, diamond IV, netherite V), a few treasures are listed,
 * and the rest fall back on vanilla rarity (common N, uncommon I, rare II, epic III).
 */
public final class Tiers {
	private static final Map<Item, Tier> FIXED = new IdentityHashMap<>();

	static {
		FIXED.put(MistinnItems.SALT, Tier.N);
		FIXED.put(MistinnItems.SALT_FANG, Tier.I);
		FIXED.put(MistinnItems.ANCHOR_BLADE, Tier.II);
		FIXED.put(MistinnItems.MIST_SPEAR, Tier.II);
		FIXED.put(MistinnItems.ORE_BELL, Tier.II);
		FIXED.put(MistinnItems.BELL_GREATSWORD, Tier.III);
		FIXED.put(MistinnItems.GOLDEN_BELL, Tier.V);
		FIXED.put(MistinnItems.RUST_GOD_SALT, Tier.VI);
		for (Tier tier : Tier.values()) {
			FIXED.put(MistinnItems.HELMETS.get(tier.ordinal()), tier);
			FIXED.put(MistinnItems.ARMORS.get(tier.ordinal()), tier);
			FIXED.put(MistinnItems.BACKPACKS.get(tier.ordinal()), tier);
		}
		for (Item item : new Item[] {Items.NETHER_STAR, Items.DRAGON_EGG, Items.BEACON, Items.ENCHANTED_GOLDEN_APPLE}) {
			FIXED.put(item, Tier.VI);
		}
		for (Item item : new Item[] {Items.ELYTRA, Items.TOTEM_OF_UNDYING, Items.HEAVY_CORE, Items.MACE, Items.ANCIENT_DEBRIS, Items.TRIDENT}) {
			FIXED.put(item, Tier.V);
		}
		for (Item item : new Item[] {Items.DIAMOND_ORE, Items.DEEPSLATE_DIAMOND_ORE, Items.ECHO_SHARD}) {
			FIXED.put(item, Tier.IV);
		}
	}

	private Tiers() {
	}

	public static Tier of(final ItemStack stack) {
		return stack.isEmpty() ? Tier.N : of(stack.getItem(), stack.getRarity());
	}

	public static Tier of(final Item item, final Rarity rarity) {
		Tier fixed = FIXED.get(item);
		if (fixed != null) {
			return fixed;
		}
		String name = BuiltInRegistries.ITEM.getKey(item).getPath();
		if (name.contains("netherite")) {
			return Tier.V;
		}
		if (name.contains("diamond")) {
			return Tier.IV;
		}
		if (name.contains("emerald")) {
			return Tier.III;
		}
		if (name.startsWith("golden_") || name.startsWith("gold_") || name.equals("raw_gold")) {
			return Tier.II;
		}
		if (name.startsWith("iron_") || name.equals("raw_iron") || name.startsWith("chainmail_")) {
			return Tier.I;
		}
		return switch (rarity) {
			case COMMON -> Tier.N;
			case UNCOMMON -> Tier.I;
			case RARE -> Tier.II;
			case EPIC -> Tier.III;
		};
	}
}
