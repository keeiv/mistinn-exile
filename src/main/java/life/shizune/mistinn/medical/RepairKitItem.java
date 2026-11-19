package life.shizune.mistinn.medical;

import life.shizune.mistinn.registry.MistinnComponents;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.tier.Tier;
import life.shizune.mistinn.tier.Tiers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 護甲維修包 / 頭盔維修包: mends the worn body armour or helmet ({@link RepairRules} has the
 * numbers). One kit, one repair. Masks sit in the grid, not on the head, and are not mended.
 */
public class RepairKitItem extends TreatmentItem {
	public final Tier tier;
	public final EquipmentSlot slot;

	public RepairKitItem(final Properties properties, final Tier tier, final EquipmentSlot slot) {
		super(properties);
		this.tier = tier;
		this.slot = slot;
	}

	@Override
	public Treatment treatment(final ServerPlayer player, final ItemStack stack) {
		return this.slot == EquipmentSlot.HEAD ? Treatment.HELMET_REPAIR : Treatment.ARMOR_REPAIR;
	}

	@Override
	public ItemStack target(final ServerPlayer player) {
		return player.getItemBySlot(this.slot);
	}

	@Override
	public @Nullable String refusal(final ServerPlayer player, final ItemStack stack) {
		ItemStack worn = this.target(player);
		if (worn.isEmpty() || !worn.isDamageableItem()) {
			return this.slot == EquipmentSlot.HEAD ? "message.mistinn.repair.no_helmet" : "message.mistinn.repair.no_armor";
		}
		if (!worn.isDamaged()) {
			return "message.mistinn.repair.whole";
		}
		return null;
	}

	@Override
	public int ticks(final ServerPlayer player, final ItemStack stack) {
		return RepairRules.ticks(this.tier, Tiers.of(this.target(player)));
	}

	@Override
	public boolean stillValid(final ServerPlayer player, final ItemStack stack) {
		ItemStack worn = this.target(player);
		return !worn.isEmpty() && worn.isDamageableItem() && TreatmentService.sameTarget(player, worn);
	}

	@Override
	public void finish(final ServerPlayer player, final ItemStack stack, final InteractionHand hand) {
		ItemStack worn = this.target(player);
		RepairRules.Plan plan = apply(this.tier, worn);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.35F, plan.poor() ? 0.8F : 1.3F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.GOLDEN_BELL_REST, SoundSource.PLAYERS, 0.15F, 1.8F);
		String key = this.tier == Tier.VI ? "message.mistinn.repair.red" : plan.poor() ? "message.mistinn.repair.poor" : "message.mistinn.repair.done";
		player.sendSystemMessage(Component.translatable(key, worn.getHoverName(), worn.getMaxDamage() - worn.getDamageValue(), worn.getMaxDamage()), true);
		stack.consume(1, player);
	}

	/** Mends {@code worn} with a kit of {@code kit}'s tier, wearing its maximum down where the rules say. */
	public static RepairRules.Plan apply(final Tier kit, final ItemStack worn) {
		int base = baseMax(worn);
		RepairRules.Plan plan = RepairRules.plan(kit, Tiers.of(worn), base, worn.getMaxDamage());
		if (plan.newMax() != worn.getMaxDamage()) {
			worn.set(DataComponents.MAX_DAMAGE, plan.newMax());
			worn.set(MistinnComponents.REPAIR_WEAR, base - plan.newMax());
		}
		worn.setDamageValue(RepairRules.damageAfter(plan, worn.getDamageValue()));
		return plan;
	}

	/** The piece's maximum before any repair wore it down. */
	public static int baseMax(final ItemStack stack) {
		Integer base = stack.getItem().components().get(DataComponents.MAX_DAMAGE);
		return base == null ? stack.getMaxDamage() : base;
	}
}
