package life.shizune.mistinn.client.expedition;

import life.shizune.mistinn.client.grid.GridPanel;
import life.shizune.mistinn.expedition.SearchMenu;
import life.shizune.mistinn.grid.ItemSizes;
import life.shizune.mistinn.grid.Placed;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Searching a container in an expedition's ground, laid out like a hunter's pack. What has not been searched is a grey
 * shape the size of what lies there and nothing more; the one being searched fills as the search goes on; what has been
 * searched shows on its tier's colour and can be taken (clicked onto the cursor, or shift-clicked into the hunter's own
 * grid). The hunter's grid stands beside it, drawn by the grid panel as beside any container.
 */
public final class SearchScreen extends AbstractContainerScreen<SearchMenu> {
	private static final int CELL = SearchMenu.CELL;
	private static final int UNKNOWN = 0xFF34322F;
	private static final int UNKNOWN_LINE = 0xFF5A5650;
	private static final int SEARCHING = 0xFF6B675F;
	private static final int MARK = 0xFF8E887E;

	public SearchScreen(final SearchMenu menu, final Inventory inventory, final Component title) {
		super(menu, inventory, title, width(menu), height(menu));
		this.titleLabelX = SearchMenu.LEFT;
		this.titleLabelY = 6;
	}

	private static int width(final SearchMenu menu) {
		return Math.max(SearchMenu.LEFT * 2 + menu.view().width() * CELL, SearchMenu.LEFT * 2 + Inventory.getSelectionSize() * 18);
	}

	private static int height(final SearchMenu menu) {
		return SearchMenu.TOP + menu.view().height() * CELL + 14 + 18 + 6;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x0 = this.leftPos;
		int y0 = this.topPos;
		g.fill(x0, y0, x0 + this.imageWidth, y0 + this.imageHeight, GridPanel.PANEL);
		g.outline(x0, y0, this.imageWidth, this.imageHeight, GridPanel.PANEL_LINE);
		SearchMenu.View view = this.menu.view();
		int gx = x0 + SearchMenu.LEFT;
		int gy = y0 + SearchMenu.TOP;
		for (int cx = 0; cx < view.width(); cx++) {
			for (int cy = 0; cy < view.height(); cy++) {
				g.fill(gx + cx * CELL, gy + cy * CELL, gx + cx * CELL + CELL, gy + cy * CELL + CELL, GridPanel.CELL_LINE);
				g.fill(gx + cx * CELL + 1, gy + cy * CELL + 1, gx + cx * CELL + CELL - 1, gy + cy * CELL + CELL - 1, GridPanel.EMPTY_CELL);
			}
		}
		for (Slot slot : this.menu.slots) {
			if (!(slot instanceof SearchMenu.CacheSlot)) {
				g.fill(x0 + slot.x - 1, y0 + slot.y - 1, x0 + slot.x + 17, y0 + slot.y + 17, GridPanel.CELL_LINE);
				g.fill(x0 + slot.x, y0 + slot.y, x0 + slot.x + 16, y0 + slot.y + 16, GridPanel.EMPTY_CELL);
			}
		}
		int searching = this.menu.searching();
		for (int i = 0; i < this.menu.things(); i++) {
			SearchMenu.Footprint at = view.footprints().get(i);
			int x = gx + at.x() * CELL;
			int y = gy + at.y() * CELL;
			int w = at.w() * CELL;
			int h = at.h() * CELL;
			if (this.menu.searched(i)) {
				ItemStack stack = this.menu.slots.get(i).getItem();
				if (!stack.isEmpty()) {
					int[] upright = ItemSizes.of(stack);
					GridPanel.drawPlaced(g, this.font, new Placed(0, 0, 0, upright[0] != at.w(), stack), x, y);
				}
				continue;
			}
			g.fill(x + 1, y + 1, x + w - 1, y + h - 1, UNKNOWN);
			g.outline(x, y, w, h, UNKNOWN_LINE);
			if (i == searching && this.menu.needed() > 0) {
				// the search fills the shape from the bottom up, and three dots go round
				int filled = (int) Math.round((h - 2) * Math.min(1.0, this.menu.progress() / (double) this.menu.needed()));
				g.fill(x + 1, y + h - 1 - filled, x + w - 1, y + h - 1, SEARCHING);
				long tick = this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime() : 0L;
				String dots = ".".repeat(1 + (int) (tick / 6 % 3));
				g.text(this.font, dots, x + (w - this.font.width("...")) / 2, y + h / 2 - 4, GridPanel.LABEL, false);
			} else {
				g.text(this.font, "?", x + (w - this.font.width("?")) / 2 + 1, y + h / 2 - 4, MARK, false);
			}
		}
	}

	@Override
	protected void extractLabels(final GuiGraphicsExtractor g, final int mouseX, final int mouseY) {
		g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, GridPanel.LABEL, false);
		int left = 0;
		for (int i = 0; i < this.menu.things(); i++) {
			if (!this.menu.searched(i)) {
				left++;
			}
		}
		Component status = left == 0 ? Component.translatable("gui.mistinn.search.done") : Component.translatable("gui.mistinn.search.left", left);
		g.text(this.font, status, this.imageWidth - SearchMenu.LEFT - this.font.width(status), this.titleLabelY, GridPanel.LABEL_DIM, false);
	}

	@Override
	public void extractContents(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractContents(g, mouseX, mouseY, a);
		int i = this.thingAt(mouseX, mouseY);
		if (i >= 0 && this.menu.getCarried().isEmpty()) {
			ItemStack stack = this.menu.slots.get(i).getItem();
			if (this.menu.searched(i) && !stack.isEmpty()) {
				g.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
			} else if (!this.menu.searched(i)) {
				g.setTooltipForNextFrame(this.font, Component.translatable("gui.mistinn.search.unknown"), mouseX, mouseY);
			}
		}
	}

	/** The thing whose footprint is under the pointer, or -1. */
	private int thingAt(final double mx, final double my) {
		SearchMenu.View view = this.menu.view();
		double cx = (mx - this.leftPos - SearchMenu.LEFT) / CELL;
		double cy = (my - this.topPos - SearchMenu.TOP) / CELL;
		for (int i = 0; i < this.menu.things(); i++) {
			SearchMenu.Footprint at = view.footprints().get(i);
			if (cx >= at.x() && cy >= at.y() && cx < at.x() + at.w() && cy < at.y() + at.h()) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		int i = this.thingAt(event.x(), event.y());
		if (i < 0) {
			return super.mouseClicked(event, doubleClick);
		}
		Slot slot = this.menu.slots.get(i);
		if (this.menu.searched(i) && slot.hasItem() && this.menu.getCarried().isEmpty()) {
			this.slotClicked(slot, slot.index, event.button(), event.hasShiftDown() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
		}
		// nothing goes into a cache, and nothing unsearched comes out
		return true;
	}

	/** The centre of the {@code index}th thing's footprint on screen, for tests. */
	public double[] thingCentre(final int index) {
		SearchMenu.Footprint at = this.menu.view().footprints().get(index);
		return new double[] {this.leftPos + SearchMenu.LEFT + (at.x() + at.w() / 2.0) * CELL, this.topPos + SearchMenu.TOP + (at.y() + at.h() / 2.0) * CELL};
	}
}
