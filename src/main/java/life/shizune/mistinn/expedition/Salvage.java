package life.shizune.mistinn.expedition;

import java.util.List;
import life.shizune.mistinn.grid.Carried;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.tier.Tier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 遠征物資: the forebears' salvage, found by searching the grounds' containers and sold to the tillers for salt. Eighteen
 * pieces from rusted scrap (N) to the finest thing in the dam (VI), in the footprints a grid knows (1×1 to 4×2). What is
 * found out there is at stake until it is home: none of it goes in the safe box.
 */
public final class Salvage {
	/** One piece: its id, tier, footprint upright, what the tillers pay for one, and how many stack. */
	public record Kind(String id, Tier tier, int w, int h, int value, int stack) {
	}

	/** Every piece, lowest first; {@link MistinnItems#SALVAGE} holds their items in the same order. */
	public static final List<Kind> KINDS = List.of(
		new Kind("rusted_bolt", Tier.N, 1, 1, 2, 8),
		new Kind("broken_wiring", Tier.N, 1, 2, 3, 1),
		new Kind("old_valve", Tier.N, 2, 2, 5, 1),
		new Kind("copper_gear", Tier.I, 1, 1, 6, 8),
		new Kind("old_filter", Tier.I, 1, 2, 8, 1),
		new Kind("pressure_gauge", Tier.I, 2, 2, 12, 1),
		new Kind("lattice_fragment", Tier.II, 1, 1, 15, 4),
		new Kind("coolant_tube", Tier.II, 1, 2, 18, 1),
		new Kind("control_panel", Tier.II, 2, 3, 30, 1),
		new Kind("resonance_coil", Tier.III, 1, 2, 40, 1),
		new Kind("lattice_cell", Tier.III, 2, 2, 55, 1),
		new Kind("gate_gearbox", Tier.III, 3, 3, 80, 1),
		new Kind("record_chip", Tier.IV, 1, 1, 90, 1),
		new Kind("pressure_regulator", Tier.IV, 2, 2, 120, 1),
		new Kind("sky_gauge_part", Tier.IV, 4, 2, 160, 1),
		new Kind("core_stabilizer", Tier.V, 1, 2, 220, 1),
		new Kind("star_chart_plate", Tier.V, 2, 3, 320, 1),
		new Kind("shida_sky_lens", Tier.VI, 2, 2, 700, 1));

	private Salvage() {
	}

	/** What piece this item is, if it is one. */
	public static @Nullable Kind of(final Item item) {
		int index = MistinnItems.SALVAGE.indexOf(item);
		return index < 0 ? null : KINDS.get(index);
	}

	/** Whether this is a piece of salvage. */
	public static boolean is(final ItemStack stack) {
		return !stack.isEmpty() && of(stack.getItem()) != null;
	}

	/** What the tillers pay for this stack. */
	public static int value(final ItemStack stack) {
		Kind kind = stack.isEmpty() ? null : of(stack.getItem());
		return kind == null ? 0 : kind.value() * stack.getCount();
	}

	/** The hunter sells the tillers every piece they carry (hotbar, pockets, pack and rig); what it came to in salt. */
	public static int sell(final ServerPlayer hunter) {
		int total = 0;
		for (int i = 0; i < KINDS.size(); i++) {
			int taken = Carried.take(hunter, MistinnItems.SALVAGE.get(i), Integer.MAX_VALUE);
			total += taken * KINDS.get(i).value();
		}
		if (total > 0) {
			SaltService.add(hunter, total);
		}
		return total;
	}
}
