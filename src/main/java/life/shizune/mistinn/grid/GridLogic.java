package life.shizune.mistinn.grid;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Pure placement rules for a grid: no players, no networking. */
public final class GridLogic {
	private GridLogic() {
	}

	/** Index of the item covering the cell, or -1. */
	public static int itemAt(final List<Placed> items, final int section, final int x, final int y) {
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).covers(section, x, y)) {
				return i;
			}
		}
		return -1;
	}

	/** Whether a w x h footprint at (x, y) lies inside the section and touches no other item. */
	public static boolean fits(final GridShape shape, final List<Placed> items, final int section, final int x, final int y, final int w, final int h, final int ignore) {
		if (section < 0 || section >= shape.sections().size()) {
			return false;
		}
		GridShape.Section s = shape.sections().get(section);
		if (x < 0 || y < 0 || x + w > s.width() || y + h > s.height()) {
			return false;
		}
		for (int i = 0; i < items.size(); i++) {
			if (i == ignore) {
				continue;
			}
			Placed other = items.get(i);
			if (other.section() != section) {
				continue;
			}
			boolean apart = x + w <= other.x() || other.x() + other.width() <= x || y + h <= other.y() || other.y() + other.height() <= y;
			if (!apart) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Puts as much of the stack as possible into the grid: first onto matching stacks, then into
	 * the first free spot (upright, then turned). Returns the new list; {@code stack} keeps what
	 * did not fit.
	 */
	public static List<Placed> insert(final GridShape shape, final List<Placed> items, final ItemStack stack) {
		List<Placed> result = topUp(items, stack);
		if (stack.isEmpty() || !canStore(stack)) {
			return result;
		}
		Placed spot = freeSpot(shape, result, stack);
		if (spot != null) {
			result.add(spot);
			stack.setCount(0);
		}
		return result;
	}

	/**
	 * Tops up the matching stacks already in the grid with what the stack holds, taking no new
	 * cell. Returns the new list; {@code stack} keeps what did not go on.
	 */
	public static List<Placed> topUp(final List<Placed> items, final ItemStack stack) {
		List<Placed> result = new ArrayList<>(items);
		if (stack.isEmpty() || !canStore(stack)) {
			return result;
		}
		for (int i = 0; i < result.size() && !stack.isEmpty(); i++) {
			ItemStack held = result.get(i).stack();
			if (ItemStack.isSameItemSameComponents(held, stack) && held.getCount() < held.getMaxStackSize()) {
				int move = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
				result.set(i, result.get(i).withStack(held.copyWithCount(held.getCount() + move)));
				stack.shrink(move);
			}
		}
		return result;
	}

	/**
	 * Where a whole stack can lie: the first of the given corners {section, x, y} where it fits
	 * upright or turned (the preferred way first), else the first free spot; null when nowhere.
	 */
	public static @org.jspecify.annotations.Nullable Placed spotFor(final GridShape shape, final List<Placed> items, final ItemStack stack, final boolean preferTurned, final int[]... corners) {
		if (stack.isEmpty() || !canStore(stack)) {
			return null;
		}
		int[] size = ItemSizes.of(stack);
		for (int[] corner : corners) {
			for (boolean turned : new boolean[] {preferTurned, !preferTurned}) {
				int w = turned ? size[1] : size[0];
				int h = turned ? size[0] : size[1];
				if (fits(shape, items, corner[0], corner[1], corner[2], w, h, -1)) {
					return new Placed(corner[0], corner[1], corner[2], turned, stack.copy());
				}
			}
		}
		return freeSpot(shape, items, stack);
	}

	/** The first spot where the whole stack fits, scanning sections then rows; null when full. */
	public static Placed freeSpot(final GridShape shape, final List<Placed> items, final ItemStack stack) {
		int[] size = ItemSizes.of(stack);
		for (boolean rotated : new boolean[] {false, true}) {
			int w = rotated ? size[1] : size[0];
			int h = rotated ? size[0] : size[1];
			if (rotated && w == h) {
				continue;
			}
			for (int section = 0; section < shape.sections().size(); section++) {
				GridShape.Section s = shape.sections().get(section);
				for (int y = 0; y + h <= s.height(); y++) {
					for (int x = 0; x + w <= s.width(); x++) {
						if (fits(shape, items, section, x, y, w, h, -1)) {
							return new Placed(section, x, y, rotated, stack.copy());
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * No bag in a bag: a backpack or a chest rig, full or empty, never goes into a grid (pockets, safe
	 * box, backpack or rig). They are worn, carried on the hotbar or left in a chest.
	 */
	public static boolean canStore(final ItemStack stack) {
		if (GridInventory.backpackOf(stack) != null || GridInventory.rigOf(stack) != null) {
			return false;
		}
		List<Placed> contents = stack.get(life.shizune.mistinn.registry.MistinnComponents.BACKPACK_CONTENTS);
		return contents == null || contents.isEmpty();
	}

	/** What {@link #repack} kept in place and what it could not place again. */
	public record Repacked(List<Placed> kept, List<ItemStack> loose, boolean changed) {
	}

	/**
	 * Keeps every item that still fits where it lies and places the others again. Needed when a
	 * container's layout changes between versions: an item may now lie outside its compartment
	 * or on top of another. What finds no place at all is returned, never lost.
	 */
	public static Repacked repack(final GridShape shape, final List<Placed> items) {
		List<Placed> kept = new ArrayList<>();
		List<ItemStack> loose = new ArrayList<>();
		for (Placed placed : items) {
			if (fits(shape, kept, placed.section(), placed.x(), placed.y(), placed.width(), placed.height(), -1)) {
				kept.add(placed);
			} else {
				loose.add(placed.stack().copy());
			}
		}
		if (loose.isEmpty()) {
			return new Repacked(items, List.of(), false);
		}
		List<ItemStack> left = new ArrayList<>();
		for (ItemStack stack : loose) {
			kept = insert(shape, kept, stack);
			if (!stack.isEmpty()) {
				left.add(stack);
			}
		}
		return new Repacked(kept, left, true);
	}
}
