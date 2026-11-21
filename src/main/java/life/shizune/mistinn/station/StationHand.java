package life.shizune.mistinn.station;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A member of Rust Anchor's staff: wears a player skin, keeps to their post, turns to look at a
 * hunter who comes near and talks when spoken to. Nothing a hunter or a creature does hurts them,
 * so nothing is earned by striking them; only commands that bypass invulnerability remove them.
 */
public class StationHand extends PathfinderMob {
	private static final EntityDataAccessor<Integer> DATA_ROLE = SynchedEntityData.defineId(StationHand.class, EntityDataSerializers.INT);

	public StationHand(final EntityType<? extends StationHand> type, final Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, 20.0)
			.add(Attributes.MOVEMENT_SPEED, 0.25);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ROLE, StaffRole.DOCK_WORKER_1.ordinal());
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(5, new RouteGoal(this));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	public StaffRole role() {
		return StaffRole.byIndex(this.entityData.get(DATA_ROLE));
	}

	public void setRole(final StaffRole role) {
		this.entityData.set(DATA_ROLE, role.ordinal());
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("role", this.role().id);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setRole(StaffRole.byId(input.getStringOr("role", StaffRole.DOCK_WORKER_1.id)));
	}

	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, final float damage) {
		// blades, heavy blows and keepers pass through; /kill and the void still apply
		return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurtServer(level, source, damage);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(final double distance) {
		return false;
	}

	@Override
	protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		if (player instanceof ServerPlayer hunter && hand == InteractionHand.MAIN_HAND) {
			StationTalk.open(hunter, this);
		}
		return InteractionResult.SUCCESS;
	}
}
