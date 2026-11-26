package life.shizune.mistinn.client.mixin;

import life.shizune.mistinn.client.enemy.ExecutionView;
import life.shizune.mistinn.client.story.SceneScreen;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** During a story scene the view is the scene's: placed by it and turned on what it watches. */
@Mixin(Camera.class)
public abstract class SceneCameraMixin {
	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	/**
	 * The drop turns the hunter's own head where they are falling, before the view is set up from
	 * it: the view and what the world draws for it (which turns on where the hunter looks) agree.
	 */
	@Inject(method = "update", at = @At("HEAD"))
	private void mistinn$drop(final DeltaTracker deltaTracker, final CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		float[] drop = life.shizune.mistinn.client.training.DropCinematic.view(mc, deltaTracker.getGameTimeDeltaPartialTick(false));
		if (drop != null && mc.player != null) {
			mc.player.setYRot(drop[0]);
			mc.player.yRotO = drop[0];
			mc.player.setYHeadRot(drop[0]);
			mc.player.yHeadRotO = drop[0];
			mc.player.setXRot(drop[1]);
			mc.player.xRotO = drop[1];
		}
	}

	@Inject(method = "update", at = @At("TAIL"))
	private void mistinn$scene(final DeltaTracker deltaTracker, final CallbackInfo ci) {
		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		life.shizune.mistinn.client.naval.NavalIntro intro = life.shizune.mistinn.client.naval.NavalIntro.current();
		if (intro != null) {
			Vec3 eye = intro.eye(partial);
			if (eye != null) {
				this.mistinn$aim(eye, intro.target());
				return;
			}
		}
		SceneScreen scene = SceneScreen.current();
		if (scene == null) {
			ExecutionView.Shot shot = ExecutionView.shot(Minecraft.getInstance(), partial);
			if (shot != null) {
				this.mistinn$aim(shot.eye(), shot.lookAt());
			}
			return;
		}
		Vec3 eye = scene.eye(partial);
		if (eye != null) {
			this.mistinn$aim(eye, scene.target());
		}
	}

	private void mistinn$aim(final Vec3 eye, final Vec3 target) {
		Vec3 look = target.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(look.z, look.x)) - 90.0F;
		float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
		this.setPosition(eye);
		this.setRotation(yaw, pitch);
	}
}
