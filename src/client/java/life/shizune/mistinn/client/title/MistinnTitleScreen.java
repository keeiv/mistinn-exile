package life.shizune.mistinn.client.title;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnSounds;
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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The title screen of 霧宿：追放: a dead lighthouse on a misty shore at dusk. The art is layered
 * pixel art (480x270) drawn to cover the screen; fog drifts in three bands, glints run across
 * the water, salt motes rise off the shore, an ember flickers in the dead lamp room, and every
 * layer shifts a little with the mouse. The menu sits in the mist on the left, souls-style.
 * It is a {@link TitleScreen} (other mods and the game test look for one) that keeps none of
 * the vanilla parts: no panorama, logo, splash or buttons, only the version line.
 */
public class MistinnTitleScreen extends TitleScreen {
	private static final Identifier WORDMARK_ZH = tex("wordmark_zh");
	private static final Identifier WORDMARK_EN = tex("wordmark_en");
	private static final int WORDMARK_ZH_W = 516;
	private static final int WORDMARK_EN_W = 612;
	private static final int WORDMARK_H = 120;
	private static final int SPLASH_COUNT = 50;
	private static final float FADE_SECONDS = 1.6F;

	private final long opened = System.nanoTime();
	private final List<MenuButton> menu = new ArrayList<>();
	/** One line for this visit, so resizing or going back from options does not reroll it. */
	private final Component splash = Component.translatable("title.mistinn.splash." + (RandomSource.create().nextInt(SPLASH_COUNT) + 1));
	private @Nullable SimpleSoundInstance shoreBell;
	private int mouseX;
	private int mouseY;
	/** How far the wordmark and the menu move up so the menu's last entry stays clear of the icons below it. */
	private int lift;

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
		// again after coming back from another screen, which stopped it
		if (this.shoreBell == null || !this.minecraft.getSoundManager().isActive(this.shoreBell)) {
			this.shoreBell = SimpleSoundInstance.forMusic(MistinnSounds.MUSIC_SHORE);
			this.minecraft.getSoundManager().play(this.shoreBell);
		}
		this.menu.clear();
		int x = Math.max(24, this.width / 14);
		int w = 150;
		int step = 22;
		int entries = FabricLoader.getInstance().isModLoaded("modmenu") ? 6 : 5;
		// on a short screen the whole block moves up, so the last entry clears the language and accessibility icons
		this.lift = Math.max(0, this.height / 2 - 6 + (entries - 1) * step + 20 - (this.height - 40));
		int y = this.height / 2 - 6 - this.lift;
		this.add(new MenuButton(x, y, w, Component.translatable("menu.singleplayer"), () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))));
		MenuButton multiplayer = this.add(new MenuButton(x, y += step, w, Component.translatable("menu.multiplayer"), () -> {
			Screen screen = this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
			this.minecraft.gui.setScreen(screen);
		}));
		multiplayer.active = this.minecraft.allowsMultiplayer();
		this.add(new MenuButton(x, y += step, w, Component.translatable("menu.mistinn.world_map"),
			() -> this.minecraft.gui.setScreen(new life.shizune.mistinn.client.worldmap.WorldMapScreen(this))));
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

	@Override
	public void removed() {
		super.removed();
		if (this.shoreBell != null) {
			this.minecraft.getSoundManager().stop(this.shoreBell);
		}
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
		ShoreBackdrop.draw(g, this.width, this.height, this.mouseX, this.mouseY, (System.nanoTime() - this.opened) / 1.0E9F, true);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		super.extractRenderState(g, mouseX, mouseY, a);
		int x = Math.max(24, this.width / 14);
		boolean english = this.minecraft.getLanguageManager().getSelected().startsWith("en");
		int textureWidth = english ? WORDMARK_EN_W : WORDMARK_ZH_W;
		float markScale = Math.min(230, this.width / 3) / (float) WORDMARK_ZH_W;
		int markW = Math.round(textureWidth * markScale);
		int markH = Math.round(WORDMARK_H * markScale);
		int markY = this.height / 2 - 26 - markH - 14 - this.lift;
		g.blit(RenderPipelines.GUI_TEXTURED, english ? WORDMARK_EN : WORDMARK_ZH, x - 4, markY, 0.0F, 0.0F, markW, markH, textureWidth, WORDMARK_H, textureWidth, WORDMARK_H);
		int taglineWidth = Math.min(this.width - x - 8, Math.max(190, this.width / 2 - x));
		float elapsed = (System.nanoTime() - this.opened) / 1.0E9F;
		if (!this.minecraft.options.hideSplashTexts().get()) {
			this.drawSplash(g, x, markY, taglineWidth, elapsed);
		}
		int taglineY = markY + markH + 6;
		for (FormattedCharSequence line : this.font.split(Component.translatable("title.mistinn.tagline"), taglineWidth)) {
			g.text(this.font, line, x, taglineY, 0xFFB8B0A0, true);
			taglineY += this.font.lineHeight + 2;
		}

		// the screen comes up out of the dark
		float fade = 1.0F - Math.min(1.0F, elapsed / FADE_SECONDS);
		if (fade > 0.0F) {
			g.fill(0, 0, this.width, this.height, (int) (fade * fade * 255) << 24);
		}
	}

	/** The line brightens and dims like the ember across the water. */
	private void drawSplash(final GuiGraphicsExtractor g, final int x, final int markY, final int width, final float elapsed) {
		List<FormattedCharSequence> lines = this.font.split(this.splash, width);
		int spacing = this.font.lineHeight + 2;
		int y = markY - 8 - lines.size() * spacing;
		int alpha = Math.round(224.0F + 24.0F * Mth.sin(elapsed * 1.55F));
		for (FormattedCharSequence line : lines) {
			g.text(this.font, line, x, y, alpha << 24 | 0xEDC27F, true);
			y += spacing;
		}
	}
}
