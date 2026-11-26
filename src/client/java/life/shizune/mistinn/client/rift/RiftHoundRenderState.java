package life.shizune.mistinn.client.rift;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the rift hound's model needs to pose it: how far crouched to lunge, how far into the lunge, and its bite. */
public class RiftHoundRenderState extends LivingEntityRenderState {
	/** 0 on its feet, 1 crouched low to lunge. */
	public float crouch;
	/** 0 on the ground, 1 stretched out in a lunge. */
	public float lunge;
	/** 0 jaw shut, 1 open to bite. */
	public float bite;
}
