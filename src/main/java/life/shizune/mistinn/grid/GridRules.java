package life.shizune.mistinn.grid;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** When the grid replaces the vanilla storage slots. */
public final class GridRules {
	private GridRules() {
	}

	/** Creative and spectator players keep the vanilla inventory. */
	public static boolean usesGrid(final Player player) {
		return player != null && !player.isCreative() && !player.isSpectator();
	}

	/** The 27 storage slots of the player inventory (indexes 9 to 35), unused under the grid. */
	public static boolean isHiddenSlot(final Player player, final int index) {
		return index >= Inventory.getSelectionSize() && index < Inventory.INVENTORY_SIZE && usesGrid(player);
	}
}
