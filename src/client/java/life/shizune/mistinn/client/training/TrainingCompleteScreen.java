package life.shizune.mistinn.client.training;

import java.util.function.Consumer;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.anim.Motion;
import life.shizune.mistinn.story.WorldMode;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/** The hunter chooses their deployment from inside the pod. The server closes this screen. */
public final class TrainingCompleteScreen extends Screen {
	private static final Identifier HEART = Mistinn.id("textures/gui/training/living_heart.png");
	private static final int TEXTURE_SIZE = 48;
	private static final int GOLD = 0xFFF2CC80;
	private static final int RETRY_TICKS = 24;
	private static final float HEART_PERIOD = 0.84F;
	private static final float HEART_GROWTH = 0.12F;

	private final Consumer<WorldMode> choose;
	private final long opened = System.nanoTime();
	private @Nullable WorldMode pending;
	private int retryTicks;
	private @Nullable CameraType previousCamera;
	private boolean hudWasHidden;
	private float titleScale;
	private int top;
	private int heartOffset;
	private int heartSize;

	public TrainingCompleteScreen(final Consumer<WorldMode> choose) {
		super(Component.translatable("training.mistinn.complete.title"));
		this.choose = choose;
	}

	@Override
	public void added() {
		super.added();
		this.previousCamera = this.minecraft.options.getCameraType();
		this.minecraft.options.setCameraType(CameraType.FIRST_PERSON);
		this.hudWasHidden = this.minecraft.gui.hud.isHidden();
		if (!this.hudWasHidden) {
			this.minecraft.gui.hud.toggle();
		}
	}

	@Override
	public void removed() {
		if (this.previousCamera != null) {
			this.minecraft.options.setCameraType(this.previousCamera);
		}
		if (!this.hudWasHidden && this.minecraft.gui.hud.isHidden()) {
			this.minecraft.gui.hud.toggle();
		}
		super.removed();
	}

	@Override
	protected void init() {
		int margin = 8;
		int gap = this.height >= 160 ? 12 : 5;
		int choiceHeight = this.height >= 160 ? 20 : 15;
		float maxTitleScale = this.height >= 220 ? 3.0F : 2.0F;
		this.titleScale = Math.min(maxTitleScale, (float) Math.max(1, this.width - 2 * margin) / Math.max(1, this.font.width(this.title)));
		int choicesOffset = (int) Math.ceil(this.font.lineHeight * this.titleScale) + gap;
		this.heartOffset = choicesOffset + 2 * choiceHeight + gap;
		int preferredHeartSize = this.height >= 220 ? 96 : this.height >= 160 ? 72 : 48;
		int heartRoom = Math.min(this.width, this.height - this.heartOffset) - 2 * margin;
		this.heartSize = Math.max(1, Math.min(preferredHeartSize, (int) (heartRoom / (1.0F + HEART_GROWTH))));
		this.top = Math.max(0, (this.height - this.heartOffset - Math.round(this.heartSize * (1.0F + HEART_GROWTH))) / 2);

		int width = Math.max(1, Math.min(152, this.width - 2 * margin));
		int x = (this.width - width) / 2;
		int y = this.top + choicesOffset;
		this.addRenderableWidget(new Choice(x, y, width, choiceHeight, WorldMode.STORY));
		this.addRenderableWidget(new Choice(x, y + choiceHeight, width, choiceHeight, WorldMode.FREE));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void tick() {
		if (this.retryTicks > 0 && --this.retryTicks == 0) {
			this.pending = null;
		}
	}

	private void submit(final WorldMode mode) {
		if (this.pending == null) {
			this.pending = mode;
			this.retryTicks = RETRY_TICKS;
			this.choose.accept(mode);
		}
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0xAD000000);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(this.width / 2.0F, this.top);
		pose.scale(this.titleScale, this.titleScale);
		graphics.text(this.font, this.title, -this.font.width(this.title) / 2, 0, GOLD, true);
		pose.popMatrix();
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		float seconds = (System.nanoTime() - this.opened) / 1.0E9F;
		float pulse = Motion.heartbeat(seconds, HEART_PERIOD);
		int w = Math.round(this.heartSize * (1.0F + HEART_GROWTH * pulse));
		int h = Math.round(this.heartSize * (1.0F + 0.08F * pulse));
		int y = this.top + this.heartOffset + (Math.round(this.heartSize * (1.0F + HEART_GROWTH)) - h) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, HEART, (this.width - w) / 2, y, 0.0F, 0.0F,
			w, h, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
	}

	private final class Choice extends AbstractButton {
		private final WorldMode mode;
		private final Component label;
		private float hover;
		private long lastFrame = System.nanoTime();

		Choice(final int x, final int y, final int width, final int height, final WorldMode mode) {
			super(x, y, width, height, Component.translatable("mode.mistinn." + mode.id));
			this.mode = mode;
			this.label = Component.literal("> ").append(this.getMessage());
		}

		@Override
		public boolean isActive() {
			return super.isActive() && TrainingCompleteScreen.this.pending == null;
		}

		@Override
		public void onPress(final InputWithModifiers input) {
			TrainingCompleteScreen.this.submit(this.mode);
		}

		@Override
		protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick) {
			long now = System.nanoTime();
			float dt = Math.min(0.1F, (now - this.lastFrame) / 1.0E9F);
			this.lastFrame = now;
			WorldMode pending = TrainingCompleteScreen.this.pending;
			boolean highlighted = pending == null ? this.isHoveredOrFocused() : pending == this.mode;
			this.hover = Motion.approach(this.hover, highlighted ? 1.0F : 0.0F, dt, 16.0F);
			Font font = TrainingCompleteScreen.this.font;
			int x = this.getX() + (this.getWidth() - font.width(this.label)) / 2 + Math.round(this.hover * 3.0F);
			int y = this.getY() + (this.getHeight() - font.lineHeight) / 2;
			int base = pending == null ? 0xFFE4DED0 : 0xFFA29A8C;
			graphics.text(font, this.label, x, y, ARGB.srgbLerp(this.hover, base, GOLD), true);
		}

		@Override
		protected void updateWidgetNarration(final NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
