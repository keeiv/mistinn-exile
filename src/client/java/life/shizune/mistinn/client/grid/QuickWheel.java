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
 * uses it where it lies. The middle of the ring is a place of its own: bring the pick back there and
 * letting go puts the wheel away with nothing used. Anything that takes the hunter away from the
 * wheel (a screen opening, going down, dying) puts it away the same way.
 */
public final class QuickWheel implements HudElement {
	/** How far the mouse's pick reaches from the centre, in its own units, and how far it must go to leave the middle. */
	private static final double REACH = 60.0;
	private static final double DEAD = 20.0;
	private static final int RADIUS = 46;
	/** Half the size of the middle's box, in GUI pixels. */
	private static final int MIDDLE = 11;

	private record Entry(QuickUse.Spot spot, ItemStack stack) {
	}

	private static KeyMapping key;
	private static boolean open;
	private static List<Entry> entries = List.of();
	private static double pickX;
	private static double pickY;
	/** Put away while the key was still held: it opens again only after the key comes up. */
	private static boolean waitingForRelease;

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

	/** Whether the pick is in the middle: letting go now uses nothing. */
	public static boolean inMiddle() {
		return Math.hypot(pickX, pickY) < DEAD;
	}

	/** The entry picked now, or -1 when the pick is in the middle. */
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
			close();
			return;
		}
		boolean free = mc.gui.screen() == null && mc.player.isAlive() && !DownService.isDown(mc.player);
		if (open && !free) {
			// a screen came up, or the hunter went down: the wheel goes away and nothing is used
			close();
		}
		boolean held = key.isDown() && free;
		if (held && !open && !waitingForRelease) {
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
			List<Entry> shown = entries;
			close();
			if (pick >= 0) {
				ClientPlayNetworking.send(new QuickUse.QuickUsePayload(shown.get(pick).spot()));
			}
		}
		if (!key.isDown()) {
			waitingForRelease = false;
		}
		while (key.consumeClick()) {
			// the press itself does nothing more
		}
	}

	/** Puts the wheel away without using anything; the key must come up before it opens again. */
	private static void close() {
		if (open && key != null && key.isDown()) {
			waitingForRelease = true;
		}
		open = false;
		entries = List.of();
		pickX = 0.0;
		pickY = 0.0;
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
		// the middle: bring the pick back here and letting go puts the wheel away
		boolean middle = pick < 0;
		g.fill(cx - MIDDLE - 1, cy - MIDDLE - 1, cx + MIDDLE + 1, cy + MIDDLE + 1, middle ? 0xE0E9C46A : 0xB0101014);
		g.fill(cx - MIDDLE, cy - MIDDLE, cx + MIDDLE, cy + MIDDLE, middle ? 0xF0262018 : 0xC01A1C20);
		int cross = middle ? 0xFFE9C46A : 0xFF6E6A74;
		for (int i = -4; i <= 4; i++) {
			g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, cross);
			g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, cross);
		}
		// where the mouse points
		int px = cx + (int) Math.round(pickX / REACH * (RADIUS - 6));
		int py = cy + (int) Math.round(pickY / REACH * (RADIUS - 6));
		g.fill(px - 1, py - 1, px + 2, py + 2, 0xFFE9E6EA);
		// what letting go would do, above the ring: clear of the hotbar and the bars under it
		Component name = middle ? Component.translatable("hud.mistinn.quick.away") : entries.get(pick).stack().getHoverName();
		g.text(font, name, cx - font.width(name) / 2, cy - RADIUS - 28, middle ? 0xFFB8B2A8 : 0xFFE9C46A, true);
		Component hint = Component.translatable(middle ? "hud.mistinn.quick.hint_away" : "hud.mistinn.quick.hint", key.getTranslatedKeyMessage());
		g.text(font, hint, cx - font.width(hint) / 2, cy - RADIUS - 40, 0xFF8D8A94, true);
	}
}
