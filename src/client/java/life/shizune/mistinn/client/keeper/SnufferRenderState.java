package life.shizune.mistinn.client.keeper;

import life.shizune.mistinn.keeper.Snuffer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** 熄燈人 as the renderer sees it: the humanoid pose, its move, and which flame burns in its lantern. */
public class SnufferRenderState extends HumanoidRenderState {
	/** 冷焰: the dark half, when the lantern burns cold blue (a soul lantern in the head slot). */
	public boolean soulFlame;
	/** What the body is doing, and for how long (ticks, with the partial tick). */
	public Snuffer.Action action = Snuffer.Action.NONE;
	public float actionTicks;
	/** How fast the body is turning, degrees per tick: the lantern swings out against it. */
	public float turnSpeed;
}
