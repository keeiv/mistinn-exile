package life.shizune.mistinn.client.story;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import life.shizune.mistinn.story.StoryProgress;
import life.shizune.mistinn.story.StoryScript;
import life.shizune.mistinn.story.StoryService;
import life.shizune.mistinn.story.StoryStep;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * The story on the client: the lines being spoken (radio subtitles, or an in-world scene), the
 * cards waiting to be shown, and the flash when the objective changes.
 */
public final class StoryClient {
	/** Characters typed per second in subtitles and cards. */
	public static final float TYPE_SPEED = 26.0F;
	/** How long a new objective stays highlighted, in ticks. */
	private static final int FLASH_TICKS = 100;

	/** A sequence being played: its lines, and the entity a scene holds the view on (-1 for none). */
	public record Playing(List<StoryScript.Line> lines, int focus, String cardAfter) {
	}

	private static @Nullable Playing playing;
	private static int line;
	private static int lineTicks;
	private static final Deque<String> CARDS = new ArrayDeque<>();
	private static @Nullable StoryStep lastStep;
	private static int flash;

	private StoryClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(StoryService.CardPayload.TYPE, (payload, context) -> CARDS.add(payload.card()));
		ClientPlayNetworking.registerGlobalReceiver(StoryService.LinesPayload.TYPE, (payload, context) -> start(payload));
	}

	private static void start(final StoryService.LinesPayload payload) {
		playing = new Playing(StoryScript.sequence(payload.sequence()), payload.focus(), payload.cardAfter());
		line = 0;
		lineTicks = 0;
		if (payload.focus() >= 0) {
			Minecraft mc = Minecraft.getInstance();
			mc.gui.setScreen(new SceneScreen(payload.focus()));
		}
	}

	public static @Nullable StoryProgress progress() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? null : mc.player.getAttached(StoryService.PROGRESS);
	}

	public static @Nullable Playing playing() {
		return playing;
	}

	public static boolean inScene() {
		return playing != null && playing.focus() >= 0;
	}

	/** The line on screen now, or null. */
	public static StoryScript.@Nullable Line line() {
		return playing == null || line >= playing.lines().size() ? null : playing.lines().get(line);
	}

	/** Seconds the current line has been showing. */
	public static float lineSeconds(final float partial) {
		return (lineTicks + partial) / 20.0F;
	}

	/** Ticks the new-objective highlight has left. */
	public static int flash() {
		return flash;
	}

	/** How long a line stays: typed out, then held long enough to read. */
	private static int lineLength(final StoryScript.Line current) {
		int chars = Component.translatable(current.key()).getString().length();
		return Math.round((chars / TYPE_SPEED + 1.6F + chars * 0.045F) * 20.0F);
	}

	/** Skips the rest of the line being typed, or on to the next line. */
	public static void advanceLine() {
		StoryScript.Line current = line();
		if (current == null) {
			return;
		}
		int chars = Component.translatable(current.key()).getString().length();
		int typed = Math.round(chars / TYPE_SPEED * 20.0F);
		if (lineTicks < typed) {
			lineTicks = typed;
		} else {
			line++;
			lineTicks = 0;
		}
	}

	/** Ends whatever is playing at once (a scene skipped with Esc). */
	public static void skip() {
		if (playing != null) {
			line = playing.lines().size();
		}
	}

	public static void tick(final Minecraft mc) {
		if (mc.player == null) {
			playing = null;
			CARDS.clear();
			lastStep = null;
			return;
		}
		StoryProgress progress = progress();
		StoryStep step = progress == null ? null : progress.step();
		if (step != null && lastStep != null && step != lastStep) {
			flash = FLASH_TICKS;
			mc.player.playSound(SoundEvents.UI_TOAST_IN, 0.8F, 1.0F);
		}
		lastStep = step;
		if (flash > 0) {
			flash--;
		}
		if (playing != null) {
			StoryScript.Line current = line();
			if (current != null && ++lineTicks > lineLength(current)) {
				line++;
				lineTicks = 0;
			}
			if (line() == null) {
				if (!playing.cardAfter().isEmpty()) {
					CARDS.add(playing.cardAfter());
				}
				boolean scene = playing.focus() >= 0;
				playing = null;
				if (scene && mc.gui.screen() instanceof SceneScreen) {
					mc.gui.setScreen(null);
				}
			}
		}
		if (!CARDS.isEmpty() && playing == null && mc.gui.screen() == null) {
			StoryScript.Card card = StoryScript.card(CARDS.poll());
			if (card != null) {
				mc.gui.setScreen(new StoryCardScreen(card));
			}
		}
	}
}
