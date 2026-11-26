package life.shizune.mistinn.client.terra;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.anim.EffectVertex;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 晶光: over a Terra highland on a clear night, the lattice's light hangs in the sky in slow
 * curtains, jade at their foot fading up to a pale violet, swaying and brightening along their
 * length. It comes up as the night darkens and goes with the dawn, and only over the highlands.
 * Drawn like the sky rifts: a curtain far off is drawn nearer and smaller in the same proportion.
 */
public final class TerraAurora {
	private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(Mistinn.id("textures/effect/rift_glow.png"));
	private static final RenderStateDataKey<List<Curtain>> CURTAINS = RenderStateDataKey.create(() -> "Mistinn Terra aurora");
	/** How many curtains, how long each, how high they hang over the eye, and how tall at the most. */
	private static final int COUNT = 3;
	private static final double LENGTH = 240.0;
	private static final double OVER = 80.0;
	private static final double HEIGHT = 46.0;
	private static final int SEGMENTS = 40;
	private static final double DRAWN_WITHIN = 60.0;
	/** How far out past a highland's edge it is still seen. */
	private static final double MARGIN = 48.0;
	/** How fast it comes and goes, of the whole way each frame's tick. */
	private static final float STEP = 0.01F;
	/** Jade at the foot, pale violet up top. */
	private static final int FOOT = 0x6CF2C8;
	private static final int CROWN = 0x9A86FF;

	private static float shown;

	/** One curtain this frame: its middle, which way it runs, its time and its own seed, and how bright. */
	private record Curtain(Vec3 middle, double angle, double time, int seed, float bright) {
	}

	private TerraAurora() {
	}

	/** How much of the aurora shows now: 0 none, 1 the full night over a highland. */
	public static float shown() {
		return shown;
	}

	public static void extract(final LevelExtractionContext context) {
		ClientLevel level = Minecraft.getInstance().level;
		FabricRenderState frame = (FabricRenderState) context.levelState();
		Vec3 camera = context.camera().position();
		Domain over = level == null ? null : over(level, camera);
		float night = level == null ? 0.0F : Mth.clamp((level.getSkyDarken() - 3) / 6.0F, 0.0F, 1.0F);
		float want = over == null ? 0.0F : night;
		shown += Mth.clamp(want - shown, -STEP, STEP);
		if (shown <= 0.001F || over == null || level == null) {
			frame.setData(CURTAINS, List.of());
			return;
		}
		double now = level.getGameTime() + context.deltaTracker().getGameTimeDeltaPartialTick(false);
		List<Curtain> curtains = new ArrayList<>();
		for (int k = 0; k < COUNT; k++) {
			int seed = over.id() * 31 + k;
			double angle = (over.id() * 1.7 + k * 0.9) % Math.PI;
			double off = (k - 1) * 70.0;
			Vec3 middle = new Vec3(over.centre().getX() + 0.5 + Math.sin(angle) * off, camera.y + OVER + k * 6.0,
				over.centre().getZ() + 0.5 - Math.cos(angle) * off);
			curtains.add(new Curtain(middle, angle, now, seed, shown * (0.75F + 0.25F * k / (COUNT - 1.0F))));
		}
		frame.setData(CURTAINS, curtains);
	}

	/** The highland the camera is over, or near enough to see its sky. */
	private static Domain over(final ClientLevel level, final Vec3 camera) {
		List<Domain> view = level.getAttached(DomainService.VIEW);
		if (view == null) {
			return null;
		}
		Domain nearest = null;
		double best = Double.MAX_VALUE;
		for (Domain domain : view) {
			double distance = domain.distance(camera.x, camera.z);
			if (domain.kind() == Domain.Kind.TERRA && distance <= domain.radius() + MARGIN && distance < best) {
				best = distance;
				nearest = domain;
			}
		}
		return nearest;
	}

	public static void submit(final LevelRenderContext context) {
		List<Curtain> curtains = ((FabricRenderState) context.levelState()).getData(CURTAINS);
		if (curtains == null || curtains.isEmpty()) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GLOW, (pose, buffer) -> {
			for (Curtain curtain : curtains) {
				draw(pose, buffer, camera, curtain);
			}
		});
	}

	/** A curtain: a wavering line high up, hung with light from its foot, brightening and dimming along its length. */
	private static void draw(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 camera, final Curtain curtain) {
		double distance = camera.distanceTo(curtain.middle());
		double shrink = distance > DRAWN_WITHIN ? DRAWN_WITHIN / distance : 1.0;
		Vec3 origin = camera.add(curtain.middle().subtract(camera).scale(shrink));
		Vec3 along = new Vec3(Math.cos(curtain.angle()), 0.0, Math.sin(curtain.angle()));
		Vec3 side = new Vec3(-along.z, 0.0, along.x);
		double t = curtain.time();
		Vec3[] foot = new Vec3[SEGMENTS + 1];
		double[] tall = new double[SEGMENTS + 1];
		float[] glow = new float[SEGMENTS + 1];
		for (int i = 0; i <= SEGMENTS; i++) {
			double s = i / (double) SEGMENTS;
			double reach = (s - 0.5) * LENGTH;
			double sway = Math.sin(t * 0.006 + s * 7.0 + curtain.seed()) * 16.0 + Math.sin(t * 0.011 + s * 15.0) * 5.0;
			double dip = Math.sin(t * 0.004 + s * 5.0 + curtain.seed() * 0.7) * 6.0;
			foot[i] = origin.add(along.scale(reach * shrink)).add(side.scale(sway * shrink)).add(0.0, dip * shrink, 0.0);
			tall[i] = HEIGHT * (0.55 + 0.45 * Math.sin(s * 9.0 + t * 0.008 + curtain.seed())) * shrink;
			// fading out at both ends, brightening in slow pulses along the way
			double ends = Math.pow(Math.sin(Math.PI * s), 0.6);
			double pulse = 0.55 + 0.45 * Math.sin(t * 0.02 - s * 11.0 + curtain.seed() * 1.3);
			glow[i] = (float) (ends * pulse) * curtain.bright();
		}
		for (int i = 0; i < SEGMENTS; i++) {
			Vec3 a = foot[i].subtract(camera);
			Vec3 b = foot[i + 1].subtract(camera);
			Vec3 at = a.add(0.0, tall[i], 0.0);
			Vec3 bt = b.add(0.0, tall[i + 1], 0.0);
			int fa = color(FOOT, 0.55F * glow[i]);
			int fb = color(FOOT, 0.55F * glow[i + 1]);
			int ca = color(CROWN, 0.12F * glow[i]);
			int cb = color(CROWN, 0.12F * glow[i + 1]);
			// both faces, so it shows from either side
			EffectVertex.put(pose, buffer, a, fa, 0.0F, 0.5F);
			EffectVertex.put(pose, buffer, at, ca, 0.0F, 0.0F);
			EffectVertex.put(pose, buffer, bt, cb, 1.0F, 0.0F);
			EffectVertex.put(pose, buffer, b, fb, 1.0F, 0.5F);
			EffectVertex.put(pose, buffer, b, fb, 1.0F, 0.5F);
			EffectVertex.put(pose, buffer, bt, cb, 1.0F, 0.0F);
			EffectVertex.put(pose, buffer, at, ca, 0.0F, 0.0F);
			EffectVertex.put(pose, buffer, a, fa, 0.0F, 0.5F);
		}
	}

	private static int color(final int rgb, final float alpha) {
		return (Mth.clamp((int) (alpha * 255.0F), 0, 255) << 24) | rgb;
	}
}
