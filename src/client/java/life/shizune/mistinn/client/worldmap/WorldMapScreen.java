package life.shizune.mistinn.client.worldmap;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.anim.Motion;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.story.StoryScript;
import life.shizune.mistinn.story.StoryStep;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * The world map, opened from the title screen: one tall picture of the world from the End down to
 * the Nether, drawn across the screen's width and scrolled up and down. It opens on Rust Anchor
 * Station and sinks with the hunter through the water to the misty coast while the way the story
 * walks draws itself, stop by stop. A stop opens a strip from the bottom with its story card;
 * stops past the last card seen on this computer stay in the fog unless the hunter asks to see all.
 */
public class WorldMapScreen extends Screen {
	private static final Identifier MAP = Mistinn.id("textures/gui/worldmap/map.png");
	private static final Identifier VEIL = Mistinn.id("textures/gui/worldmap/veil.png");
	private static final int VEIL_W = 48;
	private static final int VEIL_H = 32;
	private static final int CARD_W = 320;
	private static final int CARD_H = 180;

	private static final int TEXT = 0xFFEDE6D8;
	private static final int GOLD = 0xFFE0A050;
	private static final int GREY = 0xFF968F84;
	private static final int DIM = 0xFF6E6A62;
	private static final int ACCENT = 0xD89A50;
	private static final int SALT = 0xE8E2D2;
	private static final int RAIL_X = 6;

	/** Seconds the opening takes to sink from the station to the coast, and when it starts. */
	private static final float SINK_AT = 0.5F;
	private static final float SINK_FOR = 2.6F;
	/** Seconds the way takes to draw itself. */
	private static final float TRACE_AT = 1.4F;
	private static final float TRACE_FOR = 3.0F;
	private static final float FADE_FOR = 0.8F;

	private final Screen parent;
	private final long opened = System.nanoTime();
	private long last = System.nanoTime();
	private float scroll = WorldMap.OPEN_AT;
	private float target = WorldMap.OPEN_AT;
	/** While true the view still follows the opening; any scroll of the hunter's own ends it. */
	private boolean sinking = true;
	private WorldMap.@Nullable Place selected;
	private int card;
	private float sheet;
	private double pressX;
	private double pressY;
	private boolean pressedOnMap;
	private boolean dragged;
	private @Nullable SimpleSoundInstance music;

