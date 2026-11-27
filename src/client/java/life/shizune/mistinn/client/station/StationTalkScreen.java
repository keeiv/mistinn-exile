package life.shizune.mistinn.client.station;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.station.StaffRole;
import life.shizune.mistinn.station.StationTalk;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A talk with a member of the staff: their face and name on a paper slip, the line typed out a
 * few letters a tick, and once a topic is told, the questions the hunter may ask next. The world
 * keeps moving behind it.
 */
public final class StationTalkScreen extends Screen {
	private static final Identifier BOX = Mistinn.id("textures/gui/station/talk_box.png");
	private static final int BOX_WIDTH = 256;
	private static final int BOX_HEIGHT = 72;
	private static final int TEXT_LEFT = 56;
	private static final int TEXT_WIDTH = 188;
	private static final int INK = 0xFF2B2117;
	private static final int INK_SOFT = 0xFF6E5B45;
	private static final int PAPER = 0xFFE4DED0;
	private static final int GOLD = 0xFFE0AF63;
	private static final float LETTERS_PER_TICK = 1.6F;
	/** The name tab in the texture: x 8 to 95, 13 high, with 5 px ends. */
	private static final int TAB_LEFT = 8;
	private static final int TAB_WIDTH = 87;
	private static final int TAB_HEIGHT = 13;
	private static final int TAB_CAP = 5;

	private final int entity;
	private final StaffRole role;
	private final List<String> topics;
	private String topic = StationTalk.HELLO;
	private List<String> lines = List.of();
	private int line;
	private float shown;
	private boolean asking;
	private int age;

	public StationTalkScreen(final int entity, final StaffRole role, final String opening, final List<String> topics) {
		super(Component.translatable(role.key()));
		this.entity = entity;
		this.role = role;
		this.topics = List.copyOf(topics);
		this.tell(opening);
	}

	public StaffRole role() {
		return this.role;
	}

	/** The topic being told, or {@code null} while the hunter picks a question. */
	public String topic() {
		return this.asking ? null : this.topic;
	}

	/** Every line of this member's topic, read from the language file until the numbers run out. */
	public static List<String> lines(final StaffRole role, final String topic) {
		List<String> lines = new ArrayList<>();
		Language language = Language.getInstance();
		for (int n = 0; n < 32; n++) {
			String key = role.key() + "." + topic + "." + n;
			if (!language.has(key)) {
				break;
			}
			lines.add(language.getOrDefault(life.shizune.mistinn.client.ledger.Haunting.key(key)));
		}
		return lines;
	}

	private void tell(final String topic) {
		this.topic = topic;
		this.lines = lines(this.role, topic);
		this.line = 0;
		this.shown = 0.0F;
		this.asking = this.lines.isEmpty();
		if (this.minecraft != null) {
			this.rebuildWidgets();
		}
	}

	@Override
	protected void init() {
		if (!this.asking) {
			return;
		}
		int left = this.boxLeft() + BOX_WIDTH - 150;
		int y = this.boxTop() - 6;
		List<String> asks = new ArrayList<>(this.topics);
		asks.add(null);
		for (int i = asks.size() - 1; i >= 0; i--) {
			y -= 14;
			this.addRenderableWidget(new Ask(left, y, 150, 14, asks.get(i)));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		this.age++;
		if (!this.asking && this.shown < this.current().length()) {
			this.shown = Math.min(this.current().length(), this.shown + LETTERS_PER_TICK);
		}
	}

	private String current() {
		return this.lines.isEmpty() ? "" : this.lines.get(this.line);
	}

	/** A click or a key: finish typing the line, then go on to the next, then to the questions. */
	public void advance() {
		if (this.asking) {
			return;
		}
		if (this.shown < this.current().length()) {
			this.shown = this.current().length();
		} else if (this.line + 1 < this.lines.size()) {
			this.line++;
			this.shown = 0.0F;
		} else {
			if (!StationTalk.HELLO.equals(this.topic)) {
				ClientPlayNetworking.send(new StationTalk.HeardPayload(this.entity, this.topic));
			}
			this.asking = true;
			this.rebuildWidgets();
		}
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		this.advance();
		return true;
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_SPACE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			this.advance();
			return true;
		}
		return super.keyPressed(event);
	}

	private int boxLeft() {
		return (this.width - BOX_WIDTH) / 2;
	}

