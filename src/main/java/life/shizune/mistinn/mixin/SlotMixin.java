package life.shizune.mistinn.mixin;

import life.shizune.mistinn.grid.GridRules;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shuts the vanilla storage slots in every menu while the grid is in use: not shown, not
 * hoverable, nothing can be put in, taken out or swapped, whatever the client sends.
 */
@Mixin(Slot.class)
public abstract class SlotMixin {
	@Shadow
	@Final
	public Container container;

	@Shadow
	public abstract int getContainerSlot();

	private boolean mistinn$hidden() {
		return this.container instanceof Inventory inventory && GridRules.isHiddenSlot(inventory.player, this.getContainerSlot());
	}

	@Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
	private void mistinn$inactive(final CallbackInfoReturnable<Boolean> cir) {
		if (this.mistinn$hidden()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void mistinn$noPickup(final Player player, final CallbackInfoReturnable<Boolean> cir) {
		if (this.mistinn$hidden()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "allowModification", at = @At("HEAD"), cancellable = true)
	private void mistinn$noModification(final Player player, final CallbackInfoReturnable<Boolean> cir) {
		if (this.mistinn$hidden()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void mistinn$noPlace(final ItemStack stack, final CallbackInfoReturnable<Boolean> cir) {
		if (this.mistinn$hidden()) {
			cir.setReturnValue(false);
		}
	}
}
