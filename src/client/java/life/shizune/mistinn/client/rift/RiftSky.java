package life.shizune.mistinn.client.rift;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.anim.EffectVertex;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.rift.RiftService;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The sky rift itself: a long jagged tear hanging high over the middle of an open rift: a dark red
 * depth seen through it, edged white-hot, rimmed vermilion, in a haze of red light; it slowly
 * breathes and drips light. Drawn from the domains
 * every client is told of, so it is seen from far off; a rift that is far away is drawn nearer and
 * smaller in the same proportion, so it keeps its size in the sky without being lost past the
 * far plane. When the rift is sealed the tear narrows shut.
 */
public final class RiftSky {
	private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(Mistinn.id("textures/effect/rift_glow.png"));
	private static final RenderStateDataKey<List<Tear>> TEARS = RenderStateDataKey.create(() -> "Mistinn sky rifts");
	/** How long the tear is, and how wide at its widest. */
	public static final double LENGTH = 150.0;
	public static final double WIDTH = 17.0;
	private static final int SEGMENTS = 26;
	/** Past this far the tear is drawn nearer, scaled to keep its size in the sky. */
	private static final double DRAWN_WITHIN = 56.0;
	/** How long a new tear takes to open. */
	private static final double OPENING = 80.0;

	/** One tear to draw this frame: where it hangs, which way it runs, how open it is, and the time for its breathing. */
	private record Tear(Vec3 middle, double angle, double open, double time, int seed) {
	}

	private RiftSky() {
	}

	public static void extract(final LevelExtractionContext context) {
		ClientLevel level = Minecraft.getInstance().level;
		FabricRenderState frame = (FabricRenderState) context.levelState();
		List<Domain> view = level == null ? null : level.getAttached(DomainService.VIEW);
		if (view == null || view.isEmpty()) {
			frame.setData(TEARS, List.of());
			return;
		}
		double now = level.getGameTime() + context.deltaTracker().getGameTimeDeltaPartialTick(false);
		List<Tear> tears = new ArrayList<>();
		for (Domain domain : view) {
			if (domain.kind() != Domain.Kind.RIFT) {
				continue;
			}
			double open;
			if (domain.open()) {
				open = domain.phase() == Domain.Phase.OPEN ? Mth.clamp((now - domain.since()) / OPENING, 0.0, 1.0) : 1.0;
			} else if (domain.phase() == Domain.Phase.SCAR && now - domain.since() < RiftService.CLOSING) {
				open = 1.0 - (now - domain.since()) / RiftService.CLOSING;
			} else {
				continue;
			}
			Vec3 middle = new Vec3(domain.centre().getX() + 0.5, domain.centre().getY() + RiftService.HEIGHT, domain.centre().getZ() + 0.5);
			tears.add(new Tear(middle, (domain.id() * 2.399) % Math.PI, open, now, domain.id()));
			drip(level, middle, (domain.id() * 2.399) % Math.PI, open, context.camera().position());
		}
		frame.setData(TEARS, tears);
	}

	/** Now and then a drop of the rift's light falls from the tear, for whoever is near enough to see it. */
	private static void drip(final ClientLevel level, final Vec3 middle, final double angle, final double open, final Vec3 camera) {
		if (open < 0.5 || camera.distanceTo(middle) > 160.0 || level.getRandom().nextInt(3) != 0) {
			return;
		}
		double along = (level.getRandom().nextDouble() - 0.5) * LENGTH * 0.8;
		level.addParticle(ParticleTypes.FALLING_LAVA, middle.x + Math.cos(angle) * along, middle.y - 1.0, middle.z + Math.sin(angle) * along, 0.0, 0.0, 0.0);
	}

