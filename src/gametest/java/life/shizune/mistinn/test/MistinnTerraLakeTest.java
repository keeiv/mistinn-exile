package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.terra.ResearchShip;
import life.shizune.mistinn.terra.TerraLake;
import life.shizune.mistinn.terra.TerraService;
import life.shizune.mistinn.terra.TerraShaper;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The great lake of a Terra highland, dug into land as it lies, strictly round anyone's work: its water at one level
 * with a bed under it, deepest in the middle, the lake's own biome; a cut bank where ground stood high by it; and left
 * exactly as they are a column with a block the land did not grow, the ground near a chest, a roof of earth over air, and
 * a chunk someone has lived in. An older world's highland widens, never into another's ground.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnTerraLakeTest implements FabricClientGameTest {
	private static final int Y = 250;
	private static final int SPAN = 88;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 6000");
			context.waitTicks(20);
			BlockPos[] middle = new BlockPos[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				BlockPos centre = player.blockPosition().atY(Y).offset(420, 0, 0);
				Domain domain = TerraService.settleAt(level, centre, TerraService.RADIUS);
				BlockPos m = TerraLake.middle(domain).atY(Y);
				middle[0] = m;
				// flat ground high in the sky round the lake: stone, earth, grass
				for (int x = -SPAN; x <= SPAN; x++) {
					for (int z = -SPAN; z <= SPAN; z++) {
						for (int y = Y - 20; y < Y; y++) {
							level.setBlock(m.offset(x, y - Y, z), (y == Y - 1 ? Blocks.GRASS_BLOCK : y >= Y - 3 ? Blocks.DIRT : Blocks.STONE).defaultBlockState(), 2);
						}
					}
				}
				// someone's work in it: a cobblestone post, a chest, a hut roofed with earth, a chunk lived in
				for (int y = 0; y < 3; y++) {
					level.setBlock(m.offset(10, y, 0), Blocks.COBBLESTONE.defaultBlockState(), 2);
				}
				level.setBlock(m.offset(-20, 0, 6), Blocks.CHEST.defaultBlockState(), 2);
				for (int x = 0; x < 3; x++) {
					for (int z = 20; z < 23; z++) {
						level.setBlock(m.offset(x, 3, z), Blocks.DIRT.defaultBlockState(), 2);
					}
				}
				BlockPos lived = m.offset(0, 0, -32);
				level.getChunkAt(lived).setInhabitedTime(TerraLake.LIVED_IN + 100L);
				// and what the land grew: coal in the stone
				level.setBlock(m.offset(5, -6, -5), Blocks.COAL_ORE.defaultBlockState(), 2);
				// a knoll of stone just past the shore, east
				int shore = 0;
				while (TerraLake.depthPart(domain, m.getX(), m.getZ(), m.getX() + shore, m.getZ()) < 1.0) {
					shore++;
				}
				for (int x = shore + 4; x <= shore + 7; x++) {
					for (int z = -1; z <= 1; z++) {
						for (int y = 0; y < 10; y++) {
							level.setBlock(m.offset(x, y, z), (y == 9 ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState(), 2);
						}
					}
				}
				// the highland's turning leaves the lake's ground to the lake
				BlockPos under = m.offset(1, -1, 21);
				new TerraShaper().column(level, level.getChunkAt(under), domain, under.getX(), under.getZ(), domain.distance(under.getX() + 0.5, under.getZ() + 0.5));
				check(level.getBlockState(under).is(Blocks.GRASS_BLOCK), "the highland's turning keeps off the lake");
				// dig every chunk of it
				int dug = 0;
				ChunkPos from = new ChunkPos((m.getX() - SPAN) >> 4, (m.getZ() - SPAN) >> 4);
				ChunkPos to = new ChunkPos((m.getX() + SPAN) >> 4, (m.getZ() + SPAN) >> 4);
				for (int cx = from.x(); cx <= to.x(); cx++) {
					for (int cz = from.z(); cz <= to.z(); cz++) {
						dug += TerraLake.dig(level, level.getChunk(cx, cz), domain);
					}
				}
				TerraLake.Lake lake = TerraLake.placed(level, domain.id());
				check(lake != null && lake.level() == Y - 2, "the lake lies a little below its lowest shore: " + (lake == null ? "none" : lake.level()));
				check(dug > 4000, "a great lake is dug: " + dug + " columns");
				// water to its level, a bed under it, deepest in the middle
				check(level.getBlockState(m.offset(0, -2, 0)).is(Blocks.WATER) && level.getBlockState(m.offset(0, -12, 0)).is(Blocks.WATER),
					"water down eleven blocks in the middle");
				BlockState bed = level.getBlockState(m.offset(0, -13, 0));
				check(bed.is(Blocks.CLAY) || bed.is(Blocks.GRAVEL), "on a bed of clay or gravel: " + bed);
				check(level.getBlockState(m.offset(0, -1, 0)).isAir() && level.getBlockState(m).isAir(), "and open air over the water");
				check(level.getBiome(m.offset(0, -4, 0)).is(TerraLake.LAKE), "the lake has its own biome");
				// ore is the land's own: dug
				check(level.getBlockState(m.offset(5, -2, -5)).is(Blocks.WATER), "a column with ore in it is the land's, and dug");
				// someone's work, left exactly as it is
				check(level.getBlockState(m.offset(10, 0, 0)).is(Blocks.COBBLESTONE) && level.getBlockState(m.offset(10, -1, 0)).is(Blocks.GRASS_BLOCK),
					"a column with a block the land did not grow is left");
				check(level.getBlockState(m.offset(-20, 0, 6)).is(Blocks.CHEST) && level.getBlockState(m.offset(-17, -1, 9)).is(Blocks.GRASS_BLOCK),
					"the ground near a chest is left");
				check(level.getBlockState(m.offset(-12, -2, 6)).is(Blocks.WATER), "and farther from it, dug");
				check(level.getBlockState(m.offset(1, 3, 21)).is(Blocks.DIRT) && level.getBlockState(under).is(Blocks.GRASS_BLOCK),
					"a roof of earth over air is someone's: left");
				check(level.getBlockState(lived.below()).is(Blocks.GRASS_BLOCK), "a chunk someone has lived in is left");
				// the knoll by the shore cut down to a bank
				BlockPos knoll = m.offset(shore + 5, 0, 0);
				check(level.getBlockState(knoll.above(9)).isAir() && level.getBlockState(knoll.above(7)).isAir(), "the knoll past the shore is cut down");
				int face = Y + 9;
				while (face > Y - 2 && level.getBlockState(knoll.atY(face)).isAir()) {
					face--;
				}
				BlockState cut = level.getBlockState(knoll.atY(face));
				check(face > Y && face < Y + 7 && (cut.is(Blocks.SAND) || cut.is(life.shizune.mistinn.registry.MistinnBlocks.JADE_TURF)),
					"to a bank sloping up from the water, faced with turf: " + (face - Y) + " " + cut);
				// a ship comes to the lake only once its middle is all dug, and only on open water: the cobblestone post stands
				// in its way when it would lie east and west
				ResearchShip.chance = 1.0;
				ResearchShip.consider(level, domain);
				check(ResearchShip.of(level, domain.id()) == null, "no ship before the lake's middle is all dug");
				for (int cx = from.x(); cx <= to.x(); cx++) {
					for (int cz = from.z(); cz <= to.z(); cz++) {
						level.getChunk(cx, cz).setAttached(TerraLake.DUG, TerraLake.VERSION);
					}
				}
				ResearchShip.consider(level, domain);
				ResearchShip.Ship ship = ResearchShip.of(level, domain.id());
				check(ship != null && ship.present() == !ship.alongX(), "then it comes, unless someone's work stands in the water: "
					+ (ship == null ? "none" : ship.present() + " along x " + ship.alongX()));
				// an older world's highland widens, short of a rift that came near it
				// (somewhere well clear of the world's own highlands)
				BlockPos old = null;
				for (int i = 0; i < 16 && old == null; i++) {
					double angle = Math.PI + i * Math.PI / 8.0;
					BlockPos at = player.blockPosition().atY(Y).offset((int) (Math.cos(angle) * 760), 0, (int) (Math.sin(angle) * 760));
					boolean clear = true;
					for (Domain other : DomainService.all(server)) {
						clear &= other.distance(at.getX() + 0.5, at.getZ() + 0.5) - other.radius() - TerraService.GAP > TerraService.RADIUS;
					}
					old = clear ? at : null;
				}
				check(old != null, "(a clear place for an older highland)");
				Domain narrow = TerraService.settleAt(level, old, 180);
				Domain rift = DomainService.add(server, Domain.Kind.RIFT, old.offset(300, 0, 0), 40, Domain.Phase.SETTLED, 47);
				int expected = 300 - 40 - TerraService.GAP;
				TerraService.widen(server);
				Domain wide = DomainService.of(server, Domain.Kind.TERRA).stream().filter(d -> d.id() == narrow.id()).findFirst().orElseThrow();
				double gap = wide.distance(rift.centre().getX() + 0.5, rift.centre().getZ() + 0.5) - wide.radius() - rift.radius();
				check(wide.radius() == expected && gap >= TerraService.GAP - 1, "an older highland widens (" + wide.radius() + "), short of the rift by " + gap);
				Domain whole = DomainService.of(server, Domain.Kind.TERRA).stream().filter(d -> d.id() == domain.id()).findFirst().orElseThrow();
				check(whole.radius() == TerraService.RADIUS, "a highland already wide is left as it is");
				// look over it
				player.teleportTo(level, m.getX() + 0.5, Y + 22, m.getZ() - 70.5, Set.of(), 0.0F, 18.0F, true);
			});
			context.waitTicks(80);
			world.getConnection().waitForChunksRender();
			context.takeScreenshot(TestScreenshotOptions.of("lake").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("lake")));
		}
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn lake] ok: " + what);
	}
}
