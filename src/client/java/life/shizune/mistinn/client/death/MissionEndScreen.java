package life.shizune.mistinn.client.death;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnSounds;
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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * 任務終止: the death screen. For the first seconds it shows nothing at all and leaves the world
 * to the death scene; then the world dims behind it, the words come up in red, the cause, and a
 * line from Rust Anchor that does not hide its disappointment, typed out. Under the two choices,
 * a heart run through by a knife; after an execution, a heart bitten open, and the word for it in the cause in crimson.
 * <p>
 * That line is the station as the hunter carries it in their head, the voice they trained to: no radio sends it down
 * (the radio only receives). In a world whose story ended at the Mist Inn's fire the voice has gone quiet; what is
 * left is the hunter's own thought, and getting up is going back, not back to a mission.
 * <p>
 * It is a {@link DeathScreen}, so the game and other mods still know it for one.
 */
public class MissionEndScreen extends DeathScreen {
	private static final Identifier HEART = Mistinn.id("textures/gui/death/stabbed_heart.png");
	private static final Identifier TORN_HEART = Mistinn.id("textures/gui/death/torn_heart.png");
	private static final int HEART_SIZE = 48;
	private static final int RED = 0xC23A2E;
	/** The colour of the word "executed" in an execution's cause line. */
	public static final int CRIMSON = 0xB3121F;
	/** How many lines each cause has to choose from. */
	private static final int LINES = 4;
	/** On the tillers' way: how many last thoughts a fallen tiller may have. */
	public static final int PATH_LINES = 8;
	/** After the Mist Inn's ending: how many thoughts of their own the hunter may have. */
	public static final int HOME_LINES = 6;
	/** Offsets down the column from its top. */
	private static final int CAUSE_AT = 36;
	private static final int QUOTE_AT = 52;
	private static final int CHOICES_AT = 74;
	private static final int HEART_AT = 124;

	private final @Nullable Component cause;
	private final boolean hardcore;
	private final LocalPlayer player;
	private final Component quote;
	/** On the tillers' way (not as the thirteenth): a tiller fell, not a hunter. */
	private final boolean path;
	/** This world's story ended at the Mist Inn's fire: the station's voice is gone from the hunter's head. */
	private final boolean home;
	/** What going on means now, on the tillers' way: the next one walking, or what is left when no one is. */
	private final String onward;
	private boolean shown;
	/** Where in its beat the drawn heart was last tick; negative before the first. */
	private float beatPhase = -1.0F;
	private final java.util.List<Choice> choices = new java.util.ArrayList<>();

	public MissionEndScreen(final @Nullable Component cause, final boolean hardcore, final LocalPlayer player) {
		super(cause, hardcore, player);
		this.hardcore = hardcore;
		this.player = player;
		DeathScene.begin(player);
		this.cause = DeathScene.cause() == DeathScene.Cause.EXECUTION ? executed(cause) : cause;
		String kind = DeathScene.cause().name().toLowerCase(java.util.Locale.ROOT);
		life.shizune.mistinn.path.PathService.View view = player.getAttached(life.shizune.mistinn.path.PathService.VIEW);
		this.path = view != null && !life.shizune.mistinn.path.Thirteenth.stitched(player);
		this.home = !this.path && wentHome(player);
		// a tiller's last thought is their own: the one who moved them is gone from it; so is the hunter's, once home
		this.quote = this.path ? Component.translatable("death.mistinn.quote.path." + player.getRandom().nextInt(PATH_LINES))
			: this.home ? Component.translatable("death.mistinn.quote.home." + player.getRandom().nextInt(HOME_LINES))
			: Component.translatable("death.mistinn.quote." + kind + "." + player.getRandom().nextInt(LINES));
		this.onward = this.path ? onward(view) : this.home ? "death.mistinn.return.home" : "death.mistinn.return";
	}

	/** Whether this world's story ended at the Mist Inn's fire: the story is over, and its last call was the inn's. */
	private static boolean wentHome(final LocalPlayer player) {
		life.shizune.mistinn.story.StoryProgress progress = player.getAttached(life.shizune.mistinn.story.StoryService.PROGRESS);
		return progress != null && progress.step() == life.shizune.mistinn.story.StoryStep.ENDED
			&& progress.heard().contains(life.shizune.mistinn.story.StoryScript.INN_END);
	}

	/**
	 * The way's "go on", as it is now: after the sea, back home; with the last of the twelve gone, not stopping (where
	 * the twelve will be stitched) or watching the way end; otherwise, the next one taking it up.
	 */
	private static String onward(final life.shizune.mistinn.path.PathService.View view) {
		if (view.stage() == life.shizune.mistinn.path.PathService.Step.ENDED) {
			return "death.mistinn.return.path.home";
		}
		if (view.fallen() + 1 >= life.shizune.mistinn.path.PathService.PEOPLE) {
			return life.shizune.mistinn.client.ledger.LedgerClient.ledger().cruelStory() ? "death.mistinn.return.path.last" : "death.mistinn.return.path.watch";
		}
		return "death.mistinn.return.path";
	}

