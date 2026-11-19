package life.shizune.mistinn.weapon;

import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.tier.Tier;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * The materials ordinary weapons are made of, one for each stage of the game: the same ladder as
 * vanilla tools, so a hunter always has a weapon of every branch at hand.
 */
public enum WeaponMaterial {
	WOOD("wooden", 0.0, 59, Tier.N, "wooden_tool_materials", 1.08F),
	STONE("stone", 1.0, 131, Tier.N, "stone_tool_materials", 1.0F),
	IRON("iron", 2.0, 250, Tier.I, "iron_tool_materials", 1.0F),
	DIAMOND("diamond", 3.0, 1561, Tier.IV, "diamond_tool_materials", 1.04F),
	NETHERITE("netherite", 4.0, 2031, Tier.V, "netherite_tool_materials", 0.92F);

	/** Item id prefix: wooden_blade, stone_blade, ... */
	public final String prefix;
	/** Damage added on top of the branch's base. */
	public final double bonus;
	public final int durability;
	public final Tier tier;
	public final TagKey<Item> repair;
	/** Swing pitch: light wood whistles higher, netherite drags lower. */
	public final float pitch;

	WeaponMaterial(final String prefix, final double bonus, final int durability, final Tier tier, final String repairTag, final float pitch) {
		this.prefix = prefix;
		this.bonus = bonus;
		this.durability = durability;
		this.tier = tier;
		this.repair = TagKey.create(Registries.ITEM, net.minecraft.resources.Identifier.withDefaultNamespace(repairTag));
		this.pitch = pitch;
	}

	/** Each material sounds different on impact: wood thuds, stone cracks, metal rings. */
	public SoundEvent hitSound() {
		return switch (this) {
			case WOOD -> MistinnSounds.HIT_WOOD;
			case STONE -> MistinnSounds.HIT_STONE;
			case IRON -> MistinnSounds.HIT_IRON;
			case DIAMOND -> MistinnSounds.HIT_DIAMOND;
			case NETHERITE -> MistinnSounds.HIT_NETHERITE;
		};
	}
}
