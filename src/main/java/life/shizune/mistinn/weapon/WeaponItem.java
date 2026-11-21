package life.shizune.mistinn.weapon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * One of the mod's weapons. The attack key swings it; holding the use key charges a heavy blow
 * ({@link HeavyService}) that lands when the key is let go.
 */
public class WeaponItem extends Item {
	/** Long enough that a charge is only ever ended by letting go. */
	private static final int HOLD = 72000;

	public WeaponItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		HeavyService.Refusal refusal = HeavyService.refusal(player, stack);
		if (refusal != HeavyService.Refusal.NONE) {
			if (player instanceof ServerPlayer hunter) {
				refuse(hunter, stack, refusal);
			}
			return InteractionResult.FAIL;
		}
		if (player instanceof ServerPlayer hunter) {
			life.shizune.mistinn.medical.TreatmentService.interrupt(hunter);
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	private static void refuse(final ServerPlayer hunter, final ItemStack stack, final HeavyService.Refusal refusal) {
		if (refusal == HeavyService.Refusal.UNFIT) {
			SpecialWeapon special = SpecialWeapon.of(stack);
			SpecialWeapon.Requirement lacking = special == null ? null : special.lacking(hunter);
			if (lacking != null) {
				hunter.sendSystemMessage(Component.translatable("message.mistinn.unfit", lacking.describe()), true);
			}
		}
	}

	@Override
	public void onUseTick(final Level level, final LivingEntity entity, final ItemStack stack, final int remaining) {
		WeaponStyle style = WeaponStyle.of(stack);
		if (style == null || !(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel)) {
			return;
		}
		int held = this.getUseDuration(stack, entity) - remaining;
		if (held == HeavyService.chargeTicks(player, style)) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
				life.shizune.mistinn.registry.MistinnSounds.CHARGE_FULL, SoundSource.PLAYERS, 1.0F, 1.0F);
		}
	}

	@Override
	public boolean releaseUsing(final ItemStack stack, final Level level, final LivingEntity entity, final int remaining) {
		int held = this.getUseDuration(stack, entity) - remaining;
		if (entity instanceof ServerPlayer player) {
			return HeavyService.release(player, stack, held);
		}
		if (level.isClientSide() && entity instanceof Player player) {
			releasedOnClient.accept(player, held);
		}
		return false;
	}

	/** The client starts its own blow at once instead of waiting for the server's word. */
	public static java.util.function.BiConsumer<Player, Integer> releasedOnClient = (player, held) -> {
	};

	@Override
	public int getUseDuration(final ItemStack stack, final LivingEntity entity) {
		return HOLD;
	}

	@Override
	public ItemUseAnimation getUseAnimation(final ItemStack stack) {
		return ItemUseAnimation.NONE;
	}
}
