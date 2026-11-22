package life.shizune.mistinn.client.voice;

import life.shizune.mistinn.client.view.ViewMotion;
import life.shizune.mistinn.voice.Voice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/**
 * 自己的聲音: the client half of a hunter's voice. The first time a hunter stands in a world without a voice, in any
 * mode, they are asked which they will play with ({@link VoiceScreen}). After that they hear themself: breathing hard
 * when winded, panting when spent, and the wind knocked out of them by a hard landing.
 */
public final class VoiceClient {
	/** Ticks in a world before the question is asked, so the world is there to be seen behind it. */
	private static final int SETTLE = 30;
	/** Ticks an answer is given to reach the server and come back before the question may be asked again. */
	private static final int ANSWER_WAIT = 200;
	/** How winded a hunter is before they are heard breathing, and before the breaths turn to panting. */
	public static final float BREATHING = 0.3F;
	public static final float PANTING = 0.8F;
	/** A landing this hard knocks the wind out. */
	public static final float HARD_LANDING = 0.45F;

	/** Under the test harness nobody is there to answer: the question is asked only when a test wants it. */
	private static final boolean HARNESS = System.getProperty("fabric.client.gametest") != null;
	public static boolean askUnderHarness;

	private static int settled;
	private static int untilBreath;
	private static boolean landed;

	private VoiceClient() {
	}

	public static void tick(final Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			settled = 0;
			return;
		}
		Voice voice = Voice.of(player);
		if (voice == null) {
			settled++;
			if (settled >= SETTLE && shouldAsk(mc, player)) {
				mc.gui.setScreen(new VoiceScreen());
			}
			return;
		}
		settled = 0;
		if (!player.isAlive() || mc.isPaused()) {
			return;
		}
		float winded = ViewMotion.winded(1.0F);
		if (untilBreath > 0) {
			untilBreath--;
		}
		if (winded >= BREATHING && untilBreath == 0) {
			boolean spent = winded >= PANTING;
			play(mc, spent ? voice.pant : voice.breath, 0.35F + 0.4F * winded, 0.97F + player.getRandom().nextFloat() * 0.06F);
			// one breath follows another: quicker and shallower when spent
			untilBreath = spent ? 17 : 23;
		}
		boolean hard = ViewMotion.landing() >= HARD_LANDING;
		if (hard && !landed && player.hurtTime == 0) {
			play(mc, voice.land, 0.6F, 0.97F + player.getRandom().nextFloat() * 0.06F);
		}
		landed = hard;
	}

	/** The hunter has just answered: the question waits while the choice travels to the server and back. */
	public static void answered() {
		settled = -ANSWER_WAIT;
	}

	/** Whether the hunter should be asked now: nothing else is on screen or playing out. */
	public static boolean shouldAsk(final Minecraft mc, final LocalPlayer player) {
		if (HARNESS && !askUnderHarness) {
			return false;
		}
		return mc.gui.screen() == null && player.isAlive() && !player.isSpectator()
			&& life.shizune.mistinn.client.story.SceneScreen.current() == null
			&& life.shizune.mistinn.client.training.DropCinematic.view(mc, 1.0F) == null;
	}

	/** Heard by this hunter alone, at their own ear. */
	public static void play(final Minecraft mc, final SoundEvent sound, final float volume, final float pitch) {
		mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}
}
