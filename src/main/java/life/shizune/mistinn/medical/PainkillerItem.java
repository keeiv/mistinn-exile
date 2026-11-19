package life.shizune.mistinn.medical;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 止疼片: for {@link #NUMB_TICKS} the hunter stops feeling destroyed parts: no slowed legs, no
 * weak arms, no torso hunger, and a roll is possible even on two dead legs. The body still
 * pays: while any part is destroyed, every roll or dive lands {@link TreatmentService#NUMB_STRAIN}
 * on the whole body, split evenly over the living parts. Four doses to a bottle.
 */
public class PainkillerItem extends TreatmentItem {
	/** 2 s to take. */
	public static final int USE_TICKS = 40;
	/** 90 s of numbness. */
	public static final int NUMB_TICKS = 1800;
	/** Doses in a bottle (its durability). */
	public static final int DOSES = 4;

	public PainkillerItem(final Properties properties) {
		super(properties);
	}

	@Override
	public Treatment treatment(final ServerPlayer player, final ItemStack stack) {
		return Treatment.PILLS;
	}

	@Override
	public @Nullable String refusal(final ServerPlayer player, final ItemStack stack) {
		return null;
	}

	@Override
	public int ticks(final ServerPlayer player, final ItemStack stack) {
		return USE_TICKS;
	}

	@Override
	public void finish(final ServerPlayer player, final ItemStack stack, final InteractionHand hand) {
		TreatmentService.numb(player, NUMB_TICKS);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.5F, 1.5F);
		player.sendSystemMessage(Component.translatable("message.mistinn.painkillers.used", NUMB_TICKS / 20), true);
		stack.hurtAndBreak(1, player, hand);
	}
}
