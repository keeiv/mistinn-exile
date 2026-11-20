package life.shizune.mistinn.medical;

import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.WoundService;
import life.shizune.mistinn.body.Wounds;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 止血帶: stops one part's bleeding, the most bled first. Surgery takes it off.
 * If left on longer than {@link WoundService#TOURNIQUET_SAFE}, the part starts to die.
 */
public class TourniquetItem extends TreatmentItem {
	/** 2 s to tie. */
	public static final int USE_TICKS = 40;

	public TourniquetItem(final Properties properties) {
		super(properties);
	}

	/** The bleeding part with the least health left, not yet tied; null when none bleeds. */
	public static @Nullable BodyPart next(final ServerPlayer player) {
		Wounds wounds = WoundService.get(player);
		var body = life.shizune.mistinn.body.BodyService.get(player);
		BodyPart worst = null;
		for (BodyPart part : BodyPart.values()) {
			Wounds.Wound wound = wounds.get(part);
			if (wound.bleeding() && !wound.tied() && !body.destroyed(part) && (worst == null || body.get(part) < body.get(worst))) {
				worst = part;
			}
		}
		return worst;
	}

	@Override
	public Treatment treatment(final ServerPlayer player, final ItemStack stack) {
		return Treatment.TOURNIQUET;
	}

	@Override
	public @Nullable String refusal(final ServerPlayer player, final ItemStack stack) {
		return next(player) == null ? "message.mistinn.tourniquet.none" : null;
	}

	@Override
	public int ticks(final ServerPlayer player, final ItemStack stack) {
		return USE_TICKS;
	}

	@Override
	public void finish(final ServerPlayer player, final ItemStack stack, final InteractionHand hand) {
		BodyPart part = next(player);
		if (part == null) {
			return;
		}
		Wounds wounds = WoundService.get(player);
		// 0 means no tourniquet, so a world's first tick counts as its second
		WoundService.set(player, wounds.with(part, wounds.get(part).tourniquet(Math.max(1L, player.level().getGameTime()))));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.REPAIR_STRAP, SoundSource.PLAYERS, 0.8F, 0.9F);
		player.sendSystemMessage(Component.translatable("message.mistinn.tourniquet.done", WoundService.partName(part), WoundService.TOURNIQUET_SAFE / 1200), true);
		stack.consume(1, player);
	}
}
