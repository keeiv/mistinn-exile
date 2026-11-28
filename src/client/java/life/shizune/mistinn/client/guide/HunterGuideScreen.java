package life.shizune.mistinn.client.guide;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.skill.SkillTree;
import life.shizune.mistinn.skill.Skills;
import life.shizune.mistinn.skill.Stat;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * 追獵者手冊: an open field manual with seven bookmarks. The guide's pages read as spreads; the
 * stats page shows what each stat does for the hunter now and what they carry to spend; the
 * skills page is the tree, where a node is bought with a second click; the crafting pages show
 * the recipes; the supply page is the anchor station's list, ordered from the same way; the
 * bestiary keeps what the hunter knows of what they hunt; the achievements page lists what the hunter has done, and what is still to do.
 */
public class HunterGuideScreen extends Screen {
	/** The guide's pages, by the bookmark that opens each. */
	public enum Tab {
		PAGES("gui.mistinn.guide.tab.pages"),
		STATS("gui.mistinn.guide.tab.stats"),
		SKILLS("gui.mistinn.guide.tab.skills"),
		RECIPES("gui.mistinn.guide.tab.recipes"),
		SUPPLY("gui.mistinn.guide.tab.supply"),
		BESTIARY("gui.mistinn.guide.tab.bestiary"),
		ACHIEVEMENTS("gui.mistinn.guide.tab.achievements");

		final String key;

		Tab(final String key) {
			this.key = key;
		}

		/** The bookmark's label. */
		public String key() {
			return this.key;
		}
	}

	public static final int W = 320;
	/** How wide a page's text runs, and how many rows of it fit above the page number. */
	public static final int PAGE_WIDTH = 128;
	public static final int PAGE_ROWS = 16;
	public static final int H = 200;
	/** How wide a bookmark is: seven of them, just overlapping, run along the top edge. */
	public static final int TAB_W = 46;
	/** The bookmark texture is wider than a bookmark: it is drawn as its two ends. */
	private static final int TABS_TEX_W = 64;
	private static final int TAB_H = 20;
	private static final int MAJOR = 32;
	private static final int MINOR = 24;
	static final int INK = 0xFF3B2A1E;
	static final int FADED = 0xFF7A6650;
	static final int RED = 0xFF8E2A1E;
	private static final int AMBER = 0xFFB8822E;
	/** A stat the Rust Anchor has blessed: rust, past the cap. */
	private static final int BLESSED = 0xFF9A4A1C;
	static final int LINE_DARK = 0xFF6E5A44;

	private static final Identifier SPREAD = tex("spread");
	private static final Identifier TABS = tex("tabs");
	private static final Identifier NODE_MAJOR = tex("node_major");
	private static final Identifier NODE_MINOR = tex("node_minor");
	private static final Identifier ICONS = tex("icons");
	static final Identifier ARROWS = tex("arrows");
	private static final Identifier GLOW = tex("glow");

	/** The answer the server last gave to an unlock request, for the line under the tree. */
	private static @Nullable Component notice;
	private static long noticeAt;
	private static @Nullable String glowNode;
	private static long glowAt;

	private Tab tab = Tab.PAGES;
	private int spread;
	/** Whether the pages' contents are open instead of a spread. */
	private boolean contents;
	/** Which spread of the contents is open, when they run over more than one. */
	private int contentsSpread;
	/** The crafting pages' open spread: the contents and the first recipe, then two recipes at a time. */
	private int recipeSpread;
	private @Nullable String armed;

	public HunterGuideScreen() {
		super(Component.translatable("item.mistinn.hunter_guide"));
	}

