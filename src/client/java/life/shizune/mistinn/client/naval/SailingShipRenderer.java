package life.shizune.mistinn.client.naval;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import life.shizune.mistinn.naval.SailingShip;
import life.shizune.mistinn.naval.ShipFrame;
import life.shizune.mistinn.naval.ShipPlan;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws a pirate ship under sail whole, block by block from its plan, riding the swell: a slow
 * roll about the keel, a pitch and a heave, all settling as it comes to its berth, so the real ship
 * that takes its place there stands where the last of it was drawn. Its lanterns burn bright in the fog.
 */
public class SailingShipRenderer extends EntityRenderer<SailingShip, SailingShipRenderer.State> {
	/** Full light: a lantern is lit whatever the night. */
	private static final int LIT = 0xF000F0;
	private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();

	private final BlockModelResolver resolver;
	private final Map<BlockState, BlockModelRenderState> models = new IdentityHashMap<>();
	private List<Map.Entry<BlockPos, BlockState>> visible;

	public SailingShipRenderer(final EntityRendererProvider.Context context) {
		super(context);
		this.resolver = context.getBlockModelResolver();
		this.shadowRadius = 0.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(final SailingShip ship, final State state, final float partialTicks) {
		super.extractRenderState(ship, state, partialTicks);
		state.yaw = new ShipFrame(BlockPos.ZERO, ship.turn()).yaw();
		state.time = ship.tickCount + partialTicks;
		state.settled = ship.progress();
		if (this.visible == null) {
			this.visible = ShipPlan.visible();
		}
		// the models are looked up again each frame, so a resource reload never leaves the ship drawn with old ones
		for (Map.Entry<BlockPos, BlockState> block : this.visible) {
			this.models.computeIfAbsent(block.getValue(), s -> new BlockModelRenderState());
		}
		for (Map.Entry<BlockState, BlockModelRenderState> model : this.models.entrySet()) {
			this.resolver.update(model.getValue(), model.getKey(), CONTEXT);
		}
	}

	@Override
	protected boolean affectedByCulling(final SailingShip ship) {
		// its middle may be off screen while its bow or masts are not
		return false;
	}

	@Override
	public void submit(final State state, final PoseStack pose, final SubmitNodeCollector collector, final CameraRenderState camera) {
		if (this.visible == null) {
			return;
		}
		float t = state.time;
		// the swell rocks it less and less as it comes to its berth, and not at all once there
		float calm = (1.0F - state.settled) * (1.0F - state.settled);
		pose.pushPose();
		pose.rotateDegrees(Axis.YP, -(state.yaw + 90.0F));
		pose.translate(0.0F, Mth.sin(t * 0.07F) * 0.18F * calm, 0.0F);
		pose.rotateDegrees(Axis.XP, Mth.sin(t * 0.05F) * 2.2F * calm);
		pose.rotateDegrees(Axis.ZP, Mth.sin(t * 0.037F + 1.3F) * 1.0F * calm);
		for (Map.Entry<BlockPos, BlockState> block : this.visible) {
			BlockModelRenderState model = this.models.get(block.getValue());
			if (model == null || model.isEmpty()) {
				continue;
			}
			BlockPos at = block.getKey();
			pose.pushPose();
			pose.translate(at.getX() - 0.5F, at.getY(), at.getZ() - 0.5F);
			model.submit(pose, collector, ShipPlan.glows(block.getValue()) ? LIT : state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
		pose.popPose();
	}

	/** Which way it lies, how long it has sailed, and how near its berth it is. */
	public static final class State extends EntityRenderState {
		public float yaw;
		public float time;
		public float settled;
	}
}