	private int boxTop() {
		return this.height - BOX_HEIGHT - 12;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		graphics.fillGradient(0, this.height - 120, this.width, this.height, 0x00000000, 0x90000000);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int left = this.boxLeft();
		int top = this.boxTop();
		graphics.blit(RenderPipelines.GUI_TEXTURED, BOX, left, top, 0.0F, 0.0F, BOX_WIDTH, BOX_HEIGHT, BOX_WIDTH, BOX_HEIGHT, BOX_WIDTH, BOX_HEIGHT);
		Identifier skin = this.role.texture();
		// the face and the hat layer over it
		graphics.blit(RenderPipelines.GUI_TEXTURED, skin, left + 12, top + 20, 8.0F, 8.0F, 32, 32, 8, 8, 64, 64);
		graphics.blit(RenderPipelines.GUI_TEXTURED, skin, left + 12, top + 20, 40.0F, 8.0F, 32, 32, 8, 8, 64, 64);
		Component name = Component.translatable(this.role.key()).append(" · ").append(Component.translatable(this.role.key() + ".title"));
		this.nameTab(graphics, left, top, this.font.width(name) + 2 * TAB_CAP);
		graphics.text(this.font, name, left + TAB_LEFT + TAB_CAP, top + 3, INK, false);
		String text = this.asking ? Language.getInstance().getOrDefault(this.role.key() + ".ask", Language.getInstance().getOrDefault("staff.mistinn.ask"))
			: this.current().substring(0, Math.min(this.current().length(), (int) this.shown));
		int y = top + 20;
		for (var row : this.font.split(Component.literal(text), TEXT_WIDTH)) {
			graphics.text(this.font, row, left + TEXT_LEFT, y, this.asking ? INK_SOFT : INK, false);
			y += 11;
		}
		if (!this.asking && this.shown >= this.current().length() && (this.age / 8) % 2 == 0) {
			graphics.text(this.font, "▼", left + BOX_WIDTH - 16, top + BOX_HEIGHT - 14, INK_SOFT, false);
		}
	}

	/** Stretches the painted name tab to fit a longer name: its left end, its plain middle repeated, its right end. */
	private void nameTab(final GuiGraphicsExtractor graphics, final int left, final int top, final int width) {
		if (width <= TAB_WIDTH) {
			return;
		}
		int middle = TAB_WIDTH - 2 * TAB_CAP;
		int x = left + TAB_LEFT + TAB_CAP;
		int remaining = width - 2 * TAB_CAP;
		while (remaining > 0) {
			int w = Math.min(middle, remaining);
			graphics.blit(RenderPipelines.GUI_TEXTURED, BOX, x, top, TAB_LEFT + TAB_CAP, 0.0F, w, TAB_HEIGHT, w, TAB_HEIGHT, BOX_WIDTH, BOX_HEIGHT);
			x += w;
			remaining -= w;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, BOX, x, top, TAB_LEFT + TAB_WIDTH - TAB_CAP, 0.0F, TAB_CAP, TAB_HEIGHT, TAB_CAP, TAB_HEIGHT, BOX_WIDTH, BOX_HEIGHT);
	}

	/** One question to ask, or the goodbye ({@code topic} null). */
	private final class Ask extends AbstractButton {
		private final String ask;

		Ask(final int x, final int y, final int width, final int height, final String ask) {
			super(x, y, width, height, ask == null ? Component.translatable("staff.mistinn.bye")
				: Component.translatable(StationTalkScreen.this.role.key() + ".ask." + ask));
			this.ask = ask;
		}

		@Override
		public void onPress(final InputWithModifiers input) {
			if (this.ask == null) {
				StationTalkScreen.this.onClose();
			} else {
				StationTalkScreen.this.tell(this.ask);
			}
		}

		@Override
		protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
			Font font = StationTalkScreen.this.font;
			boolean lit = this.isHoveredOrFocused();
			Component label = Component.literal(lit ? "› " : "  ").append(this.getMessage());
			int width = font.width(label);
			graphics.fill(this.getRight() - width - 6, this.getY(), this.getRight(), this.getBottom(), lit ? 0xC0201812 : 0x90100C08);
			graphics.text(font, label, this.getRight() - width - 3, this.getY() + 3, lit ? GOLD : PAPER, false);
		}

		@Override
		protected void updateWidgetNarration(final NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