	private static Identifier tex(final String name) {
		return Mistinn.id("textures/gui/guide/" + name + ".png");
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		super.init();
		// 史詩的行囊: in the epic once written, to seal it; elsewhere, while a pack waits, to take it out
		life.shizune.mistinn.epic.EpicState epic = life.shizune.mistinn.client.epic.EpicClient.state(this.minecraft);
		boolean seal = epic != null && epic.at() == life.shizune.mistinn.epic.EpicStep.ENDED;
		if (seal || epic == null && !life.shizune.mistinn.client.epic.Legacy.pack().isEmpty()) {
			this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("epic.mistinn.legacy.title"),
				b -> this.minecraft.gui.setScreen(new life.shizune.mistinn.client.epic.LegacyScreen(this)))
				.bounds(this.width / 2 - 60, this.packButtonY(), 120, 18).build());
		}
	}

	public Tab tab() {
		return this.tab;
	}

	/** Which two pages are open (0 for pages 1 and 2). */
	public int spread() {
		return this.spread;
	}

	/** Whether the pages' contents are open. */
	public boolean contentsOpen() {
		return this.contents;
	}

	/** Centre of the link that opens the contents, in GUI pixels. */
	public double[] contentsLink() {
		int[] at = GuideContents.link(this.font, this.left(), this.top());
		return new double[] {at[0] + at[2] / 2.0, at[1] + at[3] / 2.0};
	}

	/** Which spread of the contents is open (0: the first). */
	public int contentsSpread() {
		return this.contentsSpread;
	}

	/** Centre of the previous-page arrow, in GUI pixels. */
	public double[] previousPage() {
		return new double[] {this.left() + 16 + 9.0, this.top() + 176 + 6.0};
	}

	/** Centre of the contents' line for the chapter starting on this page, in GUI pixels. */
	public double[] contentsLine(final int page) {
		return GuideContents.line(this.left(), this.top(), GuideContents.CHAPTERS.indexOf(page));
	}

	/** Which crafting spread is open (0: the contents and the first recipe). */
	public int recipeSpread() {
		return this.recipeSpread;
	}

	/** Centre of a chapter's line on the crafting contents page, in GUI pixels. */
	public double[] chapterLine(final GuideRecipes.Chapter chapter) {
		return GuideRecipePages.chapterLine(this.left(), this.top(), chapter);
	}

	private boolean recipesClicked(final double mx, final double my) {
		int turned = GuideRecipePages.clicked(this.left(), this.top(), this.recipeSpread, mx, my);
		if (turned < 0) {
			return false;
		}
		this.recipeSpread = turned;
		click(SoundEvents.BOOK_PAGE_TURN);
		return true;
	}

	net.minecraft.client.gui.Font guideFont() {
		return this.font;
	}

	/** The bestiary's open list page. */
	public int bestiaryPage() {
		return GuideBestiaryPage.page();
	}

	/** The creature open on the bestiary's right page, or -1. */
	public int bestiarySelected() {
		return GuideBestiaryPage.selected();
	}

	/** Opens a creature's page in the bestiary (and the list page it is on). */
	public void selectBestiary(final int index) {
		GuideBestiaryPage.select(index);
	}

	/** Centre of a creature's line in the bestiary's list, in GUI pixels. */
	public double[] bestiaryRow(final int index) {
		return GuideBestiaryPage.rowCentre(this.left(), this.top(), index);
	}

	/** Centre of the next-page arrow, in GUI pixels. */
	public double[] nextPage() {
		return new double[] {this.left() + W - 34 + 9.0, this.top() + 176 + 6.0};
	}

	/** Below the book where the window has room for it; on a short window, over the spine at the foot of the spread. */
	private int packButtonY() {
		int below = this.top() + H + 6;
		return below + 18 <= this.height - 2 ? below : this.top() + H - 26;
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return (this.height - H) / 2 + TAB_H / 2;
	}

	// ── Where things are (also used by the tests) ─────────────────

	/** Centre of a tab, in GUI pixels. */
	public double[] tabCentre(final Tab tab) {
		// seven bookmarks along the top edge, each just overlapping the next
		return new double[] {this.left() + 2 + tab.ordinal() * (TAB_W - 1) + TAB_W / 2.0, this.top() - TAB_H / 2.0};
	}

	/** Top-left of a node's frame on the skills page, relative to the spread. */
	private static int[] nodeAt(final SkillTree.Node node) {
		if (node.major()) {
			return node.stat() == null ? new int[] {20, 84} : reservedAt(node.stat().ordinal());
		}
		int row = node.stat().ordinal();
		int col = Integer.parseInt(node.id().substring(node.id().lastIndexOf('_') + 1)) - 1;
		int x = col < 3 ? 66 + col * 30 : 176 + (col - 3) * 30;
		return new int[] {x, 22 + row * 42 + (MAJOR - MINOR) / 2};
	}

	/** The major node closing a stat's row, or null while that place is still kept for a later one. */
	private static SkillTree.@Nullable Node closing(final Stat stat) {
		for (SkillTree.Node node : SkillTree.NODES) {
			if (node.major() && node.stat() == stat) {
				return node;
			}
		}
		return null;
	}

	/** Top-left of the locked major node that closes each row (a later keeper's skill). */
	private static int[] reservedAt(final int row) {
		return new int[] {250, 22 + row * 42};
	}

	/** Centre of an offer's row on the supply page, in GUI pixels. */
	public double[] offerCentre(final int index) {
		return GuideSupplyPage.rowCentre(this.left(), this.top(), index);
	}

	/** The anchor station answered an order. */
	public static void supplied(final life.shizune.mistinn.supply.SupplyService.OrderResultPayload payload) {
		GuideSupplyPage.answered(payload);
	}

	/** Centre of a node on the skills page, in GUI pixels. */
	public double[] nodeCentre(final String id) {
		SkillTree.Node node = SkillTree.node(id);
		int[] at = nodeAt(node);
		int size = node.major() ? MAJOR : MINOR;
		return new double[] {this.left() + at[0] + size / 2.0, this.top() + at[1] + size / 2.0};
	}

	// ── Input ─────────────────────────────────────────────────────

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		// a button laid over the book (the epic's pack, on a short window) answers before the page under it
		if (this.children().stream().anyMatch(child -> child.isMouseOver(mx, my))) {
			return super.mouseClicked(event, doubleClick);
		}
		for (Tab each : Tab.values()) {
			double[] c = this.tabCentre(each);
			if (Math.abs(mx - c[0]) <= TAB_W / 2.0 && Math.abs(my - c[1]) <= TAB_H / 2.0) {
				if (each == Tab.ACHIEVEMENTS) {
					// achievements are the game's own advancements: the bookmark opens them, on the hunter's tab
					click(SoundEvents.BOOK_PAGE_TURN);
					life.shizune.mistinn.client.achievement.ClientAchievements.open(this.minecraft, this);
					return true;
				}
				if (this.tab != each) {
					this.tab = each;
					this.armed = null;
					GuideSupplyPage.disarm();
					click(SoundEvents.BOOK_PAGE_TURN);
				}
				return true;
			}
		}
		if (this.tab == Tab.RECIPES && this.recipesClicked(mx, my)) {
			return true;
		}
		if (this.tab == Tab.SUPPLY && GuideSupplyPage.clicked(this.left(), this.top(), mx, my)) {
			return true;
		}
		if (this.tab == Tab.BESTIARY && GuideBestiaryPage.clicked(this.left(), this.top(), mx, my)) {
			return true;
		}
		if (this.tab == Tab.PAGES) {
			int x = this.left();
			int y = this.top();
			int[] link = GuideContents.link(this.font, x, y);
			if (inside(mx, my, link[0], link[1], link[2], link[3])) {
				this.contents = !this.contents;
				if (this.contents) {
					// open on the spread that lists the chapter being read
					this.contentsSpread = GuideContents.spreadOf(GuideContents.chapterOf(this.spread * 2 + 1));
				}
				click(SoundEvents.BOOK_PAGE_TURN);
				return true;
			}
			if (this.contents) {
				if (this.contentsSpread > 0 && inside(mx, my, x + 16, y + 176, 18, 12)) {
					this.contentsSpread--;
					click(SoundEvents.BOOK_PAGE_TURN);
					return true;
				}
				if (this.contentsSpread < GuideContents.spreads() - 1 && inside(mx, my, x + W - 34, y + 176, 18, 12)) {
					this.contentsSpread++;
					click(SoundEvents.BOOK_PAGE_TURN);
					return true;
				}
				int chapter = GuideContents.at(x, y, this.contentsSpread, mx, my);
				if (chapter >= 0 && GuideContents.CHAPTERS.get(chapter) <= MistinnItems.GUIDE_PAGES) {
					this.spread = (GuideContents.CHAPTERS.get(chapter) - 1) / 2;
					this.contents = false;
					click(SoundEvents.BOOK_PAGE_TURN);
				}
				return true;
			}
			if (this.spread > 0 && inside(mx, my, x + 16, y + 176, 18, 12)) {
				this.spread--;
				click(SoundEvents.BOOK_PAGE_TURN);
				return true;
			}
			if (this.spread < spreads() - 1 && inside(mx, my, x + W - 34, y + 176, 18, 12)) {
				this.spread++;
				click(SoundEvents.BOOK_PAGE_TURN);
				return true;
			}
		}
		if (this.tab == Tab.SKILLS) {
			SkillTree.Node node = this.nodeUnder(mx, my);
			if (node != null) {
				Skills skills = SkillService.get(this.minecraft.player);
				if (skills.has(node.id())) {
					this.armed = null;
				} else if (node.id().equals(this.armed)) {
					// the second click buys it
					ClientPlayNetworking.send(new SkillService.UnlockPayload(node.id()));
					this.armed = null;
				} else {
					this.armed = node.id();
					click(SoundEvents.UI_BUTTON_CLICK.value());
				}
				return true;
			}
			this.armed = null;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** A page turned or a line chosen, for the pages kept in their own classes. */
	static void sound(final net.minecraft.sounds.SoundEvent sound) {
		click(sound);
	}

	private static void click(final net.minecraft.sounds.SoundEvent sound) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 0.8F));
	}

	static boolean inside(final double mx, final double my, final int x, final int y, final int w, final int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private SkillTree.@Nullable Node nodeUnder(final double mx, final double my) {
		for (SkillTree.Node node : SkillTree.NODES) {
			int[] at = nodeAt(node);
			int size = node.major() ? MAJOR : MINOR;
			if (inside(mx, my, this.left() + at[0], this.top() + at[1], size, size)) {
				return node;
			}
		}
		return null;
	}

	/** The server answered an unlock request. */
	public static void answered(final SkillService.UnlockResultPayload payload) {
		SkillService.Result result = SkillService.Result.byId(payload.result());
		noticeAt = System.currentTimeMillis();
		if (result == SkillService.Result.UNLOCKED) {
			glowNode = payload.id();
			glowAt = noticeAt;
			notice = Component.translatable("gui.mistinn.guide.unlocked", Component.translatable(nodeKey(payload.id())));
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(MistinnSounds.SKILL_UNLOCK, 1.0F, 0.9F));
		} else {
			notice = Component.translatable("gui.mistinn.guide.result." + result.name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	// ── Drawing ───────────────────────────────────────────────────

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int x = this.left();
		int y = this.top();
		for (Tab each : Tab.values()) {
			double[] c = this.tabCentre(each);
			int tx = (int) (c[0] - TAB_W / 2.0);
			int ty = (int) (c[1] - TAB_H / 2.0);
			boolean hover = inside(mouseX, mouseY, tx, ty, TAB_W, TAB_H);
			int v = each == this.tab ? 2 * TAB_H : hover ? TAB_H : 0;
			int half = TAB_W / 2;
			g.blit(RenderPipelines.GUI_TEXTURED, TABS, tx, ty, 0.0F, v, half, TAB_H, TABS_TEX_W, TAB_H * 3);
			g.blit(RenderPipelines.GUI_TEXTURED, TABS, tx + half, ty, TABS_TEX_W - (TAB_W - half), v, TAB_W - half, TAB_H, TABS_TEX_W, TAB_H * 3);
			Component label = Component.translatable(each.key);
			g.text(this.font, label, tx + (TAB_W - this.font.width(label)) / 2, ty + 7, each == this.tab ? INK : 0xFFE8DCC4, false);
		}
		g.blit(RenderPipelines.GUI_TEXTURED, SPREAD, x, y, 0.0F, 0.0F, W, H, W, H);
		switch (this.tab) {
			case PAGES -> this.pages(g, x, y, mouseX, mouseY);
			case STATS -> this.stats(g, x, y);
			case SKILLS -> this.skills(g, x, y, mouseX, mouseY);
			case RECIPES -> GuideRecipePages.draw(this, g, x, y, mouseX, mouseY);
			case SUPPLY -> GuideSupplyPage.draw(this, g, x, y, mouseX, mouseY);
			case BESTIARY -> GuideBestiaryPage.draw(this.font, g, x, y, mouseX, mouseY);
			case ACHIEVEMENTS -> {
			}
		}
	}

	private static int spreads() {
		return (MistinnItems.GUIDE_PAGES + 1) / 2;
	}

	private void pages(final GuiGraphicsExtractor g, final int x, final int y, final int mouseX, final int mouseY) {
		if (this.contents) {
			GuideContents.draw(g, this.font, x, y, this.contentsSpread, GuideContents.chapterOf(this.spread * 2 + 1), mouseX, mouseY);
			this.arrows(g, x, y, mouseX, mouseY, this.contentsSpread > 0, this.contentsSpread < GuideContents.spreads() - 1);
			GuideContents.drawLink(g, this.font, x, y, mouseX, mouseY);
			return;
		}
		for (int side = 0; side < 2; side++) {
			int page = this.spread * 2 + side + 1;
			if (page > MistinnItems.GUIDE_PAGES) {
				break;
			}
			int px = x + (side == 0 ? 20 : 172);
			int py = y + 18;
			for (FormattedCharSequence row : this.font.split(Component.translatable("item.mistinn.hunter_guide.page." + page), PAGE_WIDTH)) {
				g.text(this.font, row, px, py, INK, false);
				py += 10;
			}
			String number = String.valueOf(page);
			g.text(this.font, number, px + 64 - this.font.width(number) / 2, y + 180, FADED, false);
		}
		this.arrows(g, x, y, mouseX, mouseY, this.spread > 0, this.spread < spreads() - 1);
		GuideContents.drawLink(g, this.font, x, y, mouseX, mouseY);
	}

	/** The page-turning arrows, each only where there is somewhere to turn to. */
	private void arrows(final GuiGraphicsExtractor g, final int x, final int y, final int mouseX, final int mouseY, final boolean back,
		final boolean forward) {
		if (back) {
			boolean hover = inside(mouseX, mouseY, x + 16, y + 176, 18, 12);
			g.blit(RenderPipelines.GUI_TEXTURED, ARROWS, x + 16, y + 176, 0.0F, hover ? 12 : 0, 18, 12, 36, 24);
		}
		if (forward) {
			boolean hover = inside(mouseX, mouseY, x + W - 34, y + 176, 18, 12);
			g.blit(RenderPipelines.GUI_TEXTURED, ARROWS, x + W - 34, y + 176, 18.0F, hover ? 12 : 0, 18, 12, 36, 24);
		}
	}

	private void stats(final GuiGraphicsExtractor g, final int x, final int y) {
		LocalPlayer player = this.minecraft.player;
		int px = x + 20;
		int py = y + 18;
		g.text(this.font, Component.translatable("gui.mistinn.guide.stats"), px, py, INK, false);
		py += 16;
		int rx = x + 172;
		int ry = y + 18;
		for (Stat stat : Stat.values()) {
			// three on the left page, the last at the top of the right one
			if (stat == Stat.INTELLECT) {
				px = rx;
				py = ry;
			}
			int value = SkillService.stat(player, stat);
			icon(g, 1 + stat.ordinal(), px, py);
			g.text(this.font, Component.translatable(stat.key()), px + 20, py + 4, INK, false);
			String number = value + " / " + Stat.CAP;
			// the anchor's blessing shows in its own rust-gold, past the cap if need be
			int ink = life.shizune.mistinn.achievement.ApostleService.blessing(player) > 0 ? BLESSED : INK;
			g.text(this.font, number, px + 128 - this.font.width(number), py + 4, ink, false);
			py += 18;
			for (FormattedCharSequence row : this.font.split(effect(stat, value), 124)) {
				g.text(this.font, row, px + 4, py, FADED, false);
				py += 10;
			}
			py += 4;
		}
		ry = py + 8;
		g.text(this.font, Component.translatable("gui.mistinn.guide.carried"), rx, ry, INK, false);
		ry += 16;
		icon(g, 5, rx, ry);
		g.text(this.font, Component.translatable("gui.mistinn.guide.salt", player.getAttachedOrElse(MistinnAttachments.SALT, 0)), rx + 20, ry + 4, INK, false);
		ry += 20;
		icon(g, 6, rx, ry);
		g.text(this.font, Component.translatable("gui.mistinn.guide.rust_salt", rustSalt(player)), rx + 20, ry + 4, INK, false);
		ry += 28;
		Skills skills = SkillService.get(player);
		int total = (int) SkillTree.NODES.stream().filter(node -> !node.major()).count();
		g.text(this.font, Component.translatable("gui.mistinn.guide.minor_count", skills.minorCount(), total), rx, ry, INK, false);
		ry += 12;
		if (skills.minorCount() < total) {
			g.text(this.font, Component.translatable("gui.mistinn.guide.next_price", SkillService.MINOR_PRICE + SkillService.MINOR_STEP * skills.minorCount()), rx, ry, FADED, false);
		}
	}

	/** What a stat does for the hunter at this value, as one line. */
	public static Component effect(final Stat stat, final int value) {
		return switch (stat) {
			case STRENGTH -> Component.translatable("gui.mistinn.guide.effect.strength", percent(SkillService.damageScale(value) - 1.0));
			case SPEED -> Component.translatable("gui.mistinn.guide.effect.speed", percent(SkillService.moveScale(value) - 1.0),
				percent(SkillService.swingScale(value) - 1.0), percent(SkillService.rollScale(value) - 1.0), SkillService.dodgeBonus(value));
			case CONSTITUTION -> Component.translatable("gui.mistinn.guide.effect.constitution", (int) SkillService.partCeiling(value),
				(int) SkillService.staminaCeiling(value), percent(1.0 - SkillService.woundScale(value)));
			case INTELLECT -> Component.translatable("gui.mistinn.guide.effect.intellect", percent(1.0 - SkillService.treatmentScale(value)),
				percent(SkillService.healScale(value) - 1.0));
		};
	}

	private static String percent(final double share) {
		double p = Math.round(share * 1000.0) / 10.0;
		return p == Math.rint(p) ? String.valueOf((int) p) : String.valueOf(p);
	}

	private static int rustSalt(final LocalPlayer player) {
		int count = player.getInventory().countItem(MistinnItems.RUST_GOD_SALT);
		GridInventory grid = player.getAttachedOrElse(MistinnAttachments.GRID, GridInventory.EMPTY);
		for (Area area : Area.values()) {
			for (Placed placed : grid.items(area)) {
				if (placed.stack().is(MistinnItems.RUST_GOD_SALT)) {
					count += placed.stack().getCount();
				}
			}
		}
		return count;
	}

	private void skills(final GuiGraphicsExtractor g, final int x, final int y, final int mouseX, final int mouseY) {
		Skills skills = SkillService.get(this.minecraft.player);
		long now = System.currentTimeMillis();
		// the lines first, under the nodes: root to each row, along each row, on to the locked major
		int[] root = nodeAt(SkillTree.node(SkillTree.ROOT));
		int rootCy = y + root[1] + MAJOR / 2;
		for (Stat stat : Stat.values()) {
			int rowCy = y + 22 + stat.ordinal() * 42 + MAJOR / 2;
			int spine = x + root[0] + MAJOR + 6;
			int lit = skills.has(SkillTree.ROOT) ? AMBER : LINE_DARK;
			hLine(g, x + root[0] + MAJOR, spine, rootCy, lit);
			vLine(g, spine, Math.min(rootCy, rowCy), Math.max(rootCy, rowCy), lit);
			for (int i = 1; i <= SkillTree.BRANCH; i++) {
				int[] at = nodeAt(SkillTree.node(stat.id + "_" + i));
				int from = i == 1 ? spine : x + nodeAt(SkillTree.node(stat.id + "_" + (i - 1)))[0] + MINOR;
				boolean on = skills.has(stat.id + "_" + i);
				hLine(g, from, x + at[0], rowCy, on ? AMBER : LINE_DARK);
			}
			int[] last = nodeAt(SkillTree.node(stat.id + "_" + SkillTree.BRANCH));
			SkillTree.Node closing = closing(stat);
			hLine(g, x + last[0] + MINOR, x + reservedAt(stat.ordinal())[0], rowCy, closing != null && skills.has(closing.id()) ? AMBER : LINE_DARK);
		}
		SkillTree.Node hovered = null;
		for (SkillTree.Node node : SkillTree.NODES) {
			int[] at = nodeAt(node);
			int size = node.major() ? MAJOR : MINOR;
			int nx = x + at[0];
			int ny = y + at[1];
			boolean has = skills.has(node.id());
			boolean open = node.parent() == null || skills.has(node.parent());
			int state = has ? 2 : open ? 1 : 0;
			if (node.id().equals(this.armed) && (now / 250) % 2 == 0) {
				state = 2;
			}
			g.blit(RenderPipelines.GUI_TEXTURED, node.major() ? NODE_MAJOR : NODE_MINOR, nx, ny, (float) (state * size), 0.0F, size, size, size * 3, size);
			int icon = node.stat() == null ? 0 : 1 + node.stat().ordinal();
			icon(g, icon, nx + (size - 16) / 2, ny + (size - 16) / 2, has || open ? 0xFFFFFFFF : 0xFF707070);
			if (node.id().equals(glowNode) && now - glowAt < 700) {
				float k = 1.0F - (now - glowAt) / 700.0F;
				int alpha = (int) (255 * k);
				g.blit(RenderPipelines.GUI_TEXTURED, GLOW, nx + size / 2 - 24, ny + size / 2 - 24, 0.0F, 0.0F, 48, 48, 48, 48, alpha << 24 | 0xFFFFFF);
			}
			if (inside(mouseX, mouseY, nx, ny, size, size)) {
				hovered = node;
			}
		}
		for (int row = 0; row < Stat.values().length; row++) {
			if (closing(Stat.values()[row]) != null) {
				continue;
			}
			int[] at = reservedAt(row);
			g.blit(RenderPipelines.GUI_TEXTURED, NODE_MAJOR, x + at[0], y + at[1], 0.0F, 0.0F, MAJOR, MAJOR, MAJOR * 3, MAJOR);
			icon(g, 7, x + at[0] + 8, y + at[1] + 8, 0xFF909090);
			if (inside(mouseX, mouseY, x + at[0], y + at[1], MAJOR, MAJOR)) {
				g.setTooltipForNextFrame(this.font, List.of(Component.translatable("gui.mistinn.guide.reserved"),
					Component.translatable("gui.mistinn.guide.reserved.hint").withColor(0xFFA0A0A0)), java.util.Optional.empty(), mouseX, mouseY);
			}
		}
		if (notice != null && now - noticeAt < 4000) {
			g.text(this.font, notice, x + 172, y + 182, INK, false);
		}
		if (hovered != null) {
			g.setTooltipForNextFrame(this.font, this.describe(hovered, skills), java.util.Optional.empty(), mouseX, mouseY);
		}
	}

	/** The node's name, what it gives, its price and whether a second click buys it. */
	private List<Component> describe(final SkillTree.Node node, final Skills skills) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(nodeKey(node.id())).withColor(0xFFE8C070));
		if (node.major()) {
			lines.add(Component.translatable("skill.mistinn." + node.id() + ".desc"));
		} else {
			lines.add(Component.translatable("gui.mistinn.guide.node.minor", Component.translatable(node.stat().key())));
		}
		if (skills.has(node.id())) {
			lines.add(Component.translatable("gui.mistinn.guide.node.learned").withColor(0xFF9ACD70));
			return lines;
		}
		if (node.parent() != null && !skills.has(node.parent())) {
			lines.add(Component.translatable("gui.mistinn.guide.node.locked", Component.translatable(nodeKey(node.parent()))).withColor(0xFFC06050));
			return lines;
		}
		lines.add(node.major()
			? Component.translatable("gui.mistinn.guide.node.price_rust")
			: Component.translatable("gui.mistinn.guide.node.price", SkillService.price(skills, node.id())));
		lines.add(Component.translatable(node.id().equals(this.armed) ? "gui.mistinn.guide.node.confirm" : "gui.mistinn.guide.node.click").withColor(0xFFA0A0A0));
		return lines;
	}

	/** Translation key of a node's name: majors have their own, minors are their stat and a numeral. */
	public static String nodeKey(final String id) {
		SkillTree.Node node = SkillTree.node(id);
		if (node == null || node.major()) {
			return "skill.mistinn." + id;
		}
		return "skill.mistinn.minor." + id;
	}

	private static void icon(final GuiGraphicsExtractor g, final int index, final int x, final int y) {
		icon(g, index, x, y, 0xFFFFFFFF);
	}

	private static void icon(final GuiGraphicsExtractor g, final int index, final int x, final int y, final int tint) {
		g.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y, index * 16.0F, 0.0F, 16, 16, 16, 16, 128, 16, tint);
	}

	private static void hLine(final GuiGraphicsExtractor g, final int x0, final int x1, final int y, final int colour) {
		g.fill(Math.min(x0, x1), y - 1, Math.max(x0, x1), y + 1, colour);
	}

	private static void vLine(final GuiGraphicsExtractor g, final int x, final int y0, final int y1, final int colour) {
		g.fill(x - 1, y0, x + 1, y1 + 1, colour);
	}
}
