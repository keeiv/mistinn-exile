package life.shizune.mistinn.body;

import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 鐘鳴膏: a salve worked from the same gold as the one-time bells. Outside a golden bell it is the
 * only thing that brings a destroyed body part back.
 */
public class BellSalveItem extends Item {
	/** Health a destroyed part comes back with. */
	public static final float REVIVE = 30.0F;
	/** Health spread over the living parts as well. */
	public static final float MEND = 24.0F;

	public BellSalveItem(final Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(final ItemStack stack, final Level level, final LivingEntity entity) {
		if (entity instanceof ServerPlayer player && BodyService.applies(player)) {
			BodyService.salve(player, REVIVE, MEND);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.GOLDEN_BELL_REST, SoundSource.PLAYERS, 0.6F, 1.3F);
			player.sendSystemMessage(Component.translatable("message.mistinn.bell_salve.used"), true);
		}
		return super.finishUsingItem(stack, level, entity);
	}
}
