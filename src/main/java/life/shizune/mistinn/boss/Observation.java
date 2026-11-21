package life.shizune.mistinn.boss;

import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.medical.TreatmentService;
import life.shizune.mistinn.stamina.RollService;
import life.shizune.mistinn.stamina.StaminaService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One tick of a hunter as a boss sees them: where they are and how fast they go (blocks per tick, from
 * where they were last seen, since a player's own motion is the client's), which way they face, what they
 * are doing, how much stamina is left, whether both legs are gone, and how far their weapon reaches.
 */
public record Observation(long time, Vec3 pos, Vec3 velocity, float yaw, PlayerAction action, float stamina, boolean legsGone, double reach) {
	/** Faster than this, flat, is a sprint; slower than {@link #STILL} is standing. */
	private static final double SPRINTING = 0.2;
	private static final double STILL = 0.02;

	public static Observation of(final ServerPlayer player, final long time, final @Nullable Observation last) {
		Vec3 pos = player.position();
		Vec3 velocity = last == null || time <= last.time() ? Vec3.ZERO : pos.subtract(last.pos()).scale(1.0 / (time - last.time()));
		Body body = BodyService.get(player);
		return new Observation(time, pos, velocity, player.getYRot(), action(player, velocity), StaminaService.get(player),
			body.destroyed(BodyPart.LEFT_LEG) && body.destroyed(BodyPart.RIGHT_LEG), player.entityInteractionRange());
	}

	private static PlayerAction action(final ServerPlayer player, final Vec3 velocity) {
		if (RollService.dodging(player)) {
			return PlayerAction.ROLL;
		}
		if (TreatmentService.active(player)) {
			return PlayerAction.TREAT;
		}
		if (player.isSwinging()) {
			return PlayerAction.SWING;
		}
		double speed = velocity.horizontalDistance();
		if (speed > SPRINTING || player.isSprinting()) {
			return PlayerAction.SPRINT;
		}
		return speed > STILL ? PlayerAction.WALK : PlayerAction.IDLE;
	}
}
