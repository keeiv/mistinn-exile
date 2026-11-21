package life.shizune.mistinn.story;

import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * 追獵者手冊: the field manual every hunter is dropped with. Using it opens its own screen (the
 * guide's pages, the stats and the skill tree), drawn by the client.
 */
public class HunterGuideItem extends Item {
	/** Opens the guide's screen; the client sets it. */
	public static Runnable opener = () -> {
	};

	public HunterGuideItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (level.isClientSide()) {
			opener.run();
		} else {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.GUIDE_OPEN, SoundSource.PLAYERS, 0.7F, 1.0F);
			player.awardStat(Stats.ITEM_USED.get(this));
		}
		return InteractionResult.SUCCESS;
	}
}
