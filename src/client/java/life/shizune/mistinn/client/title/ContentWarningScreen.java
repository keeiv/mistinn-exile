package life.shizune.mistinn.client.title;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.ContentLevel;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * 內容警告: shown on black before the title screen, the first time a version runs (see {@link ContentWarning}). It is
 * dressed like an anti-cheat system's alert: one plain dialog, a header bar, the bell drawn in red line, the warning,
 * and the one choice it asks for (the content level: full, normal or light). Until it is answered a warm music box plays, and a few small
 * creatures of the shore keep the bell company on either side of the title, so it reads as care, not as the horror
 * begun. Esc does not dismiss it, and for its first moment it takes no input. On a small window the whole dialog is
 * drawn smaller, its two choices with it, at a size that keeps the font's pixels whole where it can.
 */
public class ContentWarningScreen extends Screen {
	/** The lullaby behind the warning; the title's music takes over once it is answered. */
	public static final Music MUSIC = new Music(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(MistinnSounds.MUSIC_CONTENT_WARNING), 0, 0, true);
	private static final Identifier ICON = Mistinn.id("textures/gui/warning/icon.png");
	private static final int ICON_TEXTURE = 64;
	private static final int ICON_SIZE = 32;
	/** Seconds before the choices answer: nothing is chosen by a click meant for something else. */
	public static final float READY = 1.2F;

	private static final int BLACK = 0xFF000000;
	private static final int PANEL = 0xFF0C0C0E;
	private static final int BORDER = 0xFF2C2C31;
	private static final int HEADER = 0xFF151518;
	private static final int HEADER_TEXT = 0xFF8C877F;
	private static final int CRIMSON = 0xFFC0262D;
	private static final int TEXT = 0xFFD8D2C6;
	private static final int DIM = 0xFF9E988E;

	private static final int MAX_WIDTH = 420;
	private static final int PAD = 14;
	private static final int HEADER_H = 14;
	private static final int LINE = 10;
	private static final int BULLET = 10;
	private static final int BUTTON_H = 20;
	private static final int BUTTON_GAP = 8;
	/** The title band: the bell, the title under it, the rule under that; the creatures stand on the rule. */
	private static final int ICON_Y = HEADER_H + 8;
	private static final int TITLE_Y = ICON_Y + ICON_SIZE + 4;
	private static final int RULE_Y = TITLE_Y + 18 + 5;
	private static final int TEXT_Y = RULE_Y + 1 + 7;
	private static final int SPRITE = 32;

	private final Runnable next;
	private final long opened = System.nanoTime();

	// the dialog, laid out in its own units and drawn at scale from its top left
	private float scale = 1.0F;
	private int left;
	private int top;
	private int panelW;
	private int panelH;
	private final List<FormattedCharSequence> lead = new ArrayList<>();
	private final List<FormattedCharSequence> age = new ArrayList<>();
	private final List<List<FormattedCharSequence>> items = new ArrayList<>();
	private final List<FormattedCharSequence> kept = new ArrayList<>();
	private final List<FormattedCharSequence> advice = new ArrayList<>();
	private int choiceY;
	private int buttonX;
	private int buttonW;
	private final List<Choice> choices = new ArrayList<>();

	/** {@code next}: what was to come before the warning stepped in (the title screen, or joining a friend's room). */
	public ContentWarningScreen(final Runnable next) {
		super(Component.translatable("warning.mistinn.title"));
		this.next = next;
	}

	/** The music while the warning is up, or null. */
	public static @Nullable Music music() {
		return Minecraft.getInstance().gui.screen() instanceof ContentWarningScreen ? MUSIC : null;
	}

	@Override
	protected void init() {
		this.layout();
		this.choices.clear();
		int y = this.top + Math.round(this.choiceY * this.scale);
		int h = Math.round(BUTTON_H * this.scale);
		int w = Math.round(this.buttonW * this.scale);
		ContentLevel[] levels = ContentLevel.values();
		for (int i = 0; i < levels.length; i++) {
			ContentLevel level = levels[i];
			int x = this.left + Math.round((this.buttonX + i * (this.buttonW + BUTTON_GAP)) * this.scale);
			this.choices.add(this.addRenderableWidget(new Choice(x, y, w, h, level, () -> this.answer(level))));
		}
	}

