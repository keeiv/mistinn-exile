package life.shizune.mistinn.keeper;

import java.util.List;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 熄燈人's moves. Each opens with its own sound and a body that can be read: the lantern flares and
 * the keeper leans back for the sweep, salt rises where the slam will land, the hook is drawn back on
 * its rattling chain. The clip of a move is {@code anim/keeper/<id>}, over the move's whole length.
 */
public final class SnufferMoves {
	private SnufferMoves() {
	}

	/** Torso, or the arm on the side the pole comes from. */
	private static final PartRule SWEEP_PART = (boss, player, random) -> {
		float roll = random.nextFloat();
		if (roll < 0.5F) {
			return BodyService.Hit.on(BodyPart.TORSO, false);
		}
		return BodyService.Hit.on(roll < 0.75F ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM, false);
	};

	/** A wide horizontal swing in front: the torso, or an arm. */
	public static final BossMove SWEEP = BossMove.builder("sweep", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SNUFFER_SWEEP, 16).timing(1, 23)
		.strike(16, new HitShape.Sector(5.0, 101.5, 3.0), SWEEP_PART, 7.0F)
		.range(0.0, 4.5).families("sweep")
		.seen((level, boss, target, tick) -> level.sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getEyeY() + 0.4, boss.getZ(), 3, 0.15, 0.15, 0.15, 0.01))
		.script(context -> {
			if (context.tick() == 16) {
				context.boss.swing(InteractionHand.MAIN_HAND, context.boss.getMainHandItem().getAttackAnimation(), true);
				context.level.playSound(null, context.boss.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.6F);
			}
		})
		.build();

	/** Leaps at the target and lands: a ring of salt that takes the legs of anyone standing in it. */
	public static final BossMove SLAM = BossMove.builder("slam", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SNUFFER_SLAM, 20).timing(10, 26)
		.strike(BossMove.Strike.SCRIPTED, new HitShape.Circle(5.5, true), PartRule.always(BodyService.Hit.LEGS), 6.0F)
		.range(0.0, 12.0).families("leap")
		.seen((level, boss, target, tick) -> {
			if (target != null) {
				level.sendParticles(ParticleTypes.WHITE_ASH, target.getX(), target.getY() + 0.1, target.getZ(), 12, 1.6, 0.05, 1.6, 0.0);
			}
		})
		.script(SnufferMoves::slam)
		.build();

	/** Throws the hook: the target is dragged in, then struck in the face. */
	public static final BossMove HOOK = BossMove.builder("hook", Kind.ATTACK).weight(Weight.HEAVY)
		.tell(MistinnSounds.SNUFFER_HOOK, 14).timing(10, 16)
		.strike(BossMove.Strike.SCRIPTED, new HitShape.Circle(4.0, false), PartRule.always(BodyService.Hit.on(BodyPart.HEAD, true)), 8.0F)
		.range(0.0, 12.5).families("hook")
		.seen((level, boss, target, tick) -> level.sendParticles(ParticleTypes.CRIT, boss.getX(), boss.getEyeY(), boss.getZ(), 2, 0.3, 0.3, 0.3, 0.05))
		.script(SnufferMoves::hook)
		.build();

	/** Thrown off balance by the lit lamps: hunched and swaying. Suffered, never chosen. */
	public static final BossMove STAGGER = BossMove.builder("stagger", Kind.REACTION)
		.unannounced(0).timing(0, Snuffer.STAGGER_TICKS)
		.build();

	public static final MoveSet SET = new MoveSet(List.of(SWEEP, SLAM, HOOK, STAGGER), List.of());

	/** The leap at tick 20; in the air through 30, held there until it comes down; the landing strikes. */
	private static void slam(final MoveRunner.Context context) {
		int tick = context.tick();
		if (tick == 20) {
			Vec3 to = context.target == null ? Vec3.ZERO : context.target.position().subtract(context.boss.position());
			Vec3 flat = new Vec3(to.x, 0, to.z);
			double reach = Math.min(flat.length(), 8.0) / 8.0;
			Vec3 push = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(0.9 * reach);
			context.boss.setDeltaMovement(push.x, 0.75, push.z);
			context.boss.needsSync = true;
			context.note("air", 0);
			return;
		}
		Integer air = context.note("air");
		if (air == null || tick < 20 || tick > 30) {
			return;
		}
		air++;
		context.note("air", air);
		boolean landed = air > 2 && context.boss.onGround();
		if (landed || air > 60) {
			land(context);
			context.note("air", null);
			context.skipTo(30);
		} else if (tick == 30) {
			context.hold();
		}
	}

	private static void land(final MoveRunner.Context context) {
		var level = context.level;
		var boss = context.boss;
		level.playSound(null, boss.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 1.4F);
		level.sendParticles(ParticleTypes.WHITE_ASH, boss.getX(), boss.getY() + 0.1, boss.getZ(), 80, 2.5, 0.1, 2.5, 0.05);
		level.sendParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 0.2, boss.getZ(), 1, 0, 0, 0, 0);
		BossMove.Strike strike = context.move().strikes().getFirst();
		for (Player player : level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(6.0, 1.5, 6.0))) {
			if (strike.shape().hits(boss, player)) {
				context.strikeOne(player, strike);
				player.setDeltaMovement(player.getDeltaMovement().add(0, 0.35, 0));
				player.needsSync = true;
			}
		}
	}

	/** The throw at tick 14 drags the target in; ten ticks later the pole strikes the face, if they are still there. */
	private static void hook(final MoveRunner.Context context) {
		int tick = context.tick();
		if (tick == 14) {
			if (!(context.target instanceof ServerPlayer player) || context.boss.distanceTo(player) > 12.5 || !context.boss.hasLineOfSight(player)) {
				return;
			}
			Vec3 pull = context.boss.position().subtract(player.position());
			Vec3 flat = new Vec3(pull.x, 0, pull.z);
			if (flat.lengthSqr() > 1.0E-4) {
				Vec3 drag = flat.normalize().scale(Math.min(1.6, 0.25 * flat.length()));
				player.setDeltaMovement(drag.x, 0.3, drag.z);
				player.needsSync = true;
			}
			context.level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.5F, 0.8F);
			context.note("hooked", player);
		} else if (tick == 24) {
			ServerPlayer player = context.note("hooked");
			context.note("hooked", null);
			if (player != null && player.isAlive() && player.level() == context.level && context.boss.distanceTo(player) <= 4.0) {
				context.boss.swing(InteractionHand.MAIN_HAND, context.boss.getMainHandItem().getAttackAnimation(), true);
				context.strikeOne(player, context.move().strikes().getFirst());
			}
		}
	}
}
