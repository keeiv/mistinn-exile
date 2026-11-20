package life.shizune.mistinn.client.enemy;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import life.shizune.mistinn.enemy.EnemyRig;
import life.shizune.mistinn.enemy.ExecutionService;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
		boolean aggressive, boolean riding, float finishing) {
	}

	private EnemyMotion() {
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
			if (elapsed < ExecutionService.DURATION) {
				finishing = Math.max(0.0F, elapsed);
				state.bodyRot = execution.yaw();
				state.yRot = 0.0F;
				state.xRot = 0.0F;
			}
		}
		state.setData(KEY, new Sample(rig, (entity.getId() & 63) * 0.22F,
			entity.isSwinging() ? entity.getSwingAnimation(partialTicks) : 0.0F,
			Mth.clamp((entity.hurtTime - partialTicks) / 10.0F, 0.0F, 1.0F),
			(float) entity.getKnownMovement().y, entity.onGround(), entity instanceof Mob mob && mob.isAggressive(),
			entity.isPassenger(), finishing));
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
		if (motion.finishing >= 0.0F && motion.rig.finisher() != null) {
			finisher(bones, motion.rig.finisher(), motion.finishing, time);
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
		float limp = m.rig == EnemyRig.HUSK || m.rig == EnemyRig.UNDEAD ? 0.12F * walk : 0.0F;
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

	static float ease(final float time, final float start, final float end) {
		float t = Mth.clamp((time - start) / (end - start), 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static void finisher(final Bones b, final EnemyRig.Finisher kind, final float time, final float beat) {
		float prepare = ease(time, 0, 6);
		float wind = ease(time, 5, 15) * (1.0F - ease(time, 15, 19));
		float contact = ease(time, 14, 18) * (1.0F - ease(time, 19, 25));
		float recover = ease(time, 19, 24) * (1.0F - ease(time, 24, ExecutionService.DURATION));
		float alive = prepare * (1.0F - ease(time, 25, ExecutionService.DURATION));
		float strain = wind * Mth.sin(beat * 0.90F) * 0.05F;
		// Ease out of the stopped walk, coil, overshoot the hit, then let the weight settle.
		b.turn("head", -0.14F * wind + 0.29F * contact + strain, 0, -0.1F * recover);
		switch (kind) {
			case GRAB -> {
				b.lean(0.26F * prepare - 0.42F * wind + 0.47F * contact, -0.21F * wind + 0.19F * contact,
					1.55F * alive - 1.2F * wind + 1.2F * contact, 4.1F * contact);
				b.set("right_arm", -0.65F - 1.70F * wind + 0.15F * contact, -0.22F * wind, 0.29F + 0.42F * wind, alive);
				b.set("left_arm", -0.52F - 0.58F * wind + 0.28F * recover, 0.29F, -0.31F, alive);
				b.turn("body", 0.26F * contact, -0.26F * wind + 0.18F * contact, 0.09F * wind);
				b.turn("left_leg", -0.52F * alive, 0.09F * alive, 0.10F * alive);
				b.turn("right_leg", 0.52F * alive + 0.26F * contact, -0.07F * alive, -0.13F * alive);
			}
			case BLADE -> {
				b.lean(-0.18F * wind + 0.54F * contact, 0.35F * wind - 0.34F * contact, 0.32F * alive + 1.65F * contact, 4.0F * contact);
				b.set("right_arm", -2.62F * wind - 0.90F * contact + 0.18F * recover, -0.78F * wind + 0.78F * contact,
					0.50F * wind + 0.45F * contact, alive);
				b.set("left_arm", -0.58F * wind - 0.25F * contact, 0.32F * alive, -0.38F * alive, alive);
				b.turn("body", 0.14F * contact, 0.37F * wind - 0.44F * contact, 0);
				b.turn("left_leg", -0.50F * contact, 0.15F * wind, 0.08F * alive);
				b.turn("right_leg", -0.41F * wind + 0.34F * contact, -0.13F * wind, -0.08F * alive);
				b.turn("arms", -1.15F * wind + 0.43F * contact, 0.24F * wind - 0.4F * contact, 0.17F * alive);
			}
			case CRUSH -> {
				b.lean(-0.31F * wind + 0.46F * contact, strain, 2.8F * prepare - 5.8F * wind + 1.9F * contact, 4.3F * contact);
				b.squash(0.22F * wind - 0.27F * contact);
				b.set("left_arm", -2.74F * wind - 0.31F * contact, 0.12F * alive, -0.42F * alive, alive);
				b.set("right_arm", -2.67F * wind - 0.29F * contact, -0.12F * alive, 0.42F * alive, alive);
				b.turn("left_leg", -0.27F * wind + 0.40F * contact, 0, 0.12F * alive);
				b.turn("right_leg", -0.27F * wind + 0.40F * contact, 0, -0.12F * alive);
			}
			case POUNCE -> {
				b.lean(-0.33F * wind + 0.39F * contact, strain, 1.9F * wind - 3.8F * ease(time, 14, 16) * (1 - ease(time, 16, 19)), 6.2F * contact);
				b.turn("body1", -0.38F * wind + 0.20F * contact, 0, 0);
				b.turn("left_wing_base", 0.24F * wind, -0.17F * wind, -0.42F * contact);
				b.turn("right_wing_base", 0.24F * wind, 0.17F * wind, 0.42F * contact);
				for (int i = 0; i < SPIDER_LEFT.length; i++) {
					float reach = (i + 1) * 0.16F;
					b.turn(SPIDER_LEFT[i], -0.15F * wind, -reach * contact, -0.40F * wind + 0.27F * recover);
					b.turn(SPIDER_RIGHT[i], -0.15F * wind, reach * contact, 0.40F * wind - 0.27F * recover);
				}
			}
			case GORE -> {
				b.lean(0.23F * wind - 0.28F * contact, 0.14F * wind, 1.9F * wind - 1.8F * contact, 5.6F * contact);
				b.turn("neck", 0.54F * wind - 0.59F * contact, -0.17F * wind, -0.07F * wind);
				b.turn("head", 0.27F * wind - 0.23F * contact, -0.17F * wind + 0.13F * contact, 0);
				b.turn("mouth", 0.38F * contact, 0, 0);
				b.turn("left_front_leg", -0.42F * contact, 0, 0.15F * wind);
				b.turn("right_front_leg", -0.31F * wind + 0.25F * contact, 0, -0.15F * wind);
				b.turn("left_hind_leg", 0.28F * contact, 0, 0);
				b.turn("right_hind_leg", 0.38F * contact, 0, 0);
			}
		}
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

		private void squash(final float amount) {
			this.root.yScale *= 1.0F + amount;
			this.root.xScale *= 1.0F - amount * 0.48F;
			this.root.zScale *= 1.0F - amount * 0.48F;
			this.root.y -= Math.max(0.0F, 24.0F - this.root.getInitialPose().y()) * amount;
		}
	}
}
