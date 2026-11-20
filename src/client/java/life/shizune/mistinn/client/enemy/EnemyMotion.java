package life.shizune.mistinn.client.enemy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import life.shizune.mistinn.enemy.EnemyRig;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.enemy.ExecutionMove;
import life.shizune.mistinn.enemy.ExecutionService;
import life.shizune.mistinn.enemy.WindupService;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.warden.Warden;
import org.jspecify.annotations.Nullable;

/** Runs after vanilla poses its model, keeping attacks, casting, swimming and other species states intact. */
public final class EnemyMotion {
	public static final RenderStateDataKey<Sample> KEY = RenderStateDataKey.create(() -> "mistinn:enemy_motion");
	private static final Map<Model<?>, Bones> BONES = new WeakHashMap<>();
	private static final String[] SPIDER_LEFT = {"left_hind_leg", "left_middle_hind_leg", "left_middle_front_leg", "left_front_leg"};
	private static final String[] SPIDER_RIGHT = {"right_hind_leg", "right_middle_hind_leg", "right_middle_front_leg", "right_front_leg"};
	private static final String[] WINGS_LEFT = {"left_wing", "left_wing_base", "left_wing_tip"};
	private static final String[] WINGS_RIGHT = {"right_wing", "right_wing_base", "right_wing_tip"};

	public record Sample(EnemyRig rig, float seed, float swing, float hurt, float fall, boolean grounded,
		boolean aggressive, boolean riding, float finishing, int id, float hunting, float windup, boolean twitchy,
		@Nullable ExecutionMove move, float size, @Nullable Clip finisher) {
	}

	/** A sudden jerk: which kind (0 none, 1 head snap, 2 shoulder jerk, 3 stumble), how far into it, and to which side. */
	public record Twitch(int kind, float amount, float side, int window) {
		static final Twitch NONE = new Twitch(0, 0.0F, 1.0F, -1);
	}

	/** Where the release falls in every windup clip; the charge is drawn before it, the follow-through after. */
	public static final float RELEASE_KEY = 0.6F;
	/** Ticks the hunting stance takes to come in or go out. */
	private static final float HUNT_EASE = 8.0F;
	private static final int IDLE_WINDOW = 80;
	private static final int HUNT_WINDOW = 30;
	private static final Map<Entity, float[]> HUNTING = new WeakHashMap<>();
	private static final Map<Entity, Integer> RATTLED = new WeakHashMap<>();

	private EnemyMotion() {
	}

	/** Every clip the creatures use, for checks that they are all present. */
	public static List<String> clipNames() {
		List<String> names = new ArrayList<>();
		for (EnemyRig rig : EnemyRig.values()) {
			String hunt = huntClip(rig);
			String windup = windupClip(rig);
			if (hunt != null && !names.contains(hunt)) {
				names.add(hunt);
			}
			if (windup != null && !names.contains(windup)) {
				names.add(windup);
			}
		}
		return names;
	}

	private static @Nullable String huntClip(final EnemyRig rig) {
		return switch (rig) {
			case UNDEAD, HUSK, DROWNED -> "enemy/hunt_undead";
			case SKELETON -> "enemy/hunt_skeleton";
			case ILLAGER, PIGLIN -> "enemy/hunt_brute";
			case ENDERMAN -> "enemy/hunt_enderman";
			case WARDEN, CREAKING -> "enemy/hunt_heavy";
			case SPIDER -> "enemy/hunt_spider";
			case QUADRUPED, HOGLIN, RAVAGER -> "enemy/hunt_beast";
			case VERMIN -> "enemy/hunt_vermin";
			default -> null;
		};
	}

	private static @Nullable String windupClip(final EnemyRig rig) {
		if (rig.windupTicks() <= 0) {
			return null;
		}
		return switch (rig) {
			case UNDEAD, HUSK, DROWNED, ENDERMAN -> "enemy/windup_grab";
			case SKELETON, ILLAGER, PIGLIN -> "enemy/windup_weapon";
			case WARDEN, CREAKING -> "enemy/windup_heavy";
			case SPIDER -> "enemy/windup_spider";
			case VERMIN -> "enemy/windup_vermin";
			default -> "enemy/windup_beast";
		};
	}

