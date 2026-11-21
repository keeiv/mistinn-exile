package life.shizune.mistinn.client.combat;

import java.util.List;
import life.shizune.mistinn.weapon.SpecialWeapon;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Weapon tooltips: how to charge a heavy blow, and for a special weapon what it asks of its bearer,
 * each requirement green when this hunter meets it and red when not.
 */
public final class WeaponText {
	private static final int MET = 0xFF7FC97A;
	private static final int UNMET = 0xFFD0564B;
	private static final int PLAIN = 0xFFB9B4A8;

	private WeaponText() {
	}

	public static void decorate(final ItemStack stack, final List<Component> lines) {
		if (stack.isEmpty() || lines.isEmpty() || WeaponStyle.of(stack) == null) {
			return;
		}
		lines.add(Component.translatable("tooltip.mistinn.heavy").withColor(PLAIN));
		SpecialWeapon special = SpecialWeapon.of(stack);
		if (special == null) {
			return;
		}
		lines.add(Component.translatable("tooltip.mistinn.special." + special.id).withColor(0xFFE0C27A));
		lines.add(Component.translatable("tooltip.mistinn.requires").withColor(PLAIN));
		Player player = Minecraft.getInstance().player;
		for (SpecialWeapon.Requirement requirement : special.requirements) {
			boolean met = player != null && requirement.met(player);
			lines.add(Component.literal(met ? "  ✔ " : "  ✘ ").append(requirement.describe()).withColor(met ? MET : UNMET));
		}
		if (player != null && !special.fits(player)) {
			lines.add(Component.translatable("tooltip.mistinn.unfit").withColor(UNMET));
		}
	}
}
