package life.shizune.mistinn.client.keeper;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;

/** What the Rust Wheel is drawn from: how far the wheel has turned, its move, and whether it is stuck or broken. */
public class RustWheelRenderState extends LivingEntityRenderState {
	public float turned;
	public @Nullable String move;
	public float moveTicks;
	public int moveWindup;
	public boolean stuck;
	public boolean broken;
	public int momentum;
}
