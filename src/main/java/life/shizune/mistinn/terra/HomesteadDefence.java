package life.shizune.mistinn.terra;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.block.RelayPylonBlock;
import life.shizune.mistinn.block.TurretBlock;
import life.shizune.mistinn.registry.MistinnBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A woken homestead holds itself in a crystal tide.
 * <ul>
 * <li>Its relay pylons discharge: every two seconds each pylon's head strikes the nearest of the
 * tide's creatures within reach, a little hurt and a long slowing.</li>
 * <li>At its second step it raises two crystal turrets on the deck, out to either side of the hub,
 * that fire on the tide's creatures harder and farther.</li>
 * </ul>
 * Neither holds a tide off alone: they thin and slow it for the hunters standing in it.
 */
public final class HomesteadDefence {
	/** A pylon's discharge: how often, how far it reaches, what it does. */
	public static final int DISCHARGE = 40;
	public static final double DISCHARGE_REACH = 10.0;
	public static final float DISCHARGE_HURT = 3.0F;
	public static final int SLOWED = 60;
	/** A turret's shot: how often, how far, how hard. */
	public static final int SHOT = 30;
	public static final double SHOT_REACH = 14.0;
	public static final float SHOT_HURT = 5.0F;
	/** The step a homestead raises its turrets at, and how far out from the hub they stand. */
	public static final int TURRET_STEP = 2;
	public static final int TURRET_OUT = 10;
	/** Counts of strikes and shots (for tests). */
	public static int discharges;
	public static int shots;
	/** Where each homestead's pylon heads and turrets stand, found once a server run. */
	private static final Map<Integer, List<BlockPos>> HEADS = new HashMap<>();
	private static final Map<Integer, List<BlockPos>> TURRETS = new HashMap<>();

