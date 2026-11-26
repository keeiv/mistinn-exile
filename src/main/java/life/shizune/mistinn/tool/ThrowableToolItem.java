package life.shizune.mistinn.tool;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A salt bomb, a flare or a bell lure: used, it is thrown where the hunter looks, a second before the next. */
public class ThrowableToolItem extends Item {
	/** Ticks before the next can be thrown. */
	public static final int COOLDOWN = 20;

	public ThrowableToolItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F,
			0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
		if (level instanceof ServerLevel server) {
			Projectile.spawnProjectileFromRotation(ThrownTool::new, server, stack, player, 0.0F, 1.2F, 1.0F);
		}
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
