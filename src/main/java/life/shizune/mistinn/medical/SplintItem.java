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
 * 夾板: holds one broken limb for {@link WoundService#SPLINT_TICKS}, legs first, so it can be
 * used again; the bone itself only mends under surgery or at a golden bell.
 */
public class SplintItem extends TreatmentItem {
	/** 3 s to bind. */
	public static final int USE_TICKS = 60;
	/** Which broken limb is bound first: the legs carry the hunter. */
	private static final BodyPart[] ORDER = {BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG, BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM};

	public SplintItem(final Properties properties) {
		super(properties);
	}

	/** The first broken limb not held by a splint; null when there is none. */
	public static @Nullable BodyPart next(final ServerPlayer player) {
		for (BodyPart part : ORDER) {
			if (WoundService.broken(player, part)) {
				return part;
			}
		}
		return null;
	}

	@Override
	public Treatment treatment(final ServerPlayer player, final ItemStack stack) {
		return Treatment.SPLINT;
	}

	@Override
	public @Nullable String refusal(final ServerPlayer player, final ItemStack stack) {
		return next(player) == null ? "message.mistinn.splint.none" : null;
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
		WoundService.set(player, wounds.with(part, wounds.get(part).splint(player.level().getGameTime() + WoundService.SPLINT_TICKS)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.REPAIR_STRAP, SoundSource.PLAYERS, 0.8F, 1.05F);
		player.sendSystemMessage(Component.translatable("message.mistinn.splint.done", WoundService.partName(part), WoundService.SPLINT_TICKS / 1200), true);
		stack.consume(1, player);
	}
}
