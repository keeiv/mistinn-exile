package life.shizune.mistinn.food;

import com.mojang.serialization.Codec;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/**
 * How full a hunter can still get. Damp food ruins the stomach: each one eaten takes a
 * drumstick off the hunger bar for good, down to {@link #FLOOR}. Only death gives it back.
 */
public final class HungerCap {
	/** A whole stomach: vanilla's full hunger bar. */
	public static final int FULL = 20;
	/** No hunter's stomach shrinks below this: four drumsticks, enough to still sprint. */
	public static final int FLOOR = 8;
	/** What one damp meal takes: a whole drumstick. */
	public static final int SPOIL = 2;

	/** Not copied on death: a respawned hunter's stomach is whole again. */
	public static final AttachmentType<Integer> CAP = AttachmentRegistry.<Integer>builder()
		.persistent(Codec.INT)
		.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Mistinn.id("hunger_cap"));

	private HungerCap() {
	}

	public static int of(final Player player) {
		return player.getAttachedOrElse(CAP, FULL);
	}

	/** A damp meal has gone down: a drumstick less, for good. */
	public static void spoil(final ServerPlayer player) {
		int before = of(player);
		int cap = Math.max(FLOOR, before - SPOIL);
		player.setAttached(CAP, cap);
		FoodData food = player.getFoodData();
		if (food.getFoodLevel() > cap) {
			food.setFoodLevel(cap);
		}
		if (food.getSaturationLevel() > food.getFoodLevel()) {
			food.setSaturation(food.getFoodLevel());
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.6F, 0.55F);
		player.sendSystemMessage(Component.translatable(cap < before ? "message.mistinn.food.spoiled" : "message.mistinn.food.spoiled_floor"), true);
	}

	public static void init() {
		// Loading the class registers the attachment.
	}
}
