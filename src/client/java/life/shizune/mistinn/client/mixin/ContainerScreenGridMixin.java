package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.grid.GridPanel;
import life.shizune.mistinn.grid.GridRules;
import life.shizune.mistinn.tier.Tiers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts the grid panel beside every container screen, gives every slot its tier colour, and
 * routes shift-clicks on chest (and, in the inventory, hotbar) slots into the grid.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenGridMixin {
	@Shadow
	@Final
	protected AbstractContainerMenu menu;

	@Shadow
	protected int leftPos;

	@Shadow
	protected int topPos;

	@Shadow
	@Final
	protected int imageWidth;

	@Inject(method = "extractContents", at = @At("TAIL"))
	private void mistinn$panel(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a, final CallbackInfo ci) {
		if (!GridPanel.active()) {
			return;
		}
		// cover the frames of the vanilla storage slots the grid replaces
		for (Slot slot : this.menu.slots) {
			if (slot.container instanceof Inventory inventory && GridRules.isHiddenSlot(inventory.player, slot.getContainerSlot())) {
				graphics.fill(this.leftPos + slot.x - 1, this.topPos + slot.y - 1, this.leftPos + slot.x + 17, this.topPos + slot.y + 17, 0xFFC6C6C6);
			}
		}
		GridPanel.render(graphics, this.menu, this.leftPos, this.topPos, this.imageWidth, mouseX, mouseY);
	}

	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void mistinn$tierBackground(final GuiGraphicsExtractor graphics, final Slot slot, final int mouseX, final int mouseY, final CallbackInfo ci) {
		if (slot.hasItem()) {
			int colour = Tiers.of(slot.getItem()).withAlpha(0x70);
			graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, colour);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void mistinn$click(final MouseButtonEvent event, final boolean doubleClick, final CallbackInfoReturnable<Boolean> cir) {
		if (GridPanel.click(this.menu, event.x(), event.y(), event.button(), event.hasShiftDown())) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void mistinn$key(final KeyEvent event, final CallbackInfoReturnable<Boolean> cir) {
		if (GridPanel.key(this.menu, event.key())) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), cancellable = true)
	private void mistinn$shiftIntoGrid(final Slot slot, final int slotId, final int buttonNum, final ContainerInput input, final CallbackInfo ci) {
		if (slot == null || input != ContainerInput.QUICK_MOVE || !GridPanel.active() || Minecraft.getInstance().player == null
			|| !slot.hasItem() || !slot.mayPlace(slot.getItem())) {
			return;
		}
		boolean fromContainer = !(slot.container instanceof Inventory);
		boolean fromHotbar = slot.container instanceof Inventory && slot.getContainerSlot() < Inventory.getSelectionSize()
			&& (Object) this instanceof InventoryScreen;
		if (fromContainer || fromHotbar) {
			GridPanel.grab(slot.index);
			ci.cancel();
		}
	}
}
