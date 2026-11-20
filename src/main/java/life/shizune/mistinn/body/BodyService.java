package life.shizune.mistinn.body;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.grid.GridRules;
import life.shizune.mistinn.medical.TreatmentService;
import life.shizune.mistinn.mixin.LivingEntityInvoker;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Health split into body parts. Outside creative, every hit lands on a part (or spreads over
 * several), each part is protected only by the gear that covers it, and three destroyed parts
 * kill. Vanilla health is kept in step as a hidden summary so that the rest of the game (death,
 * mobs, effects) keeps working.
 */
public final class BodyService {
	/** Part health per point of vanilla damage: a zombie's 3-point blow takes 18 from a part. */
	public static final float DAMAGE_SCALE = 6.0F;
	/** Damage that reaches a destroyed part spreads over the others at this rate. */
	public static final float OVERFLOW = 0.7F;
	/** Natural recovery: every this many ticks, each damaged but living part regains one point. */
	public static final int REGEN_INTERVAL = 100;
	/** Natural recovery needs at least this much food. */
	public static final int REGEN_FOOD = 6;
	/** Extra exhaustion per tick while the torso is destroyed: hunger runs about three times as fast. */
	public static final float TORSO_HUNGER = 0.01F;
	/** A totem saves a hunter with three dead parts by bringing them back to this. */
	public static final float TOTEM_REVIVE = 8.0F;

	private static final Identifier LEGS_ID = Mistinn.id("body_legs");
	private static final Identifier ARMS_ID = Mistinn.id("body_arms");

	/** True while {@link #hurt} runs: vanilla's own death check follows it. */
	private static boolean hurting;

	private BodyService() {
	}

	// ── State ────────────────────────────────────────────────────

	public static boolean applies(final Player player) {
		return GridRules.usesGrid(player);
	}

	public static Body get(final Player player) {
		return player.getAttachedOrElse(MistinnAttachments.BODY, Body.FULL);
	}

