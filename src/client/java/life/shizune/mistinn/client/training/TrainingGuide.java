package life.shizune.mistinn.client.training;

import java.util.Comparator;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.anim.EffectVertex;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.keeper.LampStandBlock;
import life.shizune.mistinn.training.TrainingLesson;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/** An amber pier marker, with a direction arrow when the next station leaves the view. */
public final class TrainingGuide {
	private static final Identifier ARROW = Mistinn.id("textures/effect/training_arrow.png");
	private static final RenderType LIGHT = RenderTypes.entityTranslucentEmissive(ARROW);
	private static final RenderStateDataKey<Vec3> MARK = RenderStateDataKey.create(() -> "Mistinn training guide");
	private static final int SIZE = 24;
	private static final double DRAW_DISTANCE = 64.0;

	private TrainingGuide() {
	}

	public static @Nullable BlockPos target(final Minecraft mc, final TrainingLesson lesson) {
		if (mc.player == null) {
			return null;
		}
		var player = mc.player;
		return switch (lesson) {
			case GEAR -> TrainingLesson.KIT;
			case CRAFT -> TrainingLesson.CRAFTING_TABLE;
			case RECOVER -> {
				var mark = player.getAttached(MistinnAttachments.DEATH_MARK);
				yield mark != null && mark.pos().dimension().equals(player.level().dimension()) ? mark.pos().pos() : null;
			}
			case LAMPS -> TrainingLesson.LAMP_POSITIONS.stream().filter(pos -> {
				var state = player.level().getBlockState(pos);
				return !state.is(MistinnBlocks.LAMP_STAND) || !state.getValue(LampStandBlock.LIT);
			}).min(Comparator.comparingDouble(pos -> player.distanceToSqr(Vec3.atCenterOf(pos)))).orElse(null);
			case BOARD -> TrainingLesson.BOARD_BUTTON;
			case DEPLOY -> null;
			default -> lesson.target;
		};
	}

	/** Where to lead the hunter now: along the course's route toward the lesson's spot, or the spot when near. */
	public static @Nullable BlockPos lead(final Minecraft mc, final TrainingLesson lesson) {
		BlockPos spot = target(mc, lesson);
		if (spot == null || mc.player == null) {
			return spot;
		}
		return life.shizune.mistinn.training.TrainingGround.nextStop(mc.player.position(), lesson, spot);
	}

	public static void extract(final LevelExtractionContext context) {
		Minecraft mc = Minecraft.getInstance();
		TrainingLesson lesson = TrainingClient.current(mc);
		BlockPos target = lesson == null || mc.gui.hud.isHidden() || mc.gui.screen() != null ? null : lead(mc, lesson);
		Vec3 marker = target == null ? null : Vec3.atCenterOf(target).add(0.0, 1.30, 0.0);
		((FabricRenderState) context.levelState()).setData(MARK, marker);
	}

	public static void submit(final LevelRenderContext context) {
		var state = context.levelState();
		Vec3 marker = ((FabricRenderState) state).getData(MARK);
		if (marker == null) {
			return;
		}
		var camera = state.cameraRenderState;
		Vec3 offset = marker.subtract(camera.pos);
		double distance = offset.length();
		if (distance < 0.65 || distance > DRAW_DISTANCE) {
			return;
		}
		double pulse = Math.sin((state.gameTime % 50L + state.worldPartialTicks) * Math.PI / 25.0);
		Vec3 center = offset.add(0.0, 0.14 * pulse, 0.0);
		float size = (float) Mth.clamp(distance * 0.034, 0.22, 0.99);
		Vec3 right = new Vec3(camera.orientation.transform(new Vector3f(size, 0.0F, 0.0F)));
		Vec3 up = new Vec3(camera.orientation.transform(new Vector3f(0.0F, size, 0.0F)));
		int color = ((int) (217.0 + 33.0 * pulse) << 24) | 0xFFFFFF;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), LIGHT, (pose, buffer) -> {
			EffectVertex.put(pose, buffer, center.subtract(right).subtract(up), color, 1.0F, 0.0F);
			EffectVertex.put(pose, buffer, center.add(right).subtract(up), color, 0.0F, 0.0F);
			EffectVertex.put(pose, buffer, center.add(right).add(up), color, 0.0F, 1.0F);
			EffectVertex.put(pose, buffer, center.subtract(right).add(up), color, 1.0F, 1.0F);
		});
	}

	static void drawDirection(final GuiGraphicsExtractor graphics, final Minecraft mc, final TrainingLesson lesson) {
		BlockPos target = lead(mc, lesson);
		if (target == null) {
			return;
		}
		var camera = mc.gameRenderer.mainCamera();
		Vec3 offset = Vec3.atCenterOf(target).add(0.0, 1.30, 0.0).subtract(camera.position());
		double forward = offset.dot(new Vec3(camera.forwardVector()));
		double right = -offset.dot(new Vec3(camera.leftVector()));
		double up = offset.dot(new Vec3(camera.upVector()));
		double verticalEdge = forward * Math.tan(Math.toRadians(camera.getFov()) * 0.5) * 0.92;
		double horizontalEdge = verticalEdge * graphics.guiWidth() / graphics.guiHeight();
		if (forward > 0.0 && offset.lengthSqr() <= DRAW_DISTANCE * DRAW_DISTANCE
			&& Math.abs(right) < horizontalEdge && Math.abs(up) < verticalEdge) {
			return;
		}
		// Behind the camera, point sideways so the player knows which way to turn.
		double angle = forward > 0.0 ? Math.atan2(right, up) : Math.copySign(Math.PI / 2.0, right);
		float x = graphics.guiWidth() * (0.5F + 0.22F * (float) Math.sin(angle));
		float y = graphics.guiHeight() * 0.30F;
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.rotate((float) angle);
		graphics.blit(RenderPipelines.GUI_TEXTURED, ARROW, -SIZE / 2, -SIZE / 2, 0, 0, SIZE, SIZE, SIZE, SIZE);
		pose.popMatrix();
	}
}
