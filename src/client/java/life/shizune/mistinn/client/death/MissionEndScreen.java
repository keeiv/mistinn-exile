package life.shizune.mistinn.client.death;

import life.shizune.mistinn.Mistinn;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * 任務終止: the death screen. For the first seconds it shows nothing at all and leaves the world
 * to the death scene; then the world dims behind it, the words come up in red, the cause, and a
 * line from Rust Anchor that does not hide its disappointment, typed out. Under the two choices,
 * a heart run through.
 * It is a {@link DeathScreen}, so the game and other mods still know it for one.
 */
public class MissionEndScreen extends DeathScreen {
	private static final Identifier HEART = Mistinn.id("textures/gui/death/pierced_heart.png");
	private static final int HEART_SIZE = 32;
	private static final int RED = 0xC23A2E;
	/** How many lines each cause has to choose from. */
	private static final int LINES = 4;
	/** Offsets down the column from its top. */
	private static final int CAUSE_AT = 36;
	private static final int QUOTE_AT = 52;
	private static final int CHOICES_AT = 74;
	private static final int HEART_AT = 124;

	private final @Nullable Component cause;
	private final boolean hardcore;
	private final LocalPlayer player;
	private final Component quote;
	private boolean shown;
	private final java.util.List<Choice> choices = new java.util.ArrayList<>();

	public MissionEndScreen(final @Nullable Component cause, final boolean hardcore, final LocalPlayer player) {
		super(cause, hardcore, player);
		this.cause = cause;
		this.hardcore = hardcore;
		this.player = player;
		DeathScene.begin(player);
		String kind = DeathScene.cause().name().toLowerCase(java.util.Locale.ROOT);
		this.quote = Component.translatable("death.mistinn.quote." + kind + "." + player.getRandom().nextInt(LINES));
	}

	/** Seconds since the screen itself appeared, after the death scene. */
	private float shownFor() {
		return DeathScene.seconds() - DeathScene.SCREEN;
	}

	/** The heart's size: smaller on short windows, so everything fits. */
	private int heartSize() {
		return this.height >= 360 ? HEART_SIZE * 3 : HEART_SIZE * 2;
	}

	/** The top of the whole column: title, cause, line, choices, heart. */
	private int top() {
		return Math.max(6, (this.height - (HEART_AT + this.heartSize())) / 2);
	}

	@Override
	protected void init() {
		int w = 150;
		int x = this.width / 2 - w / 2;
		int y = this.top() + CHOICES_AT;
		this.choices.clear();
		this.choices.add(this.addRenderableWidget(new Choice(x, y, w, Component.translatable("death.mistinn.return"), () -> {
			this.player.respawn();
			DeathScene.end();
		})));
		this.choices.add(this.addRenderableWidget(new Choice(x, y + 22, w, Component.translatable("death.mistinn.rest"), this::rest)));
	}

	/** For the client game test: the centre of a choice (0 return, 1 rest), in GUI pixels. */
	public double[] choiceCentre(final int index) {
		Choice choice = this.choices.get(index);
		return new double[] {choice.getX() + choice.getWidth() / 2.0, choice.getY() + choice.getHeight() / 2.0};
	}

	/** Back to the title screen, saving the world. */
	private void rest() {
		DeathScene.end();
		if (this.minecraft.level != null) {
			this.minecraft.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
		}
		this.minecraft.disconnectWithSavingScreen();
		this.minecraft.gui.setScreen(new TitleScreen());
	}

	@Override
	public boolean isPauseScreen() {
		// the world keeps moving: the scene plays out in it
		return false;
	}

	@Override
	public void removed() {
		DeathScene.end();
		super.removed();
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		float t = Mth.clamp(this.shownFor() / 0.8F, 0.0F, 1.0F);
		if (t > 0.0F) {
			g.fill(0, 0, this.width, this.height, (int) (0xB4 * t) << 24);
		}
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		float since = this.shownFor();
		if (since < 0.0F) {
			// the death scene: nothing over the world
			return;
		}
		this.extractBackground(g, mouseX, mouseY, a);
		int alpha = (int) (255 * Mth.clamp(since / 0.6F, 0.0F, 1.0F));
		if (alpha < 8) {
			return;
		}
		Font font = this.font;
		int cx = this.width / 2;
		int top = this.top();

		// 任務終止, large and red
		Component title = Component.translatable("death.mistinn.title");
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, top);
		pose.scale(3.0F, 3.0F);
		g.text(font, title, -font.width(title) / 2, 0, alpha << 24 | RED, true);
		pose.popMatrix();

		// the cause, in quotation marks
		if (this.cause != null) {
			Component cause = Component.literal("“").append(this.cause).append("”");
			g.text(font, cause, cx - font.width(cause) / 2, top + CAUSE_AT, alpha << 24 | 0xC8B8B0, false);
		}

		// Rust Anchor's line, typed out
		String line = this.quote.getString();
		int shown = Mth.clamp((int) ((since - 0.6F) * 22.0F), 0, line.length());
		if (shown > 0) {
			String typed = "「" + line.substring(0, shown) + (shown == line.length() ? "」" : "");
			g.text(font, typed, cx - font.width("「" + line + "」") / 2, top + QUOTE_AT, alpha << 24 | 0xE8E0D2, false);
		}

		if (!this.shown && since > 1.4F) {
			this.shown = true;
		}
		// the choices; not the vanilla screen's own title and score
		for (Choice choice : this.choices) {
			choice.extractRenderState(g, mouseX, mouseY, a);
		}

		// a heart run through, beating faintly
		float beat = 1.0F + 0.04F * Math.max(0.0F, Mth.sin(since * 5.0F));
		int size = Math.round(this.heartSize() * beat);
		int hy = top + HEART_AT + (this.heartSize() - size) / 2;
		g.blit(RenderPipelines.GUI_TEXTURED, HEART, cx - size / 2, hy, 0.0F, 0.0F, size, size, HEART_SIZE, HEART_SIZE, HEART_SIZE, HEART_SIZE, alpha << 24 | 0xFFFFFF);
	}

	/** The choices wait until the screen has come up, so nothing is chosen by a stray click. */
	private boolean ready() {
		return this.shown && this.shownFor() > 1.4F;
	}

	/** A choice written as "> words": brightens and steps forward under the pointer. */
	private final class Choice extends AbstractButton {
		private final Runnable action;

		Choice(final int x, final int y, final int width, final Component message, final Runnable action) {
			super(x, y, width, 20, message);
			this.action = action;
		}

		@Override
		public void onPress(final InputWithModifiers input) {
			if (MissionEndScreen.this.ready()) {
				this.action.run();
			}
		}

		@Override
		protected void extractContents(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
			if (!MissionEndScreen.this.ready()) {
				return;
			}
			boolean hot = this.isHoveredOrFocused();
			Font font = MissionEndScreen.this.font;
			Component text = Component.literal("> ").append(this.getMessage());
			int x = this.getX() + (this.getWidth() - font.width(text)) / 2 + (hot ? 3 : 0);
			g.text(font, text, x, this.getY() + 6, hot ? 0xFFF2CC80 : 0xFFB8B0A0, true);
		}

		@Override
		protected void updateWidgetNarration(final NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
