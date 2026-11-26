package life.shizune.mistinn.gear;

import java.util.IdentityHashMap;
import java.util.Map;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What the hunter's gear costs them all together: the helmet, the body armour, the rig and the pack
 * each add their {@link Gear.Burden}. Every noise the hunter makes carries {@link #noise} times as
 * far, and stamina comes back {@link #stamina} times as fast.
 */
public final class GearBurden {
	/** However quiet the gear, noise still carries at least this share of its reach. */
	public static final float QUIETEST = 0.5F;
	/** However heavy the gear, stamina still comes back at least this share as fast. */
	public static final float SLOWEST = 0.4F;
	/** A whole light set (helmet, armour and pack of one tier's light line): quieter and easier on the breath again. */
	public static final float LIGHT_SET_NOISE = -0.15F;
	public static final float LIGHT_SET_STAMINA = 0.10F;
	/** A whole heavy set (helmet, armour, rig and pack of one tier's heavy line): wounds open and bones break this much less often. */
	public static final float HEAVY_SET_WOUNDS = 0.75F;

	private static @Nullable Map<Item, Gear.Burden> burdens;
	private static @Nullable Map<Item, Gear.Line> lines;

	private GearBurden() {
	}

	private static void index() {
		if (burdens != null) {
			return;
		}
		Map<Item, Gear.Burden> b = new IdentityHashMap<>();
		Map<Item, Gear.Line> l = new IdentityHashMap<>();
		for (int i = 0; i < Gear.HELMETS.size(); i++) {
			b.put(MistinnItems.HELMETS.get(i), Gear.HELMETS.get(i).burden());
			l.put(MistinnItems.HELMETS.get(i), Gear.HELMETS.get(i).line());
		}
		for (int i = 0; i < Gear.ARMORS.size(); i++) {
			b.put(MistinnItems.ARMORS.get(i), Gear.ARMORS.get(i).burden());
			l.put(MistinnItems.ARMORS.get(i), Gear.ARMORS.get(i).line());
		}
		for (int i = 0; i < Gear.BACKPACKS.size(); i++) {
			b.put(MistinnItems.BACKPACKS.get(i), Gear.BACKPACKS.get(i).burden());
			l.put(MistinnItems.BACKPACKS.get(i), Gear.BACKPACKS.get(i).line());
		}
		for (int i = 0; i < Gear.RIGS.size(); i++) {
			b.put(MistinnItems.RIGS.get(i), Gear.RIGS.get(i).burden());
			l.put(MistinnItems.RIGS.get(i), Gear.RIGS.get(i).line());
		}
		lines = l;
		burdens = b;
	}

	/** A piece's own burden, or none for anything that is not gear. */
	public static Gear.Burden of(final ItemStack stack) {
		index();
		return stack.isEmpty() ? Gear.Burden.NONE : burdens.getOrDefault(stack.getItem(), Gear.Burden.NONE);
	}

	/** Which line made a piece, or null for anything that is not a helmet, body armour, rig or pack. */
	public static Gear.@Nullable Line line(final ItemStack stack) {
		index();
		return stack.isEmpty() ? null : lines.get(stack.getItem());
	}

	/** The helmet's visor: armour over the face on top of the mask's. */
	public static int face(final ItemStack helmet) {
		int index = MistinnItems.HELMETS.indexOf(helmet.getItem());
		return index < 0 ? 0 : Gear.HELMETS.get(index).face();
	}

	/** How far the hunter's noises carry, for what they wear: 1 for none of it. */
	public static float noise(final Player player) {
		float sum = set(player) == Gear.Line.LIGHT ? LIGHT_SET_NOISE : 0.0F;
		for (ItemStack worn : worn(player)) {
			sum += of(worn).noise();
		}
		return Math.max(QUIETEST, 1.0F + sum);
	}

	/** How fast stamina comes back, for what the hunter wears: 1 for none of it. */
	public static float stamina(final Player player) {
		float sum = set(player) == Gear.Line.LIGHT ? LIGHT_SET_STAMINA : 0.0F;
		for (ItemStack worn : worn(player)) {
			sum += of(worn).stamina();
		}
		return Mth.clamp(1.0F + sum, SLOWEST, 2.0F);
	}

	/** How often a blow opens a wound or breaks a bone, for what the hunter wears: 1, or less in a whole heavy set. */
	public static float wounds(final Player player) {
		return set(player) == Gear.Line.HEAVY ? HEAVY_SET_WOUNDS : 1.0F;
	}

	/**
	 * 整套: the line whose whole set of one tier the hunter wears (helmet, body armour and pack; for
	 * the heavy line its rig too), or null.
	 */
	public static Gear.@Nullable Line set(final Player player) {
		ItemStack[] worn = worn(player);
		int helmet = MistinnItems.HELMETS.indexOf(worn[0].getItem());
		int armor = MistinnItems.ARMORS.indexOf(worn[1].getItem());
		int rig = MistinnItems.RIGS.indexOf(worn[2].getItem());
		int pack = MistinnItems.BACKPACKS.indexOf(worn[3].getItem());
		if (helmet < 0 || armor < 0 || pack < 0) {
			return null;
		}
		Gear.Helmet h = Gear.HELMETS.get(helmet);
		Gear.BodyArmor a = Gear.ARMORS.get(armor);
		Gear.Backpack p = Gear.BACKPACKS.get(pack);
		if (h.line() == Gear.Line.STANDARD || h.line() != a.line() || h.line() != p.line() || h.tier() != a.tier() || h.tier() != p.tier()) {
			return null;
		}
		if (h.line() == Gear.Line.HEAVY && (rig < 0 || Gear.RIGS.get(rig).line() != Gear.Line.HEAVY || Gear.RIGS.get(rig).tier() != h.tier())) {
			return null;
		}
		return h.line();
	}

	private static ItemStack[] worn(final Player player) {
		GridInventory grid = player.getAttachedOrElse(life.shizune.mistinn.registry.MistinnAttachments.GRID, GridInventory.EMPTY);
		return new ItemStack[] {player.getItemBySlot(EquipmentSlot.HEAD), player.getItemBySlot(EquipmentSlot.CHEST), grid.rig(), grid.backpack()};
	}
}
