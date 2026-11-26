package life.shizune.mistinn.body;

import life.shizune.mistinn.gear.Gear;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Protection worn outside the vanilla equipment slots: the mask, kept with the grid inventory. */
public final class BodyGear {
	private BodyGear() {
	}

	/** Armour the worn mask gives the face; nothing else protects it. */
	public static float faceArmor(final ServerPlayer player) {
		Gear.Mask mask = GridInventory.maskOf(GridService.get(player).mask());
		// a heavy helmet's visor adds to the mask
		int visor = life.shizune.mistinn.gear.GearBurden.face(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
		return (mask == null ? 0.0F : mask.armor()) + visor;
	}

	/** A hit on the face wears the mask down like any armour piece; a broken mask is gone. */
	public static void wearMask(final ServerPlayer player, final float damage) {
		GridInventory inv = GridService.get(player);
		ItemStack mask = inv.mask().copy();
		if (mask.isEmpty() || damage <= 0.0F) {
			return;
		}
		mask.hurtAndBreak((int) Math.max(1.0F, damage / 4.0F), player.level(), player, item -> {
		});
		GridService.set(player, inv.withMask(mask.isEmpty() ? ItemStack.EMPTY : mask));
	}
}