	/** Wraps the text to the dialog's width and works out its height, then the scale that fits it on screen. */
	private void layout() {
		Font font = this.font;
		this.panelW = Math.max(220, Math.min(MAX_WIDTH, this.width - 8));
		int textW = this.panelW - 2 * PAD;
		this.lead.clear();
		this.lead.addAll(life.shizune.mistinn.client.text.LineBreak.split(font, Component.translatable("warning.mistinn.lead"), textW));
		this.age.clear();
		this.age.addAll(life.shizune.mistinn.client.text.LineBreak.split(font, Component.translatable("warning.mistinn.age"), textW));
		this.items.clear();
		for (int i = 0; i < 3; i++) {
			this.items.add(life.shizune.mistinn.client.text.LineBreak.split(font, Component.translatable("warning.mistinn.item." + i), textW - BULLET));
		}
		this.kept.clear();
		this.kept.addAll(life.shizune.mistinn.client.text.LineBreak.split(font, Component.translatable("warning.mistinn.kept"), textW));
		this.advice.clear();
		this.advice.addAll(life.shizune.mistinn.client.text.LineBreak.split(font, Component.translatable("warning.mistinn.advice"), textW));

		int y = TEXT_Y;
		y += this.lead.size() * LINE + 5;
		y += this.age.size() * LINE + 3;
		for (List<FormattedCharSequence> item : this.items) {
			y += item.size() * LINE + 1;
		}
		y += 3;
		y += this.kept.size() * LINE + 4;
		y += this.advice.size() * LINE + 8;
		this.choiceY = y;
		// under the choices, up to two lines on what the one under the pointer means
		this.panelH = y + BUTTON_H + 4 + 2 * LINE + 6;
		// the choice row: what it switches on the left, the two choices filling the rest
		int label = font.width(Component.translatable("warning.mistinn.choice")) + 12;
		this.buttonX = PAD + label;
		this.buttonW = (textW - label - 2 * BUTTON_GAP) / 3;

		float fit = Math.min(1.0F, Math.min((this.height - 8) / (float) this.panelH, (this.width - 8) / (float) this.panelW));
		// keep the font's pixels whole: a step of the GUI scale, unless that would shrink it by far more than it needs
		double gui = Minecraft.getInstance().getWindow().getGuiScale();
		float whole = (float) (Math.floor(fit * gui) / gui);
		this.scale = fit >= 1.0F ? 1.0F : whole > 0.0F && fit - whole <= 0.25F ? whole : fit;
		this.left = Math.round((this.width - this.panelW * this.scale) / 2.0F);
		this.top = Math.round((this.height - this.panelH * this.scale) / 2.0F);
	}

	private void answer(final ContentLevel level) {
		ContentWarning.answer(level);
		if (this.minecraft != null) {
			this.minecraft.getMusicManager().stopPlaying(MUSIC);
		}
		this.next.run();
	}

	/** Seconds since the warning came up. */
	private float shownFor() {
		return (System.nanoTime() - this.opened) / 1.0E9F;
	}

