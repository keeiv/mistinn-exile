package life.shizune.mistinn.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/** Full-bright vertices for effects that should keep their colour in the mist. */
public final class EffectVertex {
	private EffectVertex() {
	}

	public static void put(final PoseStack.Pose pose, final VertexConsumer buffer, final Vec3 point,
		final int color, final float u, final float v) {
		buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setColor(color)
			.setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
			.setNormal(pose, 0.0F, 0.0F, 1.0F);
	}
}
