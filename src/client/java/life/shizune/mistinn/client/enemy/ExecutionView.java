package life.shizune.mistinn.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import life.shizune.mistinn.client.death.DeathScene;
import life.shizune.mistinn.enemy.ExecutionService;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The victim's staged pose, shared by all viewers, and the local hunter's view of it. */
public final class ExecutionView {
	public record Pose(float time, float yaw, int striker) {
	}

	public record Shot(Vec3 eye, Vec3 lookAt) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "mistinn:execution_victim");
	private static @Nullable CameraType savedCamera;

	private ExecutionView() {
	}

	public static @Nullable Pose sample(final LivingEntity entity, final float partialTicks) {
		ExecutionService.Sequence sequence = entity.getAttached(ExecutionService.SEQUENCE);
		if (sequence == null || sequence.striker() || entity.isSpectator()) {
			return null;
		}
		if (entity.isDeadOrDying() && !sequence.completed() && !ExecutionService.wasExecuted(entity.getLastDamageSource())) {
			return null;
		}
		float time = entity.level().getGameTime() - sequence.start() + partialTicks;
		if (!sequence.completed() && time > ExecutionService.DURATION + 3) {
			return null;
		}
		return new Pose(Mth.clamp(time, 0.0F, ExecutionService.DURATION), sequence.yaw(), sequence.counterpart());
	}

	public static void tick(final Minecraft minecraft) {
		LocalPlayer hunter = minecraft.player;
		if (hunter != null && hunter.isAlive() && !DeathScene.active() && sample(hunter, 0) != null) {
			if (savedCamera == null) {
				savedCamera = minecraft.options.getCameraType();
				minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
		} else if (savedCamera != null && (hunter == null || hunter.isAlive())) {
			minecraft.options.setCameraType(takeCamera());
		}
	}

	/** Transfers ownership of the earlier camera setting to the mission end screen. */
	public static @Nullable CameraType takeCamera() {
		CameraType camera = savedCamera;
		savedCamera = null;
		return camera;
	}

	public static @Nullable Shot shot(final Minecraft minecraft, final float partialTicks) {
		LocalPlayer hunter = minecraft.player;
		if (hunter == null || minecraft.level == null || (savedCamera == null
			&& (!DeathScene.active() || DeathScene.cause() != DeathScene.Cause.EXECUTION))) {
			return null;
		}
		Pose pose = sample(hunter, partialTicks);
		if (pose == null) {
			return null;
		}
		float angle = pose.yaw * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(angle), 0, Mth.cos(angle));
		Vec3 side = new Vec3(forward.z, 0, -forward.x);
		Vec3 feet = hunter.getPosition(partialTicks);
		Entity striker = minecraft.level.getEntity(pose.striker);
		float turn = EnemyMotion.ease(pose.time, 0, ExecutionService.DURATION);
		boolean nearby = striker != null && striker.distanceToSqr(hunter) < 25.0;
		double span = nearby ? striker.distanceTo(hunter) : 1.0;
		float height = nearby ? Mth.clamp(striker.getBbHeight() * 0.50F, 0.65F, 1.8F) : 0.8F;
		Vec3 focus = feet.add(forward.scale(Math.min(span * 0.5, 1.3))).add(0, height, 0);
		Vec3 desired = focus.add(side.scale(3.02 + height * 0.35 - turn * 0.24)).add(forward.scale(-0.65 - turn * 0.35)).add(0, 0.72 + turn * 0.16, 0);
		HitResult collision = minecraft.level.clip(new ClipContext(focus, desired, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, hunter));
		Vec3 eye = collision.getType() == HitResult.Type.MISS ? desired : collision.getLocation().lerp(focus, 0.14);
		return new Shot(eye, focus);
	}

	public static void root(final Pose pose, final PoseStack stack, final float scale) {
		float t = pose.time;
		float buckle = EnemyMotion.ease(t, 0, 8);
		float bracing = EnemyMotion.ease(t, 5, 14);
		float recoil = EnemyMotion.ease(t, 16, 18) * (1.0F - EnemyMotion.ease(t, 18, 23));
		float fall = EnemyMotion.ease(t, 18, 27);
		stack.translate(0.025F * buckle * scale, (-0.44F * buckle - 0.24F * fall) * scale,
			(-0.09F * bracing + 0.24F * recoil + 0.22F * fall) * scale);
		stack.translate(0, 0.50F * scale, 0);
		stack.rotateDegrees(Axis.XP, 14.0F * buckle - 22.0F * bracing + 39.0F * recoil + 63.0F * fall);
		stack.rotateDegrees(Axis.ZP, -10.0F * buckle + 11.0F * recoil - 52.0F * fall);
		stack.translate(0, -0.50F * scale, 0);
	}

	public static void limbs(final Pose pose, final HumanoidModel<?> model) {
		float t = pose.time;
		float buckle = EnemyMotion.ease(t, 0, 8);
		float bracing = EnemyMotion.ease(t, 5, 14);
		float recoil = EnemyMotion.ease(t, 15, 18) * (1.0F - EnemyMotion.ease(t, 18, 22));
		float fall = EnemyMotion.ease(t, 18, 27);
		float weight = EnemyMotion.ease(t, 0, 4);
		float tremor = bracing * (1.0F - EnemyMotion.ease(t, 15, 19)) * Mth.sin(t * 1.5F) * 0.055F;
		set(model.head, -0.22F * bracing - 0.34F * recoil + 0.25F * fall, 0.16F * bracing, 0.10F * fall, weight);
		set(model.body, 0.41F * buckle - 0.42F * recoil - 0.26F * fall, 0.20F * bracing - 0.20F * recoil, 0.12F * fall, weight);
		set(model.rightArm, -0.58F * buckle - 1.36F * bracing - 0.23F * recoil + 1.47F * fall + tremor,
			-0.35F * bracing, 0.27F * buckle + 0.22F * recoil + 0.24F * fall, weight);
		set(model.leftArm, 0.35F * buckle - 0.36F * bracing - 1.23F * recoil + 0.27F * fall,
			0.15F * bracing, -0.31F * buckle - 0.25F * recoil - 0.28F * fall, weight);
		set(model.rightLeg, -1.43F * buckle + 1.20F * fall, 0.16F * bracing, 0.16F * buckle, weight);
		set(model.leftLeg, 0.92F * buckle - 1.15F * fall, -0.19F * bracing, -0.22F * buckle, weight);
	}

	private static void set(final ModelPart part, final float x, final float y, final float z, final float weight) {
		part.setRotation(Mth.lerp(weight, part.xRot, x), Mth.lerp(weight, part.yRot, y), Mth.lerp(weight, part.zRot, z));
	}
}
