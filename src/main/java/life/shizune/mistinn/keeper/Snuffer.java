package life.shizune.mistinn.keeper;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.salt.SaltService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 熄燈人, the first Lighthouse Keeper: tall and thin, an unlit lantern for a head, a long hook in
 * hand. Each move strikes a different part of the body, so the hunter's gear matters: the sweep
 * cuts across the torso and arms, the slam's shockwave takes the legs, and the hook drags the
 * hunter in for a blow to the face, where only a mask helps.
 * Below half health the lamp room goes dark. Lighting all four lamp stands throws it off balance
 * for a few seconds, taking half again as much damage, until it snuffs them out one by one.
 */
public class Snuffer extends Keeper {
	public static final float MAX_HEALTH = 300.0F;
	/** Lamp stands this close to the keeper belong to its room. */
	private static final int ROOM_RADIUS = 12;

	private boolean dark;
	private int snuffTimer;

	public Snuffer(final EntityType<? extends Snuffer> type, final Level level) {
		super(type, level, SnufferMoves.SET, BossEvent.BossBarColor.YELLOW);
		this.xpReward = 120;
		this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LANTERN));
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(MistinnItems.MIST_SPEAR));
		this.setDropChance(EquipmentSlot.HEAD, 0.0F);
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, MAX_HEALTH)
			.add(Attributes.ARMOR, 8.0)
			.add(Attributes.ATTACK_DAMAGE, 6.0)
			.add(Attributes.MOVEMENT_SPEED, 0.27)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected String key() {
		return "snuffer";
	}

	@Override
	protected int roomRadius() {
		return ROOM_RADIUS;
	}

	public boolean dark() {
		return this.dark;
	}

	// ── Every tick ────────────────────────────────────────────────

	/** The lamp room: the middle of its four lamp stands, the floor under them; none without the four. */
	@Override
	protected MistWall.@org.jspecify.annotations.Nullable Room sealedRoom(final ServerLevel level) {
		List<BlockPos> lamps = this.lamps(level);
		if (lamps.size() < 4) {
			return null;
		}
		double x = 0.0;
		double z = 0.0;
		for (BlockPos lamp : lamps) {
			x += lamp.getX();
			z += lamp.getZ();
		}
		BlockPos centre = BlockPos.containing(x / lamps.size(), lamps.getFirst().getY(), z / lamps.size());
		return new MistWall.Room(centre, centre.getY() - 1, 7.4, 6.6);
	}

	/** Below half health, the dark. */
	@Override
	protected void roomRules(final ServerLevel level) {
		if (!this.dark && this.getHealth() <= this.getMaxHealth() / 2.0F) {
			this.goDark(level);
		}
		if (this.dark) {
			this.darkRoom(level);
		}
	}

	/** The phase of the fight, as its moves know it. */
	@Override
	protected String phase() {
		return this.dark ? "dark" : "light";
	}

	// ── The dark half ─────────────────────────────────────────────

	private void goDark(final ServerLevel level) {
		this.dark = true;
		this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.SOUL_LANTERN));
		for (BlockPos lamp : this.lamps(level)) {
			level.setBlockAndUpdate(lamp, level.getBlockState(lamp).setValue(LampStandBlock.LIT, false));
		}
		level.playSound(null, this.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0F, 0.5F);
		for (ServerPlayer player : this.watchers()) {
			player.sendSystemMessage(Component.translatable("message.mistinn.snuffer.dark"), true);
		}
		// 霧中分身: two shades come up out of the dark beside it
		life.shizune.mistinn.shore.MistShade.summonClones(level, this);
	}

	private void darkRoom(final ServerLevel level) {
		for (ServerPlayer player : this.watchers()) {
			if (player.distanceTo(this) < ROOM_RADIUS + 4 && (this.tickCount % 40 == 0 || !player.hasEffect(MobEffects.DARKNESS))) {
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
			}
		}
		List<BlockPos> lamps = this.lamps(level);
		long lit = lamps.stream().filter(pos -> level.getBlockState(pos).getValue(LampStandBlock.LIT)).count();
		// in the dark, the lamps it has to be beaten by
		this.showOnBar(lamps.isEmpty() ? null : "entity.mistinn.snuffer.lamps", (int) lit, lamps.size());
		if (this.staggered <= 0 && lamps.size() >= 4 && lit == lamps.size()) {
			this.stagger(level, "message.mistinn.snuffer.stagger");
		}
		// after a stagger it snuffs the lamps again, one at a time: as soon as it is free between moves, it turns to one
		if (this.staggered <= 0 && lit > 0 && --this.snuffTimer <= 0 && !this.busy()) {
			this.snuffTimer = 60;
			this.perform("snuff");
		}
	}

	/** Puts out the first lit lamp stand of its room, with a puff of smoke. */
	public void snuffOne(final ServerLevel level) {
		for (BlockPos pos : this.lamps(level)) {
			BlockState state = level.getBlockState(pos);
			if (state.getValue(LampStandBlock.LIT)) {
				level.setBlockAndUpdate(pos, state.setValue(LampStandBlock.LIT, false));
				level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10, 0.1, 0.1, 0.1, 0.02);
				return;
			}
		}
	}

	/** After a stagger it leaves the lamps burning a while before it turns to snuff them. */
	@Override
	protected void staggeredBy(final ServerLevel level) {
		this.snuffTimer = STAGGER_TICKS + 40;
	}

	/** The lamp stands of this keeper's room: around the room's centre, wherever in it the keeper has got to. */
	public List<BlockPos> lamps(final ServerLevel level) {
		List<BlockPos> found = new ArrayList<>();
		BlockPos centre = this.home == null ? this.blockPosition() : BlockPos.containing(this.home);
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-ROOM_RADIUS, -3, -ROOM_RADIUS), centre.offset(ROOM_RADIUS, 3, ROOM_RADIUS))) {
			if (level.getBlockState(pos).is(MistinnBlocks.LAMP_STAND)) {
				found.add(pos.immutable());
			}
		}
		return found;
	}

	// ── Voice ────────────────────────────────────────────────────

	/** Breath drawn through the empty lantern, its frame creaking and the hook's chain stirring. */
	@Override
	protected SoundEvent getAmbientSound() {
		return MistinnSounds.SNUFFER_AMBIENT;
	}

	/** The lantern head struck: tin and cracked glass, and a hiss. */
	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return MistinnSounds.SNUFFER_HURT;
	}

	/** A long exhale, then the lantern crashing down. */
	@Override
	protected SoundEvent getDeathSound() {
		return MistinnSounds.SNUFFER_DEATH;
	}

	/** A heavy, slow footfall with the hook's chain swinging. */
	@Override
	protected void playStepSound(final BlockPos pos, final BlockState state) {
		this.playSound(MistinnSounds.SNUFFER_STEP, 0.7F, 0.9F + this.random.nextFloat() * 0.2F);
	}

	// ── Health bar and spoils ─────────────────────────────────────

	@Override
	protected void dropCustomDeathLoot(final ServerLevel level, final DamageSource source, final boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.spawnAtLocation(level, new ItemStack(MistinnItems.RUST_GOD_SALT));
		// its hook, for a hunter strong enough to swing it
		this.spawnAtLocation(level, new ItemStack(MistinnItems.SNUFFER_HOOK));
		// every hunter near enough to see it fall remembers it
		for (ServerPlayer near : level.getPlayers(p -> p.isAlive() && p.distanceTo(this) <= BAR_RANGE)) {
			life.shizune.mistinn.achievement.AchievementService.grant(near, life.shizune.mistinn.achievement.AchievementService.SNUFFER);
		}
		if (this.random.nextFloat() < 0.3F) {
			this.spawnAtLocation(level, new ItemStack(this.gearDrop()));
		}
		if (source.getEntity() instanceof ServerPlayer player) {
			SaltService.add(player, 3000);
		}
		this.bar.removeAllPlayers();
	}

	/** How long the fallen keeper lies before it is gone, in a story world: its last words play over it. */
	public static final int STORY_DEATH_TICKS = 220;

	@Override
	protected void tickDeath() {
		if (!(this.level() instanceof ServerLevel level) || !life.shizune.mistinn.story.StoryService.active(level.getServer())) {
			super.tickDeath();
			return;
		}
		this.deathTime++;
		if (this.deathTime >= STORY_DEATH_TICKS && !this.isRemoved()) {
			level.broadcastEntityEvent(this, (byte) 60);
			this.remove(RemovalReason.KILLED);
		}
	}

	/** One piece of tier III or IV gear, of any line. */
	private net.minecraft.world.item.Item gearDrop() {
		int tier = 3 + this.random.nextInt(2);
		List<net.minecraft.world.item.Item> pool = MistinnItems.gearOfTier(tier);
		return pool.get(this.random.nextInt(pool.size()));
	}
}
