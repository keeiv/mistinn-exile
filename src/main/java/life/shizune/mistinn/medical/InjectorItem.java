package life.shizune.mistinn.medical;

import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 強效注射器: a cold dose into the forearm. Over the next {@link #HEAL_TICKS} it gives
 * {@link #HEAL} part health back to the living parts, most hurt first; it cannot reach a
 * destroyed part.
 */
public class InjectorItem extends TreatmentItem {
	/** 3 s to use. */
	public static final int USE_TICKS = 60;
	/** Part health restored in total (of 480 for a whole body). */
	public static final float HEAL = 60.0F;
	/** Over 4 s. */
	public static final int HEAL_TICKS = 80;

	public InjectorItem(final Properties properties) {
		super(properties);
	}

	@Override
	public Treatment treatment(final ServerPlayer player, final ItemStack stack) {
		return Treatment.INJECT;
	}

	@Override
	public @Nullable String refusal(final ServerPlayer player, final ItemStack stack) {
		var body = BodyService.get(player);
		for (BodyPart part : BodyPart.values()) {
			if (!body.destroyed(part) && body.get(part) < BodyService.max(player)) {
				return null;
			}
		}
		return "message.mistinn.injector.whole";
	}

	@Override
	public int ticks(final ServerPlayer player, final ItemStack stack) {
		return USE_TICKS;
	}

	@Override
	public void finish(final ServerPlayer player, final ItemStack stack, final InteractionHand hand) {
		TreatmentService.inject(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.INJECT_FINISH, SoundSource.PLAYERS, 0.8F, 1.0F);
		player.sendSystemMessage(Component.translatable("message.mistinn.injector.used"), true);
		stack.consume(1, player);
	}
}
