package life.shizune.mistinn.client.tier;

import java.util.List;
import life.shizune.mistinn.gear.Gear;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridShape;
import life.shizune.mistinn.grid.ItemSizes;
import life.shizune.mistinn.tier.Tier;
import life.shizune.mistinn.tier.Tiers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/** "◆ name" in the tier's colour, and the tier, size and story lines of the tooltip. */
public final class TierText {
	private static final String DIAMOND = "◆ ";

	private TierText() {
	}

	public static MutableComponent name(final ItemStack stack) {
		Tier tier = Tiers.of(stack);
		return Component.literal(DIAMOND).withColor(tier.rgb()).append(stack.getHoverName().copy().withColor(tier.rgb()));
	}

	public static void decorate(final ItemStack stack, final List<Component> lines) {
		if (stack.isEmpty() || lines.isEmpty()) {
			return;
		}
		Tier tier = Tiers.of(stack);
		lines.set(0, name(stack));
		int at = 1;
		int[] size = ItemSizes.of(stack);
		lines.add(at++, Component.translatable("tooltip.mistinn.tier_size", tier.label, size[0], size[1]).withColor(0xFF9AA0A6));

		Gear.Backpack pack = GridInventory.backpackOf(stack);
		if (pack != null) {
			StringBuilder layout = new StringBuilder();
			for (GridShape.Section section : pack.shape().sections()) {
				if (!layout.isEmpty()) {
					layout.append("、");
				}
				layout.append(section.width()).append("×").append(section.height());
			}
			lines.add(at++, Component.translatable("tooltip.mistinn.capacity", pack.shape().cells(), layout.toString()).withColor(0xFFE9E6EA));
			if (pack.speedPenalty() > 0) {
				lines.add(at++, Component.translatable("tooltip.mistinn.speed", Math.round(pack.speedPenalty() * 100)).withColor(0xFFC54242));
			}
		}
		String base = "item." + BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace() + "." + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		for (int i = 1; i <= 3; i++) {
			String key = base + ".lore." + i;
			if (Language.getInstance().has(key)) {
				lines.add(at++, Component.translatable(key).withColor(0xFF8D8A94).withStyle(s -> s.withItalic(true)));
			}
		}
	}
}
