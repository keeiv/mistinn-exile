package life.shizune.mistinn.naval;

import life.shizune.mistinn.Mistinn;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A round shot from a ship's gun. It flies a plain arc (no drag: it lands where it was aimed, given
 * the time it was given), trailing smoke, and bursts where it first meets the sea, the ground or a
 * body. The burst hurts and throws what is near and smashes boats, but breaks no blocks: a ship's
 * own hull and the hunters' builds stand. Pirates are not struck by their own guns' shot.
 */
public class CannonShot extends Projectile implements ItemSupplier {
	public static final ResourceKey<DamageType> CANNON = ResourceKey.create(Registries.DAMAGE_TYPE, Mistinn.id("cannon"));
	/** Speed lost to the fall each tick, blocks a tick. */
	public static final double GRAVITY = 0.05;
	/** How far the burst reaches. */
	public static final float BURST = 2.8F;
	/** A shot that has met nothing in this long goes. */
	private static final int LIFE = 200;

	private int age;

	public CannonShot(final EntityType<? extends CannonShot> type, final Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
	}

	/** Sends the shot from where it stands to land at {@code at} after {@code ticks} ticks. */
	public void aim(final Vec3 at, final int ticks) {
		Vec3 d = at.subtract(this.position());
		// each tick the fall is taken before the move: y(T) = T vy - g T (T + 1) / 2
		double vy = d.y / ticks + GRAVITY * (ticks + 1) / 2.0;
		this.setDeltaMovement(d.x / ticks, vy, d.z / ticks);
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 velocity = this.getDeltaMovement().add(0.0, -GRAVITY, 0.0);
		this.setDeltaMovement(velocity);
		Vec3 from = this.position();
		Vec3 to = from.add(velocity);
		if (this.level() instanceof ServerLevel level) {
			HitResult hit = this.firstHit(level, from, to);
			if (hit != null) {
				boolean sea = hit instanceof BlockHitResult block && !level.getFluidState(block.getBlockPos()).isEmpty();
				this.burst(level, hit.getLocation(), sea);
				return;
			}
			if (++this.age > LIFE || to.y < level.getMinY()) {
				this.discard();
				return;
			}
		} else {
			// smoke streams behind it
			for (int i = 0; i < 2; i++) {
				Vec3 at = from.add(velocity.scale(i * 0.5));
				this.level().addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0.0, 0.01, 0.0);
			}
		}
		this.setPos(to);
		this.updateRotation();
	}

	private HitResult firstHit(final ServerLevel level, final Vec3 from, final Vec3 to) {
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
		Vec3 stop = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, this, from, stop, this.getBoundingBox().expandTowards(stop.subtract(from)).inflate(1.0),
			this::canHitEntity);
		if (entity != null) {
			return entity;
		}
		return block.getType() == HitResult.Type.MISS ? null : block;
	}

	@Override
	protected boolean canHitEntity(final Entity entity) {
		return super.canHitEntity(entity) && !(entity instanceof Pirate) && !(entity instanceof CannonShot);
	}

	/** The burst: harm and a throw for all near, a boat smashed, spray or splinters by what it struck. */
	private void burst(final ServerLevel level, final Vec3 at, final boolean sea) {
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(CANNON), this, this.getOwner());
		level.explode(this, source, null, at.x, at.y, at.z, BURST, false, Level.ExplosionInteraction.NONE);
		NavalFx.send(level, sea ? NavalFx.SPLASH : NavalFx.BLAST, at, at);
		this.discard();
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final net.minecraft.world.damagesource.DamageSource source, final float damage) {
		return false;
	}

	@Override
	public ItemStack getItem() {
		return new ItemStack(life.shizune.mistinn.registry.MistinnItems.CANNONBALL);
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("age", this.age);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.age = input.getIntOr("age", 0);
	}
}
