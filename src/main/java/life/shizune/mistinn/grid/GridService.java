package life.shizune.mistinn.grid;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.gear.Gear;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
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
		showWorn(player, inventory);
	}

	/**
	 * Everyone nearby sees the worn mask, backpack and rig: their tiers go out to all, the rest of
	 * the grid only to its owner.
	 */
	public static void showWorn(final ServerPlayer player, final GridInventory inventory) {
		show(player, MistinnAttachments.WORN_MASK, MistinnItems.MASKS.indexOf(inventory.mask().getItem()));
		show(player, MistinnAttachments.WORN_BACKPACK, MistinnItems.BACKPACKS.indexOf(inventory.backpack().getItem()));
		show(player, MistinnAttachments.WORN_RIG, MistinnItems.RIGS.indexOf(inventory.rig().getItem()));
	}

	/** Only sends when the tier changed, so moving items around inside the grid costs nothing. */
	private static void show(final ServerPlayer player, final AttachmentType<Integer> type, final int tier) {
		if (player.getAttachedOrElse(type, -1) != tier) {
			player.setAttached(type, tier);
		}
	}

	/** Where things go in the grid, in this order: the pockets, then the backpack, then the rig. */
	public static final Area[] ORDER = {Area.POCKETS, Area.BACKPACK, Area.RIG};

	/**
	 * Puts the stack into the grid: onto matching stacks wherever they lie, then into the first free
	 * spot of the pockets, the backpack, the rig, in that order. Returns true when any of it went in;
	 * {@code stack} keeps the rest.
	 */
	public static boolean insert(final ServerPlayer player, final ItemStack stack) {
		int before = stack.getCount();
		GridInventory inv = get(player);
		for (Area area : ORDER) {
			if (!stack.isEmpty() && !inv.shape(area).sections().isEmpty()) {
				inv = inv.with(area, GridLogic.topUp(inv.items(area), stack));
			}
		}
		for (Area area : ORDER) {
			if (!stack.isEmpty() && !inv.shape(area).sections().isEmpty()) {
				inv = inv.with(area, GridLogic.insert(inv.shape(area), inv.items(area), stack));
			}
		}
		if (stack.getCount() != before) {
			set(player, inv);
			return true;
		}
		return false;
	}

	/**
	 * Where what a hunter picks up goes: onto matching stacks on the hotbar and in the grid first,
	 * then an empty hotbar slot, then the pockets, the backpack and the rig. Returns true when any of
	 * it was taken; {@code stack} keeps the rest.
	 */
	public static boolean store(final ServerPlayer player, final ItemStack stack) {
		int before = stack.getCount();
		Inventory inventory = player.getInventory();
		for (int i = 0; i < Inventory.getSelectionSize() && !stack.isEmpty(); i++) {
			ItemStack held = inventory.getItem(i);
			if (ItemStack.isSameItemSameComponents(held, stack) && held.getCount() < inventory.getMaxStackSize(held)) {
				int move = Math.min(stack.getCount(), inventory.getMaxStackSize(held) - held.getCount());
				held.grow(move);
				held.setPopTime(5);
				stack.shrink(move);
			}
		}
		if (!stack.isEmpty()) {
			GridInventory inv = get(player);
			GridInventory topped = inv;
			for (Area area : ORDER) {
				if (!stack.isEmpty() && !topped.shape(area).sections().isEmpty()) {
					topped = topped.with(area, GridLogic.topUp(topped.items(area), stack));
				}
			}
			if (topped != inv) {
				set(player, topped);
			}
		}
		for (int i = 0; i < Inventory.getSelectionSize() && !stack.isEmpty(); i++) {
			if (inventory.getItem(i).isEmpty()) {
				inventory.setItem(i, stack.copyAndClear());
				inventory.getItem(i).setPopTime(5);
			}
		}
		if (!stack.isEmpty()) {
			insert(player, stack);
		}
		return stack.getCount() != before;
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
		cue(player, GridSounds.pickup(taken.stack()));
	}

	/** Puts the cursor's stack down at (section, x, y), merging onto a matching stack there. */
	public static void place(final ServerPlayer player, final Area area, final int section, final int x, final int y, final boolean rotated) {
		AbstractContainerMenu menu = player.containerMenu;
		ItemStack carried = menu.getCarried();
		if (carried.isEmpty()) {
			return;
		}
		if (!GridLogic.canStore(carried)) {
			cue(player, MistinnSounds.GRID_REJECT);
			return;
		}
		GridInventory inv = get(player);
		GridShape shape = inv.shape(area);
		List<Placed> items = new ArrayList<>(inv.items(area));

		int target = GridLogic.itemAt(items, section, x, y);
		if (target >= 0) {
			Placed lying = items.get(target);
			ItemStack there = lying.stack();
			if (ItemStack.isSameItemSameComponents(there, carried)) {
				if (there.getCount() >= there.getMaxStackSize()) {
					cue(player, MistinnSounds.GRID_REJECT);
					return;
				}
				int move = Math.min(carried.getCount(), there.getMaxStackSize() - there.getCount());
				items.set(target, lying.withStack(there.copyWithCount(there.getCount() + move)));
				carried.shrink(move);
				set(player, inv.with(area, items));
				menu.setCarried(carried);
				menu.broadcastChanges();
				cue(player, GridSounds.place(there));
				return;
			}
			// something else lies there: what is carried takes its place, and it goes on the cursor
			items.remove(target);
			Placed swapped = GridLogic.spotFor(shape, items, carried, rotated,
				new int[] {lying.section(), lying.x(), lying.y()}, new int[] {section, x, y});
			if (swapped == null || !sameCorner(swapped, lying) && !sameCorner(swapped, section, x, y)) {
				cue(player, MistinnSounds.GRID_REJECT);
				return;
			}
			items.add(swapped);
			set(player, inv.with(area, items));
			menu.setCarried(there.copy());
			menu.broadcastChanges();
			cue(player, GridSounds.place(carried));
			return;
		}
		int[] size = ItemSizes.of(carried);
		int w = rotated ? size[1] : size[0];
		int h = rotated ? size[0] : size[1];
		if (!GridLogic.fits(shape, items, section, x, y, w, h, -1)) {
			cue(player, MistinnSounds.GRID_REJECT);
			return;
		}
		items.add(new Placed(section, x, y, rotated, carried.copy()));
		set(player, inv.with(area, items));
		menu.setCarried(ItemStack.EMPTY);
		menu.broadcastChanges();
		cue(player, GridSounds.place(carried));
	}

	/**
	 * Takes every backpack and chest rig out of the grid: from before bags could not hold bags, an old
	 * save may still keep one in the pockets, the safe box or a worn bag. They go to the hotbar, or at
	 * the hunter's feet. Returns how many were taken out.
	 */
	public static int unnest(final ServerPlayer player) {
		GridInventory inv = get(player);
		List<ItemStack> out = new ArrayList<>();
		for (Area area : Area.values()) {
			List<Placed> items = inv.items(area);
			List<Placed> kept = new ArrayList<>();
			for (Placed placed : items) {
				if (GridLogic.canStore(placed.stack())) {
					kept.add(placed);
				} else {
					out.add(placed.stack().copy());
				}
			}
			if (kept.size() != items.size()) {
				inv = inv.with(area, kept);
			}
		}
		if (out.isEmpty()) {
			return 0;
		}
		set(player, inv);
		Inventory inventory = player.getInventory();
		for (ItemStack bag : out) {
			int free = inventory.getFreeSlot();
			if (free >= 0) {
				inventory.setItem(free, bag);
			} else {
				player.drop(bag, false, Prediction.SERVER_ONLY);
			}
		}
		player.containerMenu.broadcastChanges();
		player.sendSystemMessage(Component.translatable("message.mistinn.grid.unnested", out.size()));
		return out.size();
	}

	/** Clicking the backpack slot: equip the backpack on the cursor, or take the worn one off. */
	public static void backpackSlot(final ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		ItemStack carried = menu.getCarried();
		if (!carried.isEmpty() && GridInventory.backpackOf(carried) == null) {
			cue(player, MistinnSounds.GRID_REJECT);
			return;
		}
		GridInventory inv = get(player);
		ItemStack worn = inv.backpack();
		if (carried.isEmpty() && worn.isEmpty()) {
			return;
		}
		set(player, inv.withBackpack(carried.copy()));
		menu.setCarried(worn.copy());
		menu.broadcastChanges();
		gear(player, MistinnSounds.EQUIP_BACKPACK);
		applyWeight(player);
		// a bag from an old save may hold another
		unnest(player);
	}

	/** Clicking the rig slot: wear the rig on the cursor, or take the worn one off. */
	public static void rigSlot(final ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		ItemStack carried = menu.getCarried();
		if (!carried.isEmpty() && GridInventory.rigOf(carried) == null) {
			cue(player, MistinnSounds.GRID_REJECT);
			return;
		}
		GridInventory inv = get(player);
		ItemStack worn = inv.rig();
		if (carried.isEmpty() && worn.isEmpty()) {
			return;
		}
		set(player, inv.withRig(carried.copy()));
		menu.setCarried(worn.copy());
		menu.broadcastChanges();
		gear(player, MistinnSounds.EQUIP_RIG);
		unnest(player);
	}

	/** Clicking the mask slot: put on the mask on the cursor, or take the worn one off. */
	public static void maskSlot(final ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		ItemStack carried = menu.getCarried();
		if (!carried.isEmpty() && GridInventory.maskOf(carried) == null) {
			cue(player, MistinnSounds.GRID_REJECT);
			return;
		}
		GridInventory inv = get(player);
		ItemStack worn = inv.mask();
		if (carried.isEmpty() && worn.isEmpty()) {
			return;
		}
		set(player, inv.withMask(carried.copy()));
		menu.setCarried(worn.copy());
		menu.broadcastChanges();
		gear(player, MistinnSounds.EQUIP_MASK);
	}

	/** An inventory sound for the owner alone: rummaging in one's own pack is not heard across the room. */
	private static void cue(final ServerPlayer player, final SoundEvent sound) {
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS,
			player.getX(), player.getY(), player.getZ(), 0.7F, 0.95F + player.getRandom().nextFloat() * 0.1F, player.getRandom().nextLong()));
	}

	/** Gear put on or taken off, heard by those nearby as armour is. */
	private static void gear(final ServerPlayer player, final SoundEvent sound) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.8F, 0.95F + player.getRandom().nextFloat() * 0.1F);
	}

	/**
	 * Re-lays the backpack and rig when an item no longer fits where it lies (a layout changed
	 * between versions). Items with no place left go to pockets or the hotbar, else the ground.
	 */
	public static void tidy(final ServerPlayer player) {
		GridInventory inv = get(player);
		List<ItemStack> loose = new ArrayList<>();
		boolean changed = false;
		for (Area area : new Area[] {Area.BACKPACK, Area.RIG}) {
			GridLogic.Repacked repacked = GridLogic.repack(inv.shape(area), inv.items(area));
			if (repacked.changed()) {
				inv = inv.with(area, repacked.kept());
				loose.addAll(repacked.loose());
				changed = true;
			}
		}
		if (!changed) {
			return;
		}
		set(player, inv);
		for (ItemStack stack : loose) {
			insert(player, stack);
			if (!stack.isEmpty() && !player.getInventory().add(stack)) {
				player.drop(stack, false, Prediction.SERVER_ONLY);
			}
		}
		player.containerMenu.broadcastChanges();
	}

	/** Throws an item from the grid onto the ground: one of it, or the whole stack. */
	public static void drop(final ServerPlayer player, final Area area, final int index, final boolean all) {
		GridInventory inv = get(player);
		List<Placed> items = new ArrayList<>(inv.items(area));
		if (index < 0 || index >= items.size()) {
			return;
		}
		Placed lying = items.get(index);
		ItemStack thrown;
		if (all || lying.stack().getCount() <= 1) {
			items.remove(index);
			thrown = lying.stack().copy();
		} else {
			items.set(index, lying.withStack(lying.stack().copyWithCount(lying.stack().getCount() - 1)));
			thrown = lying.stack().copyWithCount(1);
		}
		set(player, inv.with(area, items));
		player.drop(thrown, true, Prediction.SERVER_ONLY);
	}

	/**
	 * Shift-click on a grid item: it goes where the open screen would send a stack from the
	 * inventory. Into a chest or a furnace with one open, onto the body if it is armour, else to the
	 * hotbar. The stack is lent to an empty hidden storage slot and the menu's own quick move is run
	 * on it, so every container's rules apply; what the move leaves stays where it lay.
	 */
	public static void quickMove(final ServerPlayer player, final Area area, final int index) {
		AbstractContainerMenu menu = player.containerMenu;
		GridInventory inv = get(player);
		List<Placed> items = new ArrayList<>(inv.items(area));
		if (index < 0 || index >= items.size() || !menu.getCarried().isEmpty()) {
			return;
		}
		Placed lying = items.get(index);
		Inventory inventory = player.getInventory();
		int lent = -1;
		for (int slot = Inventory.getSelectionSize(); slot < Inventory.INVENTORY_SIZE; slot++) {
			if (inventory.getItem(slot).isEmpty()) {
				lent = slot;
				break;
			}
		}
		int menuSlot = -1;
		for (int i = 0; lent >= 0 && i < menu.slots.size(); i++) {
			Slot slot = menu.slots.get(i);
			if (slot.container == inventory && slot.getContainerSlot() == lent) {
				menuSlot = i;
				break;
			}
		}
		if (menuSlot < 0) {
			// a screen without the hunter's inventory in it: the hotbar is the only place to go
			ItemStack moving = lying.stack().copy();
			for (int i = 0; i < Inventory.getSelectionSize() && !moving.isEmpty(); i++) {
				ItemStack held = inventory.getItem(i);
				if (ItemStack.isSameItemSameComponents(held, moving) && held.getCount() < inventory.getMaxStackSize(held)) {
					int move = Math.min(moving.getCount(), inventory.getMaxStackSize(held) - held.getCount());
					held.grow(move);
					moving.shrink(move);
				}
			}
			for (int i = 0; i < Inventory.getSelectionSize() && !moving.isEmpty(); i++) {
				if (inventory.getItem(i).isEmpty()) {
					inventory.setItem(i, moving.copyAndClear());
				}
			}
			finishMove(player, inv, area, items, index, lying, moving);
			return;
		}
		inventory.setItem(lent, lying.stack().copy());
		Slot slot = menu.slots.get(menuSlot);
		ItemStack moved = menu.quickMoveStack(player, menuSlot);
		for (int guard = 0; guard < 8 && !moved.isEmpty() && ItemStack.isSameItem(slot.getItem(), moved); guard++) {
			moved = menu.quickMoveStack(player, menuSlot);
		}
		ItemStack left = inventory.getItem(lent).copy();
		inventory.setItem(lent, ItemStack.EMPTY);
		finishMove(player, inv, area, items, index, lying, left);
	}

	/** After a quick move: the grid keeps what was left, and the hunter hears whether anything went. */
	private static void finishMove(final ServerPlayer player, final GridInventory inv, final Area area, final List<Placed> items, final int index,
		final Placed lying, final ItemStack left) {
		if (left.getCount() == lying.stack().getCount() && ItemStack.isSameItemSameComponents(left, lying.stack())) {
			cue(player, MistinnSounds.GRID_REJECT);
			return;
		}
		if (left.isEmpty()) {
			items.remove(index);
		} else {
			items.set(index, lying.withStack(left));
		}
		set(player, inv.with(area, items));
		player.containerMenu.broadcastChanges();
		cue(player, GridSounds.pickup(lying.stack()));
	}

	/**
	 * A number key, or the offhand key, over a grid cell: what lies there trades places with that
	 * hotbar slot (40 for the offhand). Over an empty cell the slot's stack is put down there. What
	 * comes from the hotbar lies where the grid item lay if it fits, else anywhere in the grid.
	 */
	public static void swap(final ServerPlayer player, final Area area, final int section, final int x, final int y, final int hotbar, final boolean rotated) {
		if (hotbar != Inventory.SLOT_OFFHAND && (hotbar < 0 || hotbar >= Inventory.getSelectionSize())) {
			return;
		}
		Inventory inventory = player.getInventory();
		ItemStack held = inventory.getItem(hotbar);
		GridInventory inv = get(player);
		List<Placed> items = new ArrayList<>(inv.items(area));
		int index = GridLogic.itemAt(items, section, x, y);
		if (index < 0) {
			if (held.isEmpty()) {
				return;
			}
			Placed put = GridLogic.spotFor(inv.shape(area), items, held, rotated, new int[] {section, x, y});
			if (put == null || !sameCorner(put, section, x, y)) {
				cue(player, MistinnSounds.GRID_REJECT);
				return;
			}
			items.add(put);
			set(player, inv.with(area, items));
			inventory.setItem(hotbar, ItemStack.EMPTY);
			player.containerMenu.broadcastChanges();
			cue(player, GridSounds.place(held));
			return;
		}
		Placed lying = items.remove(index);
		GridInventory after = inv.with(area, items);
		if (!held.isEmpty()) {
			Placed put = GridLogic.spotFor(inv.shape(area), items, held, lying.rotated(), new int[] {lying.section(), lying.x(), lying.y()});
			if (put != null) {
				items.add(put);
				after = inv.with(area, items);
			} else {
				// no room in this part of the grid: anywhere else it fits, whole
				after = null;
				for (Area other : ORDER) {
					if (other == area || inv.shape(other).sections().isEmpty() || !GridLogic.canStore(held)) {
						continue;
					}
					Placed elsewhere = GridLogic.freeSpot(inv.shape(other), inv.items(other), held);
					if (elsewhere != null) {
						List<Placed> there = new ArrayList<>(inv.items(other));
						there.add(elsewhere);
						after = inv.with(area, items).with(other, there);
						break;
					}
				}
				if (after == null) {
					cue(player, MistinnSounds.GRID_REJECT);
					return;
				}
			}
		}
		set(player, after);
		inventory.setItem(hotbar, lying.stack().copy());
		player.containerMenu.broadcastChanges();
		cue(player, GridSounds.pickup(lying.stack()));
	}

	/**
	 * 整理 a backpack or a rig: its things laid out afresh, a kind's stacks merged, the largest
	 * first. When they would not all fit again that way nothing moves and the hunter hears so.
	 */
	public static void sort(final ServerPlayer player, final Area area) {
		if (area != Area.BACKPACK && area != Area.RIG) {
			return;
		}
		GridInventory inv = get(player);
		List<Placed> items = inv.items(area);
		if (items.isEmpty()) {
			return;
		}
		List<Placed> sorted = GridLogic.sorted(inv.shape(area), items);
		if (sorted == null) {
			cue(player, MistinnSounds.GRID_REJECT);
			return;
		}
		set(player, inv.with(area, sorted));
		player.containerMenu.broadcastChanges();
		cue(player, MistinnSounds.GRID_ROTATE);
	}

	/** Right-click on a grid item with nothing carried: half of it (the larger half) onto the cursor. */
	public static void half(final ServerPlayer player, final Area area, final int index) {
		AbstractContainerMenu menu = player.containerMenu;
		if (!menu.getCarried().isEmpty()) {
			return;
		}
		GridInventory inv = get(player);
		List<Placed> items = new ArrayList<>(inv.items(area));
		if (index < 0 || index >= items.size()) {
			return;
		}
		Placed lying = items.get(index);
		int count = lying.stack().getCount();
		int taken = (count + 1) / 2;
		if (taken >= count) {
			items.remove(index);
		} else {
			items.set(index, lying.withStack(lying.stack().copyWithCount(count - taken)));
		}
		set(player, inv.with(area, items));
		menu.setCarried(lying.stack().copyWithCount(taken));
		menu.broadcastChanges();
		cue(player, GridSounds.pickup(lying.stack()));
	}

	/** Right-click on a grid cell while carrying: one of the carried stack is put down there. */
	public static void placeOne(final ServerPlayer player, final Area area, final int section, final int x, final int y, final boolean rotated) {
		AbstractContainerMenu menu = player.containerMenu;
		ItemStack carried = menu.getCarried();
		if (carried.isEmpty()) {
			return;
		}
		if (!GridLogic.canStore(carried)) {
			cue(player, MistinnSounds.GRID_REJECT);
			return;
		}
		GridInventory inv = get(player);
		List<Placed> items = new ArrayList<>(inv.items(area));
		int target = GridLogic.itemAt(items, section, x, y);
		ItemStack kind = carried.copyWithCount(1);
		if (target >= 0) {
			ItemStack there = items.get(target).stack();
			if (!ItemStack.isSameItemSameComponents(there, carried) || there.getCount() >= there.getMaxStackSize()) {
				cue(player, MistinnSounds.GRID_REJECT);
				return;
			}
			items.set(target, items.get(target).withStack(there.copyWithCount(there.getCount() + 1)));
		} else {
			int[] size = ItemSizes.of(carried);
			int w = rotated ? size[1] : size[0];
			int h = rotated ? size[0] : size[1];
			if (!GridLogic.fits(inv.shape(area), items, section, x, y, w, h, -1)) {
				cue(player, MistinnSounds.GRID_REJECT);
				return;
			}
			items.add(new Placed(section, x, y, rotated, kind.copy()));
		}
		carried.shrink(1);
		set(player, inv.with(area, items));
		menu.setCarried(carried);
		menu.broadcastChanges();
		cue(player, GridSounds.place(kind));
	}

	/**
	 * A double click while carrying: the carried stack gathers what matches it from the pockets, the
	 * backpack, the rig and the hotbar, up to a full stack. The safe box keeps what it holds.
	 */
	public static void collect(final ServerPlayer player) {
		AbstractContainerMenu menu = player.containerMenu;
		ItemStack carried = menu.getCarried();
		if (carried.isEmpty() || carried.getCount() >= carried.getMaxStackSize()) {
			return;
		}
		int before = carried.getCount();
		GridInventory inv = get(player);
		for (Area area : ORDER) {
			List<Placed> items = new ArrayList<>(inv.items(area));
			boolean changed = false;
			for (int i = items.size() - 1; i >= 0 && carried.getCount() < carried.getMaxStackSize(); i--) {
				ItemStack there = items.get(i).stack();
				if (!ItemStack.isSameItemSameComponents(there, carried)) {
					continue;
				}
				int move = Math.min(there.getCount(), carried.getMaxStackSize() - carried.getCount());
				carried.grow(move);
				if (move >= there.getCount()) {
					items.remove(i);
				} else {
					items.set(i, items.get(i).withStack(there.copyWithCount(there.getCount() - move)));
				}
				changed = true;
			}
			if (changed) {
				inv = inv.with(area, items);
			}
		}
		Inventory inventory = player.getInventory();
		for (int i = 0; i < Inventory.getSelectionSize() && carried.getCount() < carried.getMaxStackSize(); i++) {
			ItemStack there = inventory.getItem(i);
			if (ItemStack.isSameItemSameComponents(there, carried)) {
				int move = Math.min(there.getCount(), carried.getMaxStackSize() - carried.getCount());
				carried.grow(move);
				there.shrink(move);
			}
		}
		if (carried.getCount() == before) {
			return;
		}
		set(player, inv);
		menu.setCarried(carried);
		menu.broadcastChanges();
		cue(player, GridSounds.pickup(carried));
	}

	private static boolean sameCorner(final Placed placed, final Placed other) {
		return sameCorner(placed, other.section(), other.x(), other.y());
	}

	private static boolean sameCorner(final Placed placed, final int section, final int x, final int y) {
		return placed.section() == section && placed.x() == x && placed.y() == y;
	}

	/**
	 * Shift-click on a menu slot: a stack from the hotbar goes into the grid; one from a chest goes
	 * where a pickup would (the hotbar first, then pockets, backpack and rig).
	 */
	public static void grabSlot(final ServerPlayer player, final int slotIndex) {
		AbstractContainerMenu menu = player.containerMenu;
		if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
			return;
		}
		Slot slot = menu.slots.get(slotIndex);
		// only ordinary storage slots: taking from a result slot would skip using up the ingredients
		if (!slot.hasItem() || !slot.mayPickup(player) || !slot.mayPlace(slot.getItem()) && !(slot instanceof life.shizune.mistinn.expedition.SearchMenu.CacheSlot)) {
			return;
		}
		ItemStack moving = slot.getItem().copy();
		boolean fromHotbar = slot.container == player.getInventory() && slot.getContainerSlot() < Inventory.getSelectionSize();
		if (fromHotbar && wearFromSlot(player, slot, moving)) {
			return;
		}
		// what it is, kept before the move takes the stack down to nothing
		ItemStack kind = moving.copyWithCount(1);
		int before = moving.getCount();
		if (fromHotbar ? insert(player, moving) : store(player, moving)) {
			int moved = before - moving.getCount();
			slot.safeTake(moved, moved, player);
			menu.broadcastChanges();
			cue(player, GridSounds.place(kind));
		}
	}

	/**
	 * Shift-click on a backpack, a rig or a mask on the hotbar: it is put on, as armour is, when
	 * nothing is worn there yet. Returns true when it was.
	 */
	private static boolean wearFromSlot(final ServerPlayer player, final Slot slot, final ItemStack stack) {
		GridInventory inv = get(player);
		if (GridInventory.backpackOf(stack) != null && inv.backpack().isEmpty()) {
			set(player, inv.withBackpack(stack.copyWithCount(1)));
			gear(player, MistinnSounds.EQUIP_BACKPACK);
			applyWeight(player);
		} else if (GridInventory.rigOf(stack) != null && inv.rig().isEmpty()) {
			set(player, inv.withRig(stack.copyWithCount(1)));
			gear(player, MistinnSounds.EQUIP_RIG);
		} else if (GridInventory.maskOf(stack) != null && inv.mask().isEmpty()) {
			set(player, inv.withMask(stack.copyWithCount(1)));
			gear(player, MistinnSounds.EQUIP_MASK);
		} else {
			return false;
		}
		slot.safeTake(1, 1, player);
		player.containerMenu.broadcastChanges();
		unnest(player);
		return true;
	}

	/**
	 * Empties the 27 hidden vanilla storage slots into the grid. Anything that reaches them by a
	 * route the grid does not cover (commands, other mods, a switch out of creative) is moved
	 * into pockets and backpack, then an empty hotbar slot, and what still does not fit is
	 * dropped at the hunter's feet, so no item ever sits where the hunter cannot reach it.
	 */
	public static void sweepHiddenSlots(final ServerPlayer player) {
		if (!GridRules.usesGrid(player) || !player.isAlive()) {
			return;
		}
		Inventory inventory = player.getInventory();
		boolean changed = false;
		for (int slot = Inventory.getSelectionSize(); slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack moving = stack.copy();
			inventory.setItem(slot, ItemStack.EMPTY);
			insert(player, moving);
			if (!moving.isEmpty()) {
				int free = inventory.getFreeSlot();
				if (free >= 0) {
					inventory.setItem(free, moving);
				} else {
					player.drop(moving, false, Prediction.SERVER_ONLY);
				}
			}
			changed = true;
		}
		if (changed) {
			player.containerMenu.broadcastChanges();
		}
	}

	/**
	 * On death everything but the safe box falls where the hunter fell; backpack and rig whole.
	 * For hunters this finds nothing left: their things have already gone into the gravestone
	 * ({@link life.shizune.mistinn.residue.ResidueService#collect}).
	 */
	public static void onDeath(final ServerPlayer player) {
		if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
			return;
		}
		GridInventory inv = get(player);
		for (Placed placed : inv.pockets()) {
			Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), placed.stack().copy());
		}
		for (ItemStack worn : new ItemStack[] {inv.backpack(), inv.rig(), inv.mask()}) {
			if (!worn.isEmpty()) {
				Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), worn.copy());
			}
		}
		set(player, inv.onlySafe());
	}

	/** The worn backpack (and a heavy rig) slow the hunter by their weight. */
	public static void applyWeight(final ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		GridInventory inv = get(player);
		Gear.Backpack pack = GridInventory.backpackOf(inv.backpack());
		Gear.ChestRig rig = GridInventory.rigOf(inv.rig());
		double weight = (pack == null ? 0.0 : pack.speedPenalty()) + (rig == null ? 0.0 : rig.speedPenalty());
		if (weight <= 0.0) {
			speed.removeModifier(WEIGHT_ID);
		} else {
			speed.addOrUpdateTransientModifier(new AttributeModifier(WEIGHT_ID, -weight, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