	public WorldMapScreen(final Screen parent) {
		super(Component.translatable("worldmap.mistinn.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (this.music == null || !this.minecraft.getSoundManager().isActive(this.music)) {
			this.music = SimpleSoundInstance.forMusic(MistinnSounds.MUSIC_SHORE);
			this.minecraft.getSoundManager().play(this.music);
		}
		this.target = this.clampScroll(this.target);
		this.scroll = this.clampScroll(this.scroll);
	}

	@Override
	public void removed() {
		super.removed();
		if (this.music != null) {
			this.minecraft.getSoundManager().stop(this.music);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	// ── Geometry ─────────────────────────────────────────────────────

	/** GUI pixels per map pixel: the map fills the width. */
	private float scale() {
		return this.width / (float) WorldMap.W;
	}

	/** Map pixels the screen shows, top to bottom. */
	private float viewHeight() {
		return this.height / this.scale();
	}

	private float clampScroll(final float value) {
		return Mth.clamp(value, 0.0F, Math.max(0.0F, WorldMap.H - this.viewHeight()));
	}

	private float sx(final float x) {
		return x * this.scale();
	}

	private float sy(final float y) {
		return (y - this.scroll) * this.scale();
	}

	private float seconds() {
		return (System.nanoTime() - this.opened) / 1.0E9F;
	}

	/** Where the opening ends: the coast from its sea down, centred when the screen is taller than it. */
	private float coast() {
		float top = WorldMap.place("lighthouse").y() - 72.0F;
		float coast = 1010.0F - top;
		return this.clampScroll(top - Math.max(0.0F, (this.viewHeight() - coast) / 2.0F));
	}

	private int sheetHeight() {
		return Mth.clamp(Math.round(this.height * 0.38F), 104, 164);
	}

	private int sheetTop() {
		return this.height - Math.round(this.sheetHeight() * Motion.outCubic(this.sheet));
	}

	// ── What is shown ────────────────────────────────────────────────

	/** Whether a place is out of the fog: always for landmarks, for stops once their card has been seen. */
	public static boolean shown(final WorldMap.Place place) {
		return place.revealedBy() == null || SeenStory.revealAll() || SeenStory.seen(place.revealedBy());
	}

	/** The cards a place shows: those seen here, or all of them when everything is shown. */
	public static List<String> cards(final WorldMap.Place place) {
		List<String> cards = new ArrayList<>();
		for (String id : place.cards()) {
			if (SeenStory.revealAll() || SeenStory.seen(id)) {
				cards.add(id);
			}
		}
		return cards;
	}

	/** The route as far as it is out of the fog: the points up to the first stop still hidden. */
	public static List<WorldMap.Point> visibleRoute() {
		List<WorldMap.Point> points = new ArrayList<>();
		for (WorldMap.Point point : WorldMap.ROUTE) {
			if (!shown(WorldMap.place(point.place()))) {
				break;
			}
			points.add(point);
		}
		return points;
	}

	public WorldMap.@Nullable Place selected() {
		return this.selected;
	}

	public float scroll() {
		return this.scroll;
	}

	/** Opens a place's strip and brings it into view above the strip. */
	public void select(final WorldMap.Place clicked) {
		// a cairn of the salt road opens the road
		WorldMap.Place place = WorldMap.place(clicked.of());
		if (!shown(place)) {
			return;
		}
		if (this.selected != place) {
			this.card = 0;
		}
		this.selected = place;
		this.sinking = false;
		float above = (this.height - this.sheetHeight()) / this.scale();
		this.target = this.clampScroll(place.y() + 6.0F - above / 2.0F);
		click(SoundEvents.UI_BUTTON_CLICK.value());
	}

	/** The next (or, with -1, the last) stop out of the fog, from the one open. */
	public void step(final int direction) {
		List<WorldMap.Place> stops = WorldMap.STOPS;
		int at = this.selected == null ? -1 : stops.indexOf(this.selected);
		if (at < 0) {
			return;
		}
		for (int i = at + direction; i >= 0 && i < stops.size(); i += direction) {
			if (shown(stops.get(i))) {
				this.select(stops.get(i));
				return;
			}
		}
	}

	public void close() {
		this.selected = null;
	}

	/** Shows the open stop's next card, when it has more than one. */
	public void turnCard() {
		int count = this.selected == null ? 0 : cards(this.selected).size();
		if (count > 1) {
			this.card = (this.card + 1) % count;
			click(SoundEvents.BOOK_PAGE_TURN);
		}
	}

	/** Which of the open stop's cards is shown. */
	public int card() {
		return this.card;
	}

	/** Where a place's marker is on the screen, in GUI pixels. */
	public double[] markerAt(final WorldMap.Place place) {
		return new double[] {this.sx(place.x()), this.sy(place.y())};
	}

	/** Where a layer's name is on the rail, in GUI pixels. */
	public double[] railAt(final String band) {
		return new double[] {RAIL_X + 1.0, this.railY(WorldMap.band(band).middle())};
	}

	/** Brings a layer of the world into view. */
	public void jump(final String band) {
		this.sinking = false;
		// the coast as the opening leaves it: from its sea down, clear of the title
		this.target = band.equals("overworld") ? this.coast() : this.clampScroll(WorldMap.band(band).middle() - this.viewHeight() / 2.0F);
	}

	// ── Input ─────────────────────────────────────────────────────

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		this.pressedOnMap = false;
		if (this.overReveal(mx, my)) {
			SeenStory.setRevealAll(!SeenStory.revealAll());
			if (this.selected != null && !shown(this.selected)) {
				this.selected = null;
			}
			this.card = 0;
			click(SoundEvents.UI_BUTTON_CLICK.value());
			return true;
		}
		if (this.selected != null && my >= this.sheetTop()) {
			this.clickSheet(mx, my);
			return true;
		}
		String band = this.railUnder(mx, my);
		if (band != null) {
			this.jump(band);
			click(SoundEvents.UI_BUTTON_CLICK.value());
			return true;
		}
		WorldMap.Place place = this.placeUnder(mx, my);
		if (place != null && shown(place)) {
			this.select(place);
			return true;
		}
		this.pressX = mx;
		this.pressY = my;
		this.pressedOnMap = true;
		this.dragged = false;
		return true;
	}

	private void clickSheet(final double mx, final double my) {
		int top = this.sheetTop();
		int h = this.sheetHeight();
		if (my >= top + h - 16 && mx >= this.width - 150) {
			this.step(mx < this.width - 76 ? -1 : 1);
			return;
		}
		int[] pic = this.picture(top, h);
		if (mx >= pic[0] && mx < pic[0] + pic[2] && my >= pic[1] && my < pic[1] + pic[3]) {
			this.turnCard();
		}
	}

	@Override
	public boolean mouseDragged(final MouseButtonEvent event, final double dx, final double dy) {
		if (!this.pressedOnMap) {
			return false;
		}
		if (Math.abs(event.x() - this.pressX) + Math.abs(event.y() - this.pressY) > 3.0) {
			this.dragged = true;
		}
		this.sinking = false;
		this.target = this.clampScroll(this.target - (float) (dy / this.scale()));
		this.scroll = this.target;
		return true;
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		if (this.pressedOnMap && !this.dragged && this.selected != null) {
			this.close();
		}
		this.pressedOnMap = false;
		return true;
	}

	@Override
	public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
		this.sinking = false;
		this.target = this.clampScroll(this.target - (float) scrollY * 36.0F);
		return true;
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		int key = event.key();
		if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT) {
			this.step(1);
			return true;
		}
		if (key == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT) {
			this.step(-1);
			return true;
		}
		if (key == com.mojang.blaze3d.platform.InputConstants.KEY_UP || key == com.mojang.blaze3d.platform.InputConstants.KEY_DOWN) {
			this.sinking = false;
			int sign = key == com.mojang.blaze3d.platform.InputConstants.KEY_UP ? -1 : 1;
			this.target = this.clampScroll(this.target + sign * 48.0F);
			return true;
		}
		return super.keyPressed(event);
	}

