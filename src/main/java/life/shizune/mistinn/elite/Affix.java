package life.shizune.mistinn.elite;

import life.shizune.mistinn.Mistinn;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enderman;
import org.jspecify.annotations.Nullable;

/**
 * 詞綴: what makes an elite more than its kind. Some change its body for good (attribute modifiers
 * under fixed ids, saved with it); the rest are things it does, kept by {@link EliteService}.
 */
public enum Affix {
	/** 迅捷: a third faster on its feet. */
	SWIFT("swift"),
	/** 鐵皮: armour and a stance blows hardly move. */
	IRONCLAD("ironclad"),
	/** 巨大: a third larger, near twice the life, heavier blows, a little slower. */
	HULKING("hulking"),
	/** 嗜血: its blows always open a bleeding wound, and what it takes heals it. */
	BLOODTHIRSTY("bloodthirsty"),
	/** 霧行: struck, it may go into the fog and step out beside or behind the hunter. */
	MISTWALKER("mistwalker"),
	/** 鹽爆: a breath after it dies, it bursts in a blast of salt. */
	VOLATILE("volatile"),
	/** 號令: the creatures round it go faster and harder, and after its quarry. */
	WARCHIEF("warchief");

	public final String id;

	Affix(final String id) {
		this.id = id;
	}

	public Component title() {
		return Component.translatable("elite.mistinn.affix." + this.id);
	}

	public static @Nullable Affix byId(final String id) {
		for (Affix affix : values()) {
			if (affix.id.equals(id)) {
				return affix;
			}
		}
		return null;
	}

	/** Whether this suits the creature: an enderman already walks in and out of the world. */
	public boolean fits(final Mob mob) {
		return this != MISTWALKER || !(mob instanceof Enderman);
	}

	/** Lays this affix's lasting changes on the creature's body; never twice (the ids are fixed). */
	void apply(final Mob mob) {
		switch (this) {
			case SWIFT -> add(mob, Attributes.MOVEMENT_SPEED, "speed", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			case IRONCLAD -> {
				add(mob, Attributes.ARMOR, "armor", 8.0, AttributeModifier.Operation.ADD_VALUE);
				add(mob, Attributes.KNOCKBACK_RESISTANCE, "knockback", 0.6, AttributeModifier.Operation.ADD_VALUE);
			}
			case HULKING -> {
				add(mob, Attributes.SCALE, "scale", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				add(mob, Attributes.MAX_HEALTH, "health", 0.8, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				add(mob, Attributes.ATTACK_DAMAGE, "damage", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				add(mob, Attributes.MOVEMENT_SPEED, "speed", -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				add(mob, Attributes.KNOCKBACK_RESISTANCE, "knockback", 0.4, AttributeModifier.Operation.ADD_VALUE);
			}
			default -> {
			}
		}
	}

	private void add(final Mob mob, final Holder<Attribute> attribute, final String what, final double amount, final AttributeModifier.Operation operation) {
		EliteService.modify(mob, attribute, Mistinn.id("elite_" + this.id + "_" + what), amount, operation);
	}
}
