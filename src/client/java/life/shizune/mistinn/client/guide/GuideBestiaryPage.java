package life.shizune.mistinn.client.guide;

import java.util.HashMap;
import java.util.Map;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.bestiary.Bestiary;
import life.shizune.mistinn.bestiary.BestiaryService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The guide's bestiary: on the left page, the creatures it keeps a page for, ten at a time (what
 * the hunter has not met is only a "???"); on the right, the one chosen: its name and how many
 * brought down, its picture turning after the pointer, what it is, its weakness and its tell, as
 * much as the hunter has learned.
 */
public final class GuideBestiaryPage {
	/** Creatures listed on the left page at a time. */
	public static final int PER_PAGE = 10;
	/** How wide a creature's name may run beside its count. */
	public static final int NAME_WIDTH = 100;
	/** Rows of words under the picture. */
	public static final int TEXT_ROWS = 9;
	private static final int LIST_TOP = 34;
	private static final int ROW = 13;
	private static final int MODEL_TOP = 30;
	private static final int MODEL_BOTTOM = 80;
	private static final int TEXT_TOP = 84;
	/** Ids the pictured creatures are given, counting down from here. */
	private static final int MODEL_IDS = -4000;

	private static int page;
	private static int selected = -1;
	/** One still creature of each kind to draw, made in the level they are drawn in. */
	private static final Map<EntityType<?>, LivingEntity> MODELS = new HashMap<>();
	private static @Nullable ClientLevel modelsLevel;

	private GuideBestiaryPage() {
	}

	/** A creature's words in the guide: what it is, its weakness, its tell. */
	public static String key(final Bestiary.Entry entry, final String part) {
		return "bestiary.mistinn." + entry.key() + "." + part;
	}

	static int page() {
		return page;
	}

	static int selected() {
		return selected;
	}

	static void select(final int index) {
		selected = index;
		page = index / PER_PAGE;
	}

	private static int pages() {
		return (Bestiary.ENTRIES.size() + PER_PAGE - 1) / PER_PAGE;
	}

	/** Centre of a creature's line on the left page (its list page open), in GUI pixels. */
	static double[] rowCentre(final int left, final int top, final int index) {
		int row = index % PER_PAGE;
		return new double[] {left + 20 + 64.0, top + LIST_TOP + row * ROW + 4.0};
	}

	static boolean clicked(final int left, final int top, final double mx, final double my) {
		int first = page * PER_PAGE;
		for (int i = first; i < Math.min(first + PER_PAGE, Bestiary.ENTRIES.size()); i++) {
			double[] c = rowCentre(left, top, i);
			if (Math.abs(mx - c[0]) <= 66.0 && Math.abs(my - c[1]) <= ROW / 2.0) {
				selected = i;
				HunterGuideScreen.sound(SoundEvents.BOOK_PAGE_TURN);
				return true;
			}
		}
		if (page > 0 && HunterGuideScreen.inside(mx, my, left + 16, top + 176, 18, 12)) {
			page--;
			HunterGuideScreen.sound(SoundEvents.BOOK_PAGE_TURN);
			return true;
		}
		if (page < pages() - 1 && HunterGuideScreen.inside(mx, my, left + HunterGuideScreen.W - 34, top + 176, 18, 12)) {
			page++;
			HunterGuideScreen.sound(SoundEvents.BOOK_PAGE_TURN);
			return true;
		}
		return false;
	}

	static void draw(final Font font, final GuiGraphicsExtractor g, final int x, final int y, final int mouseX, final int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		Bestiary.Record record = mc.player == null ? Bestiary.Record.EMPTY : BestiaryService.get(mc.player);
		list(font, g, x, y, mouseX, mouseY, record);
		chosen(font, g, x + 172, y, mouseX, mouseY, record);
	}

	private static void list(final Font font, final GuiGraphicsExtractor g, final int x, final int y, final int mouseX, final int mouseY, final Bestiary.Record record) {
		int px = x + 20;
		g.text(font, Component.translatable("gui.mistinn.bestiary.title"), px, y + 18, HunterGuideScreen.INK, false);
		int met = (int) Bestiary.ENTRIES.stream().filter(entry -> record.seen(entry.id())).count();
		Component count = Component.translatable("gui.mistinn.bestiary.count", met, Bestiary.ENTRIES.size());
		g.text(font, count, px + 128 - font.width(count), y + 18, HunterGuideScreen.FADED, false);
		int first = page * PER_PAGE;
		for (int i = first; i < Math.min(first + PER_PAGE, Bestiary.ENTRIES.size()); i++) {
			Bestiary.Entry entry = Bestiary.ENTRIES.get(i);
			double[] c = rowCentre(x, y, i);
			int ry = (int) (c[1] - 4.0);
			boolean hover = Math.abs(mouseX - c[0]) <= 66.0 && Math.abs(mouseY - c[1]) <= ROW / 2.0;
			boolean seen = record.seen(entry.id());
			Component label = seen ? entry.type().getDescription() : Component.translatable("gui.mistinn.bestiary.unknown");
			int colour = i == selected || hover ? HunterGuideScreen.RED : seen ? HunterGuideScreen.INK : HunterGuideScreen.FADED;
			g.text(font, font.plainSubstrByWidth(label.getString(), NAME_WIDTH), px, ry, colour, false);
			int kills = record.kills(entry.id());
			if (kills > 0) {
				String brought = "×" + kills;
				g.text(font, brought, px + 128 - font.width(brought), ry, HunterGuideScreen.FADED, false);
			}
		}
		if (page > 0) {
			boolean hover = HunterGuideScreen.inside(mouseX, mouseY, x + 16, y + 176, 18, 12);
			g.blit(RenderPipelines.GUI_TEXTURED, HunterGuideScreen.ARROWS, x + 16, y + 176, 0.0F, hover ? 12 : 0, 18, 12, 36, 24);
		}
		if (page < pages() - 1) {
			boolean hover = HunterGuideScreen.inside(mouseX, mouseY, x + HunterGuideScreen.W - 34, y + 176, 18, 12);
			g.blit(RenderPipelines.GUI_TEXTURED, HunterGuideScreen.ARROWS, x + HunterGuideScreen.W - 34, y + 176, 18.0F, hover ? 12 : 0, 18, 12, 36, 24);
		}
	}

