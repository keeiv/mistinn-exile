package life.shizune.mistinn.client.grid;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.grid.QuickUse;
import life.shizune.mistinn.team.DownService;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 快捷輪盤: hold the key and the usable things in the pockets and the rig fan out round the
 * crosshair; the mouse picks one (the view holds still meanwhile, the feet keep going) and letting go
 * uses it where it lies.
 */
public final class QuickWheel implements HudElement {
	/** How far the mouse's pick reaches from the centre, in its own units, and how far it must go to pick at all. */
	private static final double REACH = 60.0;
	private static final double DEAD = 14.0;
	private static final int RADIUS = 46;

	private record Entry(QuickUse.Spot spot, ItemStack stack) {
	}

	private static KeyMapping key;
	private static boolean open;
	private static List<Entry> entries = List.of();
	private static double pickX;
	private static double pickY;

	public static void register(final KeyMapping.Category category) {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mistinn.quick_use", InputConstants.KEY_R, category));
	}

	public static boolean open() {
		return open;
	}

	/** The mouse moved while the wheel is open: it moves the pick, not the view. */
	public static void mouse(final double dx, final double dy) {
		pickX += dx * 0.5;
		pickY += dy * 0.5;
		double length = Math.hypot(pickX, pickY);
		if (length > REACH) {
			pickX *= REACH / length;
			pickY *= REACH / length;
		}
	}

	/** The entry picked now, or -1 when the mouse is still near the centre. */
	public static int picked() {
		if (entries.isEmpty() || Math.hypot(pickX, pickY) < DEAD) {
			return -1;
		}
		double angle = Math.atan2(pickX, -pickY);
		double step = Math.PI * 2.0 / entries.size();
		return Math.floorMod((int) Math.round(angle / step), entries.size());
	}

	public static void tick(final Minecraft mc) {
		if (key == null || mc.player == null) {
			open = false;
			return;
		}
		boolean held = key.isDown() && mc.gui.screen() == null && mc.player.isAlive() && !DownService.isDown(mc.player);
		if (held && !open) {
			List<Entry> found = new ArrayList<>();
			for (QuickUse.Spot spot : QuickUse.spots(mc.player)) {
				found.add(new Entry(spot, QuickUse.at(mc.player, spot).copy()));
			}
			if (found.isEmpty()) {
				if (key.consumeClick()) {
					mc.player.sendOverlayMessage(Component.translatable("message.mistinn.quick.empty"));
				}
				return;
			}
			entries = List.copyOf(found);
			pickX = 0.0;
			pickY = 0.0;
			open = true;
		} else if (!held && open) {
			int pick = picked();
			open = false;
			if (pick >= 0) {
				ClientPlayNetworking.send(new QuickUse.QuickUsePayload(entries.get(pick).spot()));
			}
			entries = List.of();
		}
		while (key.consumeClick()) {
			// the press itself does nothing more
		}
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		if (!open || entries.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2;
		g.fill(cx - RADIUS - 18, cy - RADIUS - 18, cx + RADIUS + 18, cy + RADIUS + 18, 0x66080A0E);
		int pick = picked();
		double step = Math.PI * 2.0 / entries.size();
		for (int i = 0; i < entries.size(); i++) {
			double angle = i * step;
			int x = cx + (int) Math.round(Math.sin(angle) * RADIUS);
			int y = cy - (int) Math.round(Math.cos(angle) * RADIUS);
			boolean chosen = i == pick;
			g.fill(x - 11, y - 11, x + 11, y + 11, chosen ? 0xE0E9C46A : 0xB0101014);
			g.fill(x - 10, y - 10, x + 10, y + 10, chosen ? 0xF0262018 : 0xC01A1C20);
			ItemStack stack = entries.get(i).stack();
			g.item(stack, x - 8, y - 8);
			g.itemDecorations(font, stack, x - 8, y - 8);
		}
		// where the mouse points (out past the name), and what letting go would use
		int px = cx + (int) Math.round(pickX / REACH * (RADIUS - 14));
		int py = cy + (int) Math.round(pickY / REACH * (RADIUS - 14));
		if (Math.hypot(pickX, pickY) >= DEAD) {
			g.fill(px - 1, py - 1, px + 2, py + 2, 0xFFE9E6EA);
		}
		Component name = pick >= 0 ? entries.get(pick).stack().getHoverName() : Component.translatable("hud.mistinn.quick.pick");
		// in the middle of the ring, clear of the hotbar and the bars under it
		g.text(font, name, cx - font.width(name) / 2, cy - 4, pick >= 0 ? 0xFFE9C46A : 0xFFB8B2A8, true);
		Component hint = Component.translatable("hud.mistinn.quick.hint", key.getTranslatedKeyMessage());
		g.text(font, hint, cx - font.width(hint) / 2, cy - RADIUS - 30, 0xFF8D8A94, true);
	}
}
