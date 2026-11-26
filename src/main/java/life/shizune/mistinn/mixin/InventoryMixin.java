package life.shizune.mistinn.mixin;

import life.shizune.mistinn.grid.GridRules;
import life.shizune.mistinn.grid.GridService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Outside creative mode the 27 vanilla storage slots are not used: what a hunter picks up tops up
 * matching stacks on the hotbar and in the grid, then fills an empty hotbar slot, then goes into
 * the pockets, the backpack and the rig ({@link GridService#store}).
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
		cir.setReturnValue(GridService.store(serverPlayer, stack));
	}

	/**
	 * What a closed screen hands back (the crafting grid's leftovers, a cancelled anvil) goes where a
	 * pickup would; only what finds no room at all is dropped, as before.
	 */
	@Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)V", at = @At("HEAD"), cancellable = true)
	private void mistinn$backIntoGrid(final ItemStack stack, final boolean sendPacket, final Prediction prediction, final CallbackInfo ci) {
		if (stack.isEmpty() || !(this.player instanceof ServerPlayer serverPlayer) || !GridRules.usesGrid(this.player)) {
			return;
		}
		GridService.store(serverPlayer, stack);
		if (stack.isEmpty()) {
			ci.cancel();
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
