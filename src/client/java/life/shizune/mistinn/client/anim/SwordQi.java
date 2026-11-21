package life.shizune.mistinn.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.weapon.HeavyService;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 劍氣: every swing leaves a cut of pixel light in the air at its contact, the way a vanilla
 * sweep does, turned to the blow's direction: a diagonal slash, a chop straight down, a thrust's
 * line, two crossing cuts, a wide sweep, a rising reap. Heavy blows leave bigger, brighter ones
 * (a full charge adds a white core). The sprites ({@code textures/effect/qi_*.png}) are white
 * and grey and tinted per branch here.
 */
public final class SwordQi {
	private static final RenderStateDataKey<List<Cut>> CUTS = RenderStateDataKey.create(() -> "Mistinn sword qi");
	private static final RenderType ARC = RenderTypes.entityTranslucentEmissive(Mistinn.id("textures/effect/qi_arc.png"));
	private static final RenderType LINE = RenderTypes.entityTranslucentEmissive(Mistinn.id("textures/effect/qi_thrust.png"));
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
	/** Frames in each sprite sheet, and how many ticks a cut lasts. */
	public static final int FRAMES = 8;
	public static final float LIGHT_TICKS = 6.0F;
	public static final float HEAVY_TICKS = 8.0F;

	/**
	 * How a branch's cut is laid: {@code roll} turns the sprite in the view plane (degrees, the cut
	 * travelling from the sprite's right end to its left), {@code drop} lowers it, {@code width}
	 * and {@code height} in blocks, {@code line} for a thrust.
	 */
	public record Lay(float roll, double drop, double width, double height, boolean line, int color) {
	}

	/** One cut to draw this frame: where, which way, which frame of the sheet. */
	public record Cut(Vec3 centre, Vec3 across, Vec3 up, int frame, int color, boolean line) {
	}

	/** For the effect sheet test only: when 0 or more, every cut is drawn frozen at this share of its life. */
	public static float preview = -1.0F;

	private SwordQi() {
	}

	public static Lay lay(final WeaponStyle style, final boolean heavy, final int cut) {
		return switch (style) {
			case SLASH -> new Lay(35.0F, 0.15, heavy ? 3.4 : 2.2, heavy ? 1.7 : 1.1, false, 0xDEC38C);
			case CLEAVE -> new Lay(90.0F, 0.1, heavy ? 3.6 : 2.4, heavy ? 1.6 : 1.1, false, 0xF1AA62);
			case THRUST -> new Lay(0.0F, 0.35, heavy ? 3.8 : 2.6, heavy ? 0.75 : 0.5, true, 0xABDED6);
			case FLURRY -> new Lay(cut == 0 ? 30.0F : 150.0F, 0.15, heavy ? 2.8 : 1.8, heavy ? 1.4 : 0.9, false, 0xECD9AF);
			case SMASH -> new Lay(0.0F, 0.25, heavy ? 4.4 : 2.9, heavy ? 1.8 : 1.2, false, 0xCC8A53);
			case REAP -> heavy ? new Lay(0.0F, 0.65, 4.6, 1.6, false, 0xC9D7B2) : new Lay(205.0F, 0.2, 2.5, 1.2, false, 0xC9D7B2);
		};
	}

