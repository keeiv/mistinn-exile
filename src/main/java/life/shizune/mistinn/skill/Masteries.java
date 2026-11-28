package life.shizune.mistinn.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.mixin.LivingEntityInvoker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * What the three later major skills do, each closing a row of the tree.
 * <ul>
 * <li>裝備大師 (gear master, the intellect row): armour wears slower, the helmet and the chest piece by half, the legs and
 * the boots a little.</li>
 * <li>久經沙場 (battle-hardened, the constitution row): every blow that reaches the body does less.</li>
 * <li>千鈞 (a thousand weight, the strength row): a heavy blow costs less breath, and one charged all the way lands
 * harder.</li>
 * <li>初心本我 (the self as it began, the speed row): two things No. 3412 does, as they were trained: the guard (架) turns a
 * blow aside a little later into it, and off a blow turned aside whole, the next cut is the riposte (回刺), stepping
 * straight back in along the line it came from, for more.</li>
 * </ul>
 */
public final class Masteries {
	/** 裝備大師: what the helmet and the chest piece wear, and the legs and the boots, of what they would. */
	public static final float UPPER_WEAR = 0.5F;
	public static final float LOWER_WEAR = 0.75F;
	/** 千鈞: what a heavy blow costs in stamina, and what one charged all the way adds, from how full a charge. */
	public static final float HEAVY_COST = 0.75F;
	public static final float FULL_BLOW = 1.25F;
	public static final float FULL = 0.95F;
	/** 久經沙場: what a blow comes to on a battle-hardened body. */
	public static final float HARDENED = 0.85F;
	/** 初心本我: ticks more of the guard that turn a blow aside whole; how long the riposte waits after; what it adds. */
	public static final int GUARD_LONGER = 2;
	public static final int RIPOSTE_WINDOW = 30;
	public static final float RIPOSTE = 0.75F;
	/** How far the riposte throws the hunter in along the line, in blocks a tick. */
	public static final double RIPOSTE_STEP = 0.55;

	private static final Map<UUID, Long> RIPOSTE_UNTIL = new HashMap<>();

	private Masteries() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> RIPOSTE_UNTIL.clear());
	}

	private static boolean has(final Player player, final String id) {
		return SkillService.get(player).has(id);
	}

	// ── 千鈞 ──────────────────────────────────────────────────────

	/** What a heavy blow costs this hunter in stamina, of what it would. */
	public static float heavyCost(final Player player) {
		return has(player, SkillTree.THOUSAND_WEIGHT) ? HEAVY_COST : 1.0F;
	}

	/** What a heavy blow at this charge does for this hunter, of what it would. */
	public static float heavyBlow(final Player player, final float charge) {
		return has(player, SkillTree.THOUSAND_WEIGHT) && charge >= FULL ? FULL_BLOW : 1.0F;
	}

	// ── 久經沙場 ────────────────────────────────────────────────────

	/** What a blow that reaches this hunter's body comes to, for their hardening. */
	public static float hardened(final Player player) {
		return has(player, SkillTree.BATTLE_HARDENED) ? HARDENED : 1.0F;
	}

	// ── 裝備大師 ────────────────────────────────────────────────────

	/** The armour over where a blow landed wears from it: as vanilla wears it, or, for a gear master, slower. */
	public static void wear(final ServerPlayer player, final DamageSource source, final float damage, final EquipmentSlot... slots) {
		if (!has(player, SkillTree.GEAR_MASTER)) {
			((LivingEntityInvoker) player).mistinn$doHurtEquipment(source, damage, slots);
			return;
		}
		if (damage <= 0.0F) {
			return;
		}
		for (EquipmentSlot slot : slots) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isEmpty() || !stack.isDamageableItem() || !stack.canBeHurtBy(source)) {
				continue;
			}
			float share = slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST ? UPPER_WEAR : LOWER_WEAR;
			int units = units(Math.max(1.0F, damage / 4.0F) * share, player.getRandom().nextFloat());
			if (units > 0) {
				stack.hurtAndBreak(units, player, slot);
			}
		}
	}

	/** Whole wear for a share of it: the part left over wears one more as often as it comes to. */
	public static int units(final float wear, final float roll) {
		int whole = Mth.floor(wear);
		return whole + (roll < wear - whole ? 1 : 0);
	}

	// ── 初心本我 ────────────────────────────────────────────────────

	/** Ticks of the guard in which a blow is turned aside whole, for this hunter. */
	public static int perfectGuard(final Player player, final int base) {
		return has(player, SkillTree.TRUE_SELF) ? base + GUARD_LONGER : base;
	}

	/** A blow was turned aside whole: one who is as they began may riposte. */
	public static void parried(final ServerPlayer player) {
		if (has(player, SkillTree.TRUE_SELF)) {
			RIPOSTE_UNTIL.put(player.getUUID(), player.level().getGameTime() + RIPOSTE_WINDOW);
		}
	}

	/** Whether this hunter's next cut is a riposte. */
	public static boolean riposteReady(final ServerPlayer player) {
		Long until = RIPOSTE_UNTIL.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until;
	}

	/**
	 * A blow about to land: the hunter's cut, when their riposte is ready, is the riposte. It steps them in along the line
	 * and goes in deeper; what it comes to is returned.
	 */
	public static float riposte(final LivingEntity entity, final DamageSource source, final float amount) {
		if (amount <= 0.0F || !source.is(DamageTypes.PLAYER_ATTACK) || !(source.getEntity() instanceof ServerPlayer player)
			|| source.getDirectEntity() != player || entity == player || !riposteReady(player) || !(entity.level() instanceof ServerLevel level)) {
			return amount;
		}
		RIPOSTE_UNTIL.remove(player.getUUID());
		Vec3 line = entity.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		if (line.lengthSqr() > 1.0E-4) {
			Vec3 step = line.normalize().scale(RIPOSTE_STEP);
			player.setDeltaMovement(step.x, player.getDeltaMovement().y, step.z);
			player.syncVelocity = true;
		}
		Vec3 at = entity.position().add(0.0, entity.getBbHeight() * 0.6, 0.0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.3);
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.3F);
		return amount * (1.0F + RIPOSTE);
	}
}
