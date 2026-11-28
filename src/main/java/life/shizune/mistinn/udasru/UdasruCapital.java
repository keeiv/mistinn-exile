package life.shizune.mistinn.udasru;

import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.station.StaffRole;
import life.shizune.mistinn.world.Builder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/**
 * The Bell Capital's wall, gates and market. The wall runs round the city at {@link UdasruSites#WALL}, three blocks
 * thick and nine high with a parapet and a tower at every eighth of the round; each gate is an arch five blocks wide
 * with its guards. Pieces are built with their origin at the capital's middle, on its ground.
 */
public final class UdasruCapital {
	private static final int HEIGHT = 9;
	/** Half the width a gate leaves open. */
	private static final int GATE = 3;

	private UdasruCapital() {
	}

	/** The wall's length between two angles (radians, clockwise from the east), with a tower at its start every second length. */
	public static void wall(final Builder site, final double a0, final double a1) {
		Builder b = centred(site);
		int steps = (int) Math.ceil((a1 - a0) * (UdasruSites.WALL + 2) * 2);
		for (int i = 0; i <= steps; i++) {
			double a = a0 + (a1 - a0) * i / steps;
			for (int t = -1; t <= 1; t++) {
				int x = (int) Math.round(Math.cos(a) * (UdasruSites.WALL + t));
				int z = (int) Math.round(Math.sin(a) * (UdasruSites.WALL + t));
				if (Math.abs(x) <= GATE + 1 || Math.abs(z) <= GATE + 1) {
					continue;
				}
				b.footing(x, z, MistinnBlocks.UDASRU_STONE_BRICKS, 20);
				for (int y = 0; y <= HEIGHT; y++) {
					b.set(x, y, z, y == HEIGHT - 2 ? MistinnBlocks.BELL_BRONZE : MistinnBlocks.UDASRU_STONE_BRICKS);
				}
				if (t == 1 && i % 2 == 0) {
					b.set(x, HEIGHT + 1, z, MistinnBlocks.UDASRU_STONE_BRICK_WALL);
				}
			}
		}
		int k = (int) Math.round(a0 / (Math.PI / 8.0));
		if (k % 2 == 0 && k % 4 != 0) {
			tower(b, (int) Math.round(Math.cos(a0) * UdasruSites.WALL), (int) Math.round(Math.sin(a0) * UdasruSites.WALL));
		}
		b.settle();
	}

	/** A round wall tower, seven across, standing above the wall with a tiled cap. */
	private static void tower(final Builder b, final int cx, final int cz) {
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				if (x * x + z * z > 11) {
					continue;
				}
				b.footing(cx + x, cz + z, MistinnBlocks.UDASRU_STONE_BRICKS, 20);
				boolean rim = x * x + z * z >= 5;
				for (int y = 0; y <= HEIGHT + 5; y++) {
					b.set(cx + x, y, cz + z, rim || y == 0 || y == HEIGHT + 1 ? MistinnBlocks.UDASRU_STONE_BRICKS : Blocks.AIR);
				}
				b.set(cx + x, HEIGHT + 6, cz + z, MistinnBlocks.UDASRU_ROOF_TILES);
				if (x * x + z * z <= 4) {
					b.set(cx + x, HEIGHT + 7, cz + z, MistinnBlocks.UDASRU_ROOF_TILES);
				}
			}
		}
		b.set(cx, HEIGHT + 8, cz, MistinnBlocks.BELL_BRONZE);
		b.hanging(cx, HEIGHT + 4, cz, Blocks.LANTERN);
	}

	/** A gate: an arch through the wall, flanked by towers, a lantern each side and two guards. */
	public static void gate(final Builder site, final UdasruSites.Direction4 way) {
		Builder b = centred(site);
		int cx = way.dx * UdasruSites.WALL;
		int cz = way.dz * UdasruSites.WALL;
		// along: the wall's direction at the gate; across: through it
		int ax = way.dz != 0 ? 1 : 0;
		int az = way.dx != 0 ? 1 : 0;
		for (int s = -GATE - 3; s <= GATE + 3; s++) {
			for (int t = -2; t <= 2; t++) {
				int x = cx + ax * s + way.dx * t;
				int z = cz + az * s + way.dz * t;
				b.footing(x, z, MistinnBlocks.UDASRU_STONE_BRICKS, 20);
				boolean open = Math.abs(s) <= GATE - 1;
				for (int y = 0; y <= HEIGHT + 3; y++) {
					boolean arch = open && y >= 1 && y <= HEIGHT - 3 + (Math.abs(s) <= 1 ? 1 : 0);
					b.set(x, y, z, arch ? Blocks.AIR : y == 0 ? MistinnBlocks.UDASRU_STONE_BRICKS
						: y == HEIGHT ? MistinnBlocks.BELL_BRONZE : MistinnBlocks.UDASRU_STONE_BRICKS);
				}
				if (Math.abs(t) == 2 && (s + 16) % 2 == 0) {
					b.set(x, HEIGHT + 4, z, MistinnBlocks.UDASRU_STONE_BRICK_WALL);
				}
			}
		}
		for (int sign : new int[] {-1, 1}) {
			int x = cx + ax * sign * (GATE + 1) + way.dx * 3;
			int z = cz + az * sign * (GATE + 1) + way.dz * 3;
			b.set(x, 1, z, MistinnBlocks.UDASRU_STONE_BRICK_WALL);
			b.set(x, 2, z, Blocks.LANTERN);
			UdasruBuild.person(b, StaffRole.CAPITAL_GUARD, cx + ax * sign * 2 - way.dx * 4, 1, cz + az * sign * 2 - way.dz * 4,
				Direction.fromYRot(Math.toDegrees(Math.atan2(way.dx, -way.dz))).toYRot(), 2);
		}
		b.facing(cx - way.dx * 3, HEIGHT - 2, cz - way.dz * 3, Blocks.WALL_BANNER.blue(),
			way == UdasruSites.Direction4.NORTH ? Direction.SOUTH : way == UdasruSites.Direction4.SOUTH ? Direction.NORTH
				: way == UdasruSites.Direction4.EAST ? Direction.WEST : Direction.EAST);
		b.settle();
	}

	/** The market: rows of stalls under striped awnings, and the people who keep them. */
	public static void market(final Builder b) {
		net.minecraft.world.level.block.Block[] cloths = {Blocks.WOOL.blue(), Blocks.WOOL.orange(), Blocks.WOOL.lightBlue(), Blocks.WOOL.yellow()};
		int n = 0;
		for (int x = -12; x <= 12; x += 8) {
			for (int z = -9; z <= 9; z += 9) {
				UdasruBuild.stall(new Builder(b.level(), b.at(x, 0, z), b.random()), cloths[n % cloths.length]);
				if (n % 2 == 0) {
					UdasruBuild.person(b, n % 4 == 0 ? StaffRole.TOWNSMAN : StaffRole.TOWNSWOMAN, x, 1, z + 2, 180.0F, 3);
				}
				n++;
			}
		}
	}

	/** A builder with its origin at the capital's middle, on its ground (the site's own origin may lie elsewhere). */
	private static Builder centred(final Builder site) {
		return new Builder(site.level(), new BlockPos(UdasruPlaces.CAPITAL.getX(), UdasruSites.CAPITAL_Y, UdasruPlaces.CAPITAL.getZ()), site.random());
	}
}