	private WorldMap.@Nullable Place placeUnder(final double mx, final double my) {
		// the title band lies over the map: what is under it is neither pointed at nor clicked
		if (my < 36) {
			return null;
		}
		WorldMap.Place best = null;
		double bestDistance = 64.0;
		for (WorldMap.Place place : WorldMap.PLACES) {
			double dx = mx - this.sx(place.x());
			double dy = my - this.sy(place.y());
			double distance = dx * dx + dy * dy;
			boolean inBox = mx >= this.sx(place.x0()) && mx < this.sx(place.x1()) && my >= this.sy(place.y0()) && my < this.sy(place.y1());
			if (inBox) {
				distance = Math.min(distance, 32.0);
			}
			if (distance < bestDistance) {
				bestDistance = distance;
				best = place;
			}
		}
		return best;
	}

	// ── The rail: where in the world the view is ─────────────────────

	private int railTop() {
		return 44;
	}

	private int railBottom() {
		return Math.max(this.railTop() + 60, this.height - 24);
	}

	private int railY(final float mapY) {
		return this.railTop() + Math.round(mapY / WorldMap.H * (this.railBottom() - this.railTop()));
	}

	private @Nullable String railUnder(final double mx, final double my) {
		if (mx > RAIL_X + 12) {
			return null;
		}
		for (WorldMap.Band band : WorldMap.BANDS) {
			int y = this.railY(band.middle());
			if (Math.abs(my - y) <= 6) {
				return band.id();
			}
		}
		return null;
	}

	private Component revealLabel() {
		return Component.translatable(SeenStory.revealAll() ? "worldmap.mistinn.reveal.on" : "worldmap.mistinn.reveal.off");
	}

	/** The switch between the places walked and every place, in the top right corner. */
	private boolean overReveal(final double mx, final double my) {
		int w = this.font.width(this.revealLabel());
		return my >= 6 && my < 20 && mx >= this.width - w - 12;
	}

	// ── Drawing ────────────────────────────────────────────────────

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		g.fill(0, 0, this.width, this.height, 0xFF000000);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		long now = System.nanoTime();
		float dt = Math.min(0.1F, (now - this.last) / 1.0E9F);
		this.last = now;
		float time = this.seconds();
		if (this.sinking) {
			float p = Motion.outCubic(Motion.progress(time, SINK_AT, SINK_FOR));
			this.target = Mth.lerp(p * p * (3.0F - 2.0F * p), this.clampScroll(WorldMap.OPEN_AT), this.coast());
			this.scroll = this.target;
		} else {
			this.scroll = Motion.approach(this.scroll, this.target, dt, 10.0F);
		}
		this.sheet = Motion.approach(this.sheet, this.selected == null ? 0.0F : 1.0F, dt, 9.0F);

