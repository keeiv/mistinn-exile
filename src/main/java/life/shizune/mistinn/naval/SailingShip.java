package life.shizune.mistinn.naval;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A pirate ship under sail, coming out of the fog to where it heaves to. The client draws it whole
 * from {@link ShipPlan}; it has no blocks and nothing to stand on. It sails in along its own keel,
 * slowing as it comes, and when it is there the real ship is put in its place, block for block where
 * it was drawn. It is not saved: a world closed while it sails puts the ship straight where it was going.
 */
public class SailingShip extends Entity {
	private static final EntityDataAccessor<Integer> DATA_TURN = SynchedEntityData.defineId(SailingShip.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SHIP = SynchedEntityData.defineId(SailingShip.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_DURATION = SynchedEntityData.defineId(SailingShip.class, EntityDataSerializers.INT);

	private Vec3 from = Vec3.ZERO;
	private Vec3 to = Vec3.ZERO;
	private int duration = 1;
	private int age;

	public SailingShip(final EntityType<? extends SailingShip> type, final Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		builder.define(DATA_TURN, 0);
		builder.define(DATA_SHIP, -1);
		builder.define(DATA_DURATION, 1);
	}

	/** Sets it sailing: the ship it is, which way it lies, from where to where, over how many ticks. */
	public void sail(final int ship, final Rotation turn, final Vec3 from, final Vec3 to, final int duration) {
		this.entityData.set(DATA_SHIP, ship);
		this.entityData.set(DATA_TURN, turn.ordinal());
		this.from = from;
		this.to = to;
		this.duration = Math.max(1, duration);
		this.entityData.set(DATA_DURATION, this.duration);
		this.age = 0;
		this.setPos(from);
		this.setYRot(turnYaw(turn));
	}

	public int ship() {
		return this.entityData.get(DATA_SHIP);
	}

	public Rotation turn() {
		return Rotation.values()[Math.floorMod(this.entityData.get(DATA_TURN), 4)];
	}

	/** How far along its way it is, 0 to 1 (on the client, reckoned from how long it has been seen). */
	public float progress() {
		if (this.level().isClientSide()) {
			return Math.min(1.0F, this.tickCount / (float) Math.max(1, this.entityData.get(DATA_DURATION)));
		}
		return Math.min(1.0F, this.age / (float) this.duration);
	}

	private static float turnYaw(final Rotation turn) {
		return new ShipFrame(net.minecraft.core.BlockPos.ZERO, turn).yaw();
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		this.age++;
		float t = this.progress();
		// gliding in and slowing as it comes: the speed falls away to nothing at its berth
		double eased = 1.0 - (1.0 - t) * (1.0 - t);
		this.setPos(this.from.lerp(this.to, eased));
		if (this.age >= this.duration) {
			NavalService.arrived(level, this);
			this.discard();
		}
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(final double distance) {
		return distance < 320.0 * 320.0;
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
	}
}
