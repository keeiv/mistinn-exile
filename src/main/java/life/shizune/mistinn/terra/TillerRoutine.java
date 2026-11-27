package life.shizune.mistinn.terra;

import java.util.EnumSet;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * 作息: how a settlement keeps the day. By day the tillers work the crystal field from its sides,
 * the warden walks the deck's edge and the keeper of trade keeps by the hub; at dusk they gather
 * round the hub; at night the tillers and the keeper go in to the workshop and the warden watches
 * at the hub. A crystal squall or a crystal tide sends every one of them but the warden (who
 * fights a tide) in to the workshop; in a squall the warden goes in too.
 */
public final class TillerRoutine extends Goal {
	/** What the settlement is doing. */
	public enum Phase {
		WORK, GATHER, REST, SHELTER
	}

	/** The day's turns, in ticks of the day: work from just before sunrise, gathering at dusk, rest at night. */
	public static final long DUSK = 11000L;
	public static final long NIGHT = 13000L;
	public static final long DAWN = 23000L;
	/** How near a place counts as being there, and how often the warden moves on along the deck. */
	private static final double THERE = 0.9;
	private static final int ROUND = 600;
	/** How far round the deck's edge the warden walks, and at how many points. */
	private static final int PATROL = 9;
	private static final int POINTS = 6;

	private final Tiller tiller;
	private @Nullable BlockPos going;
	private int idle;
	private int stuck;

	public TillerRoutine(final Tiller tiller) {
		this.tiller = tiller;
		this.setFlags(EnumSet.of(Flag.MOVE));
	}

	/** What a settlement is doing at this time of day, in this weather, under this siege. */
	public static Phase phase(final long dayTime, final boolean storm, final boolean siege) {
		if (storm || siege) {
			return Phase.SHELTER;
		}
		long t = Math.floorMod(dayTime, 24000L);
		if (t >= DAWN || t < DUSK) {
			return Phase.WORK;
		}
		return t < NIGHT ? Phase.GATHER : Phase.REST;
	}

	/** The middle of a settlement's workshop, on the deck. */
	public static BlockPos workshop(final Settlements.Settlement home) {
		return Settlements.along(home.hub(), turn(home) + Math.PI / 3.0, 7).atY(home.deck() + 1);
	}

	/** The middle of a settlement's crystal field. */
	public static BlockPos field(final Settlements.Settlement home) {
		return Settlements.along(home.hub(), turn(home) + Math.PI, 7).atY(home.deck() + 1);
	}

	private static double turn(final Settlements.Settlement home) {
		return TerraShaper.hash(home.domain(), 11, 917) * Math.PI * 2.0;
	}

	/** The settlement a tiller keeps to, or null when it has none (or is not on the server). */
	public static Settlements.@Nullable Settlement home(final Tiller tiller) {
		BlockPos hub = tiller.home();
		MinecraftServer server = tiller.level().getServer();
		if (hub == null || server == null) {
			return null;
		}
		Domain domain = DomainService.at(server, hub.getX(), hub.getZ());
		return domain == null ? null : Settlements.of(server, domain.id());
	}

	/** What a settlement is doing now. */
	public static Phase phase(final Level level, final Settlements.Settlement home) {
		MinecraftServer server = level.getServer();
		Squall.Storm storm = server == null ? null : Squall.storm(server, home.domain());
		boolean squall = storm != null && storm.stage(level.getGameTime()) != Squall.Stage.CALM;
		boolean siege = server != null && Siege.watch(server, home.domain()).on();
		return phase(level.getOverworldClockTime(), squall, siege);
	}

	/** Where this tiller means to be now, or null when it keeps no settlement's day. */
	public static @Nullable BlockPos spot(final Tiller tiller) {
		Settlements.Settlement home = home(tiller);
		if (home == null) {
			return null;
		}
		Phase phase = phase(tiller.level(), home);
		BlockPos hub = home.hub();
		int seat = tiller.role().ordinal();
		boolean warden = tiller.role() == Tiller.Role.WARDEN;
		boolean tiller1 = tiller.role() == Tiller.Role.TILLER_1;
		return switch (phase) {
			case WORK -> switch (tiller.role()) {
				// either side of the field, along its rows
				case TILLER_1, TILLER_2 -> beside(home, tiller1 ? 3 : -3, (int) (tiller.level().getGameTime() / 200L % 5L) - 2);
				case WARDEN -> {
					double angle = turn(home) + Math.PI * 2.0 * (tiller.level().getGameTime() / ROUND % POINTS) / POINTS + Math.PI / POINTS;
					yield Settlements.along(hub, angle, PATROL).atY(home.deck() + 1);
				}
				case ELDER -> Settlements.along(hub, turn(home) + Math.PI / 3.0, 2).atY(home.deck() + 1);
			};
			case GATHER -> Settlements.along(hub, turn(home) + Math.PI * 2.0 * seat / Tiller.Role.values().length, 3).atY(home.deck() + 1);
			case REST -> warden ? Settlements.along(hub, turn(home) + Math.PI / 3.0, 2).atY(home.deck() + 1) : indoors(home, seat);
			case SHELTER -> warden && Siege.watch(tiller.level().getServer(), home.domain()).on() ? null : indoors(home, seat);
		};
	}

