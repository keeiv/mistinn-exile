package life.shizune.mistinn.client.medical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import life.shizune.mistinn.medical.Treatment;
import life.shizune.mistinn.medical.TreatmentService;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * 處置音效: the sounds of a timed use, laid on the beats of its animation. Each use has one-off
 * cues (the injector's cap, the needle going in) and repeated beats (hammer blows, stitches)
 * that follow the work rhythm: in one's own first-person view the faster first-person beat
 * ({@code first_person_loop}), otherwise the body's loop seen from outside. The sounds follow
 * the hunter, and a use broken off cuts them short.
 */
public final class TreatmentSounds {
	/** A one-off sound {@code at} seconds into the use; a negative time counts back from its end. */
	private record Cue(float at, SoundEvent sound, float volume) {
	}

	/**
	 * A repeated sound: from {@code from} seconds, every {@code period} at {@code phase} into
	 * the period, until {@code tail} seconds before the end.
	 */
	private record Beat(float from, float period, float phase, SoundEvent sound, float volume, float tail) {
	}

	/** What a treatment sounds like: the same beat timed for one's own view and for others. */
	private record Score(List<Cue> cues, List<Beat> ownView, List<Beat> seen) {
	}

	private static final Map<Treatment, Score> SCORES = Map.of(
		// the injector (3 s): the cap off, the needle in as it is driven into the thigh (keys 0.32
		// to 0.4), the serum pushed while it is held there, and out again (keys 0.8 to 0.9)
		Treatment.INJECT, new Score(List.of(
			new Cue(0.05F, MistinnSounds.INJECT_CAP, 0.7F),
			new Cue(1.0F, MistinnSounds.INJECT_STAB, 0.9F),
			new Cue(1.18F, MistinnSounds.INJECT_PUSH, 0.75F),
			new Cue(2.5F, MistinnSounds.INJECT_OUT, 0.8F)
		), List.of(), List.of()),
		// painkillers (2 s): shaken on the way up, tipped with the head back (keys 0.25 to 0.45), swallowed
		Treatment.PILLS, new Score(List.of(
			new Cue(0.0F, MistinnSounds.PILLS_RATTLE, 0.7F),
			new Cue(0.55F, MistinnSounds.PILLS_POUR, 0.7F),
			new Cue(1.2F, MistinnSounds.PILLS_SWALLOW, 0.8F)
		), List.of(), List.of()),
		// the surgery kit (8 s): opened, three cuts through the ruined flesh, then stitch after
		// stitch, and the thread tied off and snipped at the end
		Treatment.SURGERY, new Score(List.of(
			new Cue(0.0F, MistinnSounds.SURGERY_OPEN, 0.7F),
			new Cue(0.55F, MistinnSounds.SURGERY_CUT, 0.9F),
			new Cue(1.1F, MistinnSounds.SURGERY_CUT, 0.9F),
			new Cue(1.6F, MistinnSounds.SURGERY_CUT, 0.9F),
			new Cue(-0.4F, MistinnSounds.SURGERY_TIE, 0.8F)
		),
			// one's own view: a stitch on every other quick dip of the hand (0.5 s beat, dip at 0.22)
			List.of(new Beat(2.1F, 1.0F, 0.11F, MistinnSounds.SURGERY_STITCH, 0.8F, 0.7F)),
			// seen from outside: the needle drawn up and out (2 s loop, key 0.3)
			List.of(new Beat(2.1F, 2.0F, 0.6F, MistinnSounds.SURGERY_STITCH, 0.8F, 0.7F))),
		// armour: the splint drawn out, then a hammer blow on each press of the hand
		Treatment.ARMOR_REPAIR, new Score(List.of(
			new Cue(0.0F, MistinnSounds.REPAIR_PLATE, 0.8F)
		),
			// own view: the press down of the 0.6 s beat (key 0.14)
			List.of(new Beat(0.8F, 0.6F, 0.084F, MistinnSounds.REPAIR_HAMMER, 0.75F, 0.25F)),
			// from outside: the tap of the 1.2 s loop (key 0.35)
			List.of(new Beat(0.8F, 1.2F, 0.42F, MistinnSounds.REPAIR_HAMMER, 0.75F, 0.25F))),
		// the helmet: the splint drawn out, then turned tight with the ratchet, and a light tap
		Treatment.HELMET_REPAIR, new Score(List.of(
			new Cue(0.0F, MistinnSounds.REPAIR_PLATE, 0.8F)
		),
			List.of(
				new Beat(0.8F, 1.2F, 0.084F, MistinnSounds.REPAIR_RATCHET, 0.7F, 0.25F),
				new Beat(0.8F, 1.2F, 0.684F, MistinnSounds.REPAIR_HAMMER, 0.5F, 0.25F)
			),
			List.of(
				new Beat(0.8F, 1.4F, 0.49F, MistinnSounds.REPAIR_RATCHET, 0.7F, 0.25F),
				new Beat(0.8F, 1.4F, 0.91F, MistinnSounds.REPAIR_HAMMER, 0.5F, 0.25F)
			))
	);

	/** A use being scored: how far it has been heard, and the sounds still playing. */
	private static final class Heard {
		final Treatment treatment;
		final float duration;
		final long start = System.nanoTime();
		float last = -1.0F;
		final List<SoundInstance> playing = new ArrayList<>();

		Heard(final Treatment treatment, final float duration) {
			this.treatment = treatment;
			this.duration = duration;
		}
	}

	private static final Map<Integer, Heard> HEARD = new HashMap<>();
	/** For the client game test: the last sounds scored, by id path, newest last. */
	public static final java.util.Deque<String> LOG = new java.util.ArrayDeque<>();

	private TreatmentSounds() {
	}

	/** A use began (or ended) for {@code entity}, as the server said. */
	public static void received(final TreatmentService.TreatmentPayload payload) {
		if (payload.treatment() >= 0) {
			stop(HEARD.remove(payload.entity()));
			HEARD.put(payload.entity(), new Heard(Treatment.byId(payload.treatment()), payload.ticks() / 20.0F));
			return;
		}
		Heard heard = HEARD.remove(payload.entity());
		if (payload.result() == TreatmentService.RESULT_INTERRUPTED) {
			// broken off: whatever was mid-sound stops with the hands
			stop(heard);
		}
	}

	public static void tick(final Minecraft mc) {
		if (mc.level == null || mc.player == null || TreatmentClient.preview >= 0.0F) {
			HEARD.values().forEach(TreatmentSounds::stop);
			HEARD.clear();
			return;
		}
		long now = System.nanoTime();
		HEARD.entrySet().removeIf(entry -> {
			Entity entity = mc.level.getEntity(entry.getKey());
			Heard heard = entry.getValue();
			if (entity == null) {
				stop(heard);
				return true;
			}
			float seconds = Math.min(heard.duration, (now - heard.start) / 1.0E9F);
			Score score = SCORES.get(heard.treatment);
			if (score != null) {
				boolean ownView = entity == mc.player && mc.options.getCameraType().isFirstPerson();
				for (Cue cue : score.cues()) {
					float at = cue.at() < 0.0F ? heard.duration + cue.at() : cue.at();
					if (at > heard.last && at <= seconds) {
						play(mc, heard, entity, cue.sound(), cue.volume());
					}
				}
				for (Beat beat : ownView ? score.ownView() : score.seen()) {
					float until = heard.duration - beat.tail();
					// every beat time in (last, seconds]
					long first = (long) Math.ceil((Math.max(heard.last, beat.from() - 1.0E-4F) - beat.phase()) / beat.period());
					for (long k = Math.max(0, first); ; k++) {
						float at = beat.phase() + k * beat.period();
						if (at > seconds || at > until) {
							break;
						}
						if (at > heard.last && at >= beat.from()) {
							play(mc, heard, entity, beat.sound(), beat.volume());
						}
					}
				}
			}
			heard.last = seconds;
			heard.playing.removeIf(sound -> !mc.getSoundManager().isActive(sound));
			return seconds >= heard.duration && heard.playing.isEmpty();
		});
	}

	private static void play(final Minecraft mc, final Heard heard, final Entity entity, final SoundEvent sound, final float volume) {
		float pitch = 0.95F + entity.getRandom().nextFloat() * 0.1F;
		SoundInstance instance = new EntityBoundSoundInstance(sound, SoundSource.PLAYERS, volume, pitch, entity, entity.getRandom().nextLong());
		mc.getSoundManager().play(instance);
		heard.playing.add(instance);
		LOG.add(sound.location().getPath());
		while (LOG.size() > 64) {
			LOG.poll();
		}
	}

	private static void stop(final Heard heard) {
		if (heard == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		heard.playing.forEach(mc.getSoundManager()::stop);
		heard.playing.clear();
	}
}