	/** Whether the choices answer yet. */
	public boolean ready() {
		return this.shownFor() >= READY;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		g.fill(0, 0, this.width, this.height, BLACK);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		this.extractBackground(g, mouseX, mouseY, a);
		float t = this.shownFor();
		this.drawDialog(g, Math.min(1.0F, t / 0.4F), t);
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	private void drawDialog(final GuiGraphicsExtractor g, final float in, final float t) {
		Font font = this.font;
		int alpha = Math.max(8, (int) (255 * in));
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(this.left, this.top);
		pose.scale(this.scale, this.scale);
		int w = this.panelW;
		int h = this.panelH;
		// the window: a plain panel with a thin border and a header bar, a red line under it
		g.fill(-1, -1, w + 1, h + 1, fade(BORDER, alpha));
		g.fill(0, 0, w, h, fade(PANEL, alpha));
		g.fill(0, 0, w, HEADER_H, fade(HEADER, alpha));
		g.fill(0, HEADER_H, w, HEADER_H + 1, fade(CRIMSON, alpha));
		Component name = Component.translatable("warning.mistinn.header");
		g.text(font, name, 6, 3, fade(HEADER_TEXT, alpha), false);
		Component version = Component.translatable("warning.mistinn.version", ContentWarning.version());
		g.text(font, version, w - 6 - font.width(version), 3, fade(HEADER_TEXT, alpha), false);
		// the bell, in red line, the title under it, and the rule
		g.blit(RenderPipelines.GUI_TEXTURED, ICON, (w - ICON_SIZE) / 2, ICON_Y, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_TEXTURE, ICON_TEXTURE,
			ICON_TEXTURE, ICON_TEXTURE, alpha << 24 | 0xFFFFFF);
		int titleW = font.width(this.title) * 2;
		pose.pushMatrix();
		pose.translate(w / 2.0F, TITLE_Y);
		pose.scale(2.0F, 2.0F);
		g.text(font, this.title, -font.width(this.title) / 2, 0, fade(CRIMSON, alpha), false);
		pose.popMatrix();
		g.fill(PAD, RULE_Y, w - PAD, RULE_Y + 1, fade(BORDER, alpha));
		// the creatures, either side of the title, standing on the rule
		this.drawCreatures(g, w, Math.max(titleW, ICON_SIZE), t, alpha);
		// the warning
		int y = TEXT_Y;
		y = this.rows(g, this.lead, PAD, y, TEXT, alpha) + 5;
		y = this.rows(g, this.age, PAD, y, TEXT, alpha) + 3;
		for (List<FormattedCharSequence> item : this.items) {
			g.fill(PAD + 2, y + 3, PAD + 5, y + 6, fade(CRIMSON, alpha));
			y = this.rows(g, item, PAD + BULLET, y, TEXT, alpha) + 1;
		}
		y += 3;
		y = this.rows(g, this.kept, PAD, y, DIM, alpha) + 4;
		this.rows(g, this.advice, PAD, y, DIM, alpha);
		// what the two choices switch, beside them
		Component choice = Component.translatable("warning.mistinn.choice");
		g.text(font, choice, PAD, this.choiceY + (BUTTON_H - 8) / 2, fade(HEADER_TEXT, alpha), false);
		// what the choice under the pointer means (or, with none, a word on choosing)
		Component about = Component.translatable("warning.mistinn.choose");
		for (Choice each : this.choices) {
			if (each.isHoveredOrFocused()) {
				about = Component.translatable(each.level.about());
			}
		}
		int row = this.choiceY + BUTTON_H + 4;
		for (FormattedCharSequence line : life.shizune.mistinn.client.text.LineBreak.split(font, about, w - 2 * PAD).stream().limit(2).toList()) {
			g.text(font, line, PAD, row, fade(DIM, alpha), false);
			row += LINE;
		}
		pose.popMatrix();
	}

	/**
	 * Left of the title the bell's little one sways and a crystal lizard walks to and fro; right of it a wisp of fog
	 * floats and a small lamp-carrier keeps its lantern lit. Where the dialog is too narrow for one of them, it stays away.
	 */
	private void drawCreatures(final GuiGraphicsExtractor g, final int w, final int titleW, final float t, final int alpha) {
		int ground = RULE_Y - 1 - SPRITE;
		int leftEnd = (w - titleW) / 2 - 8;
		int rightStart = w - leftEnd;
		int color = alpha << 24 | 0xFFFFFF;
		if (leftEnd - PAD >= SPRITE) {
			sprite(g, "bell", PAD + 2, ground, frame(t, 5.0F, 0.0F), false, color);
		}
		int walk = leftEnd - (PAD + SPRITE + 4) - SPRITE;
		if (walk >= 12) {
			// to and fro across what room there is, turning at each end
			float span = 2.0F * walk;
			float along = (t * 12.0F) % span;
			boolean back = along > walk;
			int x = PAD + SPRITE + 4 + Math.round(back ? span - along : along);
			sprite(g, "lizard", x, ground, frame(t, 8.0F, 0.0F), back, color);
		}
		if (w - PAD - rightStart >= SPRITE) {
			int x = w - PAD - SPRITE - 2;
			sprite(g, "lamp", x, ground, frame(t, 4.0F, 0.5F), false, color);
		}
		if (w - PAD - rightStart >= 2 * SPRITE + 8) {
			int x = rightStart + Math.round(4.0F + Mth.sin(t * 0.6F) * 4.0F);
			int y = ground - 6 + Math.round(Mth.sin(t * 1.7F) * 2.0F);
			sprite(g, "wisp", x, y, frame(t, 4.0F, 0.3F), false, color);
		}
	}

	private static int frame(final float t, final float fps, final float phase) {
		return Math.floorMod((int) ((t + phase) * fps), 4);
	}

	private static void sprite(final GuiGraphicsExtractor g, final String name, final int x, final int y, final int frame, final boolean flipped, final int color) {
		Identifier sheet = Mistinn.id("textures/gui/warning/mascot_" + name + ".png");
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(flipped ? x + SPRITE : x, y);
		if (flipped) {
			pose.scale(-1.0F, 1.0F);
		}
		g.blit(RenderPipelines.GUI_TEXTURED, sheet, 0, 0, frame * SPRITE, 0.0F, SPRITE, SPRITE, SPRITE, SPRITE, 4 * SPRITE, SPRITE, color);
		pose.popMatrix();
	}

	private int rows(final GuiGraphicsExtractor g, final List<FormattedCharSequence> rows, final int x, final int y, final int color, final int alpha) {
		int at = y;
		for (FormattedCharSequence row : rows) {
			g.text(this.font, row, x, at, fade(color, alpha), false);
			at += LINE;
		}
		return at;
	}

	private static int fade(final int argb, final int alpha) {
		return (((argb >>> 24) * alpha / 255) << 24) | (argb & 0xFFFFFF);
	}

	/** For the client game test: the screen-space centre of choice 0 (full), 1 (normal) or 2 (light). */
	public double[] choiceCentre(final int index) {
		Choice choice = this.choices.get(index);
		return new double[] {choice.getX() + choice.getWidth() / 2.0, choice.getY() + choice.getHeight() / 2.0};
	}

	/** For the client game test: whether both choices lie wholly on screen. */
	public boolean choicesOnScreen() {
		for (Choice choice : this.choices) {
			if (choice.getX() < 0 || choice.getY() < 0 || choice.getX() + choice.getWidth() > this.width || choice.getY() + choice.getHeight() > this.height) {
				return false;
			}
		}
		return this.choices.size() == ContentLevel.values().length;
	}

	/** For the client game test: the scale the dialog is drawn at. */
	public float drawnAt() {
		return this.scale;
	}

	/** A choice: a plain framed button, its border red under the pointer, dim until the warning is ready. */
	private final class Choice extends AbstractButton {
		private final Runnable action;
		private final ContentLevel level;

		Choice(final int x, final int y, final int width, final int height, final ContentLevel level, final Runnable action) {
			super(x, y, width, height, Component.translatable(level.key()));
			this.level = level;
			this.action = action;
		}

		@Override
		public void onPress(final InputWithModifiers input) {
			if (ContentWarningScreen.this.ready()) {
				this.action.run();
			}
		}

		@Override
		protected void extractContents(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
			boolean ready = ContentWarningScreen.this.ready();
			boolean hot = ready && this.isHoveredOrFocused();
			float s = ContentWarningScreen.this.scale;
			int w = Math.round(this.getWidth() / s);
			int h = Math.round(this.getHeight() / s);
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(this.getX(), this.getY());
			pose.scale(s, s);
			g.fill(0, 0, w, h, hot ? CRIMSON : BORDER);
			g.fill(1, 1, w - 1, h - 1, hot ? 0xFF2A0D10 : HEADER);
			Font font = ContentWarningScreen.this.font;
			int color = !ready ? 0xFF5A5650 : hot ? 0xFFFFFFFF : TEXT;
			g.text(font, this.getMessage(), (w - font.width(this.getMessage())) / 2, (h - 8) / 2, color, false);
			pose.popMatrix();
		}

		@Override
		protected void updateWidgetNarration(final NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
