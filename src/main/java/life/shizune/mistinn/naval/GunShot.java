package life.shizune.mistinn.naval;

import life.shizune.mistinn.Mistinn;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A musket or pistol shot: no ball flies, the shot is decided the moment it is fired, along a line a
 * little off where it was aimed. What it meets first takes it: a wall, the sea, or a body. Boats do
 * not stop it (a hunter sitting in one is hit over the gunwale). Fellow pirates are never hit.
 */
public final class GunShot {
	public static final ResourceKey<DamageType> GUNSHOT = ResourceKey.create(Registries.DAMAGE_TYPE, Mistinn.id("gunshot"));

	private GunShot() {
	}

	/** The source of a shot's harm, owed to whoever fired it. */
	public static DamageSource source(final ServerLevel level, final Entity shooter) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(GUNSHOT), shooter);
	}

	/**
	 * Fires from {@code from} at {@code at}, off by up to about {@code spread} (radians, one standard
	 * deviation) each way. Returns what the shot struck, or null when it struck nothing living.
	 */
	public static @Nullable LivingEntity fire(final ServerLevel level, final LivingEntity shooter, final Vec3 from, final Vec3 at, final float damage,
		final double spread, final double range) {
		RandomSource random = shooter.getRandom();
		Vec3 aim = at.subtract(from);
		if (aim.lengthSqr() < 1.0E-6) {
			return null;
		}
		Vec3 direction = aim.normalize().add(random.nextGaussian() * spread, random.nextGaussian() * spread, random.nextGaussian() * spread).normalize();
		Vec3 end = from.add(direction.scale(range));
		BlockHitResult wall = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, shooter));
		Vec3 stop = wall.getType() == HitResult.Type.MISS ? end : wall.getLocation();
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, shooter, from, stop, new AABB(from, stop).inflate(1.0),
			entity -> entity.isAlive() && entity.isPickable() && entity != shooter && !entity.isSpectator() && !(entity instanceof AbstractBoat)
				&& entity != shooter.getVehicle() && !(shooter instanceof Pirate && entity instanceof Pirate), 0.3F);
		Vec3 impact = hit != null ? hit.getLocation() : stop;
		level.playSound(null, from.x, from.y, from.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6F, 1.7F + random.nextFloat() * 0.2F);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0F, 1.4F);
		NavalFx.send(level, NavalFx.GUN, from, impact);
		if (hit != null && hit.getEntity() instanceof LivingEntity struck) {
			struck.hurtServer(level, source(level, shooter), damage);
			return struck;
		}
		return null;
	}
}
