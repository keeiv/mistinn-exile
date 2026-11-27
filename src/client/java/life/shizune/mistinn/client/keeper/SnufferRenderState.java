package life.shizune.mistinn.client.keeper;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.jspecify.annotations.Nullable;

/** 熄燈人 as the renderer sees it: the humanoid pose, its move, and which flame burns in its lantern. */
public class SnufferRenderState extends HumanoidRenderState {
	/** 冷焰: the dark half, when the lantern burns cold blue (a soul lantern in the head slot). */
	public boolean soulFlame;
	/** The move the body is in (null: walking or standing), how far into it (ticks, with the partial tick), and its length. */
	public @Nullable String move;
	public float moveTicks;
	public int moveLength;
	/** The idle it plays while standing with no move (null: none now), and how far into it. */
	public @Nullable String idle;
	public float idleTicks;
	/** How fast the body is turning, degrees per tick: the lantern swings out against it. */
	public float turnSpeed;
	/** How far its head has turned to the view (0 to 1), and to where: yaw from its body and pitch, in degrees. */
	public float watch;
	public float watchYaw;
	public float watchPitch;
}
