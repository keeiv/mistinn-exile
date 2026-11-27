package life.shizune.mistinn.grid;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What a hunter carries, in the vanilla inventory and in the grid (pockets, rig, backpack): how
 * much of something they have, taking some of it, and giving them something.
 */
public final class Carried {
	private Carried() {
	}

	/** How many of an item a hunter has about them. */
	public static int count(final ServerPlayer player, final Item item) {
		int count = player.getInventory().countItem(item);
		GridInventory grid = GridService.get(player);
		for (Area area : GridService.ORDER) {
			for (Placed placed : grid.items(area)) {
				if (placed.stack().is(item)) {
					count += placed.stack().getCount();
				}
			}
		}
		return count;
	}

	/** Takes up to {@code amount} of an item, from the inventory first and then the grid; how many were taken. */
	public static int take(final ServerPlayer player, final Item item, final int amount) {
		int left = amount;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(item)) {
				int taken = Math.min(left, stack.getCount());
				stack.shrink(taken);
				left -= taken;
			}
		}
		GridInventory grid = GridService.get(player);
		for (Area area : GridService.ORDER) {
			if (left <= 0) {
				break;
			}
			List<Placed> items = new ArrayList<>(grid.items(area));
			boolean changed = false;
			for (int i = items.size() - 1; i >= 0 && left > 0; i--) {
				ItemStack there = items.get(i).stack();
				if (there.is(item)) {
					int taken = Math.min(left, there.getCount());
					left -= taken;
					changed = true;
					if (taken >= there.getCount()) {
						items.remove(i);
					} else {
						items.set(i, items.get(i).withStack(there.copyWithCount(there.getCount() - taken)));
					}
				}
			}
			if (changed) {
				grid = grid.with(area, items);
			}
		}
		GridService.set(player, grid);
		return amount - left;
	}

	/** Gives a hunter something: into the inventory, then the grid, and what still will not fit is dropped at their feet. */
	public static void give(final ServerPlayer player, final ItemStack stack) {
		ItemStack giving = stack.copy();
		if (!player.getInventory().add(giving) && !giving.isEmpty()) {
			GridService.insert(player, giving);
		}
		if (!giving.isEmpty()) {
			player.spawnAtLocation(player.level(), giving);
		}
	}
}
