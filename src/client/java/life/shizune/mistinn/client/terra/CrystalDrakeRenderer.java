package life.shizune.mistinn.client.terra;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.terra.CrystalDrake;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws a crystal drake, its eyes, core gem and seams of light glowing in the dark; the crown
 * elder is the same creature, drawn bigger in its own colours.
 */
public class CrystalDrakeRenderer<T extends CrystalDrake> extends MobRenderer<T, CrystalDrakeRenderState, CrystalDrakeModel> {
	private final Identifier texture;
	private final float size;

	public CrystalDrakeRenderer(final EntityRendererProvider.Context context, final String name, final float size) {
		super(context, new CrystalDrakeModel(context.bakeLayer(CrystalDrakeModel.LAYER)), 1.4F * size);
		this.texture = Mistinn.id("textures/entity/terra/" + name + ".png");
		this.size = size;
		RenderType glow = RenderTypes.eyes(Mistinn.id("textures/entity/terra/" + name + "_glow.png"));
		this.addLayer(new EyesLayer<>(this) {
			@Override
			public RenderType renderType() {
				return glow;
			}
		});
	}

	public static CrystalDrakeRenderer<CrystalDrake> drake(final EntityRendererProvider.Context context) {
		return new CrystalDrakeRenderer<>(context, "crystal_drake", 1.0F);
	}

	public static CrystalDrakeRenderer<life.shizune.mistinn.terra.CrystalElder> elder(final EntityRendererProvider.Context context) {
		return new CrystalDrakeRenderer<>(context, "crystal_elder", 2.2F);
	}

	@Override
	protected void scale(final CrystalDrakeRenderState state, final com.mojang.blaze3d.vertex.PoseStack poseStack) {
		poseStack.scale(this.size, this.size, this.size);
	}

	@Override
	public CrystalDrakeRenderState createRenderState() {
		return new CrystalDrakeRenderState();
	}

	@Override
	public void extractRenderState(final T entity, final CrystalDrakeRenderState state, final float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.aloft = entity.aloft(partialTicks);
		state.swoop = entity.swoop(partialTicks);
		state.bite = entity.bite(partialTicks);
		if (entity instanceof life.shizune.mistinn.terra.CrystalElder elder) {
			state.move = elder.move();
			state.moveAge = elder.moveAge(partialTicks);
		} else {
			state.move = life.shizune.mistinn.terra.CrystalElder.Move.NONE;
			state.moveAge = 0.0F;
		}
		// pitched along its flight: nose down into a dive, up as it climbs
		double rise = entity.getDeltaMovement().y;
		double across = entity.getDeltaMovement().horizontalDistance();
		state.dive = state.aloft * Mth.clamp((float) -Math.atan2(rise, Math.max(0.05, across)), -0.6F, 0.9F);
	}

	@Override
	public Identifier getTextureLocation(final CrystalDrakeRenderState state) {
		return this.texture;
	}
}
