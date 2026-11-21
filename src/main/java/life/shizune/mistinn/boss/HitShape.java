package life.shizune.mistinn.boss;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Where a blow reaches, from the boss's feet along its facing at the moment it lands. The same geometry
 * answers for a real hunter and for one the planner only imagines standing somewhere.
 */
public sealed interface HitShape permits HitShape.Sector, HitShape.Capsule, HitShape.Circle, HitShape.Grab {
	/** Whether a blow from {@code from}, facing {@code facing} (flat, unit length), reaches feet at {@code at}. */
	boolean reaches(Vec3 from, Vec3 facing, Vec3 at, double halfWidth, boolean onGround);

	default boolean hits(final Mob boss, final Player player) {
		return this.reaches(boss.position(), facing(boss), player.position(), player.getBbWidth() / 2.0, player.onGround());
	}

	/** The flat direction a boss faces. */
	static Vec3 facing(final Mob boss) {
		float yaw = boss.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
	}

	/** A swing: within the radius, no more than the half angle either side of the facing, and not far above or below. */
	record Sector(double radius, double halfAngle, double height) implements HitShape {
		@Override
		public boolean reaches(final Vec3 from, final Vec3 facing, final Vec3 at, final double halfWidth, final boolean onGround) {
			Vec3 to = at.subtract(from);
			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			if (flat.length() > this.radius + halfWidth || Math.abs(to.y) > this.height) {
				return false;
			}
			if (flat.lengthSqr() < 0.25) {
				return true;
			}
			double cos = flat.normalize().dot(facing);
			return Math.toDegrees(Math.acos(Mth.clamp(cos, -1.0, 1.0))) <= this.halfAngle;
		}
	}

	/** A thrust or a slide: a straight band from the feet forward, as wide as the radius either side. */
	record Capsule(double reach, double radius, double height) implements HitShape {
		@Override
		public boolean reaches(final Vec3 from, final Vec3 facing, final Vec3 at, final double halfWidth, final boolean onGround) {
			Vec3 to = at.subtract(from);
			if (Math.abs(to.y) > this.height) {
				return false;
			}
			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			double along = Mth.clamp(flat.dot(facing), 0.0, this.reach);
			return flat.subtract(facing.scale(along)).length() <= this.radius + halfWidth;
		}
	}

	/** A landing or a burst: everything within the radius, only those on the ground if it runs along the floor. */
	record Circle(double radius, boolean groundOnly) implements HitShape {
		@Override
		public boolean reaches(final Vec3 from, final Vec3 facing, final Vec3 at, final double halfWidth, final boolean onGround) {
			Vec3 to = at.subtract(from);
			return new Vec3(to.x, 0.0, to.z).length() <= this.radius && Math.abs(to.y) <= 1.5 && (!this.groundOnly || onGround);
		}
	}

	/** A grab: a hand's reach straight ahead. */
	record Grab(double reach) implements HitShape {
		@Override
		public boolean reaches(final Vec3 from, final Vec3 facing, final Vec3 at, final double halfWidth, final boolean onGround) {
			return new Capsule(this.reach, 0.6, 2.0).reaches(from, facing, at, halfWidth, onGround);
		}
	}
}
