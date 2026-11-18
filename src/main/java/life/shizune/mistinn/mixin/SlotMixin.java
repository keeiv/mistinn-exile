package life.shizune.mistinn.mixin;

import life.shizune.mistinn.grid.GridRules;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides the vanilla storage slots in every menu while the grid is in use. */
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

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void mistinn$noPlace(final ItemStack stack, final CallbackInfoReturnable<Boolean> cir) {
		if (this.mistinn$hidden()) {
			cir.setReturnValue(false);
		}
	}
}
