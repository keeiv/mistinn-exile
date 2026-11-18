package life.shizune.mistinn.grid;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.gear.Gear;
import life.shizune.mistinn.registry.MistinnAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Server-side changes to a hunter's grid storage. The client only asks; every move is checked
 * here. Items picked up by the cursor travel through the open menu's carried stack, so moving
 * between the grid, the hotbar and any open container uses the game's own cursor.
 */
public final class GridService {
	private static final Identifier WEIGHT_ID = Mistinn.id("backpack_weight");

	private GridService() {
	}

	public static GridInventory get(final ServerPlayer player) {
		return player.getAttachedOrElse(MistinnAttachments.GRID, GridInventory.EMPTY);
	}

	public static void set(final ServerPlayer player, final GridInventory inventory) {
		player.setAttached(MistinnAttachments.GRID, inventory);
	}

	/** Puts the stack into pockets, then the backpack. Returns true when any of it went in. */
	public static boolean insert(final ServerPlayer player, final ItemStack stack) {
		int before = stack.getCount();
		GridInventory inv = get(player);
		for (Area area : new Area[] {Area.POCKETS, Area.BACKPACK}) {
			if (stack.isEmpty()) {
				break;
			}
			GridShape shape = inv.shape(area);
			if (shape.sections().isEmpty()) {
				continue;
			}
			inv = inv.with(area, GridLogic.insert(shape, inv.items(area), stack));
		}
		if (stack.getCount() != before) {
			set(player, inv);
			return true;
		}
		return false;
	}

	/** Picks an item up from the grid onto the cursor. */
	public static void pick(final ServerPlayer player, final Area area, final int index) {
		AbstractContainerMenu menu = player.containerMenu;
		if (!menu.getCarried().isEmpty()) {
			return;
		}
		GridInventory inv = get(player);
		List<Placed> items = new ArrayList<>(inv.items(area));
		if (index < 0 || index >= items.size()) {
			return;
		}
		Placed taken = items.remove(index);
		set(player, inv.with(area, items));
		menu.setCarried(taken.stack().copy());
		menu.broadcastChanges();
	}

	/** Puts the cursor's stack down at (section, x, y), merging onto a matching stack there. */
	public static void place(final ServerPlayer player, final Area area, final int section, final int x, final int y, final boolean rotated) {
		AbstractContainerMenu menu = player.containerMenu;
		ItemStack carried = menu.getCarried();
		if (carried.isEmpty() || !GridLogic.canStore(carried)) {
			return;
		}
		GridInventory inv = get(player);
		GridShape shape = inv.shape(area);
		List<Placed> items = new ArrayList<>(inv.items(area));

		int target = GridLogic.itemAt(items, section, x, y);
		if (target >= 0) {
			ItemStack there = items.get(target).stack();
			if (ItemStack.isSameItemSameComponents(there, carried) && there.getCount() < there.getMaxStackSize()) {
				int move = Math.min(carried.getCount(), there.getMaxStackSize() - there.getCount());
				items.set(target, items.get(target).withStack(there.copyWithCount(there.getCount() + move)));
				carried.shrink(move);
				set(player, inv.with(area, items));
				menu.setCarried(carried);
				menu.broadcastChanges();
			}
			return;
		}
		int[] size = ItemSizes.of(carried);
		int w = rotated ? size[1] : size[0];
		int h = rotated ? size[0] : size[1];
		if (!GridLogic.fits(shape, items, section, x, y, w, h, -1)) {
			return;
		}
		items.add(new Placed(section, x, y, rotated, carried.copy()));
		set(player, inv.with(area, items));
		menu.setCarried(ItemStack.EMPTY);
		menu.broadcastChanges();
	}

	/** Clicking the backpack slot: equip the backpack on the cursor, or take the worn one off. */
	public static void backpackSlot(final ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		ItemStack carried = menu.getCarried();
		if (!carried.isEmpty() && GridInventory.backpackOf(carried) == null) {
			return;
		}
		GridInventory inv = get(player);
		ItemStack worn = inv.backpack();
		set(player, inv.withBackpack(carried.copy()));
		menu.setCarried(worn.copy());
		menu.broadcastChanges();
		applyWeight(player);
	}

	/** Throws an item from the grid onto the ground. */
	public static void drop(final ServerPlayer player, final Area area, final int index) {
		GridInventory inv = get(player);
		List<Placed> items = new ArrayList<>(inv.items(area));
		if (index < 0 || index >= items.size()) {
			return;
		}
		Placed taken = items.remove(index);
		set(player, inv.with(area, items));
		player.drop(taken.stack().copy(), true, Prediction.SERVER_ONLY);
	}

	/** Shift-click on a grid item: move it to a free hotbar slot. */
	public static void toHotbar(final ServerPlayer player, final Area area, final int index) {
		GridInventory inv = get(player);
		List<Placed> items = new ArrayList<>(inv.items(area));
		if (index < 0 || index >= items.size()) {
			return;
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (inventory.getItem(slot).isEmpty()) {
				inventory.setItem(slot, items.remove(index).stack().copy());
				set(player, inv.with(area, items));
				player.containerMenu.broadcastChanges();
				return;
			}
		}
	}

	/** Shift-click on a menu slot (a chest, the hotbar): move that stack into the grid. */
	public static void grabSlot(final ServerPlayer player, final int slotIndex) {
		AbstractContainerMenu menu = player.containerMenu;
		if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
			return;
		}
		Slot slot = menu.slots.get(slotIndex);
		// only ordinary storage slots: taking from a result slot would skip using up the ingredients
		if (!slot.hasItem() || !slot.mayPickup(player) || !slot.mayPlace(slot.getItem())) {
			return;
		}
		ItemStack moving = slot.getItem().copy();
		int before = moving.getCount();
		if (insert(player, moving)) {
			int moved = before - moving.getCount();
			slot.safeTake(moved, moved, player);
			menu.broadcastChanges();
		}
	}

	/** On death everything but the safe box falls where the hunter fell, the backpack whole. */
	public static void onDeath(final ServerPlayer player) {
		if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
			return;
		}
		GridInventory inv = get(player);
		for (Placed placed : inv.pockets()) {
			Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), placed.stack().copy());
		}
		if (!inv.backpack().isEmpty()) {
			Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), inv.backpack().copy());
		}
		set(player, new GridInventory(List.of(), inv.safe(), ItemStack.EMPTY));
	}

	/** The worn backpack slows the hunter by its weight. */
	public static void applyWeight(final ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		Gear.Backpack pack = GridInventory.backpackOf(get(player).backpack());
		if (pack == null || pack.speedPenalty() <= 0.0) {
			speed.removeModifier(WEIGHT_ID);
		} else {
			speed.addOrUpdateTransientModifier(new AttributeModifier(WEIGHT_ID, -pack.speedPenalty(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