	public static void extract(final LevelExtractionContext context) {
		FabricRenderState frame = (FabricRenderState) context.levelState();
		var viewer = Minecraft.getInstance().player;
		if (viewer == null) {
			frame.setData(CUTS, List.of());
			return;
		}
		float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
		List<Cut> cuts = new ArrayList<>();
		for (Player player : context.level().players()) {
			if (player.isInvisibleTo(viewer) || player.distanceToSqr(context.camera().position()) > 1024.0) {
				continue;
			}
			int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
			HeavyClient.Pose heavy = HeavyClient.sample(player, partial);
			if (heavy != null && heavy.phase() == HeavyClient.Phase.STRIKE) {
				int strike = HeavyClient.strikeTicks(heavy.style());
				float since = heavy.progress() * strike - HeavyService.shape(heavy.style(), null).landTicks() + 1.0F;
				addHeavy(cuts, player, partial, heavy.style(), side, since, heavy.charge());
				continue;
			}
			if (!player.isSwinging()) {
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
			int handSide = (player.getMainArm() == HumanoidArm.RIGHT) == main ? 1 : -1;
			float progress = WeaponSwings.progress(player.getSwingAnimation(partial));
			float since = (progress - WeaponSwings.contact(style)) * style.swingTicks + 1.0F;
			add(cuts, player, partial, style, false, 0, handSide, since / LIGHT_TICKS, 0xFF);
			if (style == WeaponStyle.FLURRY) {
				add(cuts, player, partial, style, false, 1, handSide, (since - 3.0F) / LIGHT_TICKS, 0xFF);
			}
		}
		frame.setData(CUTS, cuts);
	}

	private static void addHeavy(final List<Cut> cuts, final Player player, final float partial, final WeaponStyle style,
		final int side, final float since, final float charge) {
		add(cuts, player, partial, style, true, 0, side, since / HEAVY_TICKS, 0xFF);
		if (style == WeaponStyle.FLURRY) {
			add(cuts, player, partial, style, true, 1, side, (since - HeavyService.FLURRY_GAP) / HEAVY_TICKS, 0xFF);
		}
		if (style == WeaponStyle.CLEAVE) {
			// the chop meets the ground and a level wave runs out from it
			add(cuts, player, partial, WeaponStyle.SMASH, true, 0, side, (since - 1.0F) / HEAVY_TICKS, 0xFF);
		}
		if (charge >= 0.999F) {
			add(cuts, player, partial, style, true, -1, side, since / HEAVY_TICKS, 0xFFFFFF);
		}
	}

	/**
	 * Adds one cut {@code life} of the way through (0..1). {@code cut} 1 is the second of two;
	 * -1 is a full charge's white core, drawn smaller over the first.
	 */
	private static void add(final List<Cut> cuts, final Player player, final float partial, final WeaponStyle style,
		final boolean heavy, final int cut, final int side, final float life, final int tint) {
		float at = preview >= 0.0F ? preview : life;
		if (at < 0.0F || at >= 1.0F) {
			return;
		}
		Lay lay = lay(style, heavy, Math.max(cut, 0));
		Vec3 forward = player.getViewVector(partial);
		Vec3 right = forward.cross(UP);
		right = right.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(0.0F, player.getYRot(partial) + 90.0F) : right.normalize();
		Vec3 up = right.cross(forward).normalize();
		Vec3 eye = player.getEyePosition(partial);
		double scale = cut < 0 ? 0.6 : 1.0;
		int color = cut < 0 ? 0xFFFFFF : (tint == 0xFF ? lay.color() : tint);
		int frame = Math.min(FRAMES - 1, (int) (at * FRAMES));
		if (lay.line()) {
			// a thrust: the streak runs out along the look from below and to the weapon side of the eye,
			// its face turned to the eye so it reads as a line driven forward, tip away from the hunter
			Vec3 aside = right.scale(0.28 * side).add(up.scale(-lay.drop()));
			Vec3 centre = eye.add(aside).add(forward.scale(0.6 + lay.width() / 2.0));
			Vec3 across = forward.cross(aside).normalize();
			cuts.add(new Cut(centre, forward.scale(lay.width() * scale / 2.0), across.scale(lay.height() * scale / 2.0), frame, color, true));
			return;
		}
		double distance = heavy ? 1.6 : 1.25;
		double roll = Math.toRadians(lay.roll());
		// the sprite's right end is where the cut starts; mirrored for a left-handed blow
		Vec3 across = right.scale(Math.cos(roll) * side).add(up.scale(Math.sin(roll)));
		Vec3 sprUp = right.scale(-Math.sin(roll) * side).add(up.scale(Math.cos(roll)));
		Vec3 centre = eye.add(forward.scale(distance)).add(up.scale(-lay.drop()));
		cuts.add(new Cut(centre, across.scale(lay.width() * scale / 2.0), sprUp.scale(lay.height() * scale / 2.0), frame, color, false));
	}

	public static void submit(final LevelRenderContext context) {
		List<Cut> cuts = ((FabricRenderState) context.levelState()).getData(CUTS);
		if (cuts == null || cuts.isEmpty()) {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), ARC, (pose, buffer) -> {
			for (Cut cut : cuts) {
				if (!cut.line()) {
					draw(pose, buffer, camera, cut);
				}
			}
		});
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), LINE, (pose, buffer) -> {
			for (Cut cut : cuts) {
				if (cut.line()) {
					draw(pose, buffer, camera, cut);
				}
			}
		});
	}

	/** One quad, both faces, showing frame {@code cut.frame} of its sheet (frames stacked top to bottom). */
	private static void draw(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 camera, final Cut cut) {
		Vec3 c = cut.centre().subtract(camera);
		float v0 = cut.frame() / (float) FRAMES;
		float v1 = (cut.frame() + 1) / (float) FRAMES;
		int color = 0xF0000000 | (cut.color() & 0xFFFFFF);
		// the sprite's x runs from its left (u 0) to its right (u 1); its top is v0
		Vec3 topLeft = c.subtract(cut.across()).add(cut.up());
		Vec3 topRight = c.add(cut.across()).add(cut.up());
		Vec3 bottomRight = c.add(cut.across()).subtract(cut.up());
		Vec3 bottomLeft = c.subtract(cut.across()).subtract(cut.up());
		EffectVertex.put(pose, buffer, topLeft, color, 0.0F, v0);
		EffectVertex.put(pose, buffer, bottomLeft, color, 0.0F, v1);
		EffectVertex.put(pose, buffer, bottomRight, color, 1.0F, v1);
		EffectVertex.put(pose, buffer, topRight, color, 1.0F, v0);
		EffectVertex.put(pose, buffer, topRight, color, 1.0F, v0);
		EffectVertex.put(pose, buffer, bottomRight, color, 1.0F, v1);
		EffectVertex.put(pose, buffer, bottomLeft, color, 0.0F, v1);
		EffectVertex.put(pose, buffer, topLeft, color, 0.0F, v0);
	}
}
