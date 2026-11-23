package life.shizune.mistinn.hardship;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.registry.MistinnComponents;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * What a Rust Anchor world takes from a hunter who falls, beyond what every world takes: the gear
 * they carried is worn down, part of their salt is gone for good, and they come back weak and
 * hungry. The safe box is spared, and is bigger to make up for it.
 */
public final class RustAnchorDeath {
	/** Share of an item's full durability a death takes. */
	public static final float WEAR = 0.6F;
	/** Share of the carried salt that is lost; the rest waits in the gravestone. */
	public static final float SALT_LOST = 0.4F;
	/** Share of each part's health a hunter comes back with. */
	public static final float RESPAWN_BODY = 0.5F;
	/** Food level a hunter comes back with. */
	public static final int RESPAWN_FOOD = 10;

	private RustAnchorDeath() {
	}

	/**
	 * Wears a carried item down by {@link #WEAR} of its full durability, never below one point
	 * left: a death does not break anything outright. Backpacks and rigs pass it on to what they
	 * hold. Durability that counts doses (surgery kits, painkillers) is not gear and is left.
	 */
	public static ItemStack wear(final ItemStack stack) {
		ItemStack worn = stack;
		if (gear(stack)) {
			worn = stack.copy();
			int max = stack.getMaxDamage();
			worn.setDamageValue(Math.min(max - 1, stack.getDamageValue() + Math.round(max * WEAR)));
		}
		List<Placed> held = stack.get(MistinnComponents.BACKPACK_CONTENTS);
		if (held != null && !held.isEmpty()) {
			List<Placed> wornHeld = new ArrayList<>();
			for (Placed placed : held) {
				wornHeld.add(placed.withStack(wear(placed.stack())));
			}
			if (worn == stack) {
				worn = stack.copy();
			}
			worn.set(MistinnComponents.BACKPACK_CONTENTS, wornHeld);
		}
		return worn;
	}

	private static boolean gear(final ItemStack stack) {
		return stack.isDamageableItem() && !stack.is(MistinnItems.SURGERY_KIT) && !stack.is(MistinnItems.PAINKILLERS);
	}

	/** Of the salt a fallen hunter carried, what the gravestone keeps. */
	public static int saltKept(final int carried) {
		return carried - (int) Math.floor(carried * SALT_LOST);
	}

	/** A hunter back from the dead: half of every part and an empty stomach. */
	public static void respawned(final ServerPlayer player) {
		BodyService.set(player, Body.whole(BodyService.max(player) * RESPAWN_BODY));
		player.getFoodData().setFoodLevel(RESPAWN_FOOD);
		player.getFoodData().setSaturation(0.0F);
	}

	/** A hunter in a Rust Anchor world keeps a 3x3 safe box. */
	public static void joined(final ServerPlayer player) {
		GridInventory grid = GridService.get(player);
		if (grid.safeSize() < GridInventory.RUST_ANCHOR_SAFE_SIZE) {
			GridService.set(player, grid.withSafeSize(GridInventory.RUST_ANCHOR_SAFE_SIZE));
		}
	}
}
