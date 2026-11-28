package life.shizune.mistinn.epic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * 行囊印: given when the epic is written. Used, it seals what the hunter wears and the arms in their hands into a pack the
 * hunter's own game keeps, to be taken out of the guide in another world (as the guide's own button does).
 */
public class LegacySealItem extends Item {
	public LegacySealItem(final Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
		if (player instanceof ServerPlayer hunter) {
			LegacyService.seal(hunter);
		}
		return InteractionResult.SUCCESS;
	}
}
