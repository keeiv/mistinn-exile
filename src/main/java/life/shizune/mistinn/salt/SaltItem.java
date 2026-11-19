package life.shizune.mistinn.salt;

import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A pouch of salt found in the world: use it to add its salt to what you carry. */
public class SaltItem extends Item {
	/** Salt in one pouch. */
	public static final int PER_ITEM = 10;

	public SaltItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player instanceof ServerPlayer serverPlayer) {
			int amount = stack.getCount() * PER_ITEM;
			SaltService.add(serverPlayer, amount);
			stack.setCount(0);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.SALT_GAIN, SoundSource.PLAYERS, 0.8F, 1.0F);
			serverPlayer.sendSystemMessage(Component.translatable("message.mistinn.salt.used", amount), true);
		}
		return InteractionResult.SUCCESS;
	}
}
