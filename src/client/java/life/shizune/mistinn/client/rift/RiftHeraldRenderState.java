package life.shizune.mistinn.client.rift;

import life.shizune.mistinn.rift.RiftHerald;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the rift herald's model needs to pose it: what it is casting and how far into it, and its blow. */
public class RiftHeraldRenderState extends LivingEntityRenderState {
	/** What it is doing with its hands. */
	public RiftHerald.Cast castKind = RiftHerald.Cast.NONE;
	/** 0 at rest, 1 fully into the cast. */
	public float cast;
	/** How far through a blow, 0 to 1. */
	public float attack;
}