	/** Stores the body and brings vanilla health and the part penalties in line with it. */
	public static void set(final ServerPlayer player, final Body body) {
		Body before = get(player);
		player.setAttached(MistinnAttachments.BODY, body);
		if (body.lethal()) {
			player.setHealth(0.0F);
			if (!hurting && !player.isRemoved()) {
				// outside a hit nothing else would notice: die properly (drops, residue, death screen)
				player.die(player.damageSources().generic());
			}
		} else {
			syncHealth(player, body);
			if (body.destroyedCount() > before.destroyedCount()) {
				// a part gives out: a crack of bone and a sharp breath
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.LIMB_DESTROYED, SoundSource.PLAYERS, 1.0F, 0.95F + player.getRandom().nextFloat() * 0.1F);
			}
		}
		if (!body.equals(before)) {
			applyPenalties(player, body);
		}
	}

	private static void syncHealth(final ServerPlayer player, final Body body) {
		float share = body.total() / (BodyPart.MAX * BodyPart.values().length);
		player.setHealth(Math.max(1.0F, player.getMaxHealth() * share));
	}

	/** Golden bells and respawning: every part whole again. */
	public static void restoreAll(final ServerPlayer player) {
		set(player, Body.FULL);
	}

	// ── Where a hit lands ─────────────────────────────────────────

	/** A hit on one part (with the face as a zone of the head), on both legs, or on the whole body. */
	public record Hit(@Nullable BodyPart part, boolean face, boolean legs, boolean whole) {
		public static Hit on(final BodyPart part, final boolean face) {
			return new Hit(part, face, false, false);
		}

		public static final Hit LEGS = new Hit(null, false, true, false);
		public static final Hit WHOLE = new Hit(null, false, false, true);
	}

	/** Hits aimed by an attacker that knows where it strikes; used once, by the next hit on that hunter. */
	private static final java.util.Map<java.util.UUID, Hit> AIMED = new java.util.HashMap<>();

	/**
	 * Aims the next hit on this hunter at a part. A tall keeper would otherwise land every blow
	 * on the head; its moves choose their target (a sweep at the body, a slam at the legs).
	 */
	public static void aim(final ServerPlayer player, final Hit hit) {
		AIMED.put(player.getUUID(), hit);
	}

	/** Drops an aim that was not used (the hit was blocked or ignored). */
	public static void clearAim(final ServerPlayer player) {
		AIMED.remove(player.getUUID());
	}

	public static Hit locate(final ServerPlayer player, final DamageSource source) {
		Hit aimed = AIMED.remove(player.getUUID());
		if (aimed != null) {
			return aimed;
		}
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.STALAGMITE)) {
			return Hit.LEGS;
		}
		if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
			return Hit.on(BodyPart.HEAD, false);
		}
		if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FREEZING)
			|| source.is(DamageTypeTags.IS_LIGHTNING) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return Hit.WHOLE;
		}
		Entity direct = source.getDirectEntity();
		Vec3 from = source.getSourcePosition();
		if (direct == null && from == null) {
			// drowning, starving, poison, wither and the like
			return Hit.on(BodyPart.TORSO, false);
		}
		RandomSource random = player.getRandom();
		double height;
		if (direct instanceof LivingEntity attacker && direct == source.getEntity()) {
			// a blow lands a little below the attacker's eyes, give or take
			height = attacker.getEyeY() - 0.4 + random.nextGaussian() * 0.35;
		} else if (direct != null) {
			height = direct.getY() + direct.getBbHeight() / 2.0;
		} else {
			height = from.y;
		}
		Vec3 origin = direct != null ? direct.position() : from;
		return locate(player, (height - player.getY()) / player.getBbHeight(), origin, random);
	}

	/** Picks the part from where on the body (0 feet, 1 crown) and from which side the hit came. */
	public static Hit locate(final ServerPlayer player, final double heightShare, final Vec3 origin, final RandomSource random) {
		double dx = origin.x - player.getX();
		double dz = origin.z - player.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		double side = 0.0;
		double front = 1.0;
		if (length > 1.0E-4) {
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			// facing (-sin, cos); the right hand points to (-cos, -sin)
			front = (dx * -Mth.sin(yaw) + dz * Mth.cos(yaw)) / length;
			side = (dx * -Mth.cos(yaw) + dz * -Mth.sin(yaw)) / length;
		}
		boolean right = Math.abs(side) < 0.15 ? random.nextBoolean() : side > 0.0;
		if (heightShare >= 0.8) {
			return Hit.on(BodyPart.HEAD, front > 0.4);
		}
		if (heightShare >= 0.45) {
			double armChance = 0.25 + 0.35 * Math.abs(side);
			if (random.nextDouble() < armChance) {
				return Hit.on(right ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM, false);
			}
			return Hit.on(BodyPart.TORSO, false);
		}
		return Hit.on(right ? BodyPart.RIGHT_LEG : BodyPart.LEFT_LEG, false);
	}

	// ── Taking damage ─────────────────────────────────────────────

	/** Replaces vanilla's {@code actuallyHurt} for hunters: armour by part, then parts. */
	public static void hurt(final ServerPlayer player, final ServerLevel level, final DamageSource source, float damage) {
		if (player.isInvulnerableTo(level, source)) {
			return;
		}
		damage = Math.min(damage, 1.0E6F);
		Hit hit = locate(player, source);
		if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			EquipmentSlot[] slots = coveringSlots(hit);
			float armor = 0.0F;
			float toughness = 0.0F;
			for (EquipmentSlot slot : slots) {
				armor += attribute(player.getItemBySlot(slot), slot, Attributes.ARMOR);
				toughness += attribute(player.getItemBySlot(slot), slot, Attributes.ARMOR_TOUGHNESS);
			}
			if (hit.face()) {
				armor += BodyGear.faceArmor(player);
				BodyGear.wearMask(player, damage);
			}
			if (slots.length > 0) {
				((LivingEntityInvoker) player).mistinn$doHurtEquipment(source, damage, slots);
			}
			damage = CombatRules.getDamageAfterAbsorb(player, damage, source, armor, toughness);
		}
		damage = ((LivingEntityInvoker) player).mistinn$getDamageAfterMagicAbsorb(source, damage);
		float original = damage;
		damage = Math.max(damage - player.getAbsorptionAmount(), 0.0F);
		player.setAbsorptionAmount(player.getAbsorptionAmount() - (original - damage));
		if (damage <= 0.0F) {
			return;
		}
		player.causeFoodExhaustion(source.getFoodExhaustion());
		// a heavy blow breaks off whatever the hunter was treating or mending
		TreatmentService.hurt(player, damage);
		player.getCombatTracker().recordDamage(source, damage);
		player.awardStat(Stats.DAMAGE_TAKEN, Math.round(Math.min(damage, 1.0E5F) * 10.0F));
		hurting = true;
		try {
			set(player, apply(get(player), hit, damage * DAMAGE_SCALE));
		} finally {
			hurting = false;
		}
		player.gameEvent(GameEvent.ENTITY_DAMAGE);
	}

	/** The equipment that covers a hit: helmet for the crown, body armour for the torso, leg gear for legs. */
	private static EquipmentSlot[] coveringSlots(final Hit hit) {
		if (hit.whole()) {
			return new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		}
		if (hit.legs()) {
			return new EquipmentSlot[] {EquipmentSlot.LEGS, EquipmentSlot.FEET};
		}
		BodyPart part = hit.part();
		if (part == BodyPart.HEAD) {
			// the face is the mask's, not the helmet's
			return hit.face() ? new EquipmentSlot[0] : new EquipmentSlot[] {EquipmentSlot.HEAD};
		}
		if (part == BodyPart.TORSO) {
			return new EquipmentSlot[] {EquipmentSlot.CHEST};
		}
		if (part != null && part.isLeg()) {
			return new EquipmentSlot[] {EquipmentSlot.LEGS, EquipmentSlot.FEET};
		}
		return new EquipmentSlot[0];
	}

	private static float attribute(final ItemStack stack, final EquipmentSlot slot, final Holder<Attribute> attribute) {
		float[] sum = {0.0F};
		stack.forEachModifier(slot, (holder, modifier) -> {
			if (holder.equals(attribute) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
				sum[0] += (float) modifier.amount();
			}
		});
		return sum[0];
	}

	/** Spreads part damage according to the hit. */
	public static Body apply(Body body, final Hit hit, final float amount) {
		if (hit.whole()) {
			float each = amount / BodyPart.values().length;
			for (BodyPart part : BodyPart.values()) {
				body = damage(body, part, each);
			}
			return body;
		}
		if (hit.legs()) {
			body = damage(body, BodyPart.LEFT_LEG, amount / 2.0F);
			return damage(body, BodyPart.RIGHT_LEG, amount / 2.0F);
		}
		return damage(body, hit.part() == null ? BodyPart.TORSO : hit.part(), amount);
	}

	/** Damages one part; what a destroyed part cannot take spreads over the living ones, weakened. */
	public static Body damage(Body body, final BodyPart part, final float amount) {
		float left = body.get(part);
		float spill;
		if (left <= 0.0F) {
			spill = amount;
		} else {
			body = body.with(part, left - amount);
			spill = Math.max(0.0F, amount - left);
		}
		if (spill <= 0.0F) {
			return body;
		}
		int living = BodyPart.values().length - body.destroyedCount();
		if (living == 0) {
			return body;
		}
		float each = spill * OVERFLOW / living;
		for (BodyPart other : BodyPart.values()) {
			if (!body.destroyed(other)) {
				body = body.with(other, body.get(other) - each);
			}
		}
		return body;
	}

	/**
	 * Strain the legs cannot take any more (a dive on destroyed legs) is shared equally over the
	 * living parts of the upper body: head, torso and arms.
	 */
	public static void strainUpper(final ServerPlayer player, final float amount) {
		Body body = get(player);
		java.util.List<BodyPart> upper = new java.util.ArrayList<>();
		for (BodyPart part : BodyPart.values()) {
			if (!part.isLeg() && !body.destroyed(part)) {
				upper.add(part);
			}
		}
		if (upper.isEmpty()) {
			upper.add(BodyPart.TORSO);
		}
		for (BodyPart part : upper) {
			body = damage(body, part, amount / upper.size());
		}
		set(player, body);
	}

	/**
	 * Strain spread evenly over every living part: a roll on destroyed parts the hunter
	 * cannot feel under painkillers.
	 */
	public static void strainWhole(final ServerPlayer player, final float amount) {
		Body body = get(player);
		java.util.List<BodyPart> living = new java.util.ArrayList<>();
		for (BodyPart part : BodyPart.values()) {
			if (!body.destroyed(part)) {
				living.add(part);
			}
		}
		if (living.isEmpty()) {
			return;
		}
		for (BodyPart part : living) {
			body = damage(body, part, amount / living.size());
		}
		set(player, body);
	}

	// ── Healing ───────────────────────────────────────────────────

	/** Vanilla healing (potions, golden apples) mends living parts, most hurt first; never a destroyed one. */
	public static void heal(final ServerPlayer player, final float vanillaAmount) {
		if (vanillaAmount <= 0.0F || player.isDeadOrDying()) {
			return;
		}
		set(player, mend(get(player), vanillaAmount * DAMAGE_SCALE));
	}

	public static Body mend(Body body, float amount) {
		while (amount > 0.01F) {
			BodyPart lowest = null;
			for (BodyPart part : BodyPart.values()) {
				float value = body.get(part);
				if (value > 0.0F && value < BodyPart.MAX && (lowest == null || value < body.get(lowest))) {
					lowest = part;
				}
			}
			if (lowest == null) {
				break;
			}
			float give = Math.min(amount, Math.min(BodyPart.MAX - body.get(lowest), 4.0F));
			body = body.with(lowest, body.get(lowest) + give);
			amount -= give;
		}
		return body;
	}

	/** The bell salve: destroyed parts come back, the rest mends a little. */
	public static void salve(final ServerPlayer player, final float revive, final float mend) {
		Body body = get(player);
		for (BodyPart part : BodyPart.values()) {
			if (body.destroyed(part)) {
				body = body.with(part, revive);
			}
		}
		set(player, mend(body, mend));
	}

	// ── Every tick ────────────────────────────────────────────────

	public static void tick(final MinecraftServer server) {
		boolean regenTick = server.getTickCount() % REGEN_INTERVAL == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!applies(player) || player.isDeadOrDying()) {
				continue;
			}
			Body body = get(player);
			if (body.lethal()) {
				// alive with three dead parts: a totem caught the fall
				for (BodyPart part : BodyPart.values()) {
					if (body.destroyed(part)) {
						body = body.with(part, TOTEM_REVIVE);
					}
				}
				set(player, body);
				continue;
			}
			if (body.destroyed(BodyPart.TORSO) && !TreatmentService.numb(player)) {
				player.causeFoodExhaustion(TORSO_HUNGER);
			}
			if (regenTick && player.getFoodData().getFoodLevel() >= REGEN_FOOD) {
				Body mended = body;
				for (BodyPart part : BodyPart.values()) {
					float value = mended.get(part);
					if (value > 0.0F && value < BodyPart.MAX) {
						mended = mended.with(part, value + 1.0F);
						player.causeFoodExhaustion(0.05F);
					}
				}
				if (!mended.equals(body)) {
					set(player, mended);
					continue;
				}
			}
			if (server.getTickCount() % 20 == 0) {
				// something else (a max health change, another mod) may have moved vanilla health
				syncHealth(player, body);
			}
		}
	}

	// ── Destroyed parts ───────────────────────────────────────────

	/**
	 * Each dead leg slows and weighs down; each dead arm weakens blows and work. Painkillers
	 * hide all of it while they last.
	 */
	public static void applyPenalties(final ServerPlayer player, final Body body) {
		boolean numb = TreatmentService.numb(player);
		int legs = numb ? 0 : (body.destroyed(BodyPart.LEFT_LEG) ? 1 : 0) + (body.destroyed(BodyPart.RIGHT_LEG) ? 1 : 0);
		int arms = numb ? 0 : (body.destroyed(BodyPart.LEFT_ARM) ? 1 : 0) + (body.destroyed(BodyPart.RIGHT_ARM) ? 1 : 0);
		penalty(player, Attributes.MOVEMENT_SPEED, LEGS_ID, -0.25 * legs);
		penalty(player, Attributes.JUMP_STRENGTH, LEGS_ID, -0.25 * legs);
		penalty(player, Attributes.ATTACK_DAMAGE, ARMS_ID, -0.25 * arms);
		penalty(player, Attributes.ATTACK_SPEED, ARMS_ID, -0.2 * arms);
		penalty(player, Attributes.BLOCK_BREAK_SPEED, ARMS_ID, -0.3 * arms);
	}

	private static void penalty(final ServerPlayer player, final Holder<Attribute> attribute, final Identifier id, final double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0.0) {
			instance.removeModifier(id);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
