package life.shizune.mistinn.client.grid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.client.body.BodyHud;
import life.shizune.mistinn.client.tier.TierText;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.mixin.SlotAccessor;
import life.shizune.mistinn.tier.Tiers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * The hunter's own inventory, in place of the vanilla one outside creative. Three columns:
 * worn gear, pockets, safe box and a small crafting grid on the left; the chest rig over the
 * backpack in the middle, with the hotbar beneath; the body's parts and their health on the
 * right. Right-click the helmet slot to see the mask slot instead.
 * The vanilla player menu is reused, so crafting, armour and hotbar behave exactly as before;
 * only where its slots are drawn changes, and only while this screen is open.
 */
public class HunterScreen extends AbstractContainerScreen<InventoryMenu> {
	private static final int WIDTH = 422;
	private static final int HEIGHT = 232;
	private static final int LEFT_W = 118;
	private static final int CENTRE_X = 124;
	private static final int CENTRE_W = 176;
	private static final int RIGHT_X = 306;
	private static final int RIGHT_W = 116;
	private static final int SLOT = 18;
	/** Slots this screen does not show are moved here, out of reach of the mouse. */
	private static final int AWAY = -10000;

	// slot indexes of the vanilla player menu
	private static final int RESULT = 0;
	private static final int CRAFT_FIRST = 1;
	private static final int HEAD = 5;
	private static final int CHEST = 6;
	private static final int LEGS = 7;
	private static final int FEET = 8;
	private static final int HOTBAR_FIRST = 36;
	private static final int OFFHAND = 45;

	private final Map<Slot, int[]> original = new HashMap<>();
	private boolean showMask;
	private GridPanel.Layout gridLayout = new GridPanel.Layout(List.of(), List.of(), List.of(), false);
	private int rigTitleY;
	private int packTitleY;
	/** Height of the three columns and the row of the hotbar, from the worn rig and backpack. */
	private int panelHeight = HEIGHT;
	private int hotbarY = HEIGHT - 19;
	/** The left column needs this much for gear, pockets, safe box and crafting. */
	private static final int LEFT_MIN = 214;

	public HunterScreen(final Player player) {
		super(player.inventoryMenu, player.getInventory(), Component.translatable("container.inventory"), WIDTH, HEIGHT);
	}

	// ── Slots ─────────────────────────────────────────────────────

	@Override
	protected void init() {
		super.init();
		for (Slot slot : this.menu.slots) {
			this.original.putIfAbsent(slot, new int[] {slot.x, slot.y});
		}
		this.relayout();
	}

