package life.shizune.mistinn.client.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * One card of the opening, after the game's own logo and before the title screen: black, silent, a few seconds of a
 * mark coming in and going out. Any key or click skips it (after a moment, so the click that opened the game does not),
 * and it goes on to what was to come.
 */
public abstract class IntroScreen extends Screen {
	/** Seconds before a key or click skips the card. */
	private static final float GUARD = 0.25F;
	/** For the client game test: the time every card is drawn at, or null to run on the clock. */
	private static @Nullable Float frozen;

	private final Runnable next;
	private final long opened = System.nanoTime();
	private boolean done;

	protected IntroScreen(final Component title, final Runnable next) {
		super(title);
		this.next = next;
	}

	/** How long the card runs, in seconds. */
	protected abstract float length();

	/** Draws the card at {@code t} seconds, at {@code fade} (1 while it is up, falling to 0 as it goes out). */
	protected abstract void draw(GuiGraphicsExtractor g, float t, float fade);

	/** Seconds since the card came up (or the time the test holds it at). */
	public float seconds() {
		return frozen != null ? frozen : (System.nanoTime() - this.opened) / 1.0E9F;
	}

	/** For the client game test: hold every card at {@code t} seconds (null runs them on the clock again). */
	public static void freeze(final @Nullable Float t) {
		frozen = t;
	}

	@Override
	public void tick() {
		if (frozen == null && this.seconds() >= this.length()) {
			this.finish();
		}
	}

	/** Leaves the card for what was to come; once. */
	public void finish() {
		if (!this.done) {
			this.done = true;
			this.next.run();
		}
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (this.seconds() >= GUARD) {
			this.finish();
		}
		return true;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (this.seconds() >= GUARD) {
			this.finish();
		}
		return true;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		g.fill(0, 0, this.width, this.height, 0xFF000000);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		this.extractBackground(g, mouseX, mouseY, a);
		float t = this.seconds();
		float out = Mth.clamp((t - (this.length() - 0.6F)) / 0.6F, 0.0F, 1.0F);
		this.draw(g, t, 1.0F - out);
	}

	/** The size in GUI units that draws {@code pixels} texture pixels one to one on this window. */
	protected static float oneToOne(final int pixels) {
		return pixels / (float) Minecraft.getInstance().getWindow().getGuiScale();
	}

	/** 0 before {@code start}, 1 after {@code start + length}, eased out between. */
	protected static float ease(final float t, final float start, final float length) {
		float x = Mth.clamp((t - start) / length, 0.0F, 1.0F);
		return 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x);
	}

	/** Whether a card is on screen now: the opening is silent. */
	public static boolean showing() {
		return Minecraft.getInstance().gui.screen() instanceof IntroScreen;
	}

	/** The client game tests start without the opening (it would stand before every one of them), unless a test asks. */
	private static boolean tested;

	/** Whether the opening plays as the game starts: always, outside the game tests. */
	public static boolean due() {
		return !net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mistinn-test") || tested;
	}

	/** For the client game test: whether the opening may play in a test run. */
	public static void test(final boolean on) {
		tested = on;
	}
}
