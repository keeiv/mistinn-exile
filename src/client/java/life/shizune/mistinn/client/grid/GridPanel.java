package life.shizune.mistinn.client.grid;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.client.tier.TierText;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridActionPayload;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridLogic;
import life.shizune.mistinn.grid.GridRules;
import life.shizune.mistinn.grid.GridShape;
import life.shizune.mistinn.grid.ItemSizes;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.tier.Tier;
import life.shizune.mistinn.tier.Tiers;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * The hunter's grid storage: pockets, chest rig, backpack and safe box, plus the sockets the rig,
 * backpack and mask are worn in. Drawn beside any container screen, or wherever the hunter
 * inventory screen places it. Each item sits on its tier colour.
 * Click to pick up or put down (the game's own cursor carries the item), or drag: press on an
 * item and release over another cell, a menu slot, or from a menu slot onto the grid. R turns
 * what you carry, Q throws the item under the mouse, shift-click sends it to the hotbar.
 */
public final class GridPanel {
	public static final int CELL = 16;
	public static final int GAP = 3;
	public static final int PANEL = 0xF0201C18;
	public static final int PANEL_LINE = 0xFF6B5B3A;
	public static final int EMPTY_CELL = 0xFF2E2A26;
	public static final int CELL_LINE = 0xFF45403A;
	public static final int LABEL = 0xFFE9E6EA;
	public static final int LABEL_DIM = 0xFF9A938A;
	/** The left mouse button as screens see it (26.3 numbers buttons from 1, not as GLFW does). */
	public static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;

	/** Whether the carried item is turned on its side; reset when the cursor is empty. */
	private static boolean rotated;
	private static @Nullable Layout layout;
	private static @Nullable Hover hover;
	/** Where the open container screen sits, for {@link #slotCentre}. */
	private static int screenLeft;
	private static int screenTop;
	/** The cell a left press on the panel started on, until the button is released. */
	private static @Nullable Hover pressedAt;
	private static boolean pressedOnPanel;
	/** Where the last left press happened anywhere on the screen, to tell drags from clicks. */
	private static double pressX = Double.NaN;
	private static double pressY = Double.NaN;

	/** What the screen should do with a mouse release. */
	public enum Release {
		/** Not ours: let the screen handle it. */
		PASS,
		/** Handled by the panel: the screen must not act on it (it would throw the carried item). */
		HANDLED,
		/** Released over a menu slot while carrying from the grid: click that slot. */
		TO_SLOT
	}

	/** The worn containers and the mask, each in a one-cell socket. */
	public enum Socket {
		BACKPACK(GridActionPayload.BACKPACK),
		RIG(GridActionPayload.RIG_SLOT),
		MASK(GridActionPayload.MASK_SLOT);

		final int action;

		Socket(final int action) {
			this.action = action;
		}

		ItemStack worn(final GridInventory inv) {
			return switch (this) {
				case BACKPACK -> inv.backpack();
				case RIG -> inv.rig();
				case MASK -> inv.mask();
			};
		}
	}

	private GridPanel() {
	}

	/** A section of an area at a screen position. */
	public record Box(Area area, int section, int x, int y, int width, int height) {
		boolean contains(final double mx, final double my) {
			return mx >= this.x && my >= this.y && mx < this.x + this.width * CELL && my < this.y + this.height * CELL;
		}
	}

	/** A socket at a screen position; {@code size} is its edge in pixels. */
	public record SocketBox(Socket socket, int x, int y, int size) {
		boolean contains(final double mx, final double my) {
			return mx >= this.x && my >= this.y && mx < this.x + this.size && my < this.y + this.size;
		}
	}

	/**
	 * Where everything is; {@code panels} are the rectangles {x0, y0, x1, y1} the panel covers.
	 * With {@code claimsPanels} every click in them is the panel's (beside a container, where the
	 * screen has nothing there); without it only clicks on cells and sockets are, and the screen's
	 * own slots in the same area (the hunter screen's armour and crafting) keep theirs.
	 */
	public record Layout(List<Box> boxes, List<SocketBox> sockets, List<int[]> panels, boolean claimsPanels) {
		boolean inside(final double mx, final double my) {
			for (int[] p : this.panels) {
				if (mx >= p[0] && mx < p[2] && my >= p[1] && my < p[3]) {
					return true;
				}
			}
			return false;
		}

		@Nullable SocketBox socketAt(final double mx, final double my) {
			for (SocketBox socket : this.sockets) {
				if (socket.contains(mx, my)) {
					return socket;
				}
			}
			return null;
		}
	}

	private record Hover(Area area, int section, int cx, int cy, int index) {
	}

	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && GridRules.usesGrid(mc.player);
	}

	public static GridInventory inventory(final LocalPlayer player) {
		return player.getAttachedOrElse(MistinnAttachments.GRID, GridInventory.EMPTY);
	}

	// ── Layout ─────────────────────────────────────────────────────

	/** How a container's compartments are arranged. */
	public enum Arrange {
		/** Small compartments (two cells tall at most) in a row above the larger ones. */
		SMALL_ABOVE,
		/** Larger compartments first, the small ones stacked in a column at their right. */
		SMALL_BESIDE,
		/** Everything in one wrapping row. */
		FLOW
	}

	/** Lays the sections of an area out from (x, y). Returns {right, bottom} of what was placed. */
	public static int[] placeSections(final List<Box> boxes, final Area area, final GridShape shape, final int x, final int y, final int maxWidth, final Arrange arrange) {
		List<Integer> small = new ArrayList<>();
		List<Integer> large = new ArrayList<>();
		for (int s = 0; s < shape.sections().size(); s++) {
			(shape.sections().get(s).height() <= 2 ? small : large).add(s);
		}
		if (arrange == Arrange.FLOW || small.isEmpty() || large.isEmpty()) {
			List<Integer> all = new ArrayList<>();
			for (int s = 0; s < shape.sections().size(); s++) {
				all.add(s);
			}
			return row(boxes, area, shape, all, x, y, maxWidth);
		}
		if (arrange == Arrange.SMALL_ABOVE) {
			int[] top = row(boxes, area, shape, small, x, y, maxWidth);
			int[] below = row(boxes, area, shape, large, x, top[1] + GAP, maxWidth);
			return new int[] {Math.max(top[0], below[0]), below[1]};
		}
		int[] main = row(boxes, area, shape, large, x, y, maxWidth);
		int cx = main[0] + GAP;
		int cy = y;
		int right = cx;
		for (int s : small) {
			GridShape.Section section = shape.sections().get(s);
			boxes.add(new Box(area, s, cx, cy, section.width(), section.height()));
			cy += section.height() * CELL + GAP;
			right = Math.max(right, cx + section.width() * CELL);
		}
		return new int[] {right, Math.max(main[1], cy - GAP)};
	}

	/** Places the given sections left to right, wrapping at {@code maxWidth}. */
	private static int[] row(final List<Box> boxes, final Area area, final GridShape shape, final List<Integer> sections, final int x, final int y, final int maxWidth) {
		int cx = x;
		int cy = y;
		int rowHeight = 0;
		int right = x;
		for (int s : sections) {
			GridShape.Section section = shape.sections().get(s);
			int w = section.width() * CELL;
			if (cx > x && cx + w > x + maxWidth) {
				cx = x;
				cy += rowHeight + GAP;
				rowHeight = 0;
			}
			boxes.add(new Box(area, s, cx, cy, section.width(), section.height()));
			right = Math.max(right, cx + w);
			cx += w + GAP;
			rowHeight = Math.max(rowHeight, section.height() * CELL);
		}
		return new int[] {right, cy + rowHeight};
	}

	/** The layout beside a container screen: pockets, sockets and safe box left, rig and backpack right. */
	private static Layout beside(final GridInventory inv, final int leftPos, final int topPos, final int imageWidth, final int guiWidth) {
		List<Box> boxes = new ArrayList<>();
		List<SocketBox> sockets = new ArrayList<>();
		int colWidth = 4 * CELL + 3 * GAP;
		int lx = Math.max(4, leftPos - colWidth - 10);
		int ly = topPos + 12;
		for (int i = 0; i < 4; i++) {
			boxes.add(new Box(Area.POCKETS, i, lx + i * (CELL + GAP), ly, 1, 1));
		}
		int socketY = ly + CELL + 22;
		sockets.add(new SocketBox(Socket.BACKPACK, lx, socketY, CELL));
		sockets.add(new SocketBox(Socket.RIG, lx + CELL + 2 * GAP + 20, socketY, CELL));
		int safeY = socketY + 18 + 22;
		boxes.add(new Box(Area.SAFE, 0, lx, safeY, 2, 2));
		int leftBottom = safeY + 2 * CELL + 6;

		int rx = leftPos + imageWidth + 10;
		int maxWidth = Math.max(4 * CELL, guiWidth - 6 - rx);
		int y = topPos + 12;
		if (!inv.shape(Area.RIG).sections().isEmpty()) {
			y = placeSections(boxes, Area.RIG, inv.shape(Area.RIG), rx, y, maxWidth, Arrange.SMALL_ABOVE)[1] + 12;
		}
		int rigBottom = y;
		int bottom = placeSections(boxes, Area.BACKPACK, inv.shape(Area.BACKPACK), rx, y, maxWidth, Arrange.SMALL_ABOVE)[1];
		int right = rx + 4 * CELL;
		for (Box box : boxes) {
			if (box.area() == Area.RIG || box.area() == Area.BACKPACK) {
				right = Math.max(right, box.x() + box.width() * CELL);
			}
		}
		layoutRigTop = rigBottom;
		return new Layout(List.copyOf(boxes), List.copyOf(sockets), List.of(
			new int[] {lx - 4, topPos, lx + colWidth + 4, leftBottom},
			new int[] {rx - 4, topPos, right + 4, Math.max(bottom, topPos + 30) + 6}
		), true);
	}

	/** Where the backpack title goes in the beside layout (below the rig, if one is worn). */
	private static int layoutRigTop;

	// ── Drawing ────────────────────────────────────────────────────

	/** Draws the panel beside a container screen, with its own background and labels. */
	public static void render(final GuiGraphicsExtractor g, final AbstractContainerMenu menu, final int leftPos, final int topPos, final int imageWidth, final int mouseX, final int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !active()) {
			layout = null;
			return;
		}
		Font font = mc.font;
		GridInventory inv = inventory(player);
		Layout l = beside(inv, leftPos, topPos, imageWidth, g.guiWidth());
		for (int[] p : l.panels()) {
			g.fill(p[0], p[1], p[2], p[3], PANEL);
			g.outline(p[0], p[1], p[2] - p[0], p[3] - p[1], PANEL_LINE);
		}
		int[] left = l.panels().get(0);
		int[] right = l.panels().get(1);
		g.text(font, Component.translatable("gui.mistinn.pockets"), left[0] + 4, left[1] + 3, LABEL, false);
		SocketBox pack = l.sockets().get(0);
		SocketBox rig = l.sockets().get(1);
		g.text(font, Component.translatable("gui.mistinn.backpack_slot"), pack.x(), pack.y() - 10, LABEL, false);
		g.text(font, Component.translatable("gui.mistinn.rig_slot"), rig.x(), rig.y() - 10, LABEL, false);
		g.text(font, Component.translatable("gui.mistinn.safe"), left[0] + 4, pack.y() + 18 + 12, LABEL, false);
		int titleY = right[1] + 3;
		if (!inv.rig().isEmpty()) {
			g.text(font, TierText.name(inv.rig()), right[0] + 4, titleY, Tiers.of(inv.rig()).color, false);
			titleY = layoutRigTop - 10;
		}
		ItemStack worn = inv.backpack();
		Component packTitle = worn.isEmpty() ? Component.translatable("gui.mistinn.no_backpack") : TierText.name(worn);
		g.text(font, packTitle, right[0] + 4, titleY, worn.isEmpty() ? LABEL_DIM : Tiers.of(worn).color, false);
		draw(g, menu, l, leftPos, topPos, mouseX, mouseY);
		if (!menu.getCarried().isEmpty()) {
			g.text(font, Component.translatable("gui.mistinn.rotate_hint"), right[0] + 4, right[3] + 3, LABEL_DIM, true);
		}
	}

	/**
	 * Draws sockets, cells and items of a layout and remembers it for input. Callers draw the
	 * panel backgrounds and labels themselves.
	 */
	public static void draw(final GuiGraphicsExtractor g, final AbstractContainerMenu menu, final Layout l, final int leftPos, final int topPos, final int mouseX, final int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		Font font = mc.font;
		GridInventory inv = inventory(player);
		layout = l;
		screenLeft = leftPos;
		screenTop = topPos;
		ItemStack carried = menu.getCarried();
		if (carried.isEmpty()) {
			rotated = false;
		}

		SocketBox overSocket = null;
		for (SocketBox socket : l.sockets()) {
			ItemStack worn = socket.socket().worn(inv);
			drawCellBack(g, socket.x(), socket.y(), socket.size(), worn.isEmpty() ? null : Tiers.of(worn));
			if (!worn.isEmpty()) {
				int inset = (socket.size() - CELL) / 2;
				g.item(worn, socket.x() + inset, socket.y() + inset);
			}
			if (socket.contains(mouseX, mouseY)) {
				overSocket = socket;
			}
		}

		hover = null;
		for (Box box : l.boxes()) {
			for (int cy = 0; cy < box.height(); cy++) {
				for (int cx = 0; cx < box.width(); cx++) {
					drawCellBack(g, box.x() + cx * CELL, box.y() + cy * CELL, CELL, null);
				}
			}
			if (box.contains(mouseX, mouseY)) {
				int cx = (mouseX - box.x()) / CELL;
				int cy = (mouseY - box.y()) / CELL;
				hover = new Hover(box.area(), box.section(), cx, cy, GridLogic.itemAt(inv.items(box.area()), box.section(), cx, cy));
			}
		}
		for (Area area : Area.values()) {
			for (Placed placed : inv.items(area)) {
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
		} else if (overSocket != null && carried.isEmpty() && !overSocket.socket().worn(inv).isEmpty()) {
			g.setTooltipForNextFrame(font, overSocket.socket().worn(inv), mouseX, mouseY);
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

	public static void drawCellBack(final GuiGraphicsExtractor g, final int x, final int y, final int size, final @Nullable Tier tier) {
		g.fill(x, y, x + size, y + size, tier == null ? EMPTY_CELL : tier.withAlpha(0x90));
		g.outline(x, y, size, size, tier == null ? CELL_LINE : tier.color);
	}

	/**
	 * The item on its tier colour, drawn as large as its footprint allows, turned if rotated.
	 * Backpacks and rigs with a grid sprite fill their whole footprint with it; every other item keeps
	 * its square icon so it is never stretched.
	 */
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
		Identifier sprite = GridSprites.of(placed.stack().getItem());
		if (sprite != null) {
			// drawn upright, then turned with the footprint when the item lies on its side
			if (placed.rotated()) {
				pose.rotate((float) (Math.PI / 2));
			}
			int sw = upright[0] * CELL;
			int sh = upright[1] * CELL;
			int tw = upright[0] * GridSprites.TEXELS_PER_CELL;
			int th = upright[1] * GridSprites.TEXELS_PER_CELL;
			g.blit(RenderPipelines.GUI_TEXTURED, sprite, -sw / 2, -sh / 2, 0.0F, 0.0F, sw, sh, tw, th, tw, th);
		} else if (upright[1] >= 2 * upright[0]) {
			// long things (swords, sticks) are drawn corner to corner in vanilla: stand them up
			// along the footprint's long side, as large as it allows
			float scale = Math.min(upright[1] * 0.68F, upright[0] * 1.6F);
			pose.rotate((float) (placed.rotated() ? Math.PI / 4 : -Math.PI / 4));
			pose.scale(scale, scale);
			g.item(placed.stack(), -8, -8);
		} else {
			float scale = Math.max(1, Math.min(w, h));
			if (placed.rotated()) {
				pose.rotate((float) (Math.PI / 2));
			}
			pose.scale(scale, scale);
			g.item(placed.stack(), -8, -8);
		}
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

	/** Forgets the layout, so input stops reaching a panel that is no longer shown. */
	public static void clear() {
		layout = null;
		hover = null;
		pressedOnPanel = false;
		pressedAt = null;
	}

	/** Returns true when the click belonged to the panel. */
	public static boolean click(final AbstractContainerMenu menu, final double mx, final double my, final int button, final boolean shift) {
		if (button == LEFT) {
			pressX = mx;
			pressY = my;
			pressedOnPanel = false;
		}
		Layout l = layout;
		Minecraft mc = Minecraft.getInstance();
		if (l == null || mc.player == null || !active()) {
			return false;
		}
		SocketBox socket = l.socketAt(mx, my);
		if (socket != null) {
			if (button == LEFT) {
				send(socket.socket().action, Area.POCKETS, 0, 0, 0);
			}
			pressedOnPanel = button == LEFT;
			pressedAt = null;
			return true;
		}
		if (!l.inside(mx, my)) {
			return false;
		}
		Hover h = hover;
		if (h == null && !l.claimsPanels()) {
			// not on a cell: the screen's own slot (armour, crafting) or nothing
			return false;
		}
		if (button == LEFT) {
			pressedOnPanel = true;
			pressedAt = h;
		}
		if (h == null || button != LEFT) {
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

	/** A drag that started on the panel is the panel's; the screen must not spread the stack. */
	public static boolean dragging() {
		return pressedOnPanel;
	}

	/**
	 * A mouse release. The screen would treat a release beside its window as a click outside and
	 * throw the carried item, and the panel is beside the window, so every release that touches
	 * the panel is decided here.
	 */
	public static Release release(final AbstractContainerMenu menu, final double mx, final double my, final int button) {
		Layout l = layout;
		if (l == null || !active()) {
			pressedOnPanel = false;
			return Release.PASS;
		}
		boolean fromPanel = pressedOnPanel;
		Hover start = pressedAt;
		pressedOnPanel = false;
		pressedAt = null;
		ItemStack carried = menu.getCarried();
		boolean onCell = hover != null && l.inside(mx, my);
		boolean overPanel = l.claimsPanels() ? l.inside(mx, my) || l.socketAt(mx, my) != null : onCell || l.socketAt(mx, my) != null;
		Hover now = onCell ? hover : null;
		if (!fromPanel) {
			// a drag from a menu slot that ends on a grid cell puts the item there
			boolean moved = !Double.isNaN(pressX) && (Math.abs(mx - pressX) >= 2 || Math.abs(my - pressY) >= 2);
			if (!overPanel || button != LEFT) {
				return Release.PASS;
			}
			if (moved && now != null && !carried.isEmpty()) {
				send(GridActionPayload.PLACE, now.area(), now.section(), now.cx(), now.cy());
			}
			return Release.HANDLED;
		}
		if (button != LEFT || carried.isEmpty()) {
			// a plain click: the pick-up may still be on its way from the server
			return Release.HANDLED;
		}
		if (now != null) {
			boolean sameCell = start != null && start.area() == now.area() && start.section() == now.section() && start.cx() == now.cx() && start.cy() == now.cy();
			if (!sameCell) {
				send(GridActionPayload.PLACE, now.area(), now.section(), now.cx(), now.cy());
			}
			return Release.HANDLED;
		}
		return overPanel ? Release.HANDLED : Release.TO_SLOT;
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

	/** Screen (GUI) position of the centre of a cell, for the client game test; null when not shown. */
	public static double @Nullable [] cellCentre(final Area area, final int section, final int cx, final int cy) {
		Layout l = layout;
		Box box = l == null ? null : boxFor(l, area, section);
		return box == null ? null : new double[] {box.x() + cx * CELL + CELL / 2.0, box.y() + cy * CELL + CELL / 2.0};
	}

	/** Screen (GUI) position of the centre of a socket, for the client game test; null when not shown. */
	public static double @Nullable [] socketCentre(final Socket socket) {
		Layout l = layout;
		if (l != null) {
			for (SocketBox box : l.sockets()) {
				if (box.socket() == socket) {
					return new double[] {box.x() + box.size() / 2.0, box.y() + box.size() / 2.0};
				}
			}
		}
		return null;
	}

	/** Screen (GUI) position of the centre of a menu slot, for the client game test. */
	public static double[] slotCentre(final Slot slot) {
		return new double[] {screenLeft + slot.x + CELL / 2.0, screenTop + slot.y + CELL / 2.0};
	}

	public static void grab(final int slotIndex) {
		send(GridActionPayload.GRAB, Area.POCKETS, slotIndex, 0, 0);
	}

	private static void send(final int action, final Area area, final int a, final int b, final int c) {
		ClientPlayNetworking.send(new GridActionPayload(action, area.ordinal(), a, b, c, rotated));
	}
}
