package life.shizune.mistinn.client.terra;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the crystal drake's model needs to pose it: how far aloft it is, how far into a dive, its pitch, and its bite. */
public class CrystalDrakeRenderState extends LivingEntityRenderState {
	/** 0 on the ground, 1 in the air. */
	public float aloft;
	/** 0 flying level, 1 swept back into a dive. */
	public float swoop;
	/** How far its body is pitched nose-down, in radians (negative: climbing). */
	public float dive;
	/** 0 jaw shut, 1 open to bite. */
	public float bite;
}
