package life.shizune.mistinn.skill;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.stamina.StaminaService;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * What a hunter has learned and what it does for them. The skills are saved on the player and
 * kept through death. Strength and speed act through vanilla attributes (put back on every join
 * and respawn); the other effects are asked for by the services they change.
 */
public final class SkillService {
	/** Stamina a hunter holds with no constitution. */
	public static final float BASE_STAMINA = 100.0F;
	/** Stamina a hunter can ever hold. */
	public static final float STAMINA_CEILING = 160.0F;

	private static final Identifier STRENGTH_ID = Mistinn.id("skill_strength");
	private static final Identifier SPEED_ID = Mistinn.id("skill_speed");
	private static final Identifier SWING_ID = Mistinn.id("skill_swing");

	private SkillService() {
	}

	// ── State ─────────────────────────────────────────────────────

	public static Skills get(final Player player) {
		return player.getAttachedOrElse(MistinnAttachments.SKILLS, Skills.EMPTY);
	}

	public static int stat(final Player player, final Stat stat) {
		return get(player).stat(stat);
	}

	/** Stores the skills and brings every effect in line with them. */
	public static void set(final ServerPlayer player, final Skills skills) {
		player.setAttached(MistinnAttachments.SKILLS, skills);
		refresh(player);
	}

	/** Puts the attribute modifiers back and keeps parts and stamina within their ceilings. */
	public static void refresh(final ServerPlayer player) {
		modifier(player, Attributes.ATTACK_DAMAGE, STRENGTH_ID, damageScale(stat(player, Stat.STRENGTH)) - 1.0);
		modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, moveScale(stat(player, Stat.SPEED)) - 1.0);
		modifier(player, Attributes.ATTACK_SPEED, SWING_ID, swingScale(stat(player, Stat.SPEED)) - 1.0);
		BodyService.fitCeiling(player);
		StaminaService.fitCeiling(player);
	}

	private static void modifier(final ServerPlayer player, final Holder<Attribute> attribute, final Identifier id, final double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (Math.abs(amount) < 1.0E-9) {
			instance.removeModifier(id);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	public static void register() {
		// the modifiers are transient: every join and every respawn puts them back
		ServerPlayerEvents.JOIN.register(SkillService::refresh);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refresh(newPlayer));
	}

	// ── Each stat, per point ──────────────────────────────────────

	/** Strength: melee damage, 3% per point. */
	public static double damageScale(final int strength) {
		return 1.0 + 0.03 * strength;
	}

	/** Speed: walking and running, 1% per point. */
	public static double moveScale(final int speed) {
		return 1.0 + 0.01 * speed;
	}

	/** Speed: swings, 1.5% per point. */
	public static double swingScale(final int speed) {
		return 1.0 + 0.015 * speed;
	}

	/** Speed: the speed a roll starts with, 1.5% per point. */
	public static float rollScale(final int speed) {
		return 1.0F + 0.015F * speed;
	}

	/** Speed: a tick more dodge for every five points. */
	public static int dodgeBonus(final int speed) {
		return speed / 5;
	}

	/** Constitution: each part holds 2 more per point, up to {@link BodyPart#CEILING}. */
	public static float partCeiling(final int constitution) {
		return Math.min(BodyPart.CEILING, BodyPart.MAX + 2.0F * constitution);
	}

	/** Constitution: stamina holds 3 more per point, up to {@link #STAMINA_CEILING}. */
	public static float staminaCeiling(final int constitution) {
		return Math.min(STAMINA_CEILING, BASE_STAMINA + 3.0F * constitution);
	}

	/** Constitution: what is left of the chance that a hit opens a wound or breaks a bone, 3% less per point. */
	public static float woundScale(final int constitution) {
		return Math.max(0.0F, 1.0F - 0.03F * constitution);
	}

	/** Intellect: treatment and repair take 2.5% less time per point, never less than half. */
	public static float treatmentScale(final int intellect) {
		return Math.max(0.5F, 1.0F - 0.025F * intellect);
	}

	/** Intellect: treatment heals 3% more per point. */
	public static float healScale(final int intellect) {
		return 1.0F + 0.03F * intellect;
	}

	// ── On a hunter ───────────────────────────────────────────────

	public static float partCeiling(final Player player) {
		return partCeiling(stat(player, Stat.CONSTITUTION));
	}

	public static float staminaCeiling(final Player player) {
		return staminaCeiling(stat(player, Stat.CONSTITUTION));
	}

	public static float woundChance(final Player player, final float chance) {
		return chance * woundScale(stat(player, Stat.CONSTITUTION));
	}

	public static int treatmentTicks(final Player player, final int ticks) {
		return Math.max(1, Math.round(ticks * treatmentScale(stat(player, Stat.INTELLECT))));
	}

	public static float treatmentAmount(final Player player, final float amount) {
		return amount * healScale(stat(player, Stat.INTELLECT));
	}

	public static float rollSpeedScale(final Player player) {
		return rollScale(stat(player, Stat.SPEED));
	}

	public static int rollDodgeBonus(final Player player) {
		return dodgeBonus(stat(player, Stat.SPEED));
	}
}
