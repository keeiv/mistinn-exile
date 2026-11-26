package life.shizune.mistinn.client.rift;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the rift walker's model needs to pose it: which look it wears, its blow, whether it is leaping, and its own twitch. */
public class RiftWalkerRenderState extends LivingEntityRenderState {
	public int look;
	/** How far through a blow, 0 to 1. */
	public float attack;
	/** In the air at its quarry. */
	public boolean aloft;
	/** Its own offset for the twitches of its head, so a pack does not jerk as one. */
	public float twitch;
}
