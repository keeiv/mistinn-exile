package life.shizune.mistinn.client.enemy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.anim.Poser;
import life.shizune.mistinn.client.death.DeathScene;
import life.shizune.mistinn.enemy.ExecutionMove;
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
	public record Pose(float time, float yaw, int striker, ExecutionMove move, boolean done, Clip clip) {
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
		int length = sequence.finisher().length();
		if (sequence.finisher().ranged() && !sequence.completed()) {
			// between the release and the impact the hunter is held on the release frame
			time = Math.min(time, sequence.finisher().kill());
		}
		if (!sequence.completed() && time > length + 3) {
			return null;
		}
		return new Pose(Mth.clamp(time, 0.0F, length), sequence.yaw(), sequence.counterpart(), sequence.finisher(), sequence.completed(),
			victimClip(sequence.finisher(), entity.level().getEntity(sequence.counterpart())));
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
		float turn = EnemyMotion.ease(pose.time, 0, pose.move.length());
		boolean ranged = pose.move.ranged();
		boolean nearby = striker != null && striker.distanceToSqr(hunter) < (ranged ? 24.0 * 24.0 : 25.0);
		double span = nearby ? striker.distanceTo(hunter) : 1.0;
		float height = nearby ? Mth.clamp(striker.getBbHeight() * 0.50F, 0.65F, 1.8F) : 0.8F;
		Clip staged = victim(pose);
		if (ranged && nearby) {
			// a shot from afar: the camera stands off the line between them and takes both in; once the arrow
			// has struck it closes on the fallen hunter. The held hunter's view is narrowed by vanilla's speed
			// effect (about half its width), so it stands well back.
			float closing = EnemyMotion.ease(pose.time, pose.move.kill(), pose.move.length());
			double middle = span * Mth.lerp(closing, 0.48F, 0.12F);
			Vec3 between = feet.add(forward.scale(middle)).add(0, Mth.lerp(closing, 0.95F, 0.45F), 0);
			double away = Mth.lerp(closing, (float) Math.max(staged.number("camera_side", 3.0F), span * 1.15 + 2.6), 3.4F);
			Vec3 standoff = between.add(side.scale(away)).add(forward.scale(-0.5)).add(0, Mth.lerp(closing, 1.3F, 1.6F), 0);
			HitResult blocked = minecraft.level.clip(new ClipContext(between, standoff, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, hunter));
			return new Shot(blocked.getType() == HitResult.Type.MISS ? standoff : blocked.getLocation().lerp(between, 0.14), between);
		}
		// a move that carries the bodies away from where the hunter stands says where its middle is
		Vec3 focus = feet.add(forward.scale(staged.number("camera_forward", (float) Math.min(span * 0.5, 1.3))))
			.add(0, staged.number("camera_focus_height", height), 0);
		Clip clip = victim(pose);
		Vec3 desired = focus.add(side.scale(clip.number("camera_side", 3.02F) + height * 0.35 - turn * 0.24)).add(forward.scale(-0.65 - turn * 0.35))
			.add(0, clip.number("camera_height", 0.72F) + turn * 0.16, 0);
		HitResult collision = minecraft.level.clip(new ClipContext(focus, desired, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, hunter));
		Vec3 eye = collision.getType() == HitResult.Type.MISS ? desired : collision.getLocation().lerp(focus, 0.14);
		return new Shot(eye, focus);
	}

	/** The hunter's half of the move: the one paired with this kind of striker when the move has one, else the shared clip. */
	public static Clip victimClip(final ExecutionMove move, final @Nullable Entity striker) {
		Clip own = striker == null ? null
			: Clip.find("execution/" + move.id() + "/victim_" + net.minecraft.world.entity.EntityType.getKey(striker.getType()).getPath());
		return own != null ? own : Clip.get("execution/" + move.id() + "/victim");
	}

	public static Clip victim(final Pose pose) {
		return pose.clip();
	}

	public static void root(final Pose pose, final PoseStack stack, final float scale) {
		Poser.root(victim(pose), pose.time() / pose.move().length(), stack, 1, scale);
	}

	public static void limbs(final Pose pose, final HumanoidModel<?> model) {
		Clip clip = victim(pose);
		float p = pose.time() / pose.move().length();
		Poser.limbs(clip, p, model, 1, clip.owns(pose.time(), false));
	}

	private static void set(final ModelPart part, final float x, final float y, final float z, final float weight) {
		part.setRotation(Mth.lerp(weight, part.xRot, x), Mth.lerp(weight, part.yRot, y), Mth.lerp(weight, part.zRot, z));
	}
}
