package life.shizune.mistinn.client.ledger;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.story.SceneScreen;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.story.StoryScript;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The one time it looks back. When the ledger has gone wrong, the Snuffer's last words do not end: after them, a
 * silence (and Esc does nothing) in which it slowly turns its back on the view; then one more line, the number the
 * ledger keeps of how many have been sent down; the view stops; then its head alone comes round, all the way, to the
 * view and not to the hunter; two beats; its face, for half
 * a second, with the sound; dark; and the lamp room, lit and empty. It is remembered at once, and never shown again.
 * With scares set off it never happens.
 */
public final class SnufferScare {
	/** Where the scene is. */
	public enum Phase {
		NONE, WORDS, TURN, STILL, FACE, DARK
	}

	public static final Identifier FACE_PICTURE = Mistinn.id("textures/gui/story/scare_face.png");
	/** The added lines: the silence, and the count. */
	public static final String SILENCE = "story.mistinn.radio.snuffer_end.silence";
	public static final String COUNT = "story.mistinn.radio.snuffer_end.count";
	/** Ticks of each part: the silence's length, the wait after the count is typed, the turn, the stillness, the face, the dark. */
	public static final int SILENT = 90;
	private static final int AFTER_COUNT = 30;
	public static final int TURNING = 22;
	private static final int STILLNESS = 34;
	public static final int FACE_TICKS = 12;
	private static final int DARKNESS = 50;
	/** The card shown after the dark. */
	public static final String CARD = "lamp_lit";

	private static Phase phase = Phase.NONE;
	private static int focus = -1;
	private static int ticks;
	/** Ticks since its last words ended and the silence began. */
	private static int quiet;
	/** How long it takes to turn its back on the view, in the silence. */
	public static final int TURNING_AWAY = 60;

	private SnufferScare() {
	}

	/** Whether the wrong is due: the ledger has reached it, it has not been seen, and scares are on. */
	public static boolean due() {
		return Haunting.scares() && LedgerClient.ledger().stage() == Ledger.Stage.WRONG;
	}

	/** The Snuffer's last words, with what comes after them when the wrong is due (else as they are). */
	public static List<StoryScript.Line> lines(final String sequence, final int entity) {
		List<StoryScript.Line> lines = new ArrayList<>(StoryScript.sequence(sequence));
		if (StoryScript.SNUFFER_END.equals(sequence) && entity >= 0 && due()) {
			lines.add(new StoryScript.Line(StoryScript.NARRATION, SILENCE));
			lines.add(new StoryScript.Line(StoryScript.SNUFFER, COUNT));
			phase = Phase.WORDS;
			focus = entity;
			ticks = 0;
			quiet = 0;
		}
		return lines;
	}

	public static Phase phase() {
		return phase;
	}

	/** Whether Esc does nothing now: from the silence on. */
	public static boolean holding(final int line) {
		return phase != Phase.NONE && line >= StoryScript.sequence(StoryScript.SNUFFER_END).size();
	}

	/** How long a line of the scene stays: the silence a long while, the count until the scene ends it. */
	public static int length(final String key, final int usual) {
		if (SILENCE.equals(key)) {
			return SILENT;
		}
		return COUNT.equals(key) ? Integer.MAX_VALUE / 2 : usual;
	}

	/** How far the Snuffer has turned its back on the view in the silence, 0 to 1, for this entity (0 for any other). */
	public static float away(final int entity, final float partial) {
		if (entity != focus || phase == Phase.NONE || quiet <= 0) {
			return 0.0F;
		}
		float t = Mth.clamp((quiet + partial) / TURNING_AWAY, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	/** How far the view has come in to its head, 0 to 1: half way while the head turns, the rest in the stillness. */
	public static float closing(final float partial) {
		return switch (phase) {
			case TURN -> 0.5F * Mth.clamp((ticks + partial) / TURNING, 0.0F, 1.0F);
			case STILL -> 0.5F + 0.5F * Mth.clamp((ticks + partial) / STILLNESS, 0.0F, 1.0F);
			case FACE, DARK -> 1.0F;
			default -> 0.0F;
		};
	}

	/** Whether the view stops circling: from the count on, it holds still while the head comes round. */
	public static boolean viewHeld(final int line) {
		return phase != Phase.NONE && line >= StoryScript.sequence(StoryScript.SNUFFER_END).size() + 1;
	}

	/** How far the Snuffer's head has turned to the view, 0 to 1, for this entity (0 for any other). */
	public static float turned(final int entity, final float partial) {
		if (entity != focus || phase.ordinal() < Phase.TURN.ordinal()) {
			return 0.0F;
		}
		if (phase != Phase.TURN) {
			return 1.0F;
		}
		float t = Mth.clamp((ticks + partial) / TURNING, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	/**
	 * Each client tick, with the scene's state: {@code onCount} when the count is the line on screen, {@code typed} once it
	 * is all typed out. Returns true when the scene is over and the card should follow.
	 */
	public static boolean tick(final boolean silent, final boolean onCount, final boolean typed) {
		if (phase == Phase.NONE) {
			return false;
		}
		ticks++;
		if (silent) {
			quiet++;
		}
		switch (phase) {
			case WORDS -> {
				if (!onCount || !typed) {
					ticks = 0;
				} else if (ticks >= AFTER_COUNT) {
					next(Phase.TURN);
				}
			}
			case TURN -> {
				if (ticks >= TURNING) {
					next(Phase.STILL);
				}
			}
			case STILL -> {
				if (ticks >= STILLNESS) {
					next(Phase.FACE);
					// remembered before anything else can happen: it is never shown twice
					LedgerClient.ledger().see("scare.snuffer");
					Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(MistinnSounds.SCARE_STING, 1.0F, 1.0F));
				}
			}
			case FACE -> {
				if (ticks >= FACE_TICKS) {
					next(Phase.DARK);
				}
			}
			case DARK -> {
				if (ticks >= DARKNESS) {
					forget();
					return true;
				}
			}
			default -> {
			}
		}
		return false;
	}

	private static void next(final Phase to) {
		phase = to;
		ticks = 0;
	}

	/** The scene closed some other way (the world left): nothing more. */
	public static void forget() {
		phase = Phase.NONE;
		focus = -1;
		ticks = 0;
		quiet = 0;
	}

	/** The view's place, for the Snuffer to turn to. */
	public static net.minecraft.world.phys.@org.jspecify.annotations.Nullable Vec3 eye(final float partial) {
		SceneScreen scene = SceneScreen.current();
		return scene == null ? null : scene.eye(partial);
	}
}
