package life.shizune.mistinn.client.terra;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the crystal lizard's model needs to pose it: how flat and still it lies, and its bite. */
public class CrystalLizardRenderState extends LivingEntityRenderState {
	/** 0 up and about, 1 lying flat and still. */
	public float still;
	/** 0 jaw shut, 1 open to bite. */
	public float bite;
}