		this.extractBackground(g, mouseX, mouseY, a);
		g.blit(RenderPipelines.GUI_TEXTURED, MAP, 0, 0, 0.0F, this.scroll, this.width, this.height, WorldMap.W, Math.round(this.viewHeight()),
			WorldMap.W, WorldMap.H);
		// dark at the top and bottom, so the words over the map read
		g.fillGradient(0, 0, this.width, 40, 0xC0080A0E, 0x00080A0E);
		g.fillGradient(0, this.height - 34, this.width, this.height, 0x00080A0E, 0xC0080A0E);

		WorldMap.Place hover = this.selected != null && mouseY >= this.sheetTop() ? null : this.placeUnder(mouseX, mouseY);
		this.taken.clear();
		for (WorldMap.Place place : WorldMap.PLACES) {
			if (!shown(place)) {
				this.drawVeil(g, place, time);
			}
		}
		this.drawRoute(g, Motion.progress(time, TRACE_AT, TRACE_FOR));
		this.reserveChrome();
		if (this.sheet > 0.01F) {
			this.take(0, this.sheetTop() - 2, this.width, this.height);
		}
		for (WorldMap.Place place : WorldMap.PLACES) {
			this.drawMarker(g, place, place == hover, time);
		}
		// names: the one under the pointer and the open stop first, then the stops in order, then the landmarks
		List<WorldMap.Place> order = new ArrayList<>();
		for (WorldMap.Place place : WorldMap.PLACES) {
			if (place == hover || this.open(place)) {
				order.add(place);
			}
		}
		for (WorldMap.Place place : WorldMap.PLACES) {
			if (!order.contains(place)) {
				order.add(place);
			}
		}
		for (WorldMap.Place place : order) {
			this.drawLabel(g, place, place == hover);
		}
		// the title, the rail and the footer over the map's marks
		this.drawTitle(g);
		this.drawRail(g, mouseX, mouseY);
		this.drawFooter(g, mouseX, mouseY);
		if (this.sheet > 0.01F && this.selected != null) {
			this.drawSheet(g, this.selected, mouseX, mouseY);
		}

