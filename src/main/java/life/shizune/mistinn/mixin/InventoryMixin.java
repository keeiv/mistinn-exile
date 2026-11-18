package life.shizune.mistinn.mixin;

import life.shizune.mistinn.grid.GridRules;
import life.shizune.mistinn.grid.GridService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Outside creative mode the 27 vanilla storage slots are not used: what a hunter picks up tops up
 * matching stacks on the hotbar, then goes into pockets and backpack, and only then fills an empty
 * hotbar slot.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
	@Shadow
	@Final
	public Player player;

	@Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
	private void mistinn$intoGrid(final int slot, final ItemStack stack, final CallbackInfoReturnable<Boolean> cir) {
		if (slot != -1 || stack.isEmpty() || !(this.player instanceof ServerPlayer serverPlayer) || !GridRules.usesGrid(this.player)) {
			return;
		}
		Inventory self = (Inventory) (Object) this;
		int before = stack.getCount();
		for (int i = 0; i < Inventory.getSelectionSize() && !stack.isEmpty(); i++) {
			ItemStack held = self.getItem(i);
			if (ItemStack.isSameItemSameComponents(held, stack) && held.getCount() < self.getMaxStackSize(held)) {
				int move = Math.min(stack.getCount(), self.getMaxStackSize(held) - held.getCount());
				held.grow(move);
				held.setPopTime(5);
				stack.shrink(move);
			}
		}
		if (!stack.isEmpty()) {
			GridService.insert(serverPlayer, stack);
		}
		if (!stack.isEmpty()) {
			// whatever is left may still go into an empty hotbar slot
			int free = self.getFreeSlot();
			if (free >= 0) {
				self.setItem(free, stack.copyAndClear());
				self.getItem(free).setPopTime(5);
			}
		}
		if (stack.getCount() != before) {
			cir.setReturnValue(true);
		} else {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
	private void mistinn$hotbarOnly(final CallbackInfoReturnable<Integer> cir) {
		if (!GridRules.usesGrid(this.player)) {
			return;
		}
		Inventory self = (Inventory) (Object) this;
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			if (self.getItem(i).isEmpty()) {
				cir.setReturnValue(i);
				return;
			}
		}
		cir.setReturnValue(-1);
	}

	@Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
	private void mistinn$hotbarStacks(final ItemStack newStack, final CallbackInfoReturnable<Integer> cir) {
		if (!GridRules.usesGrid(this.player)) {
			return;
		}
		Inventory self = (Inventory) (Object) this;
		for (int i = 0; i < Inventory.getSelectionSize(); i++) {
			ItemStack held = self.getItem(i);
			if (ItemStack.isSameItemSameComponents(held, newStack) && held.isStackable() && held.getCount() < self.getMaxStackSize(held)) {
				cir.setReturnValue(i);
				return;
			}
		}
		cir.setReturnValue(-1);
	}
}
