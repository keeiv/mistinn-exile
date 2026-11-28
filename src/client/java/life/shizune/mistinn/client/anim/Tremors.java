package life.shizune.mistinn.client.anim;

import life.shizune.mistinn.terra.CrystalElder;
import life.shizune.mistinn.terra.GateSentry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * The ground shakes under great blows nearby: a gate sentry's fists coming down or its charge meeting a wall, a crown
 * elder driven into stone. Each is seen by its synced move beginning, so nothing more is sent; the nearer, the harder
 * the view shakes ({@link CameraSwing#tremor}), and not at all past {@link #FELT}.
 */
public final class Tremors {
	/** How far off a blow is felt at all, in blocks. */
	public static final double FELT = 16.0;

	private Tremors() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Tremors::tick);
	}

	private static void tick(final Minecraft mc) {
		if (mc.player == null || mc.level == null || mc.isPaused()) {
			return;
		}
		for (Entity entity : mc.level.getEntities(mc.player, new AABB(mc.player.blockPosition()).inflate(FELT), e -> e instanceof GateSentry || e instanceof CrystalElder)) {
			float strength = 0.0F;
			if (entity instanceof GateSentry sentry && sentry.moveAge(0.0F) == 0.0F) {
				strength = sentry.move() == GateSentry.Move.SLAM ? 1.0F : sentry.move() == GateSentry.Move.STUCK ? 0.6F : 0.0F;
			} else if (entity instanceof CrystalElder elder && elder.moveAge(0.0F) == 0.0F && elder.move() == CrystalElder.Move.STUNNED) {
				strength = 1.0F;
			}
			if (strength > 0.0F) {
				double near = 1.0 - Math.max(0.0, entity.distanceTo(mc.player) - 3.0) / (FELT - 3.0);
				if (near > 0.0) {
					CameraSwing.tremor((float) (strength * near));
				}
			}
		}
	}
}
