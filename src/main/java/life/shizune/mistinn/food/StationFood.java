package life.shizune.mistinn.food;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import org.jspecify.annotations.Nullable;

/**
 * Rust Anchor Station's food: what its cook makes of what the coast gives. Scraps come off the
 * animals (in a Rust Anchor world, in place of their meat), the fire makes them fit to eat, and
 * the rest is cooked from them or found in the station's crates. In a Rust Anchor world it is the
 * only food a hunter can eat.
 */
public enum StationFood {
	// raw scraps: little in them, and a raw stomach may turn
	PIG_FAT("pig_fat", 2, 0.1F, 1.6F, MobEffects.HUNGER, 600, 0.3F, false),
	BEEF_TALLOW("beef_tallow", 2, 0.1F, 1.6F, MobEffects.HUNGER, 600, 0.3F, false),
	BEEF_SINEW("beef_sinew", 1, 0.3F, 3.2F, null, 0, 0.0F, false),
	MUTTON_FAT("mutton_fat", 2, 0.1F, 1.6F, MobEffects.HUNGER, 600, 0.3F, false),
	CHICKEN_SCRAPS("chicken_scraps", 1, 0.1F, 1.6F, MobEffects.HUNGER, 600, 0.4F, false),
	FISH_OFFAL("fish_offal", 1, 0.1F, 1.6F, MobEffects.NAUSEA, 160, 0.5F, false),
	// cooked over a fire
	PORK_CRACKLINGS("pork_cracklings", 4, 0.4F, 1.6F, null, 0, 0.0F, false),
	TALLOW_CRACKLINGS("tallow_cracklings", 4, 0.4F, 1.6F, null, 0, 0.0F, false),
	ROASTED_SINEW("roasted_sinew", 3, 0.8F, 3.2F, null, 0, 0.0F, false),
	ROASTED_MUTTON_FAT("roasted_mutton_fat", 4, 0.3F, 1.6F, null, 0, 0.0F, false),
	ROASTED_CHICKEN_SCRAPS("roasted_chicken_scraps", 3, 0.5F, 1.6F, null, 0, 0.0F, false),
	GRILLED_OFFAL("grilled_offal", 3, 0.3F, 1.6F, null, 0, 0.0F, false),
	// made at a crafting table
	HARDTACK("hardtack", 3, 0.5F, 2.4F, null, 0, 0.0F, false),
	KELP_CAKE("kelp_cake", 3, 0.3F, 1.6F, null, 0, 0.0F, false),
	DRIPPINGS_PORRIDGE("drippings_porridge", 6, 0.6F, 1.6F, null, 0, 0.0F, true),
	BONE_BROTH("bone_broth", 5, 0.5F, 1.6F, null, 0, 0.0F, true),
	OFFAL_SOUP("offal_soup", 6, 0.5F, 1.6F, null, 0, 0.0F, true),
	MUSHROOM_MASH("mushroom_mash", 5, 0.5F, 1.6F, null, 0, 0.0F, true),
	// the station's own, found in its crates
	RATION_BISCUIT("ration_biscuit", 5, 0.6F, 1.6F, null, 0, 0.0F, false),
	RUST_CAN("rust_can", 8, 0.7F, 2.0F, MobEffects.POISON, 100, 0.15F, false);

	public final String id;
	public final int nutrition;
	/** Saturation per point of nutrition, as vanilla's foods give it. */
	public final float saturation;
	public final float seconds;
	public final @Nullable Holder<MobEffect> effect;
	public final int effectTicks;
	public final float effectChance;
	/** A bowl food: one at a time, and the bowl comes back. */
	public final boolean bowl;

	StationFood(final String id, final int nutrition, final float saturation, final float seconds, final @Nullable Holder<MobEffect> effect,
		final int effectTicks, final float effectChance, final boolean bowl) {
		this.id = id;
		this.nutrition = nutrition;
		this.saturation = saturation;
		this.seconds = seconds;
		this.effect = effect;
		this.effectTicks = effectTicks;
		this.effectChance = effectChance;
		this.bowl = bowl;
	}

	/** The item's properties: its food, how long it takes to eat, and what it may do to the stomach. */
	public Item.Properties properties() {
		FoodProperties food = new FoodProperties.Builder().nutrition(this.nutrition).saturationModifier(this.saturation).build();
		Consumable.Builder eating = Consumables.defaultFood().consumeSeconds(this.seconds);
		if (this.effect != null) {
			eating.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(this.effect, this.effectTicks, 0), this.effectChance));
		}
		Item.Properties properties = new Item.Properties().food(food, eating.build());
		if (this.bowl) {
			properties.stacksTo(1).usingConvertsTo(Items.BOWL);
		}
		return properties;
	}
}
