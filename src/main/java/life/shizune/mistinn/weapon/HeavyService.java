package life.shizune.mistinn.weapon;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.enemy.WindupService;
import life.shizune.mistinn.medical.TreatmentService;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.skill.SlideService;
import life.shizune.mistinn.skill.Stat;
import life.shizune.mistinn.stamina.RollService;
import life.shizune.mistinn.stamina.StaminaService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 蓄力重擊: holding the use key with one of the mod's weapons draws it back; letting go strikes.
 * The longer the charge (up to the branch's {@link WeaponStyle#chargeTicks}), the harder the
 * blow, and strength adds to it. A heavy blow hits everything in its shape, not only what the
 * crosshair is on; it lands at the strike's contact, a few ticks after the key is let go.
 */
public final class HeavyService {
	/** Let go before this share of the charge and nothing happens. */
	public static final float MIN_CHARGE = 0.3F;
	/** Durability a heavy blow costs. */
	public static final int DURABILITY = 2;
	/** Each point of strength adds this much to a heavy blow, on top of what it adds to every blow. */
	public static final double STRENGTH_PER_POINT = 0.04;
	/** How far the scythe's heavy blow drags what it reaps, and the Snuffer's hook its farthest catch, in blocks. */
	public static final double REAP_PULL = 1.2;
	public static final double HOOK_PULL = 3.0;
	/** Starting speed per block of drag (ground friction eats the rest). */
	private static final double PULL_SPEED = 0.42;
	/** 霧燈杖: the lantern bursts this far ahead, hitting all within the radius. */
	public static final double BURST_AHEAD = 2.0;
	public static final double BURST_RADIUS = 3.5;
	/** 潮鐘巨鎚: the toll hits all around within this radius. */
	public static final double TOLL_RADIUS = 4.5;
	/** 大劍: the chop shakes the ground within this radius of where it lands. */
	public static final double IMPACT_RADIUS = 1.6;
	/** Ticks between the dagger's two crossing cuts. */
	public static final int FLURRY_GAP = 4;

	/** Why a hunter may not charge right now. */
	public enum Refusal {
		NONE, NOT_A_WEAPON, NO_STAMINA, BUSY, NO_ARMS, UNFIT
	}

	/** The ground a heavy blow covers. */
	public enum Kind {
		/** A wedge in front, {@code arc} degrees wide. */
		ARC,
		/** A straight line along the look, {@code width} wide. */
		LINE,
		/** All around the hunter. */
		RING,
		/** A sphere ahead of the hunter. */
		BURST
	}

	/**
	 * One branch's heavy blow. {@code reach} is added to the hunter's reach (or, for a ring or a
	 * burst, is the radius itself). The strike lasts {@code strikeTicks} and lands at {@code contact}
	 * of it; the client's heavy clip carries the same contact.
	 */
	public record Shape(Kind kind, float arc, double reach, double width, int hits, double share, double pull,
		double knockback, double impact, int strikeTicks, float contact) {
		/** Ticks from letting go to the first hit landing. */
		public int landTicks() {
			return Math.max(1, Math.round(this.contact * this.strikeTicks));
		}
	}

	private static final Shape FLURRY = new Shape(Kind.ARC, 90.0F, 0.5, 0.0, 2, 0.6, 0.0, 0.0, 0.0, 14, 0.22F);
	private static final Shape SLASH = new Shape(Kind.ARC, 110.0F, 0.5, 0.0, 1, 1.0, 0.0, 0.0, 0.0, 14, 0.25F);
	private static final Shape THRUST = new Shape(Kind.LINE, 0.0F, 2.0, 0.9, 1, 1.0, 0.0, 0.0, 0.0, 14, 0.25F);
	private static final Shape REAP = new Shape(Kind.ARC, 200.0F, 0.8, 0.0, 1, 1.0, REAP_PULL, 0.0, 0.0, 16, 0.3F);
	private static final Shape SMASH = new Shape(Kind.ARC, 180.0F, 0.5, 0.0, 1, 1.0, 0.0, 1.2, 0.0, 18, 0.3F);
	private static final Shape CLEAVE = new Shape(Kind.ARC, 50.0F, 1.0, 0.0, 1, 1.0, 0.0, 0.0, IMPACT_RADIUS, 18, 1.0F / 3.0F);
	private static final Shape BURST = new Shape(Kind.BURST, 0.0F, BURST_RADIUS, 0.0, 1, 1.0, 0.0, 0.6, 0.0, 14, 0.25F);
	private static final Shape TOLL = new Shape(Kind.RING, 360.0F, TOLL_RADIUS, 0.0, 1, 1.0, 0.0, 0.6, 0.0, 18, 0.3F);

	/** A blow on its way: let go in {@code world}, waiting for its contact. */
	private record Strike(UUID player, ResourceKey<Level> world, WeaponStyle style, @Nullable SpecialWeapon special, Shape shape, float charge,
		double damage, long landAt, int hitsLeft) {
		Strike next(final long at) {
			return new Strike(this.player, this.world, this.style, this.special, this.shape, this.charge, this.damage, at, this.hitsLeft - 1);
		}
	}

	/** The branch of the blow landing right now, so a kill is the weapon's that struck it; else null. */
	private static @Nullable WeaponStyle landing;

	public static @Nullable WeaponStyle landing() {
		return landing;
	}

	/** 鐘舌: how far the ring of its full heavy blow carries, breaking off every windup it reaches. */
	public static final double CLAPPER_RING = 6.0;
	/** 鏽輪緣: how many ticks its full heavy blow rolls the hunter on, how fast, and how far either side of the path it runs over. */
	public static final int RIM_TICKS = 7;
	public static final double RIM_SPEED = 0.9;
	public static final double RIM_PATH = 1.3;
	/** The share of the blow each one run over takes, and how long a hunter who rolls into a wall is dazed. */
	public static final double RIM_SHARE = 0.6;
	public static final int RIM_DAZE = 40;
	private static final List<Strike> PENDING = new ArrayList<>();
	private static final List<Roll> ROLLS = new ArrayList<>();

	/** The client's word on whether the hunter is mid-roll or mid-slide, for its own prediction. */
	public static BooleanSupplier clientBusy = () -> false;

	private HeavyService() {
	}

	// ── The numbers ───────────────────────────────────────────────

	public static Shape shape(final WeaponStyle style, final @Nullable SpecialWeapon special) {
		if (special == SpecialWeapon.LANTERN_STAFF) {
			return BURST;
		}
		if (special == SpecialWeapon.TIDE_MAUL) {
			return TOLL;
		}
		return switch (style) {
			case FLURRY -> FLURRY;
			case SLASH -> SLASH;
			case THRUST -> THRUST;
			case REAP -> REAP;
			case SMASH -> SMASH;
			case CLEAVE -> CLEAVE;
		};
	}

	/** Ticks to a full charge for this hunter: speed makes it quicker, as it does every swing. */
	public static int chargeTicks(final Player player, final WeaponStyle style) {
		return Math.max(4, Math.round(style.chargeTicks / (float) SkillService.swingScale(SkillService.stat(player, Stat.SPEED))));
	}

	/** Share of a full charge after holding for {@code ticks}. */
	public static float charge(final Player player, final WeaponStyle style, final int ticks) {
		return Mth.clamp(ticks / (float) chargeTicks(player, style), 0.0F, 1.0F);
	}

	public static double heavyScale(final int strength) {
		return 1.0 + STRENGTH_PER_POINT * strength;
	}

	/** A heavy blow's damage from the hunter's attack (weapon and strength included). */
	public static double damage(final double attack, final float charge, final int strength) {
		return attack * (1.0 + charge) * heavyScale(strength);
	}

	/** 霧燈杖: the burst burns with the mind, not the arm. */
	public static double burstDamage(final int intellect, final float charge) {
		return (6.0 + intellect) * (1.0 + charge);
	}

	/** Stamina a heavy blow costs: the swing's, and as much again at a full charge. */
	public static float cost(final ItemStack stack, final float charge) {
		return StaminaService.swingCost(stack) * (1.0F + charge);
	}

	public static Refusal refusal(final Player player, final ItemStack stack) {
		if (WeaponStyle.of(stack) == null) {
			return Refusal.NOT_A_WEAPON;
		}
		SpecialWeapon special = SpecialWeapon.of(stack);
		if (special != null && !special.fits(player)) {
			return Refusal.UNFIT;
		}
		if (!StaminaService.applies(player)) {
			return Refusal.NONE;
		}
		if (StaminaService.get(player) <= 0.0F) {
			return Refusal.NO_STAMINA;
		}
		boolean busy = player.level().isClientSide() ? clientBusy.getAsBoolean() : RollService.dodging(player) || SlideService.sliding(player);
		if (busy) {
			return Refusal.BUSY;
		}
		Body body = BodyService.get(player);
		if (body.destroyed(BodyPart.LEFT_ARM) && body.destroyed(BodyPart.RIGHT_ARM)) {
			return Refusal.NO_ARMS;
		}
		return Refusal.NONE;
	}

	// ── The blow ──────────────────────────────────────────────────

	/**
	 * A roll or a slide ends a charge in progress, on the server and on the hunter's own client alike,
	 * without letting it go: no heavy blow is ever let go mid-roll, so neither side has to judge that.
	 */
	public static void breakCharge(final Player player) {
		if (player.isUsingItem() && player.getUseItem().getItem() instanceof WeaponItem) {
			player.stopUsingItem();
		}
	}

	/**
	 * Whether a charged blow may be let go now. The server and the hunter's own client both ask this
	 * (the body and the skills are synced to their owner), so the hunter never sees a strike the
	 * server refuses: the weapon must answer the hunter, and at least one arm must still work.
	 */
	public static boolean canLetGo(final Player player, final ItemStack stack) {
		SpecialWeapon special = SpecialWeapon.of(stack);
		if (special != null && !special.fits(player)) {
			return false;
		}
		Body body = BodyService.get(player);
		return !(body.destroyed(BodyPart.LEFT_ARM) && body.destroyed(BodyPart.RIGHT_ARM));
	}

	/** The hunter let go after charging for {@code ticks}; returns whether a heavy blow is on its way. */
	public static boolean release(final ServerPlayer player, final ItemStack stack, final int ticks) {
		WeaponStyle style = WeaponStyle.of(stack);
		if (style == null || player.isDeadOrDying()) {
			return false;
		}
		if (!canLetGo(player, stack)) {
			return false;
		}
		float charge = charge(player, style, ticks);
		if (charge < MIN_CHARGE) {
			return false;
		}
		SpecialWeapon special = SpecialWeapon.of(stack);
		Shape shape = shape(style, special);
		double damage = special == SpecialWeapon.LANTERN_STAFF
			? burstDamage(SkillService.stat(player, Stat.INTELLECT), charge)
			: damage(player.getAttributeValue(Attributes.ATTACK_DAMAGE), charge, SkillService.stat(player, Stat.STRENGTH));
		if (StaminaService.applies(player)) {
			StaminaService.spend(player, cost(stack, charge));
		}
		TreatmentService.interrupt(player);
		player.resetAttackStrengthTicker();
		life.shizune.mistinn.enemy.NoiseService.emit(player, life.shizune.mistinn.enemy.NoiseService.Noise.HEAVY);
		ServerLevel level = player.level();
		PENDING.add(new Strike(player.getUUID(), level.dimension(), style, special, shape, charge, damage, level.getGameTime() + shape.landTicks(), shape.hits()));
		life.shizune.mistinn.voice.Voice.effort(player);
		Weapons.Profile profile = Weapons.profile(stack);
		if (profile != null) {
			level.playSound(null, player.getX(), player.getEyeY() - 0.3, player.getZ(), profile.swing(), SoundSource.PLAYERS,
				1.0F, profile.pitch() * (0.78F + 0.08F * player.getRandom().nextFloat()));
		}
		stack.hurtAndBreak(DURABILITY, player, EquipmentSlot.MAINHAND);
		HeavyAnimPayload anim = new HeavyAnimPayload(player.getId(), style.ordinal(), charge);
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			if (watcher != player) {
				ServerPlayNetworking.send(watcher, anim);
			}
		}
		return true;
	}

	/** Lands the blows whose contact has come. */
	public static void tick(final MinecraftServer server) {
		rolls(server);
		if (PENDING.isEmpty()) {
			return;
		}
		List<Strike> follow = new ArrayList<>();
		for (Iterator<Strike> it = PENDING.iterator(); it.hasNext();) {
			Strike strike = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(strike.player());
			// a blow does not follow the hunter into another world
			if (player == null || player.isDeadOrDying() || player.level().dimension() != strike.world()) {
				it.remove();
				continue;
			}
			if (player.level().getGameTime() < strike.landAt()) {
				continue;
			}
			it.remove();
			landing = strike.style();
			try {
				land(player, strike);
			} finally {
				landing = null;
			}
			if (strike.hitsLeft() > 1) {
				follow.add(strike.next(player.level().getGameTime() + FLURRY_GAP));
			}
		}
		PENDING.addAll(follow);
	}

	private static void land(final ServerPlayer player, final Strike strike) {
		ServerLevel level = player.level();
		Shape shape = strike.shape();
		List<LivingEntity> hit = targets(player, shape);
		double amount = strike.damage() * shape.share();
		boolean full = strike.charge() >= 0.999F;
		LivingEntity farthest = null;
		int struck = 0;
		for (LivingEntity target : hit) {
			if (!blow(player, target, amount, shape, full, strike.special())) {
				continue;
			}
			struck++;
			if (farthest == null || target.distanceToSqr(player) > farthest.distanceToSqr(player)) {
				farthest = target;
			}
		}
		if (struck >= 3) {
			life.shizune.mistinn.achievement.AchievementService.grant(player, life.shizune.mistinn.achievement.Achievement.HEAVY_THREE);
		}
		if (struck > 0 && strike.special() != null) {
			life.shizune.mistinn.achievement.AchievementService.grant(player, life.shizune.mistinn.achievement.Achievement.SPECIAL_WEAPON);
		}
		if (shape.impact() > 0.0) {
			Vec3 look = flatLook(player);
			Vec3 at = player.position().add(look.scale(player.entityInteractionRange() + shape.reach()));
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(shape.impact(), 1.5, shape.impact()),
				e -> attackable(player, e) && !hit.contains(e) && flat(e.position().subtract(at)).length() - e.getBbWidth() / 2.0 <= shape.impact())) {
				blow(player, target, amount * 0.5, shape, false, null);
			}
			level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.1, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (strike.special() == SpecialWeapon.SNUFFER_HOOK && farthest != null) {
			drag(player, farthest, HOOK_PULL);
			if (farthest instanceof Mob mob && WindupService.interrupt(mob)) {
				life.shizune.mistinn.achievement.AchievementService.grant(player, life.shizune.mistinn.achievement.Achievement.BREAK_WINDUP);
			}
			level.playSound(null, farthest.getX(), farthest.getY(), farthest.getZ(), MistinnSounds.HOOK_PULL, SoundSource.PLAYERS, 1.0F, 1.0F);
		}
		if (strike.special() == SpecialWeapon.LANTERN_STAFF) {
			Vec3 at = burstCentre(player);
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 40, 1.2, 0.6, 1.2, 0.05);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
		}
		if (full && strike.special() == SpecialWeapon.BELL_CLAPPER) {
			ringBack(player, level);
		}
		if (full && strike.special() == SpecialWeapon.RUST_RIM) {
			Set<Integer> struckAlready = new HashSet<>();
			for (LivingEntity target : hit) {
				struckAlready.add(target.getId());
			}
			ROLLS.add(new Roll(player.getUUID(), flatLook(player), amount * RIM_SHARE, shape, struckAlready, player.position()));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.RUST_WHEEL_SPIN, SoundSource.PLAYERS, 0.9F, 1.4F);
		}
		if (strike.special() == SpecialWeapon.TIDE_MAUL) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2.0F, 0.5F);
			level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/**
	 * A hunter rolling on behind the rim: which way, how hard it runs over, what it already struck, how long yet, and
	 * where the roll has got to. The roll keeps its own place: the server hears where the hunter is a tick or two late.
	 */
	private static final class Roll {
		final UUID player;
		final Vec3 dir;
		final double damage;
		final Shape shape;
		final Set<Integer> struck;
		int left = RIM_TICKS;
		Vec3 at;

		Roll(final UUID player, final Vec3 dir, final double damage, final Shape shape, final Set<Integer> struck, final Vec3 at) {
			this.player = player;
			this.dir = dir;
			this.damage = damage;
			this.shape = shape;
			this.struck = struck;
			this.at = at;
		}
	}

	/**
	 * 鏽輪緣: each hunter still rolling is carried on along the way they faced; everything on the path is run over, once,
	 * and thrown aside. A wall ahead (one a step will not climb) stops the roll and dazes the hunter.
	 */
	private static void rolls(final MinecraftServer server) {
		for (Iterator<Roll> it = ROLLS.iterator(); it.hasNext();) {
			Roll roll = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(roll.player);
			if (player == null || player.isDeadOrDying()) {
				it.remove();
				continue;
			}
			if (roll.left-- <= 0) {
				// the roll is spent: it stops where it got to, not coasting on
				player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
				player.syncVelocity = true;
				it.remove();
				continue;
			}
			ServerLevel level = player.level();
			Vec3 step = roll.dir.scale(RIM_SPEED);
			AABB here = player.getBoundingBox().move(roll.at.subtract(player.position()));
			AABB ahead = here.move(step).deflate(0.05, 0.0, 0.05).move(0.0, 0.01, 0.0);
			if (!level.noCollision(player, ahead) && !level.noCollision(player, ahead.move(0.0, player.maxUpStep(), 0.0))) {
				crash(player, level);
				it.remove();
				continue;
			}
			player.setDeltaMovement(step.x, player.getDeltaMovement().y, step.z);
			player.syncVelocity = true;
			Vec3 from = flat(roll.at);
			Vec3 to = from.add(step);
			roll.at = roll.at.add(step);
			Vec3 side = new Vec3(-roll.dir.z, 0.0, roll.dir.x);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, here.expandTowards(step).inflate(RIM_PATH + 0.5, 1.0, RIM_PATH + 0.5),
				e -> attackable(player, e) && !roll.struck.contains(e.getId()))) {
				if (alongPath(from, to, flat(target.position())) - target.getBbWidth() / 2.0 > RIM_PATH) {
					continue;
				}
				roll.struck.add(target.getId());
				if (blow(player, target, roll.damage, roll.shape, false, SpecialWeapon.RUST_RIM)) {
					// run over and thrown to the side it was on, as the wheel does
					double away = Math.signum(side.dot(flat(target.position()).subtract(from)));
					target.setDeltaMovement(side.scale((away == 0.0 ? 1.0 : away) * 0.6).add(0.0, 0.3, 0.0));
					target.syncVelocity = true;
				}
			}
		}
	}

	/** The distance from {@code at} to the segment {@code from}-{@code to}. */
	private static double alongPath(final Vec3 from, final Vec3 to, final Vec3 at) {
		Vec3 line = to.subtract(from);
		double t = line.lengthSqr() < 1.0E-6 ? 0.0 : Mth.clamp(at.subtract(from).dot(line) / line.lengthSqr(), 0.0, 1.0);
		return at.distanceTo(from.add(line.scale(t)));
	}

	/** The roll runs into a wall: the hunter stops dead, rattled, as the wheel is when it jams. */
	private static void crash(final ServerPlayer player, final ServerLevel level) {
		player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
		player.syncVelocity = true;
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, RIM_DAZE, 1));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.RUST_WHEEL_CRASH, SoundSource.PLAYERS, 0.7F, 1.4F);
		Vec3 at = player.position().add(flatLook(player).scale(0.6));
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 1.0, at.z, 12, 0.3, 0.4, 0.3, 0.2);
	}

	/** 鐘舌: the clapper rings out all round the hunter, and every creature it reaches breaks off the blow it was winding up. */
	private static void ringBack(final ServerPlayer player, final ServerLevel level) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.SUNKEN_BELL_RING, SoundSource.PLAYERS, 1.6F, 1.3F);
		for (int i = 0; i < 48; i++) {
			double angle = i * Math.PI * 2.0 / 48.0;
			for (double r = 2.0; r <= CLAPPER_RING; r += 2.0) {
				level.sendParticles(ParticleTypes.CLOUD, player.getX() + Math.cos(angle) * r, player.getY() + 0.15, player.getZ() + Math.sin(angle) * r, 1, 0.05, 0.02, 0.05, 0.0);
			}
		}
		int broken = 0;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(CLAPPER_RING, 2.0, CLAPPER_RING),
			m -> m.isAlive() && flat(m.position().subtract(player.position())).length() <= CLAPPER_RING)) {
			if (WindupService.interrupt(mob)) {
				broken++;
			}
		}
		if (broken > 0) {
			life.shizune.mistinn.achievement.AchievementService.grant(player, life.shizune.mistinn.achievement.Achievement.BREAK_WINDUP);
		}
	}

	/** Strikes one target; returns whether it was hurt. */
	private static boolean blow(final ServerPlayer player, final LivingEntity target, final double amount, final Shape shape,
		final boolean full, final @Nullable SpecialWeapon special) {
		ServerLevel level = player.level();
		// a heavy blow is not swallowed by the grace a light hit just gave
		target.damageCooldownTime = 0;
		DamageSource source = player.damageSources().playerAttack(player);
		ItemStack weapon = player.getMainHandItem();
		// the weapon's enchantments count as they do for a light hit: sharpness adds, fire aspect sets alight
		float dealt = EnchantmentHelper.modifyDamage(level, weapon, target, source, (float) amount);
		if (!target.hurtServer(level, source, dealt)) {
			return false;
		}
		EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, weapon);
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		if (shape.knockback() > 0.0 && shape.kind() != Kind.RING && shape.kind() != Kind.BURST) {
			shove(target, Mth.sin(yaw), -Mth.cos(yaw), shape.knockback());
		}
		if (shape.kind() == Kind.RING || shape.kind() == Kind.BURST) {
			Vec3 from = shape.kind() == Kind.RING ? player.position() : burstCentre(player);
			Vec3 away = flat(target.position().subtract(from));
			away = away.lengthSqr() < 1.0E-4 ? flatLook(player) : away.normalize();
			target.setDeltaMovement(away.scale(shape.knockback()).add(0.0, shape.kind() == Kind.RING ? 0.45 : 0.2, 0.0));
			target.syncVelocity = true;
		}
		if (shape.pull() > 0.0) {
			drag(player, target, shape.pull());
		}
		if (special == SpecialWeapon.LANTERN_STAFF) {
			target.igniteForSeconds(2.0F);
		}
		if (full && target instanceof Mob mob) {
			if (mob instanceof life.shizune.mistinn.keeper.Keeper keeper) {
				keeper.takeHeavyBlow(level);
			}
			if (WindupService.interrupt(mob)) {
				life.shizune.mistinn.achievement.AchievementService.grant(player, life.shizune.mistinn.achievement.Achievement.BREAK_WINDUP);
			}
			// a full charge rocks it back on its heels, away from the blow
			Vec3 from = shape.kind() == Kind.RING ? flat(target.position().subtract(player.position())) : flatLook(player);
			life.shizune.mistinn.enemy.StaggerService.stagger(mob, life.shizune.mistinn.enemy.StaggerService.TICKS,
				from.lengthSqr() < 1.0E-4 ? flatLook(player) : from.normalize());
		}
		Weapons.Profile profile = Weapons.profile(player.getMainHandItem());
		if (profile != null) {
			level.playSound(null, target.getX(), target.getY(0.5), target.getZ(), profile.hit(), SoundSource.PLAYERS, 1.0F, profile.pitch() * 0.85F);
		}
		return true;
	}

	/** Knocks a target along (x, z) as vanilla knockback does, its resistance taken off. */
	private static void shove(final LivingEntity target, final double x, final double z, final double strength) {
		double push = strength * (1.0 - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		if (push <= 0.0) {
			return;
		}
		Vec3 v = target.getDeltaMovement();
		double length = Math.sqrt(x * x + z * z);
		double lift = target.onGround() ? Math.min(0.4, v.y / 2.0 + push) : v.y;
		// as vanilla: (x, z) points from the target back toward the attacker, and the target is pushed the other way
		target.setDeltaMovement(v.x / 2.0 - x / length * push, lift, v.z / 2.0 - z / length * push);
		target.syncVelocity = true;
	}

	/** Sends a target sliding toward the hunter, about {@code blocks} far. */
	private static void drag(final ServerPlayer player, final LivingEntity target, final double blocks) {
		Vec3 toward = flat(player.position().subtract(target.position()));
		double distance = toward.length();
		if (distance < 0.8) {
			return;
		}
		double travel = Math.min(blocks, distance - 0.8);
		target.setDeltaMovement(toward.scale(1.0 / distance).scale(travel * PULL_SPEED).add(0.0, 0.12, 0.0));
		target.syncVelocity = true;
	}

	// ── What a blow covers ────────────────────────────────────────

	/** Every creature this heavy blow would hit, from where the hunter stands and looks now. */
	public static List<LivingEntity> targets(final ServerPlayer player, final Shape shape) {
		ServerLevel level = player.level();
		double reach = player.entityInteractionRange();
		double radius = switch (shape.kind()) {
			case ARC -> reach + shape.reach();
			case LINE -> reach + shape.reach();
			case RING -> shape.reach();
			case BURST -> shape.reach() + BURST_AHEAD;
		};
		List<LivingEntity> found = new ArrayList<>();
		Set<LivingEntity> seen = new HashSet<>();
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius + 1.0), e -> attackable(player, e))) {
			if (seen.add(target) && covers(player, shape, target, reach)) {
				found.add(target);
			}
		}
		return found;
	}

	private static boolean covers(final ServerPlayer player, final Shape shape, final LivingEntity target, final double reach) {
		AABB box = target.getBoundingBox();
		if (shape.kind() == Kind.LINE) {
			Vec3 eye = player.getEyePosition();
			Vec3 look = player.getLookAngle();
			AABB fat = box.inflate(shape.width() / 2.0);
			double length = reach + shape.reach();
			for (double along = 0.0; along <= length; along += 0.25) {
				if (fat.contains(eye.add(look.scale(along)))) {
					return true;
				}
			}
			return false;
		}
		if (shape.kind() == Kind.BURST) {
			Vec3 centre = burstCentre(player);
			double dx = Math.max(Math.max(box.minX - centre.x, 0.0), centre.x - box.maxX);
			double dy = Math.max(Math.max(box.minY - centre.y, 0.0), centre.y - box.maxY);
			double dz = Math.max(Math.max(box.minZ - centre.z, 0.0), centre.z - box.maxZ);
			return dx * dx + dy * dy + dz * dz <= shape.reach() * shape.reach();
		}
		// arcs and rings: within reach on the ground, roughly level with the hunter
		if (box.minY > player.getY() + 2.5 || box.maxY < player.getY() - 1.0) {
			return false;
		}
		Vec3 to = flat(target.position().subtract(player.position()));
		double radius = shape.kind() == Kind.RING ? shape.reach() : reach + shape.reach();
		double distance = to.length() - target.getBbWidth() / 2.0;
		if (distance > radius) {
			return false;
		}
		if (shape.kind() == Kind.RING || to.length() < 0.8) {
			return true;
		}
		double cos = to.normalize().dot(flatLook(player));
		return cos >= Math.cos(Math.toRadians(shape.arc() / 2.0));
	}

	private static Vec3 burstCentre(final Player player) {
		return player.getEyePosition().add(player.getLookAngle().scale(BURST_AHEAD));
	}

	/** Who a heavy blow may hit: the living, not the hunter, not their pets, not other hunters unless the server allows it. */
	public static boolean attackable(final ServerPlayer player, final LivingEntity target) {
		if (target == player || !target.isAlive() || target.isSpectator() || target instanceof ArmorStand || target.isInvulnerable()) {
			return false;
		}
		if (target instanceof Player other && !player.canHarmPlayer(other)) {
			return false;
		}
		if (target instanceof TamableAnimal pet && pet.isOwnedBy(player)) {
			return false;
		}
		return player.hasLineOfSight(target);
	}

	private static Vec3 flatLook(final Player player) {
		return Vec3.directionFromRotation(0.0F, player.getYRot());
	}

	private static Vec3 flat(final Vec3 v) {
		return new Vec3(v.x, 0.0, v.z);
	}

	// ── Sync ──────────────────────────────────────────────────────

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HeavyService::tick);
		// a blow does not outlive its hunter's session or the world it was let go in
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUUID()));
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> forgetAll());
		ServerTickEvents.END_SERVER_TICK.register(SpecialWeapon::tick);
		PayloadTypeRegistry.clientboundPlay().register(HeavyAnimPayload.TYPE, HeavyAnimPayload.CODEC);
	}

	private static void forget(final UUID player) {
		PENDING.removeIf(strike -> strike.player().equals(player));
		ROLLS.removeIf(roll -> roll.player.equals(player));
	}

	/** Drops every blow still on its way (when the server stops; also a test hook). */
	public static void forgetAll() {
		PENDING.clear();
		ROLLS.clear();
	}

	/** Tells the watchers of a hunter that a heavy blow was let go, so they draw it. */
	public record HeavyAnimPayload(int entity, int style, float charge) implements CustomPacketPayload {
		public static final Type<HeavyAnimPayload> TYPE = new Type<>(Mistinn.id("heavy_anim"));
		public static final StreamCodec<ByteBuf, HeavyAnimPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HeavyAnimPayload::entity,
			ByteBufCodecs.VAR_INT, HeavyAnimPayload::style,
			ByteBufCodecs.FLOAT, HeavyAnimPayload::charge,
			HeavyAnimPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
