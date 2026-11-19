package life.shizune.mistinn.medical;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * 處置道具: an item that is used over a few seconds rather than at once. Using it starts a
 * channel on the server ({@link TreatmentService}). Holding the button repeats the click, so a
 * click while the channel runs changes nothing; switching to another slot calls it off. Only
 * the server decides whether it starts and what it does when it ends.
 */
public abstract class TreatmentItem extends Item {
	protected TreatmentItem(final Properties properties) {
		super(properties);
	}

	/** Which animation and slowdown the use has. */
	public abstract Treatment treatment(ServerPlayer player, ItemStack stack);

	/** Why it cannot be used now, as a translation key; null when it can. */
	public abstract @Nullable String refusal(ServerPlayer player, ItemStack stack);

	/** How long the use takes, in ticks. */
	public abstract int ticks(ServerPlayer player, ItemStack stack);

	/** The use has run its full time: apply it and spend the item. */
	public abstract void finish(ServerPlayer player, ItemStack stack, InteractionHand hand);

	/** The thing being worked on is still there (checked every tick). */
	public boolean stillValid(final ServerPlayer player, final ItemStack stack) {
		return true;
	}

	/** What the channel works on, remembered at the start (the worn piece for a repair). */
	public ItemStack target(final ServerPlayer player) {
		return ItemStack.EMPTY;
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			if (TreatmentService.active(serverPlayer)) {
				return InteractionResult.CONSUME;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!TreatmentService.applies(serverPlayer)) {
				return InteractionResult.PASS;
			}
			String refusal = this.refusal(serverPlayer, stack);
			if (refusal != null) {
				serverPlayer.sendSystemMessage(Component.translatable(refusal), true);
				return InteractionResult.FAIL;
			}
			TreatmentService.start(serverPlayer, hand, stack, this);
		}
		// no swing: the channel's own animation shows the use
		return InteractionResult.CONSUME;
	}
}
