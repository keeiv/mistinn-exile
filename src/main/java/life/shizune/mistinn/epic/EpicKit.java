package life.shizune.mistinn.epic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import life.shizune.mistinn.gear.Gear;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.medical.StarterKit;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.skill.SkillTree;
import life.shizune.mistinn.skill.Skills;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * What hunter No. 3412 wakes with in the epic: everything the station ever gave them, the best of it, and well kept.
 * The highest helmet, mask, armour, leggings, boots, chest rig and pack there are; the six branches of the station's
 * arms in netherite and a bow, with three kinds of element arrows; medicine and food. Every piece is enchanted, and
 * every skill is learned: they are the anchor's apostle, fully trained.
 */
public final class EpicKit {
	private EpicKit() {
	}

	/** Dresses and arms the hunter, fills their pockets and teaches them everything. */
	public static void give(final ServerPlayer player) {
		ServerLevel level = player.level();
		player.setItemSlot(EquipmentSlot.HEAD, armour(level, best(MistinnItems.HELMETS, Gear.HELMETS, h -> h.tier().ordinal()), EquipmentSlot.HEAD));
		player.setItemSlot(EquipmentSlot.CHEST, armour(level, best(MistinnItems.ARMORS, Gear.ARMORS, a -> a.tier().ordinal()), EquipmentSlot.CHEST));
		player.setItemSlot(EquipmentSlot.LEGS, armour(level, best(MistinnItems.LEGGINGS, Gear.LEGGINGS, l -> l.tier().ordinal()), EquipmentSlot.LEGS));
		player.setItemSlot(EquipmentSlot.FEET, armour(level, best(MistinnItems.BOOTS, Gear.BOOTS, b -> b.tier().ordinal()), EquipmentSlot.FEET));
		GridInventory grid = GridService.get(player)
			.withMask(kept(level, best(MistinnItems.MASKS, Gear.MASKS, m -> m.tier().ordinal())))
			.withRig(kept(level, best(MistinnItems.RIGS, Gear.RIGS, r -> r.tier().ordinal())))
			.withBackpack(kept(level, best(MistinnItems.BACKPACKS, Gear.BACKPACKS, b -> b.tier().ordinal())));
		GridService.set(player, grid);

		// the hotbar: the six branches, then the bow
		int slot = 0;
		for (WeaponStyle style : WeaponStyle.values()) {
			player.getInventory().setItem(slot++, weapon(level, MistinnItems.WEAPONS.get(style).get(WeaponMaterial.NETHERITE)));
		}
		player.getInventory().setItem(slot, bow(level));

		List<ItemStack> carried = new ArrayList<>();
		carried.add(new ItemStack(MistinnItems.BLAST_ARROW, 32));
		carried.add(new ItemStack(MistinnItems.FROST_ARROW, 32));
		carried.add(new ItemStack(MistinnItems.SHOCK_ARROW, 32));
		carried.add(new ItemStack(Items.ARROW));
		carried.addAll(StarterKit.kit());
		carried.addAll(StarterKit.kit());
		carried.add(new ItemStack(Items.COOKED_BEEF, 32));
		carried.add(new ItemStack(Items.BREAD, 16));
		carried.add(new ItemStack(Items.TORCH, 32));
		for (ItemStack stack : carried) {
			ItemStack giving = stack.copy();
			GridService.insert(player, giving);
			if (!giving.isEmpty() && !player.getInventory().add(giving)) {
				player.drop(giving, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}

		// fully trained: every skill on the tree
		SkillService.set(player, new Skills(SkillTree.NODES.stream().map(SkillTree.Node::id).collect(Collectors.toSet())));
		SkillService.refresh(player);
	}

	/** The item at the highest tier of a list of gear (the first of equals). */
	private static <G> Item best(final List<Item> items, final List<G> gear, final ToIntFunction<G> tier) {
		int at = 0;
		for (int i = 1; i < gear.size(); i++) {
			if (tier.applyAsInt(gear.get(i)) > tier.applyAsInt(gear.get(at))) {
				at = i;
			}
		}
		return items.get(at);
	}

	private static ItemStack armour(final ServerLevel level, final Item item, final EquipmentSlot slot) {
		ItemStack stack = kept(level, item);
		enchant(level, stack, Enchantments.PROTECTION, 4);
		switch (slot) {
			case HEAD -> {
				enchant(level, stack, Enchantments.RESPIRATION, 3);
				enchant(level, stack, Enchantments.AQUA_AFFINITY, 1);
			}
			case FEET -> {
				enchant(level, stack, Enchantments.FEATHER_FALLING, 4);
				enchant(level, stack, Enchantments.DEPTH_STRIDER, 3);
			}
			case LEGS -> enchant(level, stack, Enchantments.SWIFT_SNEAK, 3);
			default -> {
			}
		}
		return stack;
	}

	private static ItemStack weapon(final ServerLevel level, final Item item) {
		ItemStack stack = kept(level, item);
		enchant(level, stack, Enchantments.SHARPNESS, 5);
		enchant(level, stack, Enchantments.FIRE_ASPECT, 2);
		enchant(level, stack, Enchantments.LOOTING, 3);
		return stack;
	}

	private static ItemStack bow(final ServerLevel level) {
		ItemStack stack = new ItemStack(Items.BOW);
		enchant(level, stack, Enchantments.POWER, 5);
		enchant(level, stack, Enchantments.PUNCH, 1);
		enchant(level, stack, Enchantments.FLAME, 1);
		enchant(level, stack, Enchantments.INFINITY, 1);
		enchant(level, stack, Enchantments.UNBREAKING, 3);
		return stack;
	}

	/** A piece that lasts: unbreaking and mending. */
	private static ItemStack kept(final ServerLevel level, final Item item) {
		ItemStack stack = new ItemStack(item);
		enchant(level, stack, Enchantments.UNBREAKING, 3);
		enchant(level, stack, Enchantments.MENDING, 1);
		return stack;
	}

	private static void enchant(final ServerLevel level, final ItemStack stack, final ResourceKey<Enchantment> key, final int strength) {
		level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).ifPresent(holder -> stack.enchant(holder, strength));
	}
}