	/**
	 * How much larger than its model the body is drawn. Clip moves are in world blocks, and a model drawn larger
	 * carries its model units with it. Cube mobs are scaled up by their size when drawn (a grown sulfur cube at half
	 * that); every other creature only by its scale attribute, since young ones have smaller models of their own.
	 */
	public static float drawnSize(final LivingEntity entity) {
		if (entity instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob cube) {
			float size = Math.max(0.1F, entity.getBbHeight() / entity.getType().getHeight());
			return cube instanceof net.minecraft.world.entity.monster.cubemob.SulfurCube && !cube.isBaby() ? size * 0.5F : size;
		}
		return Math.max(0.1F, entity.getScale());
	}

	/** The striker's half of a finishing move: the creature's own variant when the move has one, else the move's shared clip. */
	public static Clip finisherClip(final ExecutionMove move, final net.minecraft.world.entity.EntityType<?> type) {
		Clip own = Clip.find("execution/" + move.id() + "/striker_" + net.minecraft.world.entity.EntityType.getKey(type).getPath());
		return own != null ? own : Clip.get("execution/" + move.id() + "/striker");
	}

	/** How far into its hunting stance a creature is, eased in and out over {@link #HUNT_EASE} ticks. */
	public static float hunting(final @Nullable LivingEntity entity, final float partialTicks) {
		if (entity == null) {
			return 0.0F;
		}
		boolean hunting = entity instanceof Mob mob && mob.isAggressive()
			|| entity instanceof Warden warden && warden.getClientAngerLevel() >= 80
			|| entity instanceof Creaking creaking && creaking.isActive();
		float age = entity.tickCount + partialTicks;
		// target, when it last changed, and the value it changed from
		float[] state = HUNTING.computeIfAbsent(entity, e -> new float[] {hunting ? 1.0F : 0.0F, -1000.0F, hunting ? 1.0F : 0.0F});
		float target = hunting ? 1.0F : 0.0F;
		if (state[0] != target) {
			state[2] = Mth.lerp(ease(age, state[1], state[1] + HUNT_EASE), state[2], state[0]);
			state[0] = target;
			state[1] = age;
		}
		return Mth.lerp(ease(age, state[1], state[1] + HUNT_EASE), state[2], state[0]);
	}

	/** Progress through the windup clip, or -1 when the creature is not winding up. */
	static float windup(final LivingEntity entity, final float partialTicks) {
		WindupService.Windup windup = entity.getAttached(WindupService.WINDUP);
		if (windup == null) {
			return -1.0F;
		}
		float elapsed = entity.level().getGameTime() - windup.start() + partialTicks;
		if (!windup.released() && elapsed < windup.length()) {
			return RELEASE_KEY * Math.max(0.0F, elapsed) / windup.length();
		}
		return RELEASE_KEY + (1.0F - RELEASE_KEY) * Mth.clamp((elapsed - windup.length()) / WindupService.FOLLOW_THROUGH, 0.0F, 1.0F);
	}

	/**
	 * The same jerk for every viewer: one per window of time (longer at rest, shorter while hunting),
	 * at a point in the window and of a kind picked from the entity id and the window.
	 */
	public static Twitch twitch(final int id, final float time, final boolean hunting) {
		int window = hunting ? HUNT_WINDOW : IDLE_WINDOW;
		int n = Mth.floor(time / window);
		int h = mix(id * 0x9E3779B1 + n * 0x85EBCA6B + (hunting ? 0x27D4EB2F : 0));
		int start = Math.floorMod(h >>> 8, window - 10);
		int hold = 1 + Math.floorMod(h >>> 16, 3);
		float t = time - n * window - start;
		float envelope;
		if (t < 0.0F) {
			return Twitch.NONE;
		} else if (t < 2.0F) {
			envelope = ease(t, 0.0F, 2.0F);
		} else if (t < 2.0F + hold) {
			envelope = 1.0F;
		} else if (t < 6.0F + hold) {
			envelope = 1.0F - ease(t, 2.0F + hold, 6.0F + hold);
		} else {
			return Twitch.NONE;
		}
		float magnitude = 0.35F + 0.25F * ((h >>> 20) & 255) / 255.0F;
		return new Twitch(1 + Math.floorMod(h, 3), envelope * magnitude, ((h >>> 4) & 1) == 0 ? 1.0F : -1.0F, n);
	}

