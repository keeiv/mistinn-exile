package life.shizune.mistinn.client.title;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommonButtons;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * The title screen of 霧宿：追放: a dead lighthouse on a misty shore at dusk. The art is layered
 * pixel art (480x270) drawn to cover the screen; fog drifts in three bands, glints run across
 * the water, salt motes rise off the shore, an ember flickers in the dead lamp room, and every
 * layer shifts a little with the mouse. The menu sits in the mist on the left, souls-style.
 * It is a {@link TitleScreen} (other mods and the game test look for one) that keeps none of
 * the vanilla parts: no panorama, logo, splash or buttons, only the version line.
 */
public class MistinnTitleScreen extends TitleScreen {
	private static final int ART_W = 480;
	private static final int ART_H = 270;
	private static final Identifier SKY = tex("sky");
	private static final Identifier CLIFF = tex("cliff");
	private static final Identifier SEA = tex("sea");
	private static final Identifier GLINTS = tex("glints");
	private static final Identifier FOG_FAR = tex("fog_far");
	private static final Identifier FOG_MID = tex("fog_mid");
	private static final Identifier FOG_NEAR = tex("fog_near");
	private static final Identifier SHORE = tex("shore");
	private static final Identifier WORDMARK_ZH = tex("wordmark_zh");
	private static final Identifier WORDMARK_EN = tex("wordmark_en");
	private static final int WORDMARK_W = 516;
	private static final int WORDMARK_H = 120;
	/** The dead lamp room's glass, in art pixels. */
	private static final int LAMP_X = 389;
	private static final int LAMP_Y = 17;
	private static final int MOTES = 46;
	private static final float FADE_SECONDS = 1.6F;

	private final long opened = System.nanoTime();
	private final List<MenuButton> menu = new ArrayList<>();
	private int mouseX;
	private int mouseY;

	public MistinnTitleScreen() {
		super(false, new NoLogo());
	}

	/** The vanilla logo is replaced by our wordmark. */
	private static final class NoLogo extends LogoRenderer {
		NoLogo() {
			super(true);
		}

		@Override
		public void extractRenderState(final GuiGraphicsExtractor graphics, final int width, final float alpha) {
		}
	}

	private static Identifier tex(final String name) {
		return Mistinn.id("textures/gui/title/" + name + ".png");
	}

