package life.shizune.mistinn.client.terra;

import life.shizune.mistinn.terra.GateSentry;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What the gate sentry's model needs to pose it: how far knelt, what move it is in and how long it has gone on. */
public class GateSentryRenderState extends LivingEntityRenderState {
	/** 0 standing, 1 knelt and powered down. */
	public float kneel;
	/** The move it is in. */
	public GateSentry.Move move = GateSentry.Move.NONE;
	/** Ticks since the move began. */
	public float moveAge;
}