	private static int mix(int h) {
		h ^= h >>> 16;
		h *= 0x7FEB352D;
		h ^= h >>> 15;
		h *= 0x846CA68B;
		return h ^ h >>> 16;
	}

	private static boolean twitchy(final LivingEntity entity, final EnemyRig rig) {
		return switch (rig) {
			case ENDERMAN, CREAKING -> true;
			case UNDEAD, HUSK, DROWNED, SKELETON, PIGLIN, HOGLIN, MOUNT, QUADRUPED -> entity.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD);
			default -> false;
		};
	}

	/** Bones rattle when a skeleton jerks: played once, as the jerk starts, where the viewer is. */
	private static void rattle(final LivingEntity entity, final Twitch twitch) {
		if (twitch.kind() == 0 || twitch.window() == RATTLED.getOrDefault(entity, Integer.MIN_VALUE)) {
			return;
		}
		RATTLED.put(entity, twitch.window());
		entity.level().playLocalSound(entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SKELETON_STEP, SoundSource.HOSTILE,
			0.35F, 1.3F + entity.getRandom().nextFloat() * 0.3F, false);
	}

	public static void extract(final LivingEntity entity, final LivingEntityRenderState state, final float partialTicks) {
		EnemyRig rig = EnemyRig.of(entity.getType());
		if (rig == null) {
			state.setData(KEY, null);
			return;
		}
		ExecutionService.Sequence execution = entity.getAttached(ExecutionService.SEQUENCE);
		float finishing = -1.0F;
		if (execution != null && execution.striker()) {
			float elapsed = entity.level().getGameTime() - execution.start() + partialTicks;
			if (elapsed < execution.finisher().length()) {
				finishing = Math.max(0.0F, elapsed);
				state.bodyRot = execution.yaw();
				state.yRot = 0.0F;
				state.xRot = 0.0F;
			}
		}
		float hunting = hunting(entity, partialTicks);
		boolean twitchy = twitchy(entity, rig);
		if (twitchy && rig == EnemyRig.SKELETON && finishing < 0.0F) {
			rattle(entity, twitch(entity.getId(), entity.tickCount, hunting > 0.5F));
		}
		state.setData(KEY, new Sample(rig, (entity.getId() & 63) * 0.22F,
			entity.isSwinging() ? entity.getSwingAnimation(partialTicks) : 0.0F,
			Mth.clamp((entity.hurtTime - partialTicks) / 10.0F, 0.0F, 1.0F),
			(float) entity.getKnownMovement().y, entity.onGround(), entity instanceof Mob mob && mob.isAggressive(),
			entity.isPassenger(), finishing, entity.getId(), hunting, windup(entity, partialTicks), twitchy,
			execution != null && execution.striker() ? execution.finisher() : null,
			drawnSize(entity),
			execution != null && execution.striker() ? finisherClip(execution.finisher(), entity.getType()) : null));
	}

	/** Used for both drawn meshes and the poses that hold equipment layers. */
	public static void apply(final Model<?> model, final Object renderState) {
		if (!(model instanceof EntityModel<?>) || !(renderState instanceof EntityRenderState state)) {
			return;
		}
		if (!(state instanceof LivingEntityRenderState living)) {
			if (state.entityType != null && EnemyRig.of(state.entityType) == EnemyRig.DRAGON) {
				dragon(BONES.computeIfAbsent(model, Bones::new), state.ageInTicks);
			}
			return;
		}
		Sample motion = state.getData(KEY);
		if (motion == null || living.deathTime > 0.0F || living.bedOrientation != null) {
			return;
		}
		Bones bones = BONES.computeIfAbsent(model, Bones::new);
		float time = state.ageInTicks + motion.seed;
		float walk = motion.riding ? 0.0F : Mth.clamp(living.walkAnimationSpeed, 0, 1);
		float impact = Mth.sin(motion.swing * Mth.PI);
		float step = living.walkAnimationPos * 0.6662F;
		float gait = Mth.sin(step) * walk;
		float breath = Mth.sin(time * 0.105F);
		if (motion.finishing >= 0.0F && motion.move != null && motion.finisher != null) {
			// the striker's half of the finishing move, on vanilla's still pose
			bones.clip(motion.finisher, motion.finishing / motion.move.length(), 1.0F, motion.size, motion.finisher.owns(motion.finishing, true));
			return;
		}

		switch (motion.rig) {
			case UNDEAD, HUSK, DROWNED, SKELETON, ILLAGER, PIGLIN, ENDERMAN, CREAKING, WARDEN ->
				humanoid(bones, motion, living.isInWater, time, walk, gait, breath, impact);
			case CREEPER -> creeper(bones, time, walk, step, gait, breath);
			case SPIDER -> spider(bones, time, walk, step, impact);
			case RAVAGER, HOGLIN, MOUNT, QUADRUPED -> quadruped(bones, motion.rig, walk, step, gait, breath, impact);
			case GEL -> gel(bones, time, motion.grounded, motion.fall);
			case VERMIN -> vermin(bones, time, walk, step);
			case GHAST -> ghast(bones, time, walk);
			case PHANTOM, VEX, BEE -> winged(bones, motion.rig, time, walk, impact, motion.fall);
			case WITHER, BLAZE, BREEZE, SHULKER -> floating(bones, motion.rig, time, gait, breath);
			case GUARDIAN, NAUTILUS -> aquatic(bones, motion.rig, time, walk, gait);
			case DRAGON -> dragon(bones, time);
		}
		String hunt = huntClip(motion.rig);
		if (hunt != null && motion.hunting > 0.0F) {
			Clip clip = Clip.get(hunt);
			float length = clip.number("length", 40.0F);
			bones.clip(clip, (time % length) / length, motion.hunting * (motion.windup >= 0.0F ? 0.3F : 1.0F), motion.size, 0.0F);
		}
		if (motion.twitchy && motion.windup < 0.0F) {
			twitch(bones, twitch(motion.id, state.ageInTicks, motion.hunting > 0.5F));
		}
		String windup = windupClip(motion.rig);
		if (windup != null && motion.windup >= 0.0F) {
			Clip clip = Clip.get(windup);
			bones.clip(clip, motion.windup, clip.weight(motion.windup), motion.size, 0.0F);
			// the strain shows as a tremor that builds toward the release
			float strain = motion.windup < RELEASE_KEY ? Mth.square(motion.windup / RELEASE_KEY) * 0.06F * Mth.sin(time * 2.3F) : 0.0F;
			bones.turn("right_arm", strain, 0, 0);
			bones.turn("left_arm", -strain, 0, 0);
			bones.turn("head", strain * 0.5F, 0, 0);
		}
		// A hit travels through the supporting body before the head catches up.
		bones.root.y += 0.5F * motion.hurt;
		bones.turn("head", -0.16F * motion.hurt, 0.11F * motion.hurt, 0.0F);
	}

	private static void humanoid(final Bones b, final Sample m, final boolean swimming, final float time,
		final float walk, final float gait, final float breath, final float hit) {
		float weight = switch (m.rig) {
			case UNDEAD -> 1.0F;
			case HUSK -> 1.30F;
			case DROWNED -> 0.80F;
			case SKELETON -> 0.64F;
			case ILLAGER -> 0.86F;
			case PIGLIN -> 1.20F;
			case ENDERMAN -> 0.50F;
			case CREAKING -> 1.38F;
			case WARDEN -> 1.50F;
			default -> 1.0F;
		};
		float limp = (m.rig == EnemyRig.HUSK || m.rig == EnemyRig.UNDEAD ? 0.12F * walk : 0.0F) * (1.0F + 0.6F * m.hunting);
		float grip = m.aggressive ? 0.07F : 0.0F;
		if (m.rig == EnemyRig.DROWNED && swimming) {
			b.lean(0.055F * Mth.sin(time * 0.30F), 0.08F * Mth.sin(time * 0.19F), 0.5F * breath, 0.0F);
			b.turn("left_arm", 0.15F * Mth.sin(time * 0.31F), 0, 0.07F);
			b.turn("right_arm", -0.15F * Mth.sin(time * 0.31F), 0, -0.07F);
			return;
		}
		float forward = (0.025F + walk * 0.12F) * weight + hit * 0.16F;
		float lurch = gait * (0.09F + limp) * weight;
		b.lean(forward, lurch, breath * (0.23F + 0.32F * walk) * weight, 0.0F);
		b.turn("head", -forward * 0.58F + 0.042F * breath, 0.07F * gait, -lurch * 0.62F);
		b.turn("body", 0.025F * breath, -gait * 0.08F * weight, lurch * 0.37F);
		b.turn("left_arm", gait * 0.18F * weight - grip + 0.055F * breath, -0.03F * gait, -0.055F - walk * 0.10F);
		b.turn("right_arm", -gait * 0.19F * weight - hit * 0.19F, 0.03F * gait, 0.066F + walk * 0.09F);
		b.turn("arms", 0.06F * breath + 0.08F * walk, -0.06F * gait, -0.04F * gait);
		b.turn("left_leg", -gait * (0.15F + limp), 0.045F * walk, 0.045F * walk);
		b.turn("right_leg", gait * 0.15F, -0.028F * walk, -0.041F * walk);
		if (m.rig == EnemyRig.WARDEN) {
			b.turn("left_tendril", breath * 0.20F, 0, gait * 0.22F);
			b.turn("right_tendril", -breath * 0.17F, 0, -gait * 0.19F);
			b.turn("left_ribcage", 0, 0.08F + 0.07F * breath, 0);
			b.turn("right_ribcage", 0, -0.08F - 0.07F * breath, 0);
		}
		if (m.rig == EnemyRig.CREAKING) {
			b.turn("upper_body", 0.045F * Mth.sin(time * 0.32F) * walk, 0.13F * gait, 0.14F * gait);
		}
	}

	private static void creeper(final Bones b, final float time, final float walk, final float step, final float gait, final float breath) {
		b.lean(0.09F * walk, 0.080F * gait, -0.62F * walk * Math.abs(gait) + 0.22F * breath, 0);
		b.turn("head", -0.10F * walk + 0.055F * Mth.sin(time * 0.19F), 0.08F * gait, -0.14F * gait);
		b.turn("body", 0.04F * breath, -0.10F * gait, 0.055F * gait);
		footfalls(b, walk, step, 0.22F);
	}

	private static void spider(final Bones b, final float time, final float walk, final float step, final float hit) {
		b.lean(-0.07F * walk + 0.19F * hit, 0.05F * Mth.sin(step) * walk, -0.9F * walk - 0.25F * Mth.sin(time * 0.22F), 0);
		b.turn("head", -0.14F * hit + 0.08F * Mth.sin(time * 0.31F), 0.080F * Mth.sin(time * 0.14F), 0);
		b.turn("body1", -0.095F * walk, 0.08F * Mth.sin(step) * walk, -0.04F * Mth.sin(step) * walk);
		for (int i = 0; i < SPIDER_LEFT.length; i++) {
			float phase = step * 1.7F + i * 1.55F;
			float scuttle = 0.16F * walk * Mth.sin(phase);
			float lift = 0.24F * walk * Math.max(0, Mth.cos(phase));
			b.turn(SPIDER_LEFT[i], -0.04F * walk, scuttle, -lift);
			b.turn(SPIDER_RIGHT[i], -0.04F * walk, scuttle, 0.24F * walk * Math.max(0, -Mth.cos(phase)));
		}
	}

	private static void quadruped(final Bones b, final EnemyRig rig, final float walk, final float step,
		final float gait, final float breath, final float hit) {
		float heavy = rig == EnemyRig.RAVAGER ? 1.45F : rig == EnemyRig.HOGLIN ? 1.19F : 0.85F;
		b.lean((0.11F * walk + 0.16F * hit) * heavy, gait * 0.09F * heavy, 0.40F * breath - Math.abs(gait) * heavy, 0.0F);
		b.turn("body", 0.055F * breath, -0.065F * gait, 0.042F * gait);
		b.turn("neck", -0.09F * walk + 0.065F * breath, 0.075F * gait, -0.07F * gait);
		b.turn("head", -0.083F * walk - 0.17F * hit, -0.085F * gait, -0.04F * gait);
		b.turn("mouth", 0.18F * hit, 0, 0);
		b.turn("left_ear", 0.055F * breath, 0.11F * gait, -0.09F * gait);
		b.turn("right_ear", -0.055F * breath, 0.11F * gait, -0.08F * gait);
		b.turn("tail", 0.07F * breath, 0.11F * gait, 0.09F * gait);
		footfalls(b, walk, step, 0.19F * heavy);
	}

	private static void footfalls(final Bones b, final float walk, final float step, final float swing) {
		float left = Mth.sin(step) * walk;
		float right = Mth.cos(step + 1.0F) * walk;
		b.turn("left_front_leg", left * swing, 0, -0.055F * walk);
		b.turn("right_hind_leg", left * swing, 0, 0.070F * walk);
		b.turn("right_front_leg", right * swing, 0, 0.055F * walk);
		b.turn("left_hind_leg", right * swing, 0, -0.070F * walk);
	}

	private static void gel(final Bones b, final float time, final boolean grounded, final float fall) {
		float pulse = Mth.sin(time * 0.41F);
		float stretch = grounded ? -0.06F + 0.035F * pulse : 0.11F + Mth.clamp(fall, -0.17F, 0.28F) * 0.24F;
		b.squash(stretch);
		b.lean(0.04F * pulse, 0.06F * Mth.sin(time * 0.18F), 0.0F, 0);
		b.turn("inside_cube", 0.04F * pulse, 0.10F * Mth.sin(time * 0.29F), 0.035F * pulse);
		b.turn("left_eye", -0.045F * pulse, 0.04F * pulse, 0);
		b.turn("right_eye", -0.045F * pulse, 0.04F * pulse, 0);
	}

	private static void vermin(final Bones b, final float time, final float walk, final float step) {
		b.lean(-0.04F, Mth.sin(time * 0.30F) * (0.05F + 0.09F * walk), 0.2F * Mth.sin(time * 0.51F), 0);
		for (int i = 0; i < 7; i++) {
			float wave = time * 0.21F + step * 0.6F - i * 0.77F;
			b.turn("segment" + i, Mth.cos(wave) * 0.07F, Mth.sin(wave) * 0.13F, 0);
		}
	}

	private static void ghast(final Bones b, final float time, final float walk) {
		b.lean(0.065F * Mth.sin(time * 0.12F), 0.075F * Mth.cos(time * 0.09F), 1.05F * Mth.sin(time * 0.115F), 0);
		for (int i = 0; i < 9; i++) {
			float wave = time * 0.14F - i * 0.7F;
			b.turn("tentacle" + i, 0.14F * Mth.sin(wave) + walk * 0.11F, 0.06F * Mth.cos(wave), 0.15F * Mth.cos(wave));
		}
	}

	private static void winged(final Bones b, final EnemyRig rig, final float time, final float walk, final float hit, final float fall) {
		float speed = rig == EnemyRig.BEE ? 1.45F : rig == EnemyRig.VEX ? 0.76F : 0.39F;
		float flap = Mth.sin(time * speed);
		float bank = Mth.sin(time * 0.17F) * (0.10F + 0.09F * walk);
		b.lean(-Mth.clamp(fall, -0.8F, 0.8F) * 0.28F + hit * 0.16F, bank,
			0.65F * Mth.cos(time * speed) + 0.50F * Mth.sin(time * 0.13F), 0);
		b.turn("head", 0.095F * flap, 0, -bank * 0.65F);
		for (int i = 0; i < WINGS_LEFT.length; i++) {
			b.turn(WINGS_LEFT[i], 0.06F * flap, 0.09F * flap, 0.14F * flap + 0.04F);
			b.turn(WINGS_RIGHT[i], 0.06F * flap, -0.09F * flap, -0.14F * flap - 0.04F);
		}
		b.turn("tail_base", -0.10F * flap, bank, 0);
		b.turn("tail_tip", 0.12F * flap, -bank, 0);
		b.turn("right_arm", -0.22F * hit, 0.06F * flap, 0);
		b.turn("left_arm", 0.13F * hit, -0.06F * flap, 0);
	}

	private static void floating(final Bones b, final EnemyRig rig, final float time, final float gait, final float breath) {
		b.lean(0.055F * breath, 0.065F * Mth.sin(time * 0.137F), (rig == EnemyRig.SHULKER ? 0.0F : 0.68F) * breath, 0);
		b.turn("head", -0.06F * breath, 0.07F * gait, 0.055F * breath);
		switch (rig) {
			case BLAZE -> {
				for (int i = 0; i < 12; i++) {
					float orbit = time * 0.21F + i * 0.93F;
					b.turn("part" + i, 0.24F * Mth.sin(orbit), 0.13F * Mth.cos(orbit), 0.15F * Mth.sin(orbit));
					b.shift("part" + i, 0, 0.45F * Mth.cos(orbit), 0);
				}
			}
			case BREEZE -> {
				b.turn("wind_body", 0.06F * breath, 0.20F * Mth.sin(time * 0.18F), 0.055F * gait);
				b.turn("rods", 0.08F * breath, -0.17F * Mth.sin(time * 0.185F), -0.08F * breath);
				b.turn("eyes", 0.06F * breath, 0.045F * gait, 0);
			}
			case WITHER -> {
				b.turn("ribcage", -0.12F * breath, 0.05F * gait, -0.08F * breath);
				b.turn("tail", 0.13F * breath, 0.055F * gait, 0.05F * breath);
				b.turn("center_head", 0.045F * breath, 0, -0.05F * breath);
				b.turn("left_head", 0.08F * Mth.sin(time * 0.22F), 0.075F * breath, -0.08F * breath);
				b.turn("right_head", 0.08F * Mth.sin(time * 0.22F + 2.0F), -0.075F * breath, 0.08F * breath);
			}
			case SHULKER -> {
				b.turn("head", 0.11F * breath, 0, -0.095F * breath);
				b.shift("lid", 0, -0.20F * (1 + breath), 0);
			}
			default -> { }
		}
	}

	private static void aquatic(final Bones b, final EnemyRig rig, final float time, final float walk, final float gait) {
		float sway = Mth.sin(time * (rig == EnemyRig.GUARDIAN ? 0.22F : 0.14F));
		b.lean(0.065F * sway, 0.11F * sway * (0.36F + walk), 0.52F * sway, 0);
		b.turn("eye", 0.065F * gait, 0.12F * gait, 0);
		for (int i = 0; i < 3; i++) {
			b.turn("tail" + i, 0.06F * Mth.cos(time * 0.19F - i), 0.12F * Mth.sin(time * 0.25F - i), 0);
		}
		b.turn("shell", 0.045F * sway, 0, -0.08F * sway);
		b.turn("tentacles", 0.12F * sway, 0.075F * gait, 0);
	}

	private static void dragon(final Bones b, final float time) {
		float breath = Mth.sin(time * 0.18F);
		b.turn("body", 0.043F * breath, 0.0F, 0.055F * Mth.sin(time * 0.13F));
		b.turn("left_wing_tip", 0.038F * breath, 0.08F * breath, 0.15F * breath);
		b.turn("right_wing_tip", 0.038F * breath, -0.08F * breath, -0.15F * breath);
		b.turn("left_front_foot", 0.06F * breath, 0, -0.035F);
		b.turn("right_front_foot", 0.06F * breath, 0, 0.035F);
		b.turn("jaw", 0.035F * (1.0F + breath), 0, 0);
	}

	private static void twitch(final Bones b, final Twitch twitch) {
		float a = twitch.amount();
		float side = twitch.side();
		switch (twitch.kind()) {
			case 1 -> b.turn("head", 0.25F * a, side * a, side * 0.5F * a);
			case 2 -> {
				b.turn(side > 0 ? "right_arm" : "left_arm", -0.8F * a, 0, side * 0.4F * a);
				b.turn("body", 0, side * 0.25F * a, side * 0.15F * a);
			}
			case 3 -> {
				b.lean(0.15F * a, side * 0.25F * a, 1.2F * a, 0);
				b.turn(side > 0 ? "right_leg" : "left_leg", 0.6F * a, 0, 0);
			}
			default -> { }
		}
	}

	static float ease(final float time, final float start, final float end) {
		float t = Mth.clamp((time - start) / (end - start), 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static final class Bones {
		private final ModelPart root;
		private final Function<String, ModelPart> find;

		private Bones(final Model<?> model) {
			this.root = model.root();
			this.find = this.root.createPartLookup();
		}

		private @Nullable ModelPart part(final String name) {
			return this.find.apply(name);
		}

		private void turn(final String name, final float x, final float y, final float z) {
			ModelPart part = this.part(name);
			if (part != null) {
				part.xRot += x;
				part.yRot += y;
				part.zRot += z;
			}
		}

		private void set(final String name, final float x, final float y, final float z, final float weight) {
			ModelPart part = this.part(name);
			if (part != null) {
				part.setRotation(Mth.lerp(weight, part.xRot, x), Mth.lerp(weight, part.yRot, y), Mth.lerp(weight, part.zRot, z));
			}
		}

		private void shift(final String name, final float x, final float y, final float z) {
			ModelPart part = this.part(name);
			if (part != null) {
				part.x += x;
				part.y += y;
				part.z += z;
			}
		}

		private void lean(final float pitch, final float roll, final float height, final float reach) {
			this.root.xRot += pitch;
			this.root.zRot += roll;
			// Keep the origin for the tilt near the feet, in vanilla model pixels.
			float foot = Math.max(0.0F, 24.0F - this.root.getInitialPose().y());
			this.root.x += foot * Mth.sin(roll);
			this.root.y += height + foot * (1.0F - Mth.cos(pitch) * Mth.cos(roll));
			this.root.z -= reach + foot * Mth.sin(pitch);
		}

		/**
		 * Adds a clip on top of the current pose, scaled by {@code weight}: {@code bones.<part>_x|y|z} in degrees,
		 * {@code root.pitch|roll|yaw} in degrees and {@code root.x|y|z} in blocks (right, up, forward).
		 */
		private void clip(final Clip clip, final float p, final float weight, final float size, final float own) {
			if (weight <= 0.0F) {
				return;
			}
			// "pose" tracks replace a part's rotation outright (as far as the clip owns the pose): a finisher cannot
			// build on vanilla's stance, which differs by creature and by mood
			if (own > 0.0F) {
				for (String track : clip.tracks("pose")) {
					int split = track.lastIndexOf('_');
					ModelPart part = split <= 0 ? null : this.part(track.substring(0, split));
					if (part == null) {
						continue;
					}
					float value = clip.rad("pose." + track, p);
					switch (track.charAt(split + 1)) {
						case 'x' -> part.xRot = Mth.lerp(own, part.xRot, value);
						case 'y' -> part.yRot = Mth.lerp(own, part.yRot, value);
						case 'z' -> part.zRot = Mth.lerp(own, part.zRot, value);
						default -> { }
					}
				}
			}
			for (String track : clip.tracks("bones")) {
				int split = track.lastIndexOf('_');
				if (split <= 0) {
					continue;
				}
				float value = clip.rad("bones." + track, p) * weight;
				String part = track.substring(0, split);
				switch (track.charAt(split + 1)) {
					case 'x' -> this.turn(part, value, 0, 0);
					case 'y' -> this.turn(part, 0, value, 0);
					case 'z' -> this.turn(part, 0, 0, value);
					default -> { }
				}
			}
			this.root.yRot += clip.rad("root.yaw", p) * weight;
			// model space: forward is -z, up is -y, the creature's right is -x
			// model units are scaled with the body when it is drawn larger, so world blocks shrink by the size
			float unit = 16.0F * weight / size;
			this.root.x -= clip.at("root.x", p) * unit;
			// "y_scaled" lifts by a share of the creature's own size (cube mobs of any size rest on the same surface)
			this.lean(clip.rad("root.pitch", p) * weight, clip.rad("root.roll", p) * weight,
				-(clip.at("root.y", p) + clip.at("root.y_scaled", p) * size) * unit, clip.at("root.z", p) * unit);
			float squash = clip.at("root.squash", p) * weight;
			if (squash != 0.0F) {
				this.squash(squash);
			}
		}

		private void squash(final float amount) {
			this.root.yScale *= 1.0F + amount;
			this.root.xScale *= 1.0F - amount * 0.48F;
			this.root.zScale *= 1.0F - amount * 0.48F;
			this.root.y -= Math.max(0.0F, 24.0F - this.root.getInitialPose().y()) * amount;
		}
	}
}
