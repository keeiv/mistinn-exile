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

	/** Puts the stack into pockets, then the backpack. Returns true when any of it went in. */
	public static boolean insert(final ServerPlayer player, final ItemStack stack) {
		int before = stack.getCount();
		GridInventory inv = get(player);
		// pockets first, then the rig's quick-reach pouches, then the backpack
		for (Area area : new Area[] {Area.POCKETS, Area.RIG, Area.BACKPACK}) {
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
			ItemStack there = items.get(target).stack();
			if (ItemStack.isSameItemSameComponents(there, carried) && there.getCount() < there.getMaxStackSize()) {
				int move = Math.min(carried.getCount(), there.getMaxStackSize() - there.getCount());
				items.set(target, items.get(target).withStack(there.copyWithCount(there.getCount() + move)));
				carried.shrink(move);
				set(player, inv.with(area, items));
				menu.setCarried(carried);
				menu.broadcastChanges();
				cue(player, GridSounds.place(there));
			} else {
				cue(player, MistinnSounds.GRID_REJECT);
			}
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
				ItemStack lifted = items.remove(index).stack().copy();
				inventory.setItem(slot, lifted);
				set(player, inv.with(area, items));
				player.containerMenu.broadcastChanges();
				cue(player, GridSounds.pickup(lifted));
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
		// what it is, kept before the move takes the stack down to nothing
		ItemStack kind = moving.copyWithCount(1);
		int before = moving.getCount();
		if (insert(player, moving)) {
			int moved = before - moving.getCount();
			slot.safeTake(moved, moved, player);
			menu.broadcastChanges();
			cue(player, GridSounds.place(kind));
		}
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
		set(player, new GridInventory(List.of(), inv.safe(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
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
