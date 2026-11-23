package life.shizune.mistinn.client.body;

import java.util.Map;
import java.util.WeakHashMap;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.LostLimbs;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import org.jspecify.annotations.Nullable;

/** A player's lost limbs on the way to the renderer: the model leaves them off, and what they held. */
public final class LostLimbsView {
	/** The lost limbs of the player being drawn, as {@link LostLimbs#LOST} has them; absent for none. */
	public static final RenderStateDataKey<Integer> KEY = RenderStateDataKey.create(() -> "mistinn:lost_limbs");

	/** Whether each armour part was shown as the game made it, before any lost limb hid it. */
	private static final Map<ModelPart, Boolean> BAKED = new WeakHashMap<>();

	private LostLimbsView() {
	}

	public static boolean lost(final @Nullable Integer mask, final BodyPart part) {
		return mask != null && LostLimbs.lost(mask, part);
	}

	/** Shows an armour part as the game made it, unless its limb is lost. */
	public static void show(final ModelPart model, final @Nullable Integer mask, final BodyPart part) {
		boolean baked = BAKED.computeIfAbsent(model, m -> m.visible);
		model.visible = baked && !lost(mask, part);
	}
}
