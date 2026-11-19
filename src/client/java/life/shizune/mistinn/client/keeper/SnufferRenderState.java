package life.shizune.mistinn.client.keeper;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** 熄燈人 as the renderer sees it: the humanoid pose, plus which flame burns in its lantern. */
public class SnufferRenderState extends HumanoidRenderState {
	/** 冷焰: the dark half, when the lantern burns cold blue (a soul lantern in the head slot). */
	public boolean soulFlame;
}