	/** The right page: the chosen creature, as much as is known of it. */
	private static void chosen(final Font font, final GuiGraphicsExtractor g, final int px, final int y, final int mouseX, final int mouseY,
		final Bestiary.Record record) {
		if (selected < 0 || selected >= Bestiary.ENTRIES.size()) {
			words(font, g, px, y + 18, Component.translatable("gui.mistinn.bestiary.choose"), HunterGuideScreen.FADED);
			return;
		}
		Bestiary.Entry entry = Bestiary.ENTRIES.get(selected);
		if (!Bestiary.knows(record, entry, Bestiary.Detail.NAME)) {
			g.text(font, Component.translatable("gui.mistinn.bestiary.unknown"), px, y + 18, HunterGuideScreen.FADED, false);
			words(font, g, px, y + TEXT_TOP, Component.translatable("gui.mistinn.bestiary.unmet"), HunterGuideScreen.FADED);
			return;
		}
		g.text(font, entry.type().getDescription(), px, y + 18, entry.great() ? HunterGuideScreen.RED : HunterGuideScreen.INK, false);
		int kills = record.kills(entry.id());
		String brought = "×" + kills;
		g.text(font, brought, px + 128 - font.width(brought), y + 18, HunterGuideScreen.FADED, false);
		LivingEntity model = model(entry);
		if (model != null) {
			int tall = MODEL_BOTTOM - MODEL_TOP;
			float extent = Math.max(model.getBbHeight(), model.getBbWidth());
			int size = (int) Math.max(5.0F, Math.min(30.0F, tall * 0.8F / extent));
			InventoryScreen.extractEntityInInventoryFollowsMouse(g, px + 14, y + MODEL_TOP, px + 114, y + MODEL_BOTTOM, size, 0.0625F, mouseX, mouseY, model);
		}
		int ty = y + TEXT_TOP;
		ty = words(font, g, px, ty, Component.translatable(key(entry, "desc")), HunterGuideScreen.INK);
		Component weak = Bestiary.knows(record, entry, Bestiary.Detail.WEAK) ? Component.translatable(key(entry, "weak"))
			: Component.translatable("gui.mistinn.bestiary.locked.weak");
		ty = words(font, g, px, ty, Component.translatable("gui.mistinn.bestiary.weak", weak), HunterGuideScreen.INK);
		int needed = (entry.great() ? 1 : Bestiary.TELL_KILLS) - Math.max(0, kills);
		Component tell = Bestiary.knows(record, entry, Bestiary.Detail.TELL) ? Component.translatable(key(entry, "tell"))
			: Component.translatable("gui.mistinn.bestiary.locked.tell", needed);
		words(font, g, px, ty, Component.translatable("gui.mistinn.bestiary.tell", tell), HunterGuideScreen.INK);
	}

	/** Words wrapped to the page from {@code ty} down; returns where the next words start. */
	private static int words(final Font font, final GuiGraphicsExtractor g, final int px, final int ty, final Component text, final int colour) {
		int at = ty;
		for (FormattedCharSequence row : font.split(text, HunterGuideScreen.PAGE_WIDTH)) {
			g.text(font, row, px, at, colour, false);
			at += 10;
		}
		return at;
	}

	/** A still creature of this kind to draw, or null when one cannot be made here. */
	private static @Nullable LivingEntity model(final Bestiary.Entry entry) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		if (modelsLevel != mc.level) {
			MODELS.clear();
			modelsLevel = mc.level;
		}
		if (!MODELS.containsKey(entry.type())) {
			LivingEntity made = null;
			try {
				Entity entity = entry.type().create(mc.level, EntitySpawnReason.LOAD);
				if (entity instanceof LivingEntity living) {
					// never added to the level: an id of its own, below any the level hands out, for the renderer to read
					living.setId(MODEL_IDS - Bestiary.ENTRIES.indexOf(entry));
					made = living;
				}
			} catch (RuntimeException e) {
				Mistinn.LOGGER.warn("The guide could not make a {} to draw", entry.id(), e);
			}
			MODELS.put(entry.type(), made);
		}
		return MODELS.get(entry.type());
	}
}
