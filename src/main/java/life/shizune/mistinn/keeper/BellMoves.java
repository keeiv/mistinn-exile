package life.shizune.mistinn.keeper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.boss.BossMove;
import life.shizune.mistinn.boss.BossMove.Kind;
import life.shizune.mistinn.boss.BossMove.Weight;
import life.shizune.mistinn.boss.HitShape;
import life.shizune.mistinn.boss.MoveRunner;
import life.shizune.mistinn.boss.MoveSet;
import life.shizune.mistinn.boss.PartRule;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 沉鐘's moves. It is slow and heavy and does not leap: it presses with the clapper, rams with the bell, and from
 * a distance tolls, and the toll rolls out along the floor as a ring a hunter must jump or roll through. The clip
 * of a move is {@code anim/bell/<id>}, over the move's whole length.
 */
public final class BellMoves {
	private BellMoves() {
	}

	private static final PartRule HEAD = PartRule.always(BodyService.Hit.on(BodyPart.HEAD, false));
	private static final PartRule TORSO = PartRule.always(BodyService.Hit.on(BodyPart.TORSO, false));
	private static final PartRule LEGS = PartRule.always(BodyService.Hit.LEGS);
	/** Torso, or the arm on the side the clapper comes from. */
	private static final PartRule SWEEP_PART = (boss, player, random) -> {
		float roll = random.nextFloat();
		if (roll < 0.5F) {
			return BodyService.Hit.on(BodyPart.TORSO, false);
		}
		return BodyService.Hit.on(roll < 0.75F ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM, false);
	};

	// ── The ring ──────────────────────────────────────────────────

	/** How thick the ring of a toll is, and how fast it rolls out, blocks a tick, from where it starts. */
	public static final double RING_WIDTH = 0.9;
	public static final double RING_SPEED = 0.75;
	public static final double RING_START = 2.0;

	/** Whether the ring, out at {@code radius} from {@code centre}, takes a hunter standing at {@code at}: on the floor, and where it passes now. */
	public static boolean ringHits(final Vec3 centre, final double radius, final Vec3 at, final double halfWidth, final boolean onGround) {
		if (!onGround || Math.abs(at.y - centre.y) > 1.5) {
			return false;
		}
		double distance = Math.hypot(at.x - centre.x, at.z - centre.z);
		return Math.abs(distance - radius) <= RING_WIDTH / 2.0 + halfWidth;
	}

