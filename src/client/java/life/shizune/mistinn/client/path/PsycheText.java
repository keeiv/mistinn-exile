package life.shizune.mistinn.client.path;

import java.util.List;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.hardship.RustAnchorWeapons;
import life.shizune.mistinn.path.PathService;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * How a thing in the hand feels to whoever holds it, under its tooltip, faint: what they are used to and what they are
 * not. A tiller on the way knows the settlement's bread and the long-handled spear from the fields, and nothing else;
 * a hunter at Rust Anchor's hardship trusts nothing from outside the station. Said as they would feel it, never why.
 */
public final class PsycheText {
	private static final int COLOUR = 0xFF8C9098;

	private PsycheText() {
	}

	public static void decorate(final ItemStack stack, final List<Component> lines) {
		Player player = Minecraft.getInstance().player;
		if (stack.isEmpty() || lines.isEmpty() || player == null) {
			return;
		}
		String key = null;
		PathService.View view = player.getAttached(PathService.VIEW);
		if (view != null && view.stage().ordinal() < PathService.Step.ENDED.ordinal()) {
			if (stack.is(Items.BREAD)) {
				key = "tooltip.mistinn.psyche.tiller.bread";
			} else if (stack.has(DataComponents.FOOD)) {
				key = "tooltip.mistinn.psyche.tiller.food";
			} else if (stack.is(MistinnItems.WEAPONS.get(WeaponStyle.THRUST).get(WeaponMaterial.STONE))) {
				key = "tooltip.mistinn.psyche.tiller.spear";
			} else if (WeaponStyle.of(stack) != null) {
				key = "tooltip.mistinn.psyche.tiller.weapon";
			}
		} else if (Hardship.of(player) == Hardship.RUST_ANCHOR) {
			if (stack.is(MistinnItems.DAMP_FOOD)) {
				key = "tooltip.mistinn.psyche.anchor.damp";
			} else {
				key = switch (RustAnchorWeapons.kind(stack)) {
					case ORDINARY -> "tooltip.mistinn.psyche.anchor.ordinary";
					case STATION -> "tooltip.mistinn.psyche.anchor.station";
					case NONE -> null;
				};
			}
		}
		if (key != null) {
			lines.add(Component.translatable(key).withColor(COLOUR).withStyle(ChatFormatting.ITALIC));
		}
	}
}
