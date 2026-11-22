package life.shizune.mistinn.keeper;

import java.util.List;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.salt.SaltService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 沉鐘, the second Lighthouse Keeper: a drowned bell-ringer whose head and shoulders are sealed in a cracked bronze
 * bell, the bell's tongue for a weapon. Every toll thickens the mist of its room by a layer, four at the most, and at
 * the fourth it gives the deep toll; a layer thins again after a quiet while. Its bell is its weak spot as well:
 * a full heavy blow rings it back and thins the mist, and three in a row throw it off balance. Below half health the
 * bell cracks: its tolls come double and the mist is slower to thin.
 */
public class SunkenBell extends Keeper {
	public static final float MAX_HEALTH = 360.0F;
	/** The phases of its fight, as its moves know them. */
	public static final String WHOLE = "whole";
	public static final String CRACKED = "cracked";
	/** How far its room reaches from where it stands. */
	private static final int ROOM_RADIUS = 10;
	/** The most mist it can toll up. */
	public static final int MAX_LAYERS = 4;
	/** How far a hunter in its room sees at each layer of its mist, in blocks (none at 0). */
	public static final float[] SIGHT = {Float.POSITIVE_INFINITY, 16.0F, 11.0F, 7.0F, 4.5F};
	/** How long a layer lasts without a toll: whole, and cracked. */
	public static final int FADE_WHOLE = 300;
	public static final int FADE_CRACKED = 400;
	/** How near a hunter must be to its room to stand in its mist. */
	public static final double MIST_REACH = ROOM_RADIUS + 6.0;

	/** The layers of its mist, for every hunter's screen. */
	private static final EntityDataAccessor<Byte> DATA_LAYERS = SynchedEntityData.defineId(SunkenBell.class, EntityDataSerializers.BYTE);

	private boolean cracked;
	private int fade;