	/**
	 * A ring that began rolling out at {@code from}: this tick, out to {@code reach}, it takes whoever it passes over on the
	 * floor, once each. Those it took are kept in the move's note {@code key}.
	 */
	private static void roll(final MoveRunner.Context context, final int from, final double reach, final String key, final float damage) {
		int tick = context.tick();
		double radius = RING_START + (tick - from) * RING_SPEED;
		if (tick < from || radius > reach + RING_SPEED) {
			return;
		}
		Set<UUID> struck = context.note(key);
		if (struck == null) {
			struck = new HashSet<>();
			context.note(key, struck);
		}
		Mob boss = context.boss;
		int points = (int) (radius * 7);
		for (int i = 0; i < points; i++) {
			double angle = i * Math.PI * 2.0 / points;
			context.level.sendParticles(ParticleTypes.CLOUD, boss.getX() + Math.cos(angle) * radius, boss.getY() + 0.15, boss.getZ() + Math.sin(angle) * radius,
				1, 0.05, 0.02, 0.05, 0.0);
		}
		for (Player player : context.level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(reach + 2.0, 2.0, reach + 2.0))) {
			if (player.isAlive() && !player.isSpectator() && !struck.contains(player.getUUID())
				&& ringHits(boss.position(), radius, player.position(), player.getBbWidth() / 2.0, player.onGround())) {
				struck.add(player.getUUID());
				MoveRunner.hit(context.level, boss, player, HEAD, damage);
			}
		}
	}

	/** The clapper struck against the bell: the ring is heard, and the mist thickens a layer. */
	private static void strike(final MoveRunner.Context context, final boolean thicken) {
		boolean cracked = context.boss instanceof SunkenBell bell && bell.cracked();
		context.level.playSound(null, context.boss.blockPosition(), cracked ? MistinnSounds.SUNKEN_BELL_RING_CRACKED : MistinnSounds.SUNKEN_BELL_RING,
			SoundSource.HOSTILE, 3.0F, 1.0F);
		context.level.sendParticles(ParticleTypes.SONIC_BOOM, context.boss.getX(), context.boss.getEyeY() + 0.4, context.boss.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		if (thicken && context.boss instanceof SunkenBell bell) {
			bell.thicken();
		}
	}

	// ── The tolls ─────────────────────────────────────────────────

	/** 鐘響: the clapper brought up to the bell's lip and struck; the ring rolls out along the floor to eleven blocks. */
	public static final BossMove TOLL = BossMove.builder("toll", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SUNKEN_BELL_TOLL, 20).timing(14, 16)
		.strike(BossMove.Strike.SCRIPTED, new HitShape.Circle(11.0, true), HEAD, 6.0F)
		.range(0.0, 10.0).cooldown(40).families("toll", SunkenBell.WHOLE)
		.seen((level, boss, target, tick) -> level.sendParticles(ParticleTypes.BUBBLE_POP, boss.getX(), boss.getEyeY() + 0.5, boss.getZ(), 2, 0.5, 0.3, 0.5, 0.0))
		.script(context -> {
			if (context.tick() == 20) {
				strike(context, true);
			}
			roll(context, 20, 11.0, "ring", context.move().strikes().getFirst().damage());
		})
		.build();

	/** 雙響: the cracked bell struck twice; the second ring close behind the first. */
	public static final BossMove DOUBLE_TOLL = BossMove.builder("double_toll", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SUNKEN_BELL_DOUBLE_TOLL, 20).timing(24, 16)
		.strike(BossMove.Strike.SCRIPTED, new HitShape.Circle(11.0, true), HEAD, 5.0F)
		.strike(BossMove.Strike.SCRIPTED, new HitShape.Circle(11.0, true), HEAD, 5.0F)
		.range(0.0, 11.0).cooldown(40).families("toll", SunkenBell.CRACKED)
		.seen((level, boss, target, tick) -> level.sendParticles(ParticleTypes.BUBBLE_POP, boss.getX(), boss.getEyeY() + 0.5, boss.getZ(), 3, 0.5, 0.3, 0.5, 0.0))
		.script(context -> {
			if (context.tick() == 20) {
				strike(context, true);
			} else if (context.tick() == 30) {
				strike(context, false);
			}
			roll(context, 20, 11.0, "first", context.move().strikes().get(0).damage());
			roll(context, 30, 11.0, "second", context.move().strikes().get(1).damage());
		})
		.build();

	/** Ticks of the deep toll's three strikes, and how far each ring rolls. */
	private static final int[] DEEP = {24, 40, 56};
	private static final double[] DEEP_REACH = {8.0, 11.0, 14.0};

	/**
	 * 深響: at the fourth layer, the clapper raised high over the bell and brought down three times, each ring further than
	 * the last; then the mist falls back to a layer. Not chosen: the room's rule calls it.
	 */
	public static final BossMove DEEP_TOLL = BossMove.builder("deep_toll", Kind.REACTION)
		.tell(MistinnSounds.SUNKEN_BELL_DEEP_TOLL, 24).timing(36, 20)
		.strike(BossMove.Strike.SCRIPTED, new HitShape.Circle(14.0, true), HEAD, 5.0F)
		.families("toll")
		.seen((level, boss, target, tick) -> level.sendParticles(ParticleTypes.BUBBLE_POP, boss.getX(), boss.getEyeY() + 0.6, boss.getZ(), 4, 0.6, 0.4, 0.6, 0.0))
		.script(context -> {
			for (int i = 0; i < DEEP.length; i++) {
				if (context.tick() == DEEP[i]) {
					strike(context, false);
				}
				roll(context, DEEP[i], DEEP_REACH[i], "deep" + i, context.move().strikes().getFirst().damage());
			}
			if (context.tick() == context.move().length() - 1 && context.boss instanceof SunkenBell bell) {
				bell.setLayers(1);
			}
		})
		.build();

	// ── The clapper ───────────────────────────────────────────────

	/** 鐘舌橫掃: the body wrenched round and the clapper swung flat through the front. */
	public static final BossMove CLAPPER_SWEEP = BossMove.builder("clapper_sweep", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SUNKEN_BELL_CLAPPER_SWEEP, 16).timing(2, 20)
		.strike(16, new HitShape.Sector(5.5, 100.0, 3.0), SWEEP_PART, 7.0F)
		.range(0.0, 5.0).families("sweep")
		.script(context -> {
			if (context.tick() == 16) {
				context.level.playSound(null, context.boss.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.6F, 0.5F);
			}
		})
		.build();

	/** 鐘舌砸: the clapper hauled up overhead and brought down on the head in front of it. */
	public static final BossMove CLAPPER_SMASH = BossMove.builder("clapper_smash", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SUNKEN_BELL_CLAPPER_SMASH, 18).timing(2, 22)
		.strike(18, new HitShape.Capsule(4.5, 0.8, 3.0), HEAD, 9.0F)
		.range(0.0, 4.5).cooldown(10).families("smash")
		.script(context -> {
			if (context.tick() == 18) {
				Vec3 at = context.boss.position().add(HitShape.facing(context.boss).scale(3.0));
				context.level.playSound(null, context.boss.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.9F, 0.4F);
				context.level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 20, 0.6, 0.05, 0.6, 0.02);
			}
		})
		.build();

	/** 頂鐘衝撞: head down, the feet scraping twice, then seven blocks at a run with the bell first. */
	public static final BossMove BELL_RAM = BossMove.builder("bell_ram", Kind.MOVEMENT).weight(Weight.HEAVY)
		.tell(MistinnSounds.SUNKEN_BELL_RAM, 14).timing(10, 16)
		.dash(14, 24, 7.0)
		.along(14, 24, new HitShape.Capsule(1.6, 0.9, 2.0), TORSO, 7.0F)
		.range(4.0, 10.0).cooldown(30).families("ram", "lunge")
		.seen((level, boss, target, tick) -> {
			if (tick % 6 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 0.1, boss.getZ(), 4, 0.3, 0.02, 0.3, 0.01);
			}
		})
		.script(context -> {
			if (context.tick() >= 14 && context.tick() <= 24) {
				context.level.sendParticles(ParticleTypes.SPLASH, context.boss.getX(), context.boss.getY() + 0.2, context.boss.getZ(), 6, 0.4, 0.1, 0.4, 0.05);
			}
		})
		.build();

	/** 鍊甩: the clapper whirled at its side on its chain and flung out low along the floor: the legs, far out. */
	public static final BossMove CHAIN_LASH = BossMove.builder("chain_lash", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SUNKEN_BELL_CHAIN_LASH, 16).timing(4, 18)
		.strike(17, new HitShape.Capsule(9.0, 0.9, 1.0), LEGS, 5.0F)
		.range(3.0, 9.0).cooldown(30).families("lash")
		.script(context -> {
			if (context.tick() == 17) {
				context.level.playSound(null, context.boss.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.4F, 0.5F);
				Vec3 facing = HitShape.facing(context.boss);
				for (double r = 1.5; r <= 9.0; r += 0.75) {
					context.level.sendParticles(ParticleTypes.CRIT, context.boss.getX() + facing.x * r, context.boss.getY() + 0.2, context.boss.getZ() + facing.z * r, 1, 0.1, 0.0, 0.1, 0.0);
				}
			}
		})
		.build();

	/** How far the undertow reaches, and how hard it hauls. */
	private static final double UNDERTOW_REACH = 10.0;

	/** 回潮: the water about its feet drawn in toward it, and everyone standing in it; then the clapper swung round its body. */
	public static final BossMove UNDERTOW = BossMove.builder("undertow", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SUNKEN_BELL_UNDERTOW, 18).timing(12, 18)
		.strike(28, new HitShape.Circle(3.6, false), TORSO, 6.0F)
		.range(3.0, 10.0).cooldown(60).families("pull")
		.seen((level, boss, target, tick) -> {
			for (int i = 0; i < 6; i++) {
				double angle = (tick * 0.4 + i) * Math.PI / 3.0;
				double r = UNDERTOW_REACH * (1.0 - tick / 20.0);
				level.sendParticles(ParticleTypes.SPLASH, boss.getX() + Math.cos(angle) * r, boss.getY() + 0.1, boss.getZ() + Math.sin(angle) * r, 2, 0.1, 0.0, 0.1, 0.0);
			}
		})
		.script(context -> {
			if (context.tick() == 18) {
				for (Player player : context.level.getEntitiesOfClass(Player.class, context.boss.getBoundingBox().inflate(UNDERTOW_REACH, 2.0, UNDERTOW_REACH))) {
					if (!(player instanceof ServerPlayer hunter) || !hunter.isAlive() || hunter.isSpectator() || hunter.isCreative()) {
						continue;
					}
					Vec3 pull = context.boss.position().subtract(hunter.position());
					Vec3 flat = new Vec3(pull.x, 0.0, pull.z);
					if (flat.length() > 1.5 && flat.length() <= UNDERTOW_REACH) {
						Vec3 drag = flat.normalize().scale(Math.min(1.4, 0.22 * flat.length()));
						hunter.setDeltaMovement(drag.x, 0.15, drag.z);
						hunter.syncVelocity = true;
					}
				}
				context.level.playSound(null, context.boss.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 1.5F, 0.6F);
			} else if (context.tick() == 28) {
				context.level.playSound(null, context.boss.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.5F);
			}
		})
		.build();

	// ── Movement ──────────────────────────────────────────────────

	private static BossMove.Motion keys(final BossMove.Motion.Key... keys) {
		return new BossMove.Motion(List.of(keys));
	}

	private static BossMove.Motion.Key key(final int tick, final double forward, final double side) {
		return new BossMove.Motion.Key(tick, forward, side, 0.0);
	}

	/** A heavy wading step to its left. */
	public static final BossMove WADE_LEFT = BossMove.builder("wade_left", Kind.MOVEMENT).weight(Weight.LIGHT)
		.tell(MistinnSounds.SUNKEN_BELL_WADE, 8).timing(8, 6)
		.motion(keys(key(8, 0, 0), key(16, 0, -2.5))).range(0.0, 7.0).families("step").build();

	/** The same, to its right. */
	public static final BossMove WADE_RIGHT = BossMove.builder("wade_right", Kind.MOVEMENT).weight(Weight.LIGHT)
		.tell(MistinnSounds.SUNKEN_BELL_WADE, 8).timing(8, 6)
		.motion(keys(key(8, 0, 0), key(16, 0, 2.5))).range(0.0, 7.0).families("step").build();

	/** Back three blocks, the clapper dragging: room for the lash, the ram or a toll. */
	public static final BossMove BACK_WADE = BossMove.builder("back_wade", Kind.MOVEMENT).weight(Weight.LIGHT)
		.tell(MistinnSounds.SUNKEN_BELL_WADE, 8).timing(8, 8)
		.motion(keys(key(8, 0, 0), key(16, -3.0, 0))).range(0.0, 5.0).cooldown(20).families("step").build();

	// ── A feint and a signal ──────────────────────────────────────

	/** The toll's very lift of the clapper, and no toll: it swings it round, or rams. */
	public static final BossMove FAKE_TOLL = BossMove.builder("fake_toll", Kind.FEINT)
		.tell(MistinnSounds.SUNKEN_BELL_TOLL, 20).timing(0, 8).imitates("toll")
		.range(0.0, 10.0).cooldown(30).families("toll")
		.seen(TOLL.tellEffect()).build();

	/** 鐘鳴: the bell hums by itself, low and long: a toll is coming. Honest, so it can be learned. */
	public static final BossMove BELL_HUM = BossMove.builder("bell_hum", Kind.SIGNAL)
		.tell(MistinnSounds.SUNKEN_BELL_HUM, 0).timing(0, 30).signals("toll").cooldown(80).build();

	/** Rung back by the blows, or by its own mist: reeling. Suffered, never chosen. */
	public static final BossMove STAGGER = BossMove.builder("stagger", Kind.REACTION)
		.unannounced(0).timing(0, Keeper.STAGGER_TICKS)
		.build();

	public static final MoveSet SET = new MoveSet(List.of(
		TOLL, DOUBLE_TOLL, DEEP_TOLL,
		CLAPPER_SWEEP, CLAPPER_SMASH, BELL_RAM, CHAIN_LASH, UNDERTOW,
		WADE_LEFT, WADE_RIGHT, BACK_WADE,
		FAKE_TOLL, BELL_HUM,
		STAGGER), List.of(
		new MoveSet.Link("clapper_sweep", "clapper_smash", 18, 28),
		new MoveSet.Link("clapper_smash", "toll", 22, 30), new MoveSet.Link("clapper_smash", "double_toll", 22, 30),
		new MoveSet.Link("fake_toll", "clapper_sweep", 10, 18), new MoveSet.Link("fake_toll", "bell_ram", 10, 18),
		new MoveSet.Link("wade_left", "clapper_sweep", 14, 20), new MoveSet.Link("wade_left", "clapper_smash", 14, 20),
		new MoveSet.Link("wade_right", "clapper_sweep", 14, 20), new MoveSet.Link("wade_right", "clapper_smash", 14, 20),
		new MoveSet.Link("back_wade", "chain_lash", 14, 22), new MoveSet.Link("back_wade", "bell_ram", 14, 22),
		new MoveSet.Link("back_wade", "toll", 14, 22), new MoveSet.Link("back_wade", "double_toll", 14, 22),
		new MoveSet.Link("chain_lash", "bell_ram", 20, 30),
		new MoveSet.Link("bell_ram", "clapper_sweep", 24, 34),
		new MoveSet.Link("undertow", "toll", 34, 44), new MoveSet.Link("undertow", "double_toll", 34, 44)));
}