	/** A place beside the field: {@code side} blocks off its middle line, {@code along} blocks along its rows. */
	private static BlockPos beside(final Settlements.Settlement home, final int side, final int along) {
		double angle = turn(home) + Math.PI;
		BlockPos middle = field(home);
		// the field's rows run across the line from the hub (see Settlements.field)
		boolean alongX = Math.abs(Math.cos(angle)) < Math.abs(Math.sin(angle));
		return alongX ? middle.offset(along, 0, side) : middle.offset(side, 0, along);
	}

	/** A place inside the workshop for each of its people, clear of the bench at the back. */
	private static BlockPos indoors(final Settlements.Settlement home, final int seat) {
		BlockPos middle = workshop(home);
		int[][] seats = {{0, 0}, {1, 1}, {-1, 1}, {1, -1}};
		int[] at = seats[seat % seats.length];
		// turned to the workshop's own facing: the bench stands at the back, away from the hub
		double angle = turn(home) + Math.PI / 3.0;
		boolean alongX = Math.abs(Math.cos(angle)) >= Math.abs(Math.sin(angle));
		return alongX ? middle.offset(at[1], 0, at[0]) : middle.offset(at[0], 0, at[1]);
	}

	@Override
	public boolean canUse() {
		if (this.tiller.isTrading() || this.tiller.getTarget() != null) {
			return false;
		}
		this.going = spot(this.tiller);
		return this.going != null;
	}

	@Override
	public boolean canContinueToUse() {
		return this.going != null && !this.tiller.isTrading() && this.tiller.getTarget() == null;
	}

	@Override
	public void start() {
		this.idle = 0;
		this.stuck = 0;
		this.walk();
	}

	@Override
	public void stop() {
		this.tiller.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		BlockPos now = spot(this.tiller);
		if (now == null) {
			this.going = null;
			return;
		}
		if (!now.equals(this.going)) {
			this.going = now;
			this.walk();
		}
		double distance = this.tiller.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(this.going));
		if (distance > THERE) {
			if (this.tiller.getNavigation().isDone() && ++this.stuck % 40 == 0) {
				this.walk();
			}
			return;
		}
		this.tiller.getNavigation().stop();
		Settlements.Settlement home = home(this.tiller);
		if (home == null) {
			return;
		}
		Phase phase = phase(this.tiller.level(), home);
		if (phase == Phase.WORK && (this.tiller.role() == Tiller.Role.TILLER_1 || this.tiller.role() == Tiller.Role.TILLER_2)) {
			// tending: facing the field, now and then a hand to the sprouts
			BlockPos field = field(home);
			this.tiller.getLookControl().setLookAt(field.getX() + 0.5, field.getY(), field.getZ() + 0.5);
			if (++this.idle % 60 == 0 && this.tiller.level() instanceof ServerLevel level) {
				this.tiller.swing(InteractionHand.MAIN_HAND, this.tiller.getMainHandItem().getAttackAnimation(), true);
				double x = (this.tiller.getX() + field.getX() + 0.5) / 2.0;
				double z = (this.tiller.getZ() + field.getZ() + 0.5) / 2.0;
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, life.shizune.mistinn.registry.MistinnBlocks.CRYSTAL_SPROUT.defaultBlockState()),
					x, this.tiller.getY() + 0.3, z, 6, 0.3, 0.1, 0.3, 0.02);
			}
		}
	}

	private void walk() {
		if (this.going != null) {
			this.tiller.getNavigation().moveTo(this.going.getX() + 0.5, this.going.getY(), this.going.getZ() + 0.5, 0.6);
		}
	}
}