	public SunkenBell(final EntityType<? extends SunkenBell> type, final Level level) {
		super(type, level, BellMoves.SET, BossEvent.BossBarColor.GREEN);
		this.xpReward = 140;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, MAX_HEALTH)
			.add(Attributes.ARMOR, 10.0)
			.add(Attributes.ATTACK_DAMAGE, 7.0)
			.add(Attributes.MOVEMENT_SPEED, 0.22)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LAYERS, (byte) 0);
	}

	@Override
	protected String key() {
		return "sunken_bell";
	}

	@Override
	protected int roomRadius() {
		return ROOM_RADIUS;
	}

	@Override
	protected String phase() {
		return this.cracked ? CRACKED : WHOLE;
	}

	public boolean cracked() {
		return this.cracked;
	}

	/** A cracked bell stays cracked through a save, and does not crack again (with its message) when it is loaded. */
	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("BellCracked", this.cracked);
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.cracked = input.getBooleanOr("BellCracked", false);
	}

	// ── The mist ──────────────────────────────────────────────────

	/** How many layers of mist it has tolled up, 0 to {@link #MAX_LAYERS}. */
	public int layers() {
		return this.entityData.get(DATA_LAYERS);
	}

	/** Sets the layers of its mist, and the time the top one lasts. Public for the game tests. */
	public void setLayers(final int layers) {
		this.entityData.set(DATA_LAYERS, (byte) Mth.clamp(layers, 0, MAX_LAYERS));
		this.fade = this.cracked ? FADE_CRACKED : FADE_WHOLE;
	}

	/** A toll: the mist thickens by a layer. */
	public void thicken() {
		this.setLayers(this.layers() + 1);
	}

	/** Its bell rung back: the mist thins by {@code by} layers. */
	private void thin(final ServerLevel level, final int by) {
		if (this.layers() > 0) {
			this.setLayers(this.layers() - by);
		}
		level.playSound(null, this.blockPosition(), MistinnSounds.SUNKEN_BELL_HURT, SoundSource.HOSTILE, 1.6F, 0.7F);
		level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getEyeY(), this.getZ(), 20, 1.2, 0.6, 1.2, 0.08);
	}

	/** How far a hunter standing at this distance from it sees in its mist (infinite: its mist does not reach them). */
	public float sight(final double distance) {
		return distance <= MIST_REACH ? SIGHT[this.layers()] : Float.POSITIVE_INFINITY;
	}

	/** The belfry: the deck it first stood on, when the great bell hangs over it; none elsewhere. */
	@Override
	protected MistWall.@org.jspecify.annotations.Nullable Room sealedRoom(final ServerLevel level) {
		if (this.home == null) {
			return null;
		}
		BlockPos centre = BlockPos.containing(this.home);
		int lip = life.shizune.mistinn.world.BellTower.BELL_LIP - life.shizune.mistinn.world.BellTower.DECK - 1;
		if (!level.getBlockState(centre.offset(2, lip, 0)).is(net.minecraft.world.level.block.Blocks.CUT_COPPER.waxed().exposed())) {
			return null;
		}
		return new MistWall.Room(centre, centre.getY() - 1, life.shizune.mistinn.world.BellTower.DECK_RADIUS, life.shizune.mistinn.world.BellTower.DECK_RADIUS - 0.8);
	}

	@Override
	protected void roomRules(final ServerLevel level) {
		if (!this.cracked && this.getHealth() <= this.getMaxHealth() / 2.0F) {
			this.crack(level);
		}
		if (this.layers() > 0 && --this.fade <= 0) {
			this.setLayers(this.layers() - 1);
		}
		// at the fourth layer, as soon as it is free and on its feet, the deep toll
		if (this.layers() >= MAX_LAYERS && this.staggered <= 0 && !this.busy() && !this.isDeadOrDying()) {
			this.perform("deep_toll");
		}
	}

	private void crack(final ServerLevel level) {
		this.cracked = true;
		level.playSound(null, this.blockPosition(), MistinnSounds.SUNKEN_BELL_RING_CRACKED, SoundSource.HOSTILE, 3.0F, 0.8F);
		level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getEyeY() + 0.3, this.getZ(), 30, 0.6, 0.6, 0.6, 0.2);
		for (ServerPlayer player : this.watchers()) {
			player.sendSystemMessage(Component.translatable("message.mistinn.sunken_bell.cracked"), true);
		}
	}

	/** A full heavy blow rings its own bell back at it: the mist thins a layer, and the third in a row throws it off balance. */
	@Override
	public void takeHeavyBlow(final ServerLevel level) {
		boolean counts = !this.staggered() && !this.isDeadOrDying();
		super.takeHeavyBlow(level);
		if (counts) {
			this.thin(level, 1);
		}
	}

	/** Off balance, its mist thins by two more. */
	@Override
	protected void staggeredBy(final ServerLevel level) {
		this.thin(level, 2);
	}

	// ── Voice ────────────────────────────────────────────────────

	/** Breath drawn inside the bell, and water in it. */
	@Override
	protected SoundEvent getAmbientSound() {
		return MistinnSounds.SUNKEN_BELL_AMBIENT;
	}

	/** The bell struck: bronze, and a groan under it. */
	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return MistinnSounds.SUNKEN_BELL_HURT;
	}

	/** One last sour toll, and the bell crashing down. */
	@Override
	protected SoundEvent getDeathSound() {
		return MistinnSounds.SUNKEN_BELL_DEATH;
	}

	/** A heavy, wet footfall. */
	@Override
	protected void playStepSound(final BlockPos pos, final BlockState state) {
		this.playSound(MistinnSounds.SUNKEN_BELL_STEP, 0.8F, 0.9F + this.random.nextFloat() * 0.2F);
	}

	// ── Spoils ────────────────────────────────────────────────────

	@Override
	protected void dropCustomDeathLoot(final ServerLevel level, final DamageSource source, final boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.spawnAtLocation(level, new ItemStack(MistinnItems.RUST_GOD_SALT));
		// every hunter near enough to see it fall remembers it
		for (ServerPlayer near : level.getPlayers(p -> p.isAlive() && p.distanceTo(this) <= BAR_RANGE)) {
			life.shizune.mistinn.achievement.AchievementService.grant(near, life.shizune.mistinn.achievement.AchievementService.SUNKEN_BELL);
		}
		if (this.random.nextFloat() < 0.3F) {
			int tier = 3 + this.random.nextInt(2);
			List<net.minecraft.world.item.Item> pool = List.of(
				MistinnItems.HELMETS.get(tier), MistinnItems.ARMORS.get(tier), MistinnItems.MASKS.get(tier),
				MistinnItems.RIGS.get(tier), MistinnItems.LEGGINGS.get(tier), MistinnItems.BOOTS.get(tier));
			this.spawnAtLocation(level, new ItemStack(pool.get(this.random.nextInt(pool.size()))));
		}
		if (source.getEntity() instanceof ServerPlayer player) {
			SaltService.add(player, 3500);
		}
		this.bar.removeAllPlayers();
	}
}