	@Override
	protected void init() {
		this.menu.clear();
		int x = Math.max(24, this.width / 14);
		int y = this.height / 2 - 6;
		int w = 150;
		int step = 22;
		this.add(new MenuButton(x, y, w, Component.translatable("menu.singleplayer"), () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))));
		MenuButton multiplayer = this.add(new MenuButton(x, y += step, w, Component.translatable("menu.multiplayer"), () -> {
			Screen screen = this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
			this.minecraft.gui.setScreen(screen);
		}));
		multiplayer.active = this.minecraft.allowsMultiplayer();
		if (FabricLoader.getInstance().isModLoaded("modmenu")) {
			this.add(new MenuButton(x, y += step, w, Component.translatable("menu.mistinn.mods"), this::openModMenu));
		}
		this.add(new MenuButton(x, y += step, w, Component.translatable("menu.options"), () -> this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options))));
		this.add(new MenuButton(x, y + step, w, Component.translatable("menu.quit"), () -> this.minecraft.stop()));

		this.addRenderableWidget(CommonButtons.language(20, b -> this.minecraft.gui.setScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager())), true))
			.setPosition(x, this.height - 36);
		this.addRenderableWidget(CommonButtons.accessibility(20, b -> this.minecraft.gui.setScreen(new AccessibilityOptionsScreen(this, this.minecraft.options)), true))
			.setPosition(x + 24, this.height - 36);
		Component credits = Component.translatable("title.credits");
		int creditsWidth = this.font.width(credits);
		this.addRenderableWidget(new PlainTextButton(this.width - creditsWidth - 4, this.height - 10, creditsWidth, 10, credits,
			b -> this.minecraft.gui.setScreen(new CreditsAndAttributionScreen(this)), this.font));
	}

	private MenuButton add(final MenuButton button) {
		this.menu.add(button);
		return this.addRenderableWidget(button);
	}

	/** Mod Menu's list, opened without a compile-time dependency on it. */
	private void openModMenu() {
		try {
			Class<?> screen = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
			this.minecraft.gui.setScreen((Screen) screen.getConstructor(Screen.class).newInstance(this));
		} catch (ReflectiveOperationException | ClassCastException e) {
			Mistinn.LOGGER.warn("Could not open the mod list", e);
		}
	}

	// ── Drawing ────────────────────────────────────────────────────

	/** Vanilla draws its panorama here; we draw the shore. */
	@Override
	protected void extractPanorama(final GuiGraphicsExtractor g, final float a) {
		int mouseX = this.mouseX;
		int mouseY = this.mouseY;
		float time = (System.nanoTime() - this.opened) / 1.0E9F;
		// cover the screen with the art, keeping its shape; the mouse leans every layer a little
		float scale = Math.max((float) this.width / ART_W, (float) this.height / ART_H);
		int w = Mth.ceil(ART_W * scale);
		int h = Mth.ceil(ART_H * scale);
		int ox = (this.width - w) / 2;
		int oy = (this.height - h) / 2;
		float lookX = (mouseX - this.width / 2.0F) / Math.max(1, this.width);
		float lookY = (mouseY - this.height / 2.0F) / Math.max(1, this.height);

		layer(g, SKY, ox, oy, w, h, 0.0F, lookX, lookY);
		layer(g, CLIFF, ox, oy, w, h, 4.0F, lookX, lookY);
		this.ember(g, ox, oy, scale, time, lookX, lookY);
		layer(g, SEA, ox, oy, w, h, 6.0F, lookX, lookY);
		scroll(g, GLINTS, ox, oy + Math.round(160 * scale), w, Math.round(60 * scale), scale, time * 6.0F, 6.0F, lookX, lookY, 0xFFFFFFFF);
		scroll(g, FOG_FAR, ox, oy + Math.round(96 * scale), w, Math.round(120 * scale), scale, time * 3.0F, 8.0F, lookX, lookY, 0xC0FFFFFF);
		scroll(g, FOG_MID, ox, oy + Math.round(132 * scale), w, Math.round(120 * scale), scale, -time * 5.0F, 12.0F, lookX, lookY, 0xD0FFFFFF);
		layer(g, SHORE, ox, oy, w, h, 16.0F, lookX, lookY);
		this.motes(g, ox, oy, scale, time);
		scroll(g, FOG_NEAR, ox, oy + Math.round(176 * scale), w, Math.round(120 * scale), scale, time * 9.0F, 22.0F, lookX, lookY, 0xB0FFFFFF);

		// the mist thickens behind the menu on the left
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(0.0F, this.height);
		pose.rotate((float) (-Math.PI / 2));
		g.fillGradient(0, 0, this.height, Math.min(this.width / 2, 300), 0xC0101418, 0x00101418);
		pose.popMatrix();
	}

	/** A full-screen layer, shifted against the mouse by {@code depth} screen pixels at most. */
	private static void layer(final GuiGraphicsExtractor g, final Identifier texture, final int x, final int y, final int w, final int h, final float depth, final float lookX, final float lookY) {
		int dx = Math.round(-lookX * depth);
		int dy = Math.round(-lookY * depth * 0.5F);
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x + dx, y + dy, 0.0F, 0.0F, w, h, ART_W, ART_H, ART_W, ART_H);
	}

	/** A band that tiles sideways (960 art pixels wide), scrolled by {@code offset} art pixels. */
	private static void scroll(final GuiGraphicsExtractor g, final Identifier texture, final int x, final int y, final int w, final int h, final float scale,
		final float offset, final float depth, final float lookX, final float lookY, final int colour) {
		int texW = 960;
		int texH = Math.round(h / scale);
		float u = ((offset % texW) + texW) % texW;
		int dx = Math.round(-lookX * depth);
		int dy = Math.round(-lookY * depth * 0.5F);
		// the visible window may run past the texture's right edge: draw the rest from its start
		float first = Math.min(ART_W, texW - u);
		int firstW = Math.round(first * scale);
		g.blit(RenderPipelines.GUI_TEXTURED, texture, x + dx, y + dy, u, 0.0F, firstW, h, Math.round(first), texH, texW, texH, colour);
		if (first < ART_W) {
			float rest = ART_W - first;
			g.blit(RenderPipelines.GUI_TEXTURED, texture, x + dx + firstW, y + dy, 0.0F, 0.0F, w - firstW, h, Math.round(rest), texH, texW, texH, colour);
		}
	}

	/** The last ember in the dead lamp room: dim, unsteady, sometimes nearly out. */
	private void ember(final GuiGraphicsExtractor g, final int ox, final int oy, final float scale, final float time, final float lookX, final float lookY) {
		float flicker = 0.55F + 0.25F * Mth.sin(time * 2.3F) + 0.2F * Mth.sin(time * 7.1F + 1.3F);
		int alpha = Mth.clamp((int) (flicker * 200), 20, 230);
		int x = ox + Math.round(LAMP_X * scale) + Math.round(-lookX * 4.0F);
		int y = oy + Math.round(LAMP_Y * scale) + Math.round(-lookY * 2.0F);
		int size = Math.max(2, Math.round(3 * scale));
		g.fill(x + size, y + size / 2, x + size * 2, y + size + size / 2, alpha << 24 | 0xE89040);
		g.fill(x + size / 2, y, x + size * 2 + size / 2, y + size * 2, (alpha / 5) << 24 | 0xE89040);
	}

	/** Salt motes lifting off the shore and fading as they rise. */
	private void motes(final GuiGraphicsExtractor g, final int ox, final int oy, final float scale, final float time) {
		int size = Math.max(1, Math.round(scale));
		for (int i = 0; i < MOTES; i++) {
			float seed = i * 12.9898F;
			float speed = 4.0F + (Mth.sin(seed) * 0.5F + 0.5F) * 6.0F;
			float life = 90.0F / speed;
			float t = ((time + i * 0.37F) % life) / life;
			float baseX = (Mth.sin(seed * 3.1F) * 0.5F + 0.5F) * ART_W;
			float x = baseX + Mth.sin(time * 0.8F + seed) * 6.0F;
			float y = 222.0F - t * 90.0F;
			int alpha = (int) (200 * Mth.sin(t * Mth.PI));
			g.fill(ox + Math.round(x * scale), oy + Math.round(y * scale), ox + Math.round(x * scale) + size, oy + Math.round(y * scale) + size, alpha << 24 | 0xE6E2D6);
		}
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		super.extractRenderState(g, mouseX, mouseY, a);
		int x = Math.max(24, this.width / 14);
		boolean english = this.minecraft.getLanguageManager().getSelected().startsWith("en");
		int markW = Math.min(230, this.width / 3);
		int markH = Math.round(markW * WORDMARK_H / (float) WORDMARK_W);
		int markY = this.height / 2 - 26 - markH - 14;
		g.blit(RenderPipelines.GUI_TEXTURED, english ? WORDMARK_EN : WORDMARK_ZH, x - 4, markY, 0.0F, 0.0F, markW, markH, WORDMARK_W, WORDMARK_H, WORDMARK_W, WORDMARK_H);
		g.text(this.font, Component.translatable("title.mistinn.tagline"), x, markY + markH + 6, 0xFFB8B0A0, true);

		// the screen comes up out of the dark
		float fade = 1.0F - Math.min(1.0F, (System.nanoTime() - this.opened) / 1.0E9F / FADE_SECONDS);
		if (fade > 0.0F) {
			g.fill(0, 0, this.width, this.height, (int) (fade * fade * 255) << 24);
		}
	}
}