		float fade = 1.0F - Math.min(1.0F, time / FADE_FOR);
		if (fade > 0.0F) {
			g.fill(0, 0, this.width, this.height, (int) (fade * fade * 255) << 24);
		}
	}

	/** The way the story walks, drawn as far as {@code progress} (0 to 1) along it: dots of salt. */
	private void drawRoute(final GuiGraphicsExtractor g, final float progress) {
		List<WorldMap.Point> points = visibleRoute();
		if (points.size() < 2 || progress <= 0.0F) {
			return;
		}
		// the fall: straight down from the crane through the water, then a curve onto where the hunter wakes
		List<float[]> path = new ArrayList<>();
		WorldMap.Point first = points.get(0);
		WorldMap.Point second = points.get(1);
		path.add(new float[] {first.x(), first.y()});
		path.add(new float[] {first.x(), WorldMap.UNDERSIDE});
		for (int i = 1; i <= 24; i++) {
			float t = i / 24.0F;
			float cx = first.x();
			float cy = second.y();
			float x = (1 - t) * (1 - t) * first.x() + 2 * (1 - t) * t * cx + t * t * second.x();
			float y = (1 - t) * (1 - t) * WorldMap.UNDERSIDE + 2 * (1 - t) * t * cy + t * t * second.y();
			path.add(new float[] {x, y});
		}
		for (int i = 2; i < points.size(); i++) {
			path.add(new float[] {points.get(i).x(), points.get(i).y()});
		}
		float total = 0.0F;
		for (int i = 1; i < path.size(); i++) {
			total += dist(path.get(i - 1), path.get(i));
		}
		float shownLength = total * Mth.clamp(progress, 0.0F, 1.0F);
		float spacing = 4.0F / this.scale();
		float walked = 0.0F;
		float next = 0.0F;
		int alpha = 0xB0;
		for (int i = 1; i < path.size(); i++) {
			float[] p0 = path.get(i - 1);
			float[] p1 = path.get(i);
			float length = dist(p0, p1);
			while (next <= walked + length && next <= shownLength) {
				float t = length <= 0.0F ? 0.0F : (next - walked) / length;
				int x = Math.round(this.sx(Mth.lerp(t, p0[0], p1[0])));
				int y = Math.round(this.sy(Mth.lerp(t, p0[1], p1[1])));
				if (y > -2 && y < this.height + 2) {
					g.fill(x, y, x + 1, y + 1, alpha << 24 | SALT);
				}
				next += spacing;
			}
			walked += length;
		}
	}

	private static float dist(final float[] a, final float[] b) {
		float dx = b[0] - a[0];
		float dy = b[1] - a[1];
		return (float) Math.sqrt(dx * dx + dy * dy);
	}

	/** Shapes on the screen that a place's name must not cover, gathered each frame as things are drawn. */
	private final List<int[]> taken = new ArrayList<>();

	private boolean free(final int x0, final int y0, final int x1, final int y1) {
		if (x0 < 2 || y0 < 2 || x1 > this.width - 2 || y1 > this.height - 2) {
			return false;
		}
		for (int[] r : this.taken) {
			if (x0 < r[2] && x1 > r[0] && y0 < r[3] && y1 > r[1]) {
				return false;
			}
		}
		return true;
	}

	private void take(final int x0, final int y0, final int x1, final int y1) {
		this.taken.add(new int[] {x0, y0, x1, y1});
	}

	/** In the fog: a veil over what is drawn there. */
	private void drawVeil(final GuiGraphicsExtractor g, final WorldMap.Place place, final float time) {
		float scale = this.scale();
		int w = Math.round((place.x1() - place.x0() + 14) * scale);
		int h = Math.round((place.y1() - place.y0() + 10) * scale);
		int vx = Math.round(this.sx((place.x0() + place.x1()) / 2.0F)) - w / 2;
		int vy = Math.round(this.sy((place.y0() + place.y1()) / 2.0F)) - h / 2;
		if (vy > this.height || vy + h < 0) {
			return;
		}
		float drift = Mth.sin(time * 0.7F + place.x()) * 1.5F;
		g.blit(RenderPipelines.GUI_TEXTURED, VEIL, vx + Math.round(drift), vy, 0.0F, 0.0F, w, h, VEIL_W, VEIL_H, VEIL_W, VEIL_H, 0xF0FFFFFF);
	}

	private void drawMarker(final GuiGraphicsExtractor g, final WorldMap.Place place, final boolean hot, final float time) {
		int x = Math.round(this.sx(place.x()));
		int y = Math.round(this.sy(place.y()));
		if (y < -10 || y > this.height + 10) {
			return;
		}
		boolean open = this.open(place);
		this.take(x - 5, y - 5, x + 6, y + 6);
		if (!shown(place)) {
			if (place.kind() == WorldMap.Kind.STOP && place.label()) {
				diamond(g, x, y, 3, 0xFF4A4741);
				g.text(this.font, "?", x - 2, y - 13, hot ? GREY : DIM, true);
				this.take(x - 3, y - 14, x + 4, y - 4);
			}
			return;
		}
		if (place.kind() == WorldMap.Kind.LANDMARK) {
			g.fill(x - 2, y - 2, x + 2, y + 2, 0xFF141418);
			g.fill(x - 1, y - 1, x + 1, y + 1, hot ? GOLD : 0xFF8A857C);
			return;
		}
		if (open) {
			float pulse = 0.5F + 0.5F * Mth.sin(time * 4.0F);
			ring(g, x, y, 8 + Math.round(pulse), (int) (160 + 80 * pulse) << 24 | ACCENT);
		}
		int r = hot || open ? 4 : 3;
		diamond(g, x, y, r + 1, 0xFF141418);
		diamond(g, x, y, r, hot || open ? GOLD : 0xFF000000 | SALT);
	}

	private boolean open(final WorldMap.Place place) {
		return this.selected != null && this.selected.id().equals(place.of());
	}

	/**
	 * A place's name beside its marker, after its chapter's mark: to the right if there is room, else to the left,
	 * below or above; where none is free, the chapter's mark alone, or nothing.
	 */
	private void drawLabel(final GuiGraphicsExtractor g, final WorldMap.Place place, final boolean hot) {
		int x = Math.round(this.sx(place.x()));
		int y = Math.round(this.sy(place.y()));
		if (y < -10 || y > this.height + 10) {
			return;
		}
		boolean shown = shown(place);
		if (!shown && !hot || !place.label()) {
			return;
		}
		boolean open = this.open(place);
		Component name = Component.translatable(shown ? place.key() : "worldmap.mistinn.hidden");
		Component chip = shown && place.kind() == WorldMap.Kind.STOP ? chip(place.chapter()) : null;
		int chipW = chip == null ? 0 : this.font.width(chip) + 5;
		int w = chipW + this.font.width(name);
		// beside the marker first; then beside the middle of the drawing, for a marker under the title
		int cy = Math.round(this.sy((place.y0() + place.y1()) / 2.0F));
		int right = Math.round(this.sx(place.x1()));
		int left = Math.round(this.sx(place.x0()));
		int[][] spots = {{x + 8, y - 4}, {x - 8 - w, y - 4}, {x - w / 2, y + 8}, {x - w / 2, y - 17}, {right + 4, cy - 4}, {left - 4 - w, cy - 4}};
		int[] chosen = null;
		for (int[] at : spots) {
			if (this.free(at[0] - 1, at[1] - 1, at[0] + w + 1, at[1] + 10)) {
				chosen = at;
				break;
			}
		}
		// the name under the pointer or of the open stop is always shown, over others if it must
		if (chosen == null && (hot || open)) {
			chosen = spots[4][0] + w < this.width - 2 ? spots[4] : spots[5];
		}
		if (chosen != null) {
			int lx = chosen[0];
			int ly = chosen[1];
			this.take(lx - 1, ly - 1, lx + w + 1, ly + 10);
			if (chip != null) {
				g.fill(lx - 1, ly - 1, lx + chipW - 2, ly + 9, 0xD20E1216);
				g.text(this.font, chip, lx + 1, ly, 0xFF000000 | ACCENT, false);
			}
			int colour = hot || open ? GOLD : shown && place.kind() == WorldMap.Kind.STOP ? TEXT : GREY;
			if (hot && !open) {
				g.fill(lx + chipW - 2, ly - 1, lx + w + 1, ly + 9, 0xA00E1216);
			}
			g.text(this.font, name, lx + chipW, ly, colour, true);
			return;
		}
		if (chip != null && this.free(x + 7, y - 5, x + 8 + chipW, y + 9)) {
			this.take(x + 7, y - 5, x + 8 + chipW, y + 9);
			g.fill(x + 7, y - 5, x + 6 + chipW, y + 5, 0xD20E1216);
			g.text(this.font, chip, x + 9, y - 4, 0xFF000000 | ACCENT, false);
		}
	}

	private static @Nullable Component chip(final int chapter) {
		return chapter < -1 ? null : Component.translatable("worldmap.mistinn.chip." + (chapter < 0 ? "training" : chapter));
	}

	private static void diamond(final GuiGraphicsExtractor g, final int x, final int y, final int r, final int colour) {
		for (int i = -r; i <= r; i++) {
			int half = r - Math.abs(i);
			g.fill(x - half, y + i, x + half + 1, y + i + 1, colour);
		}
	}

	private static void ring(final GuiGraphicsExtractor g, final int x, final int y, final int r, final int colour) {
		for (int i = 0; i < 48; i++) {
			double angle = i / 48.0 * Math.PI * 2.0;
			int px = x + (int) Math.round(Math.cos(angle) * r);
			int py = y + (int) Math.round(Math.sin(angle) * r);
			g.fill(px, py, px + 1, py + 1, colour);
		}
	}

	/** The layer of the world the middle of the view is in. */
	private WorldMap.Band here() {
		float middle = this.scroll + this.viewHeight() / 2.0F;
		for (WorldMap.Band band : WorldMap.BANDS) {
			if (middle >= band.y0() && middle < band.y1()) {
				return band;
			}
		}
		return WorldMap.BANDS.get(WorldMap.BANDS.size() - 1);
	}

	/** The title, the layer in view beside it, and a line under them; their space is kept clear of names. */
	private void drawTitle(final GuiGraphicsExtractor g) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(10.0F, 7.0F);
		pose.scale(1.5F, 1.5F);
		g.text(this.font, this.title, 0, 0, GOLD, true);
		pose.popMatrix();
		int titleW = Math.round(this.font.width(this.title) * 1.5F);
		Component here = Component.literal("· ").append(Component.translatable(this.here().key()));
		g.text(this.font, here, 10 + titleW + 6, 11, GREY, true);
		Component subtitle = Component.translatable("worldmap.mistinn.subtitle");
		g.text(this.font, subtitle, 10, 24, DIM, true);
	}

	/** Keeps the title's, the rail's, the switch's and the hint's space clear of names. */
	private void reserveChrome() {
		int titleW = Math.round(this.font.width(this.title) * 1.5F);
		Component here = Component.literal("· ").append(Component.translatable(this.here().key()));
		Component subtitle = Component.translatable("worldmap.mistinn.subtitle");
		this.take(0, 0, Math.max(10 + titleW + 12 + this.font.width(here), 14 + this.font.width(subtitle)), 35);
		this.take(0, this.railTop() - 4, RAIL_X + 6, this.railBottom() + 4);
		int w = this.font.width(this.revealLabel());
		this.take(this.width - w - 14, 6, this.width, 22);
		if (this.sheet < 0.5F) {
			this.take(8, this.height - 14, 12 + this.font.width(Component.translatable("worldmap.mistinn.hint")), this.height);
		}
	}

	/** The rail down the left edge: a mark for each layer, the part of the world in view, a layer's name under the pointer. */
	private void drawRail(final GuiGraphicsExtractor g, final int mouseX, final int mouseY) {
		int x = RAIL_X;
		int top = this.railTop();
		int bottom = this.railBottom();
		g.fill(x, top, x + 1, bottom, 0x90968F84);
		int from = this.railY(this.scroll);
		int to = this.railY(this.scroll + this.viewHeight());
		g.fill(x - 1, from, x + 2, Math.max(from + 2, to), 0x90000000 | ACCENT);
		String hover = this.railUnder(mouseX, mouseY);
		WorldMap.Band here = this.here();
		for (WorldMap.Band band : WorldMap.BANDS) {
			int y = this.railY(band.middle());
			boolean lit = band == here || band.id().equals(hover);
			diamond(g, x, y, 2, lit ? GOLD : 0xFF968F84);
			if (band.id().equals(hover)) {
				Component name = Component.translatable(band.key());
				int w = this.font.width(name);
				g.fill(x + 6, y - 6, x + 12 + w, y + 6, 0xD20E1216);
				g.text(this.font, name, x + 9, y - 4, GOLD, true);
			}
		}
	}

	private int[] picture(final int top, final int h) {
		int ph = h - 26;
		int pw = ph * 16 / 9;
		return new int[] {12, top + 9, pw, ph};
	}

	private void drawSheet(final GuiGraphicsExtractor g, final WorldMap.Place place, final int mouseX, final int mouseY) {
		int top = this.sheetTop();
		int h = this.sheetHeight();
		g.fill(0, top, this.width, this.height, 0xEC0C0E14);
		g.fill(0, top, this.width, top + 1, 0xFF000000 | ACCENT);

		int[] pic = this.picture(top, h);
		List<String> cards = cards(place);
		StoryScript.Card shownCard = cards.isEmpty() ? null : StoryScript.card(cards.get(Math.min(this.card, cards.size() - 1)));
		g.fill(pic[0] - 1, pic[1] - 1, pic[0] + pic[2] + 1, pic[1] + pic[3] + 1, 0xFF1C1E24);
		if (shownCard != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, Mistinn.id(shownCard.image()), pic[0], pic[1], 0.0F, 0.0F, pic[2], pic[3], CARD_W, CARD_H, CARD_W, CARD_H);
		} else {
			// no card for this place (or none seen yet): the map itself, close up
			int cw = 160;
			int ch = 90;
			int cx = Mth.clamp((place.x0() + place.x1()) / 2 - cw / 2, 0, WorldMap.W - cw);
			int cy = Mth.clamp((place.y0() + place.y1()) / 2 - ch / 2, 0, WorldMap.H - ch);
			g.blit(RenderPipelines.GUI_TEXTURED, MAP, pic[0], pic[1], cx, cy, pic[2], pic[3], cw, ch, WorldMap.W, WorldMap.H);
		}
		if (cards.size() > 1) {
			int dots = cards.size();
			int dx = pic[0] + pic[2] / 2 - dots * 4;
			for (int i = 0; i < dots; i++) {
				g.fill(dx + i * 8, pic[1] + pic[3] + 5, dx + i * 8 + 4, pic[1] + pic[3] + 9, i == this.card ? 0xFF000000 | ACCENT : 0xFF4A4741);
			}
		}

		int tx = pic[0] + pic[2] + 14;
		int tw = this.width - tx - 12;
		int y = top + 9;
		Component chapter = place.kind() == WorldMap.Kind.STOP
			? Component.translatable(place.chapter() < 0 ? "worldmap.mistinn.chapter.training" : "story.mistinn.chapter." + place.chapter())
			: Component.translatable(WorldMap.bandOf(place).key());
		g.text(this.font, chapter, tx, y, GOLD, true);
		y += 12;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(tx, y);
		pose.scale(1.5F, 1.5F);
		g.text(this.font, Component.translatable(place.key()), 0, 0, TEXT, true);
		pose.popMatrix();
		y += 18;
		int row = this.height - 13;
		String steps = steps(place);
		int bottom = steps == null ? row - 4 : row - 15;
		for (Component line : this.lines(place, shownCard)) {
			for (FormattedCharSequence wrapped : this.font.split(line, tw)) {
				if (y + 9 > bottom) {
					break;
				}
				g.text(this.font, wrapped, tx, y, TEXT, true);
				y += 11;
			}
		}
		if (steps != null) {
			g.text(this.font, fit(this.font, steps, tw), tx, row - 13, DIM, false);
		}
		if (place.kind() == WorldMap.Kind.STOP) {
			Component prev = Component.translatable("worldmap.mistinn.prev");
			Component next = Component.translatable("worldmap.mistinn.next");
			boolean low = mouseY >= row - 3;
			int nx = this.width - 16 - this.font.width(next);
			int px = this.width - 84 - this.font.width(prev);
			g.text(this.font, prev, px, row, low && mouseX >= this.width - 150 && mouseX < this.width - 76 ? GOLD : GREY, true);
			g.text(this.font, next, nx, row, low && mouseX >= this.width - 76 ? GOLD : GREY, true);
		}
	}

	/** The text cut to the width, with an ellipsis if anything was cut. */
	private static String fit(final net.minecraft.client.gui.Font font, final String text, final int width) {
		if (font.width(text) <= width) {
			return text;
		}
		return font.plainSubstrByWidth(text, width - font.width("…")) + "…";
	}

	/** The words under a place's name: its card's lines, or a few words about it. */
	private List<Component> lines(final WorldMap.Place place, final StoryScript.@Nullable Card card) {
		List<Component> lines = new ArrayList<>();
		if (card != null) {
			for (int i = 0; i < card.lines(); i++) {
				lines.add(Component.translatable(card.line(i)));
			}
			return lines;
		}
		for (int i = 0; Language.getInstance().has(place.about(i)); i++) {
			lines.add(Component.translatable(place.about(i)));
		}
		return lines;
	}

	/** What the stop's chapter asks of the hunter, in order. */
	private static @Nullable String steps(final WorldMap.Place place) {
		if (place.kind() != WorldMap.Kind.STOP || place.chapter() < 0) {
			return null;
		}
		StringBuilder text = new StringBuilder();
		for (StoryStep step : StoryStep.values()) {
			if (step.chapter != place.chapter() || step == StoryStep.WAIT || step == StoryStep.ENDED) {
				continue;
			}
			if (!text.isEmpty()) {
				text.append(" → ");
			}
			text.append(Component.translatable(step.key()).getString());
		}
		return text.toString();
	}

	/** The hint along the bottom while no stop is open, and the switch in the top right corner. */
	private void drawFooter(final GuiGraphicsExtractor g, final int mouseX, final int mouseY) {
		if (this.sheet < 0.5F) {
			Component hint = Component.translatable("worldmap.mistinn.hint");
			g.text(this.font, hint, 10, this.height - 12, DIM, false);
		}
		Component reveal = this.revealLabel();
		int w = this.font.width(reveal);
		g.text(this.font, reveal, this.width - w - 10, 10, this.overReveal(mouseX, mouseY) ? GOLD : GREY, true);
	}

	private static void click(final net.minecraft.sounds.SoundEvent sound) {
		net.minecraft.client.Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 0.8F));
	}
}