	private HomesteadDefence() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HomesteadDefence::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HEADS.clear();
			TURRETS.clear();
		});
	}

	private static void tick(final MinecraftServer server) {
		int now = server.getTickCount();
		boolean discharge = now % DISCHARGE == 0;
		boolean shoot = now % SHOT == 0;
		if (now % 1200 == 0) {
			// a homestead grown before turrets were raised has them raised now
			for (Settlements.Settlement home : Settlements.homesteads(server)) {
				if (HomesteadUpgrades.level(server, home.domain()) >= TURRET_STEP) {
					raiseTurrets(server.overworld(), home);
				}
			}
		}
		if (!discharge && !shoot) {
			return;
		}
		ServerLevel level = server.overworld();
		for (Settlements.Settlement home : Settlements.homesteads(server)) {
			Siege.Watch watch = Siege.watch(server, home.domain());
			if (!watch.on() || !level.isLoaded(home.hub())) {
				continue;
			}
			List<Mob> foes = foes(level, watch.foes());
			if (foes.isEmpty()) {
				continue;
			}
			if (discharge) {
				for (BlockPos head : heads(level, home)) {
					Mob foe = nearest(foes, Vec3.atCenterOf(head), DISCHARGE_REACH);
					if (foe != null) {
						strike(level, Vec3.atCenterOf(head).add(0.0, 0.3, 0.0), foe, DISCHARGE_HURT, ParticleTypes.ELECTRIC_SPARK);
						foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWED, 1));
						level.playSound(null, head, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.BLOCKS, 0.35F, 1.8F);
						discharges++;
					}
				}
			}
			if (shoot) {
				for (BlockPos turret : turrets(level, home)) {
					Mob foe = nearest(foes, Vec3.atCenterOf(turret), SHOT_REACH);
					if (foe != null) {
						strike(level, Vec3.atCenterOf(turret).add(0.0, 0.5, 0.0), foe, SHOT_HURT, ParticleTypes.END_ROD);
						level.playSound(null, turret, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.2F, 1.6F);
						level.playSound(null, turret, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.4F, 2.0F);
						shots++;
					}
				}
			}
		}
	}

	private static List<Mob> foes(final ServerLevel level, final List<UUID> ids) {
		List<Mob> foes = new ArrayList<>();
		for (UUID id : ids) {
			if (level.getEntity(id) instanceof Mob mob && mob.isAlive()) {
				foes.add(mob);
			}
		}
		return foes;
	}

	private static @Nullable Mob nearest(final List<Mob> foes, final Vec3 from, final double reach) {
		Mob best = null;
		double bestDistance = reach * reach;
		for (Mob foe : foes) {
			double distance = foe.position().distanceToSqr(from);
			if (distance <= bestDistance) {
				best = foe;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** A bolt from {@code from} to the creature: a line of light, and the hurt. */
	private static void strike(final ServerLevel level, final Vec3 from, final Mob foe, final float hurt, final ParticleOptions light) {
		Vec3 to = foe.getEyePosition();
		Vec3 step = to.subtract(from);
		int points = Math.max(4, (int) (step.length() * 3.0));
		for (int i = 0; i <= points; i++) {
			Vec3 at = from.add(step.scale((double) i / points));
			level.sendParticles(light, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		foe.hurtServer(level, level.damageSources().magic(), hurt);
	}

	/** The pylon heads round a homestead's hub. */
	private static List<BlockPos> heads(final ServerLevel level, final Settlements.Settlement home) {
		return HEADS.computeIfAbsent(home.domain(), d -> {
			List<BlockPos> found = new ArrayList<>();
			for (BlockPos at : BlockPos.betweenClosed(home.hub().offset(-Settlements.DECK, 0, -Settlements.DECK),
				home.hub().offset(Settlements.DECK, Settlements.PYLON + 1, Settlements.DECK))) {
				BlockState state = level.getBlockState(at);
				if (state.is(MistinnBlocks.RELAY_PYLON) && state.getValue(RelayPylonBlock.PART) == RelayPylonBlock.Part.HEAD) {
					found.add(at.immutable());
				}
			}
			return List.copyOf(found);
		});
	}

	private static List<BlockPos> turrets(final ServerLevel level, final Settlements.Settlement home) {
		List<BlockPos> known = TURRETS.get(home.domain());
		if (known == null) {
			List<BlockPos> found = new ArrayList<>();
			for (BlockPos at : spots(home)) {
				if (level.getBlockState(at).is(MistinnBlocks.TURRET)) {
					found.add(at);
				}
			}
			known = List.copyOf(found);
			TURRETS.put(home.domain(), known);
		}
		return known;
	}

	/** Where a homestead's turrets stand: out to either side of the hub, between its pylons and its buildings. */
	public static List<BlockPos> spots(final Settlements.Settlement home) {
		double turn = TerraShaper.hash(home.domain(), 11, 917) * Math.PI * 2.0;
		List<BlockPos> spots = new ArrayList<>();
		for (double side : new double[] {Math.PI / 2.0, Math.PI * 3.0 / 2.0}) {
			double angle = turn + side;
			spots.add(new BlockPos((int) Math.round(home.hub().getX() + Math.cos(angle) * TURRET_OUT), home.deck() + 1,
				(int) Math.round(home.hub().getZ() + Math.sin(angle) * TURRET_OUT)));
		}
		return spots;
	}

	/** Raises a homestead's turrets where they are not yet standing (and nothing else stands). */
	public static void raiseTurrets(final ServerLevel level, final Settlements.Settlement home) {
		boolean raised = false;
		for (BlockPos spot : spots(home)) {
			if (!level.isLoaded(spot) || level.getBlockState(spot).is(MistinnBlocks.TURRET) || !level.getBlockState(spot).canBeReplaced()) {
				continue;
			}
			Direction out = Direction.getApproximateNearest(spot.getX() - home.hub().getX(), 0, spot.getZ() - home.hub().getZ());
			level.setBlock(spot, MistinnBlocks.TURRET.defaultBlockState().setValue(TurretBlock.FACING, out).setValue(TurretBlock.LIT, home.lit()),
				Block.UPDATE_ALL);
			level.sendParticles(ParticleTypes.END_ROD, spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.05);
			raised = true;
		}
		if (raised) {
			level.playSound(null, home.hub(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.8F, 0.7F);
			TURRETS.remove(home.domain());
		}
	}
}
