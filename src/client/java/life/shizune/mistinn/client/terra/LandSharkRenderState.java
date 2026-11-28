package life.shizune.mistinn.client.terra;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the land shark's model needs: how far out of the ground it is (0 swimming under it, 1 leaping out). */
public class LandSharkRenderState extends LivingEntityRenderState {
	public float out;
}