	public static void submit(final LevelRenderContext context) {
		List<Tear> tears = ((FabricRenderState) context.levelState()).getData(TEARS);
		if (tears == null || tears.isEmpty()) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), GLOW, (pose, buffer) -> {
			for (Tear tear : tears) {
				draw(pose, buffer, camera, tear);
			}
		});
	}

	/** The tear as three ribbons along one jagged line, widest in the middle: a dark red halo, the vermilion body, a white-hot core. */
	private static void draw(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 camera, final Tear tear) {
		double distance = camera.distanceTo(tear.middle());
		double shrink = distance > DRAWN_WITHIN ? DRAWN_WITHIN / distance : 1.0;
		Vec3 origin = camera.add(tear.middle().subtract(camera).scale(shrink));
		Vec3 along = new Vec3(Math.cos(tear.angle()), 0.0, Math.sin(tear.angle()));
		Vec3 side = new Vec3(-along.z, 0.0, along.x);
		double breathe = 1.0 + 0.12 * Math.sin(tear.time() * 0.07 + tear.seed());
		Vec3[] line = new Vec3[SEGMENTS + 1];
		double[] width = new double[SEGMENTS + 1];
		for (int i = 0; i <= SEGMENTS; i++) {
			double t = i / (double) SEGMENTS;
			// it opens from the middle out
			double reach = (t - 0.5) * LENGTH * Math.min(1.0, tear.open() * 1.4);
			double jag = i == 0 || i == SEGMENTS ? 0.0 : (jitter(tear.seed(), i) * 7.0 + Math.sin(tear.time() * 0.03 + i * 1.7) * 0.8);
			double dip = jitter(tear.seed() + 31, i) * 5.0;
			line[i] = origin.add(along.scale(reach * shrink)).add(side.scale(jag * shrink)).add(0.0, dip * shrink, 0.0);
			width[i] = WIDTH * Math.pow(Math.sin(Math.PI * t), 0.8) * (0.7 + 0.3 * Math.abs(jitter(tear.seed() + 7, i))) * breathe * tear.open() * shrink;
		}
		// each layer a little nearer the camera than the last, so the dark depth always shows through the glow
		Vec3 toward = camera.subtract(origin).normalize().scale(0.35 * shrink);
		ribbon(pose, buffer, camera, line, width, 2.8, 0x58A01408, Vec3.ZERO);
		ribbon(pose, buffer, camera, line, width, 1.25, 0xE8F0461E, toward);
		ribbon(pose, buffer, camera, line, width, 0.95, 0xFFFFD9B0, toward.scale(2.0));
		ribbon(pose, buffer, camera, line, width, 0.62, 0xF2280406, toward.scale(3.0));
	}

	/** A band along the line, turned to face the camera, {@code scale} times the line's width, in one colour. */
	private static void ribbon(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 camera, final Vec3[] line, final double[] width,
		final double scale, final int color, final Vec3 lift) {
		// one direction across the band at each point, shared by the two pieces that meet there, so the band runs unbroken
		Vec3[] across = new Vec3[line.length];
		for (int i = 0; i < line.length; i++) {
			Vec3 dir = line[Math.min(i + 1, line.length - 1)].subtract(line[Math.max(i - 1, 0)]);
			across[i] = dir.cross(camera.subtract(line[i])).normalize();
		}
		for (int i = 0; i < line.length - 1; i++) {
			Vec3 a = line[i].add(lift);
			Vec3 b = line[i + 1].add(lift);
			Vec3 wa = across[i].scale(width[i] * scale * 0.5);
			Vec3 wb = across[i + 1].scale(width[i + 1] * scale * 0.5);
			Vec3 a0 = a.subtract(wa).subtract(camera);
			Vec3 a1 = a.add(wa).subtract(camera);
			Vec3 b0 = b.subtract(wb).subtract(camera);
			Vec3 b1 = b.add(wb).subtract(camera);
			EffectVertex.put(pose, buffer, a0, color, 0.0F, 0.0F);
			EffectVertex.put(pose, buffer, a1, color, 0.0F, 1.0F);
			EffectVertex.put(pose, buffer, b1, color, 1.0F, 1.0F);
			EffectVertex.put(pose, buffer, b0, color, 1.0F, 0.0F);
			EffectVertex.put(pose, buffer, b0, color, 1.0F, 0.0F);
			EffectVertex.put(pose, buffer, b1, color, 1.0F, 1.0F);
			EffectVertex.put(pose, buffer, a1, color, 0.0F, 1.0F);
			EffectVertex.put(pose, buffer, a0, color, 0.0F, 0.0F);
		}
	}

	/** A steady number in [-1, 1] for a tear and a point along it. */
	private static double jitter(final int seed, final int i) {
		long h = seed * 0x9E3779B97F4A7C15L + i * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0x94D049BB133111EBL;
		h ^= h >>> 29;
		return ((h >>> 11) & 0xFFFFL) / 32767.5 - 1.0;
	}
}
