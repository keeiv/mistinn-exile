package life.shizune.mistinn.food;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 受潮的食物: what ordinary food becomes in a hunter's hands in a Rust Anchor world. It can be
 * eaten, barely, and it costs a drumstick of the hunger bar for good ({@link HungerCap}).
 */
public class DampFoodItem extends Item {
	public DampFoodItem(final Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(final ItemStack stack) {
		return super.getName(stack).copy().withStyle(ChatFormatting.RED);
	}

	@Override
	public ItemStack finishUsingItem(final ItemStack stack, final Level level, final LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			HungerCap.spoil(player);
		}
		return super.finishUsingItem(stack, level, entity);
	}
}