	/**
	 * An execution's cause line, written by the screen: the same names as the death message, with the word for
	 * "executed" in crimson. The chat keeps the plain message.
	 */
	static @Nullable Component executed(final @Nullable Component cause) {
		if (cause != null && cause.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents message && message.getArgs().length >= 2) {
			return Component.translatable("death.mistinn.cause.execution", message.getArgs()[0], message.getArgs()[1],
				Component.translatable("death.mistinn.executed").withColor(CRIMSON));
		}
		return cause;
	}

	/** The cause line as drawn (for the client game test). */
	public @Nullable Component causeLine() {
		return this.cause;
	}

	/** Seconds since the screen itself appeared, after the death scene. */
	private float shownFor() {
		return DeathScene.seconds() - DeathScene.SCREEN;
	}

	/** The heart's size, in whole multiples of its pixels: smaller on short windows, so everything fits. */
	private int heartSize() {
		return this.height >= 360 ? HEART_SIZE * 2 : HEART_SIZE;
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
		this.choices.add(this.addRenderableWidget(new Choice(x, y, w, Component.translatable(this.onward), () -> {
			this.player.respawn();
			DeathScene.end();
		})));
		this.choices.add(this.addRenderableWidget(new Choice(x, y + 22, w, Component.translatable(this.path ? "death.mistinn.rest.path" : "death.mistinn.rest"), this::rest)));
	}

	/** For the client game test: the centre of a choice (0 return, 1 rest), in GUI pixels. */
	public double[] choiceCentre(final int index) {
		Choice choice = this.choices.get(index);
		return new double[] {choice.getX() + choice.getWidth() / 2.0, choice.getY() + choice.getHeight() / 2.0};
	}

	/** For the client game test: the keys of the last line shown and of the first choice. */
	public String quoteKey() {
		return ((net.minecraft.network.chat.contents.TranslatableContents) this.quote.getContents()).getKey();
	}

	public String onwardKey() {
		return this.onward;
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

	/** The heartbeat heard with the heart drawn below: each lub-dub as the drawn beat starts, fading as it slows. */
	@Override
	public void tick() {
		super.tick();
		float since = this.shownFor();
		if (since < 0.0F) {
			return;
		}
		float slowing = Mth.clamp(since / 12.0F, 0.0F, 1.0F);
		float phase = since % (1.1F + 0.9F * slowing);
		if (this.beatPhase < 0.0F || phase < this.beatPhase) {
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(MistinnSounds.HEARTBEAT, 1.0F, 0.9F - 0.5F * slowing));
		}
		this.beatPhase = phase;
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
		Component title = Component.translatable(this.path ? "death.mistinn.title.path" : this.home ? "death.mistinn.title.home" : "death.mistinn.title");
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

		// a heart run through, still beating: lub-dub, fainter and slower as it gives out
		float slowing = Mth.clamp(since / 12.0F, 0.0F, 1.0F);
		float beat = 1.0F + (0.06F - 0.03F * slowing) * life.shizune.mistinn.client.anim.Motion.heartbeat(since, 1.1F + 0.9F * slowing);
		int size = Math.round(this.heartSize() * beat);
		int hy = top + HEART_AT + (this.heartSize() - size) / 2;
		Identifier heart = DeathScene.cause() == DeathScene.Cause.EXECUTION ? TORN_HEART : HEART;
		g.blit(RenderPipelines.GUI_TEXTURED, heart, cx - size / 2, hy, 0.0F, 0.0F, size, size, HEART_SIZE, HEART_SIZE, HEART_SIZE, HEART_SIZE, alpha << 24 | 0xFFFFFF);
	}

	/** The choices wait until the screen has come up, so nothing is chosen by a stray click. */
	private boolean ready() {
		return this.shown && this.shownFor() > 1.4F;
	}

	/** A choice written as "> words": brightens and eases forward under the pointer. */
	private final class Choice extends AbstractButton {
		private final Runnable action;
		private float hover;
		private long last = System.nanoTime();

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
			long now = System.nanoTime();
			float dt = Math.min(0.1F, (now - this.last) / 1.0E9F);
			this.last = now;
			this.hover = life.shizune.mistinn.client.anim.Motion.approach(this.hover, this.isHoveredOrFocused() ? 1.0F : 0.0F, dt, 16.0F);
			// the choices rise into place and fade in once the screen is ready
			float in = life.shizune.mistinn.client.anim.Motion.outCubic(life.shizune.mistinn.client.anim.Motion.progress(MissionEndScreen.this.shownFor(), 1.4F, 0.5F));
			int alpha = Math.max(8, (int) (255 * in));
			Font font = MissionEndScreen.this.font;
			Component text = Component.literal("> ").append(this.getMessage());
			int x = this.getX() + (this.getWidth() - font.width(text)) / 2 + Math.round(3.0F * this.hover);
			int y = this.getY() + 6 + Math.round(4.0F * (1.0F - in));
			int r = (int) Mth.lerp(this.hover, 0xB8, 0xF2);
			int gr = (int) Mth.lerp(this.hover, 0xB0, 0xCC);
			int b = (int) Mth.lerp(this.hover, 0xA0, 0x80);
			g.text(font, text, x, y, alpha << 24 | r << 16 | gr << 8 | b, true);
		}

		@Override
		protected void updateWidgetNarration(final NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}
}
