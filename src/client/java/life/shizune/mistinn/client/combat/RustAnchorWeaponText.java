package life.shizune.mistinn.client.combat;

import java.util.List;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.hardship.RustAnchorWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** In a Rust Anchor world a weapon's tooltip says how the station's rule treats it. */
public final class RustAnchorWeaponText {
	private static final int WORSE = 0xFFD0564B;
	private static final int BETTER = 0xFFC9B27A;

	private RustAnchorWeaponText() {
	}

	public static void decorate(final ItemStack stack, final List<Component> lines) {
		Player player = Minecraft.getInstance().player;
		if (stack.isEmpty() || lines.isEmpty() || player == null || Hardship.of(player) != Hardship.RUST_ANCHOR) {
			return;
		}
		switch (RustAnchorWeapons.kind(stack)) {
			case ORDINARY -> lines.add(Component.translatable("tooltip.mistinn.rust_anchor.ordinary_weapon").withColor(WORSE));
			case STATION -> lines.add(Component.translatable("tooltip.mistinn.rust_anchor.station_weapon").withColor(BETTER));
			case NONE -> {
			}
		}
	}
}
