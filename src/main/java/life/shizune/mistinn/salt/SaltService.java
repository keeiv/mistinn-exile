package life.shizune.mistinn.salt;

import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/** 鹽: the hunter's currency, earned from what they defeat and lost when they fall. */
public final class SaltService {
	/** Salt never goes above this; keeps the number readable and inside an int. */
	public static final int MAX = 999_999_999;

	private SaltService() {
	}

	public static int get(final ServerPlayer player) {
		return player.getAttachedOrElse(MistinnAttachments.SALT, 0);
	}

	public static void set(final ServerPlayer player, final int amount) {
		player.setAttached(MistinnAttachments.SALT, Mth.clamp(amount, 0, MAX));
	}

	public static void add(final ServerPlayer player, final int amount) {
		set(player, (int) Math.min((long) get(player) + amount, MAX));
	}

	/**
	 * Salt for defeating {@code victim}: hostile creatures give their full health in salt,
	 * others a fifth of it, at least 1.
	 */
	public static int rewardFor(final LivingEntity victim) {
		float health = victim.getMaxHealth();
		float share = victim instanceof Enemy ? 1.0F : 0.2F;
		return Math.max(1, Math.round(health * share));
	}

	public static void reward(final ServerPlayer player, final LivingEntity victim) {
		int amount = rewardFor(victim);
		add(player, amount);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.SALT_GAIN, SoundSource.PLAYERS, 0.35F, 0.9F + player.getRandom().nextFloat() * 0.3F);
	}
}
