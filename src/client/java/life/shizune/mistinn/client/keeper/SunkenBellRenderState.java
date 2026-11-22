package life.shizune.mistinn.client.keeper;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.jspecify.annotations.Nullable;

/** 沉鐘 as the renderer sees it: the humanoid pose, its move, its idle, and whether its bell has cracked. */
public class SunkenBellRenderState extends HumanoidRenderState {
	/** The move the body is in (null: wading or standing), how far into it (ticks, with the partial tick), and its length. */
	public @Nullable String move;
	public float moveTicks;
	public int moveLength;
	/** The idle it plays while standing with no move (null: none now), and how far into it. */
	public @Nullable String idle;
	public float idleTicks;
	/** 裂鐘: below half health its bell is cracked through. */
	public boolean cracked;
}
