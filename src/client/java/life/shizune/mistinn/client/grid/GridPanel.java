package life.shizune.mistinn.client.grid;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridActionPayload;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridLogic;
import life.shizune.mistinn.grid.GridRules;
import life.shizune.mistinn.grid.GridShape;
import life.shizune.mistinn.grid.ItemSizes;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.client.tier.TierText;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.tier.Tier;
import life.shizune.mistinn.tier.Tiers;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * The hunter's grid storage drawn beside any container screen: pockets, backpack slot and safe
 * box on the left, the backpack's compartments on the right. Each item sits on its tier colour.
 * Click to pick up or put down (the game's own cursor carries the item), R turns what you carry,
 * Q throws the item under the mouse, shift-click sends it to the hotbar.
 */
public final class GridPanel {
	public static final int CELL = 16;
	private static final int GAP = 3;
	private static final int PANEL = 0xF0201C18;
	private static final int PANEL_LINE = 0xFF6B5B3A;
	private static final int EMPTY_CELL = 0xFF2E2A26;
	private static final int CELL_LINE = 0xFF45403A;
	private static final int LABEL = 0xFFE9E6EA;
	private static final int LABEL_DIM = 0xFF9A938A;

	/** Whether the carried item is turned on its side; reset when the cursor is empty. */
	private static boolean rotated;
	private static @Nullable Layout layout;
	private static @Nullable Hover hover;

	private GridPanel() {
	}

	/** A section of an area at a screen position. */
	private record Box(Area area, int section, int x, int y, int width, int height) {
		boolean contains(final double mx, final double my) {
			return mx >= this.x && my >= this.y && mx < this.x + this.width * CELL && my < this.y + this.height * CELL;
		}
	}

	private record Layout(List<Box> boxes, int packX, int packY, int left, int top, int right, int bottom, int left2, int top2, int right2, int bottom2) {
		boolean inside(final double mx, final double my) {
			return (mx >= this.left && mx < this.right && my >= this.top && my < this.bottom)
				|| (mx >= this.left2 && mx < this.right2 && my >= this.top2 && my < this.bottom2);
		}
	}

	private record Hover(Area area, int section, int cx, int cy, int index) {
	}

	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && GridRules.usesGrid(mc.player);
	}

	private static GridInventory inventory(final LocalPlayer player) {
		return player.getAttachedOrElse(MistinnAttachments.GRID, GridInventory.EMPTY);
	}

	// ── Layout ─────────────────────────────────────────────────────

	private static Layout layout(final GridInventory inv, final int leftPos, final int topPos, final int imageWidth, final int guiWidth) {
		List<Box> boxes = new ArrayList<>();
		int colWidth = 4 * CELL + 3 * GAP;
		int lx = Math.max(4, leftPos - colWidth - 10);
		int ly = topPos + 12;
		for (int i = 0; i < 4; i++) {
			boxes.add(new Box(Area.POCKETS, i, lx + i * (CELL + GAP), ly, 1, 1));
		}
		int packX = lx;
		int packY = ly + CELL + 22;
		int safeY = packY + 18 + 22;
		boxes.add(new Box(Area.SAFE, 0, lx, safeY, 2, 2));
		int leftBottom = safeY + 2 * CELL + 6;

		int rx = leftPos + imageWidth + 10;
		int ry = topPos + 12;
		int maxX = Math.max(rx + 4 * CELL, guiWidth - 6);
		int x = rx;
		int y = ry;
		int rowHeight = 0;
		int right = rx + 4 * CELL;
		GridShape shape = inv.shape(Area.BACKPACK);
		for (int s = 0; s < shape.sections().size(); s++) {
			GridShape.Section section = shape.sections().get(s);
			int w = section.width() * CELL;
			if (x > rx && x + w > maxX) {
				x = rx;
				y += rowHeight + GAP + 2;
				rowHeight = 0;
			}
			boxes.add(new Box(Area.BACKPACK, s, x, y, section.width(), section.height()));
			x += w + GAP + 2;
			rowHeight = Math.max(rowHeight, section.height() * CELL);
			right = Math.max(right, x);
		}
		int rightBottom = y + Math.max(rowHeight, CELL) + 6;
		return new Layout(List.copyOf(boxes), packX, packY, lx - 4, topPos, lx + colWidth + 4, leftBottom, rx - 4, topPos, right + 2, rightBottom);
	}

	// ── Drawing ────────────────────────────────────────────────────

	public static void render(final GuiGraphicsExtractor g, final AbstractContainerMenu menu, final int leftPos, final int topPos, final int imageWidth, final int mouseX, final int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !active()) {
			layout = null;
			return;
		}
		Font font = mc.font;
		GridInventory inv = inventory(player);
		Layout l = layout(inv, leftPos, topPos, imageWidth, g.guiWidth());
		layout = l;
		ItemStack carried = menu.getCarried();
		if (carried.isEmpty()) {
			rotated = false;
		}

		g.fill(l.left(), l.top(), l.right(), l.bottom(), PANEL);
		g.outline(l.left(), l.top(), l.right() - l.left(), l.bottom() - l.top(), PANEL_LINE);
		g.fill(l.left2(), l.top2(), l.right2(), l.bottom2(), PANEL);
		g.outline(l.left2(), l.top2(), l.right2() - l.left2(), l.bottom2() - l.top2(), PANEL_LINE);

		int lx = l.left() + 4;
		g.text(font, Component.translatable("gui.mistinn.pockets"), lx, l.top() + 3, LABEL, false);
		g.text(font, Component.translatable("gui.mistinn.backpack_slot"), lx, l.packY() - 10, LABEL, false);
		g.text(font, Component.translatable("gui.mistinn.safe"), lx, l.packY() + 18 + 12, LABEL, false);
		ItemStack pack = inv.backpack();
		Component packTitle = pack.isEmpty() ? Component.translatable("gui.mistinn.no_backpack") : TierText.name(pack);
		g.text(font, packTitle, l.left2() + 4, l.top2() + 3, pack.isEmpty() ? LABEL_DIM : Tiers.of(pack).color, false);

		// backpack slot
		drawCellBack(g, l.packX(), l.packY(), 1, 1, pack.isEmpty() ? null : Tiers.of(pack));
		if (!pack.isEmpty()) {
			g.item(pack, l.packX(), l.packY());
		}
		boolean overPack = mouseX >= l.packX() && mouseY >= l.packY() && mouseX < l.packX() + CELL && mouseY < l.packY() + CELL;

		hover = null;
		for (Box box : l.boxes()) {
			for (int cy = 0; cy < box.height(); cy++) {
				for (int cx = 0; cx < box.width(); cx++) {
					drawCellBack(g, box.x() + cx * CELL, box.y() + cy * CELL, 1, 1, null);
				}
			}
			if (box.contains(mouseX, mouseY)) {
				int cx = (mouseX - box.x()) / CELL;
				int cy = (mouseY - box.y()) / CELL;
				hover = new Hover(box.area(), box.section(), cx, cy, GridLogic.itemAt(inv.items(box.area()), box.section(), cx, cy));
			}
		}
		for (Area area : Area.values()) {
			List<Placed> items = inv.items(area);
			for (Placed placed : items) {
				Box box = boxFor(l, area, placed.section());
				if (box != null) {
					drawPlaced(g, font, placed, box.x() + placed.x() * CELL, box.y() + placed.y() * CELL);
				}
			}
		}

		if (hover != null && !carried.isEmpty()) {
			drawGhost(g, inv, carried, hover, l);
		} else if (hover != null && hover.index() >= 0) {
			Placed placed = inv.items(hover.area()).get(hover.index());
			Box box = boxFor(l, hover.area(), placed.section());
			if (box != null) {
				int px = box.x() + placed.x() * CELL;
				int py = box.y() + placed.y() * CELL;
				g.fill(px, py, px + placed.width() * CELL, py + placed.height() * CELL, 0x40FFFFFF);
			}
			g.setTooltipForNextFrame(font, placed.stack(), mouseX, mouseY);
		} else if (overPack && carried.isEmpty() && !pack.isEmpty()) {
			g.setTooltipForNextFrame(font, pack, mouseX, mouseY);
		}
		if (!carried.isEmpty()) {
			g.text(font, Component.translatable("gui.mistinn.rotate_hint"), l.left2() + 4, l.bottom2() + 3, LABEL_DIM, true);
		}
	}

	private static @Nullable Box boxFor(final Layout l, final Area area, final int section) {
		for (Box box : l.boxes()) {
			if (box.area() == area && box.section() == section) {
				return box;
			}
		}
		return null;
	}

	private static void drawCellBack(final GuiGraphicsExtractor g, final int x, final int y, final int w, final int h, final @Nullable Tier tier) {
		g.fill(x, y, x + w * CELL, y + h * CELL, tier == null ? EMPTY_CELL : tier.withAlpha(0x90));
		g.outline(x, y, w * CELL, h * CELL, tier == null ? CELL_LINE : tier.color);
	}

	/** The item on its tier colour, drawn as large as its footprint allows, turned if rotated. */
	private static void drawPlaced(final GuiGraphicsExtractor g, final Font font, final Placed placed, final int x, final int y) {
		int w = placed.width();
		int h = placed.height();
		Tier tier = Tiers.of(placed.stack());
		g.fill(x + 1, y + 1, x + w * CELL - 1, y + h * CELL - 1, tier.withAlpha(0x88));
		g.outline(x, y, w * CELL, h * CELL, tier.color);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + w * CELL / 2.0F, y + h * CELL / 2.0F);
		int[] upright = ItemSizes.of(placed.stack());
		if (upright[1] >= 2 * upright[0]) {
			// long things (swords, sticks) are drawn corner to corner in vanilla: stand them up
			// along the footprint's long side, as large as it allows
			float scale = Math.min(upright[1] * 0.68F, upright[0] * 1.6F);
			pose.rotate((float) (placed.rotated() ? Math.PI / 4 : -Math.PI / 4));
			pose.scale(scale, scale);
		} else {
			float scale = Math.max(1, Math.min(w, h));
			if (placed.rotated()) {
				pose.rotate((float) (Math.PI / 2));
			}
			pose.scale(scale, scale);
		}
		g.item(placed.stack(), -8, -8);
		pose.popMatrix();
		if (placed.stack().getCount() > 1) {
			String count = Integer.toString(placed.stack().getCount());
			g.text(font, count, x + w * CELL - font.width(count) - 1, y + h * CELL - 9, 0xFFFFFFFF, true);
		}
	}

	/** Where the carried item would land: green if it fits (or stacks), red if not. */
	private static void drawGhost(final GuiGraphicsExtractor g, final GridInventory inv, final ItemStack carried, final Hover h, final Layout l) {
		Box box = boxFor(l, h.area(), h.section());
		if (box == null) {
			return;
		}
		int[] size = ItemSizes.of(carried);
		int w = rotated ? size[1] : size[0];
		int hh = rotated ? size[0] : size[1];
		boolean ok;
		if (h.index() >= 0) {
			ItemStack there = inv.items(h.area()).get(h.index()).stack();
			ok = ItemStack.isSameItemSameComponents(there, carried) && there.getCount() < there.getMaxStackSize();
		} else {
			ok = GridLogic.canStore(carried) && GridLogic.fits(inv.shape(h.area()), inv.items(h.area()), h.section(), h.cx(), h.cy(), w, hh, -1);
		}
		int x = box.x() + h.cx() * CELL;
		int y = box.y() + h.cy() * CELL;
		g.fill(x, y, x + w * CELL, y + hh * CELL, ok ? 0x6040C060 : 0x60D04040);
	}

	// ── Input ──────────────────────────────────────────────────────

	/** Returns true when the click belonged to the panel. */
	public static boolean click(final AbstractContainerMenu menu, final double mx, final double my, final int button, final boolean shift) {
		Layout l = layout;
		Minecraft mc = Minecraft.getInstance();
		if (l == null || mc.player == null || !active()) {
			return false;
		}
		if (mx >= l.packX() && my >= l.packY() && mx < l.packX() + CELL && my < l.packY() + CELL) {
			send(GridActionPayload.BACKPACK, Area.POCKETS, 0, 0, 0);
			return true;
		}
		if (!l.inside(mx, my)) {
			return false;
		}
		Hover h = hover;
		if (h == null || button != 0) {
			return true;
		}
		ItemStack carried = menu.getCarried();
		if (carried.isEmpty() && h.index() >= 0) {
			send(shift ? GridActionPayload.HOTBAR : GridActionPayload.PICK, h.area(), h.index(), 0, 0);
		} else if (!carried.isEmpty()) {
			send(GridActionPayload.PLACE, h.area(), h.section(), h.cx(), h.cy());
		}
		return true;
	}

	/** R turns the carried item; Q throws the hovered one. Returns true when handled. */
	public static boolean key(final AbstractContainerMenu menu, final int key) {
		if (layout == null || !active()) {
			return false;
		}
		if (key == InputConstants.KEY_R && !menu.getCarried().isEmpty()) {
			rotated = !rotated;
			return true;
		}
		Hover h = hover;
		if (key == InputConstants.KEY_Q && h != null && h.index() >= 0 && menu.getCarried().isEmpty()) {
			send(GridActionPayload.DROP, h.area(), h.index(), 0, 0);
			return true;
		}
		return false;
	}

	public static void grab(final int slotIndex) {
		send(GridActionPayload.GRAB, Area.POCKETS, slotIndex, 0, 0);
	}

	private static void send(final int action, final Area area, final int a, final int b, final int c) {
		ClientPlayNetworking.send(new GridActionPayload(action, area.ordinal(), a, b, c, rotated));
	}
}