	/** Lays the screen out for what is worn now; runs every frame, since gear can change while open. */
	private void relayout() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		this.gridLayout = this.layoutGrid(GridPanel.inventory(this.minecraft.player));
		this.placeSlots();
	}

	private void placeSlots() {
		for (Slot slot : this.menu.slots) {
			move(slot, AWAY, AWAY);
		}
		// worn gear: helmet and armour above, leggings and boots below
		move(this.menu.slots.get(HEAD), 9, 17);
		move(this.menu.slots.get(CHEST), 31, 17);
		move(this.menu.slots.get(LEGS), 9, 39);
		move(this.menu.slots.get(FEET), 31, 39);
		if (this.showMask) {
			move(this.menu.slots.get(HEAD), AWAY, AWAY);
		}
		move(this.menu.slots.get(OFFHAND), 97, 39);
		for (int i = 0; i < 4; i++) {
			move(this.menu.slots.get(CRAFT_FIRST + i), 9 + (i % 2) * SLOT, 171 + (i / 2) * SLOT);
		}
		move(this.menu.slots.get(RESULT), 57, 180);
		int hotbarX = CENTRE_X + (CENTRE_W - 9 * SLOT) / 2 + 1;
		for (int i = 0; i < 9; i++) {
			move(this.menu.slots.get(HOTBAR_FIRST + i), hotbarX + i * SLOT, this.hotbarY);
		}
	}

	private static void move(final Slot slot, final int x, final int y) {
		((SlotAccessor) slot).mistinn$setX(x);
		((SlotAccessor) slot).mistinn$setY(y);
	}

	@Override
	public void removed() {
		// the player menu outlives this screen: give its slots their vanilla places back
		this.original.forEach((slot, xy) -> move(slot, xy[0], xy[1]));
		GridPanel.clear();
		super.removed();
	}

	// ── Grid layout ───────────────────────────────────────────────

	/**
	 * Lays the grid out for the worn rig and backpack, as faithfully as the screen allows: the
	 * first arrangement whose columns fit the screen's height, else the most compact one. The
	 * hotbar always goes below the backpack, and the screen is centred on what it needs.
	 */
	private GridPanel.Layout layoutGrid(final GridInventory inv) {
		GridPanel.Arrange[][] attempts = {
			{GridPanel.Arrange.SMALL_ABOVE, GridPanel.Arrange.SMALL_ABOVE},
			{GridPanel.Arrange.SMALL_ABOVE, GridPanel.Arrange.SMALL_BESIDE},
			{GridPanel.Arrange.FLOW, GridPanel.Arrange.SMALL_BESIDE}
		};
		int available = this.height - 8;
		List<GridPanel.Box> chosen = null;
		int chosenHeight = Integer.MAX_VALUE;
		int chosenRigTitle = 0;
		int chosenPackTitle = 0;
		int chosenBottom = 0;
		for (GridPanel.Arrange[] attempt : attempts) {
			// laid out from (0, 0); moved to the screen's corner below
			List<GridPanel.Box> boxes = new ArrayList<>();
			int cy = 15;
			if (!inv.shape(Area.RIG).sections().isEmpty()) {
				cy = GridPanel.placeSections(boxes, Area.RIG, inv.shape(Area.RIG), CENTRE_X + 4, cy, CENTRE_W - 8, attempt[0])[1] + 4;
			} else {
				cy += GridPanel.CELL;
			}
			int packTitle = cy;
			int bottom = GridPanel.placeSections(boxes, Area.BACKPACK, inv.shape(Area.BACKPACK), CENTRE_X + 4, cy + 11, CENTRE_W - 8, attempt[1])[1];
			int needed = Math.max(LEFT_MIN, bottom + 6 + SLOT + 4);
			if (chosen == null || needed < chosenHeight) {
				chosen = boxes;
				chosenHeight = needed;
				chosenRigTitle = 4;
				chosenPackTitle = packTitle;
				chosenBottom = bottom;
			}
			if (needed <= available) {
				chosen = boxes;
				chosenHeight = needed;
				chosenPackTitle = packTitle;
				chosenBottom = bottom;
				break;
			}
		}
		this.panelHeight = chosenHeight;
		this.hotbarY = Math.max(chosenBottom + 6, chosenHeight - SLOT - 3);
		this.topPos = Math.max(4, (this.height - this.panelHeight) / 2);
		int x = this.leftPos;
		int y = this.topPos;
		this.rigTitleY = y + chosenRigTitle;
		this.packTitleY = y + chosenPackTitle;
		List<GridPanel.Box> boxes = new ArrayList<>();
		for (GridPanel.Box box : chosen) {
			boxes.add(new GridPanel.Box(box.area(), box.section(), x + box.x(), y + box.y(), box.width(), box.height()));
		}
		for (int i = 0; i < 4; i++) {
			boxes.add(new GridPanel.Box(Area.POCKETS, i, x + 9 + i * (GridPanel.CELL + GridPanel.GAP), y + 83, 1, 1));
		}
		boxes.add(new GridPanel.Box(Area.SAFE, 0, x + 9, y + 117, 2, 2));
		List<GridPanel.SocketBox> sockets = new ArrayList<>();
		sockets.add(new GridPanel.SocketBox(GridPanel.Socket.RIG, x + 57, y + 17, GridPanel.CELL));
		sockets.add(new GridPanel.SocketBox(GridPanel.Socket.BACKPACK, x + 79, y + 17, GridPanel.CELL));
		if (this.showMask) {
			sockets.add(new GridPanel.SocketBox(GridPanel.Socket.MASK, x + 9, y + 17, GridPanel.CELL));
		}
		return new GridPanel.Layout(List.copyOf(boxes), List.copyOf(sockets), List.of(
			new int[] {x, y, x + LEFT_W, y + this.panelHeight},
			new int[] {x + CENTRE_X, y, x + CENTRE_X + CENTRE_W, y + this.panelHeight}
		), false);
	}

	// ── Drawing ───────────────────────────────────────────────────

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		// before anything is drawn: what is worn decides where everything goes, slots included
		this.relayout();
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		panel(g, x, y, LEFT_W, this.panelHeight);
		panel(g, x + CENTRE_X, y, CENTRE_W, this.panelHeight);
		panel(g, x + RIGHT_X, y, RIGHT_W, this.panelHeight);
		for (Slot slot : this.menu.slots) {
			if (slot.isActive() && slot.x > AWAY / 2) {
				g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, GridPanel.CELL_LINE);
				g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, GridPanel.EMPTY_CELL);
			}
		}
	}

	private static void panel(final GuiGraphicsExtractor g, final int x, final int y, final int w, final int h) {
		g.fill(x, y, x + w, y + h, GridPanel.PANEL);
		g.outline(x, y, w, h, GridPanel.PANEL_LINE);
	}

	@Override
	protected void extractLabels(final GuiGraphicsExtractor g, final int xm, final int ym) {
		// labels are drawn with the rest in extractContents
	}

	@Override
	public void extractContents(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractContents(g, mouseX, mouseY, a);
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		GridInventory inv = GridPanel.inventory(this.minecraft.player);
		this.labels(g, inv);
		GridPanel.draw(g, this.menu, this.gridLayout, this.leftPos, this.topPos, mouseX, mouseY);
		this.body(g);
		if (!this.menu.getCarried().isEmpty()) {
			g.text(this.font, Component.translatable("gui.mistinn.rotate_hint"), this.leftPos + CENTRE_X + 4, this.topPos + this.panelHeight + 3, GridPanel.LABEL_DIM, true);
		}
	}

	private void labels(final GuiGraphicsExtractor g, final GridInventory inv) {
		int x = this.leftPos;
		int y = this.topPos;
		g.text(this.font, Component.translatable("gui.mistinn.gear"), x + 6, y + 5, GridPanel.LABEL, false);
		Component head = Component.translatable(this.showMask ? "gui.mistinn.mask_slot" : "gui.mistinn.helmet_slot");
		g.text(this.font, head.copy().append(" ").append(Component.translatable("gui.mistinn.head_toggle")), x + 6, y + 59, GridPanel.LABEL_DIM, false);
		g.text(this.font, Component.translatable("gui.mistinn.rig_slot"), x + 55, y + 5, GridPanel.LABEL, false);
		g.text(this.font, Component.translatable("gui.mistinn.backpack_short"), x + 77, y + 5, GridPanel.LABEL, false);
		g.text(this.font, Component.translatable("gui.mistinn.offhand"), x + 95, y + 59, GridPanel.LABEL_DIM, false);
		g.text(this.font, Component.translatable("gui.mistinn.pockets"), x + 6, y + 72, GridPanel.LABEL, false);
		g.text(this.font, Component.translatable("gui.mistinn.safe"), x + 6, y + 106, GridPanel.LABEL, false);
		g.text(this.font, Component.translatable("gui.mistinn.crafting"), x + 6, y + 160, GridPanel.LABEL, false);
		int cx = x + CENTRE_X + 4;
		ItemStack rig = inv.rig();
		g.text(this.font, rig.isEmpty() ? Component.translatable("gui.mistinn.no_rig") : TierText.name(rig), cx, this.rigTitleY, rig.isEmpty() ? GridPanel.LABEL_DIM : Tiers.of(rig).color, false);
		ItemStack pack = inv.backpack();
		g.text(this.font, pack.isEmpty() ? Component.translatable("gui.mistinn.no_backpack") : TierText.name(pack), cx, this.packTitleY, pack.isEmpty() ? GridPanel.LABEL_DIM : Tiers.of(pack).color, false);
	}

	/** The right column: a large figure and each part's health. */
	private void body(final GuiGraphicsExtractor g) {
		Body body = BodyService.get(this.minecraft.player);
		int x = this.leftPos + RIGHT_X;
		int y = this.topPos;
		g.text(this.font, Component.translatable("gui.mistinn.body"), x + 6, y + 5, GridPanel.LABEL, false);
		int scale = 4;
		life.shizune.mistinn.body.Wounds wounds = life.shizune.mistinn.body.WoundService.get(this.minecraft.player);
		long now = this.minecraft.player.level().getGameTime();
		BodyHud.figure(g, body, wounds, now, x + (RIGHT_W - BodyHud.WIDTH * scale) / 2, y + 18, scale);
		int ly = y + 18 + BodyHud.HEIGHT * scale + 8;
		for (BodyPart part : BodyPart.values()) {
			float health = body.get(part);
			Component name = Component.translatable("gui.mistinn.part." + part.id);
			String value = health <= 0.0F ? "×" : Mth.ceil(health) + "/" + (int) BodyService.max(this.minecraft.player);
			g.text(this.font, name, x + 8, ly, GridPanel.LABEL, false);
			g.text(this.font, value, x + RIGHT_W - 8 - this.font.width(value), ly, BodyHud.colour(health), false);
			// what the part carries, beside its name: bleeding, a broken bone, a tourniquet or splint with its time
			Component state = woundState(wounds.get(part), health <= 0.0F, now);
			if (state != null) {
				g.text(this.font, state, x + 8 + this.font.width(name) + 4, ly, 0xFFB8B0A0, false);
			}
			ly += 10;
		}
	}

	/** One line on what a part carries, or null when it carries nothing. */
	private static @org.jspecify.annotations.Nullable Component woundState(final life.shizune.mistinn.body.Wounds.Wound wound, final boolean destroyed, final long now) {
		if (destroyed || !wound.any()) {
			return null;
		}
		net.minecraft.network.chat.MutableComponent line = Component.empty();
		if (wound.bleeding() && !wound.tied()) {
			line.append(Component.translatable("gui.mistinn.wound.bleeding").withColor(0xE0503C)).append(" ");
		}
		if (wound.tied()) {
			long left = life.shizune.mistinn.body.WoundService.TOURNIQUET_SAFE - (now - wound.tourniquet());
			line.append(left > 0
				? Component.translatable("gui.mistinn.wound.tourniquet", time(left)).withColor(0xE0A050)
				: Component.translatable("gui.mistinn.wound.necrosis").withColor(0xE0503C)).append(" ");
		}
		if (wound.fractured()) {
			line.append(wound.splinted(now)
				? Component.translatable("gui.mistinn.wound.splint", time(wound.splint() - now)).withColor(0xD4A86A)
				: Component.translatable("gui.mistinn.wound.fracture").withColor(0xF2EEE4));
		}
		return line;
	}

	/** Ticks as minutes and seconds. */
	private static String time(final long ticks) {
		long seconds = Math.max(0L, ticks) / 20L;
		return String.format("%d:%02d", seconds / 60L, seconds % 60L);
	}

	// ── Input ─────────────────────────────────────────────────────

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		// right-click the head slot to switch between helmet and mask
		double hx = event.x() - this.leftPos - 9;
		double hy = event.y() - this.topPos - 17;
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && hx >= 0 && hy >= 0 && hx < 16 && hy < 16 && this.menu.getCarried().isEmpty()) {
			this.showMask = !this.showMask;
			this.placeSlots();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected boolean hasClickedOutside(final double mx, final double my, final int xo, final int yo) {
		// the grid columns are part of this screen: a click there is never a throw
		return super.hasClickedOutside(mx, my, xo, yo) && !this.gridLayout.inside(mx, my);
	}

	/** For the client game test. */
	public boolean showsMask() {
		return this.showMask;
	}
}
