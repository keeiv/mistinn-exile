package life.shizune.mistinn.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/** Weapon light only follows the contact and follow-through of an actual swing. */
public final class SwingTrails {
	private static final RenderStateDataKey<List<Trail>> TRAILS = RenderStateDataKey.create(() -> "Mistinn swing trails");
	private static final RenderType LIGHT = RenderTypes.entityTranslucentEmissive(Mistinn.id("textures/effect/white.png"));
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
	private static final int SEGMENTS = 12;

	private record Shape(double reach, double bank, double sweep, double thickness, int color) {
	}

	private record Trail(Vec3 origin, Vec3 forward, Vec3 sideways, Shape shape, double progress, int side) {
	}

	private SwingTrails() {
	}

	public static void extract(final LevelExtractionContext context) {
		FabricRenderState frame = (FabricRenderState) context.levelState();
		var viewer = Minecraft.getInstance().player;
		if (viewer == null) {
			frame.setData(TRAILS, List.of());
			return;
		}
		float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
		List<Trail> trails = new ArrayList<>();
		for (var player : context.level().players()) {
			if (!player.isSwinging() || player.isInvisibleTo(viewer)
				|| player.distanceToSqr(context.camera().position()) > 1024.0) {
				continue;
			}
			var swing = player.getCurrentSwing();
			if (swing == null) {
				continue;
			}
			WeaponStyle style = WeaponStyle.of(player.getItemInHand(swing.hand()));
			if (style == null) {
				continue;
			}
			boolean main = swing.hand() == InteractionHand.MAIN_HAND;
			int side = (player.getMainArm() == HumanoidArm.RIGHT) == main ? 1 : -1;
			Vec3 forward = player.getViewVector(partial);
			Vec3 right = Vec3.directionFromRotation(0.0F, player.getYRot(partial)).cross(UP);
			Vec3 origin = player.getEyePosition(partial).add(0.0, -0.28, 0.0).add(forward.scale(0.22));
			float progress = WeaponSwings.progress(player.getSwingAnimation(partial));
			float contact = WeaponSwings.contact(style);
			Shape shape = shape(style);
			add(trails, origin, forward, right, shape, side, (progress - contact + 0.10) / 0.32);
			if (style == WeaponStyle.FLURRY) {
				Shape returning = new Shape(shape.reach, -shape.bank, shape.sweep, shape.thickness, shape.color);
				add(trails, origin, forward, right, returning, -side, (progress - contact - 0.12) / 0.29);
			}
		}
		frame.setData(TRAILS, trails);
	}

	private static Shape shape(final WeaponStyle style) {
		return switch (style) {
			case SLASH -> new Shape(1.72, 0.46, 1.22, 0.14, 0xDEC38C);
			case CLEAVE -> new Shape(2.10, 1.22, 1.06, 0.22, 0xF1AA62);
			case THRUST -> new Shape(2.86, 0.00, 0.00, 0.09, 0xABDED6);
			case FLURRY -> new Shape(1.28, 0.62, 0.92, 0.10, 0xECD9AF);
			case SMASH -> new Shape(1.69, 0.10, 1.50, 0.20, 0xCC8A53);
		};
	}

	private static void add(final List<Trail> trails, final Vec3 origin, final Vec3 forward, final Vec3 right,
		final Shape shape, final int side, final double progress) {
		if (progress > 0.0 && progress < 1.25) {
			Vec3 sideways = right.scale(Math.cos(shape.bank)).add(right.cross(forward).scale(Math.sin(shape.bank)));
			trails.add(new Trail(origin, forward, sideways, shape, progress, side));
		}
	}

	public static void submit(final LevelRenderContext context) {
		var trails = ((FabricRenderState) context.levelState()).getData(TRAILS);
		if (trails == null || trails.isEmpty()) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), LIGHT, (pose, buffer) -> {
			for (Trail trail : trails) {
				draw(pose, buffer, camera, trail);
			}
		});
	}

	private static void draw(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 camera, final Trail trail) {
		double head = Math.min(trail.progress, 1.0);
		double tail = Math.max(0.0, trail.progress - 0.68);
		double fade = Math.min(1.0, (1.25 - trail.progress) * 3.2);
		Vec3 origin = trail.origin.subtract(camera);
		for (int i = 0; i < SEGMENTS; i++) {
			double a = (double) i / SEGMENTS;
			double b = (double) (i + 1) / SEGMENTS;
			double t0 = Mth.lerp(a, tail, head);
			double t1 = Mth.lerp(b, tail, head);
			for (int layer = 0; layer < 2; layer++) {
				double width = trail.shape.thickness * (layer == 0 ? 1.5 : 0.47);
				double alpha = fade * (layer == 0 ? 102.0 : 225.0);
				int rgb = layer == 0 ? trail.shape.color : 0xFFF3D5;
				int c0 = ((int) (alpha * Math.sqrt(a)) << 24) | rgb;
				int c1 = ((int) (alpha * Math.sqrt(b)) << 24) | rgb;
				double w0 = width * taper(a);
				double w1 = width * taper(b);
				EffectVertex.put(pose, buffer, origin.add(point(trail, t0, -w0)), c0, 0.0F, 0.0F);
				EffectVertex.put(pose, buffer, origin.add(point(trail, t1, -w1)), c1, 1.0F, 0.0F);
				EffectVertex.put(pose, buffer, origin.add(point(trail, t1, w1)), c1, 1.0F, 1.0F);
				EffectVertex.put(pose, buffer, origin.add(point(trail, t0, w0)), c0, 0.0F, 1.0F);
			}
		}
	}

	private static double taper(final double position) {
		return 0.12 + 0.88 * Math.sin(position * Math.PI);
	}

	private static Vec3 point(final Trail trail, final double progress, final double width) {
		Shape shape = trail.shape;
		if (shape.sweep == 0.0) {
			return trail.forward.scale(Mth.lerp(progress, 0.56, shape.reach))
				.add(trail.sideways.scale(trail.side * (0.26 - progress * 0.18) + width))
				.add(0.0, -0.18 + progress * 0.15, 0.0);
		}
		double angle = (1.0 - 2.0 * progress) * shape.sweep * trail.side;
		double radius = shape.reach + width * 0.4;
		// Cant the ribbon across the swing plane so its edge still reads from the hunter's view.
		return trail.forward.scale(Math.cos(angle) * radius).add(trail.sideways.scale(Math.sin(angle) * radius))
			.add(trail.forward.cross(trail.sideways).scale(width));
	}
}
