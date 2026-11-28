package life.shizune.mistinn.expedition;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * How one ground is laid out: what stands there, where a party comes in, where it can get out, what is to be found and
 * who keeps it. The facilities are built once and never change after (the forebears' work does not break); each reset
 * only stocks them again.
 */
public interface Layout {
	/**
	 * The box the facilities stand in: its chunks are loaded for each reset, and everything in it that is not a hunter is
	 * swept away then (an entity the facilities are dressed with too: {@link #stock} puts such things back).
	 */
	BoundingBox bounds();

	/** Builds the facilities, once: the first time a party is sent. */
	void build(ServerLevel level);

	/** Where a party comes in; each hunter of it is set down at the next one. */
	List<BlockPos> starts();

	/** The ways out. */
	List<Exit> exits();

	/** Fills what is to be found and puts out who keeps it: after each sweep. */
	void stock(ServerLevel level, RandomSource random);

	/** What comes for the hunters waiting at an exit: one wave, the {@code wave}th since they began to wait. */
	void wave(ServerLevel level, Exit exit, int wave, RandomSource random);

	/** A way out: a hunter who stays within {@code radius} of {@code centre} (and near its height) for the whole count leaves. */
	record Exit(BlockPos centre, double radius) {
		/** Whether {@code entity} stands in it. */
		public boolean holds(final Entity entity) {
			double dx = entity.getX() - (this.centre.getX() + 0.5);
			double dz = entity.getZ() - (this.centre.getZ() + 0.5);
			return dx * dx + dz * dz <= this.radius * this.radius && Math.abs(entity.getY() - this.centre.getY()) <= 3.0;
		}
	}
}
