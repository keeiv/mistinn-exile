package life.shizune.mistinn.client.tier;

import java.util.List;
import life.shizune.mistinn.gear.Gear;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridShape;
import life.shizune.mistinn.grid.ItemSizes;
import life.shizune.mistinn.registry.MistinnComponents;
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

	/** Damp food's name stays red, whatever its tier: it is not food any more. */
	private static final int DAMP = 0xFFE0403A;
	/** A piece's costs in red, what it gives back in green. */
	private static final int BAD = 0xFFC54242;
	private static final int GOOD = 0xFF7FBF7F;

	/** "1×2、3×4": a grid's compartments. */
	private static String layout(final GridShape shape) {
		StringBuilder layout = new StringBuilder();
		for (GridShape.Section section : shape.sections()) {
			if (!layout.isEmpty()) {
				layout.append("、");
			}
			layout.append(section.width()).append("×").append(section.height());
		}
		return layout.toString();
	}

	/** A fraction as a signed whole percent: "+15", "-10". */
	private static String signed(final float fraction) {
		int percent = Math.round(fraction * 100.0F);
		return percent > 0 ? "+" + percent : Integer.toString(percent);
	}

	public static MutableComponent name(final ItemStack stack) {
		Tier tier = Tiers.of(stack);
		int rgb = stack.is(life.shizune.mistinn.registry.MistinnItems.DAMP_FOOD) ? DAMP : tier.rgb();
		return Component.literal(DIAMOND).withColor(rgb).append(stack.getHoverName().copy().withColor(rgb));
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

		Integer wear = stack.get(MistinnComponents.REPAIR_WEAR);
		if (wear != null && wear > 0 && stack.isDamageableItem()) {
			// repairs above the kit's tier (and every tier VI repair) wear the maximum down for good
			lines.add(at++, Component.translatable("tooltip.mistinn.durability_worn", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage(), wear).withColor(0xFFC58A42));
		}

		// light or heavy: which line made it, and what it costs to wear
		Gear.Line line = life.shizune.mistinn.gear.GearBurden.line(stack);
		if (line != null && line != Gear.Line.STANDARD) {
			lines.add(at++, Component.translatable("tooltip.mistinn.line." + line.id).withColor(line == Gear.Line.LIGHT ? GOOD : BAD));
		}
		Gear.Backpack pack = GridInventory.backpackOf(stack);
		if (pack != null) {
			lines.add(at++, Component.translatable("tooltip.mistinn.capacity", pack.shape().cells(), layout(pack.shape())).withColor(0xFFE9E6EA));
			if (pack.speedPenalty() > 0) {
				lines.add(at++, Component.translatable("tooltip.mistinn.speed", Math.round(pack.speedPenalty() * 100)).withColor(0xFFC54242));
			}
		}
		Gear.ChestRig rig = GridInventory.rigOf(stack);
		if (rig != null) {
			lines.add(at++, Component.translatable("tooltip.mistinn.rig_capacity", rig.shape().cells(), layout(rig.shape())).withColor(0xFFE9E6EA));
			if (rig.speedPenalty() > 0) {
				lines.add(at++, Component.translatable("tooltip.mistinn.speed", Math.max(1, Math.round(rig.speedPenalty() * 100))).withColor(0xFFC54242));
			}
		}
		int visor = life.shizune.mistinn.gear.GearBurden.face(stack);
		if (visor > 0) {
			lines.add(at++, Component.translatable("tooltip.mistinn.face_armor", visor).withColor(GOOD));
		}
		Gear.Burden burden = life.shizune.mistinn.gear.GearBurden.of(stack);
		if (burden.noise() != 0.0F) {
			lines.add(at++, Component.translatable("tooltip.mistinn.noise", signed(burden.noise())).withColor(burden.noise() > 0.0F ? BAD : GOOD));
		}
		if (burden.stamina() != 0.0F) {
			lines.add(at++, Component.translatable("tooltip.mistinn.stamina_regen", signed(burden.stamina())).withColor(burden.stamina() < 0.0F ? BAD : GOOD));
		}
		if (burden.swing() > 0.0F) {
			lines.add(at++, Component.translatable("tooltip.mistinn.swing", signed(-burden.swing())).withColor(BAD));
		}
		String base = "item." + BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace() + "." + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		String usage = base + ".usage";
		if (Language.getInstance().has(usage)) {
			lines.add(at++, Component.translatable(usage).withColor(0xFFE9E6EA));
		}
		for (int i = 1; i <= 3; i++) {
			String key = base + ".lore." + i;
			if (Language.getInstance().has(key)) {
				lines.add(at++, Component.translatable(key).withColor(0xFF8D8A94).withStyle(s -> s.withItalic(true)));
			}
		}
	}
}
