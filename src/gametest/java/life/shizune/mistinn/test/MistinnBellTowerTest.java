package life.shizune.mistinn.test;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.keeper.SunkenBell;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.shore.SaltCorpse;
import life.shizune.mistinn.world.BellTower;
import life.shizune.mistinn.world.Builder;
import life.shizune.mistinn.world.ShoreMist;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * 霧鐘台: the fog-bell station is built whole: a golden bell where the boats tied up, a stair a hunter can climb from the
 * door to the belfry, the three floors of stores getting better as they climb, the keeper under the great bell with room
 * over its head, and the mist remembered round it. Its world-generation feature is registered.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnBellTowerTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				check(server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
					.get(ResourceKey.create(Registries.PLACED_FEATURE, Mistinn.id("bell_tower"))).isPresent(), "the fog-bell station is placed by world generation");
				for (long seed : new long[] {5L, 9L}) {
					int x = (int) server.getPlayerList().getPlayers().getFirst().getX() + 60 + (int) seed * 30;
					int z = (int) server.getPlayerList().getPlayers().getFirst().getZ();
					level.getChunk(x >> 4, z >> 4);
					BlockPos origin = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
					BlockPos deck = BellTower.build(new Builder(level, origin, RandomSource.create(seed)));
					check(deck.equals(origin.offset(0, BellTower.DECK + 1, 0)), "the keeper's place is the middle of the belfry: " + deck);
					level.getEntitiesOfClass(SaltCorpse.class, new AABB(origin).inflate(16.0)).forEach(SaltCorpse::discard);

					// the keeper, under the great bell, with room over its head
					var keepers = level.getEntitiesOfClass(SunkenBell.class, new AABB(deck).inflate(2.0));
					check(keepers.size() == 1, "the Sunken Bell waits in its belfry (seed " + seed + "): " + keepers.size());
					check(keepers.getFirst().getBoundingBox().maxY <= origin.getY() + BellTower.BELL_LIP + 0.01,
						"the great bell hangs clear over its head: " + keepers.getFirst().getBoundingBox().maxY + " under " + (origin.getY() + BellTower.BELL_LIP));
					check(!level.getBlockState(origin.offset(2, BellTower.BELL_LIP, 0)).isAir(), "the great bell's lip is there");
					keepers.forEach(SunkenBell::discard);

					// a golden bell where the boats tied up
					boolean bell = false;
					for (BlockPos at : BlockPos.betweenClosed(origin.offset(-6, 0, 3), origin.offset(6, 3, 12))) {
						bell |= level.getBlockState(at).is(MistinnBlocks.GOLDEN_BELL);
					}
					check(bell, "a golden bell waits at the foot of the tower (seed " + seed + ")");

					// the stores: the higher the floor, the better
					Map<String, Integer> found = new HashMap<>();
					for (BlockPos at : BlockPos.betweenClosed(origin.offset(-9, 0, -9), origin.offset(9, BellTower.DECK + 2, 9))) {
						if (level.getBlockEntity(at) instanceof RandomizableContainerBlockEntity container && container.getLootTable() != null) {
							found.put(container.getLootTable().identifier().getPath(), at.getY() - origin.getY());
						}
					}
					check(found.containsKey("chests/lighthouse_base") && found.containsKey("chests/lighthouse") && found.containsKey("chests/lighthouse_lamp"),
						"three floors of stores (seed " + seed + "): " + found);
					check(found.get("chests/lighthouse_base") < found.get("chests/lighthouse") && found.get("chests/lighthouse") < found.get("chests/lighthouse_lamp"),
						"and the better ones higher up: " + found);
					check(found.get("chests/lighthouse_lamp") == BellTower.DECK + 1, "the best in the belfry: " + found);

					// a hunter can climb from the door to the belfry
					check(climbs(level, origin), "a hunter can walk in at the door and climb to the belfry (seed " + seed + ")");

					// the mist knows of it
					check(ShoreMist.lighthouses(level).contains(deck), "the mist is remembered round it: " + ShoreMist.lighthouses(level));
				}
			});
			sealsTheHatch(context, world);
			photographed(context, world);
		}
	}

	/** Up in the belfry with its keeper, the mist shuts the hatch; when the keeper falls it opens again. */
	private static void sealsTheHatch(final ClientGameTestContext context, final TestSingleplayerContext world) {
		BlockPos origin = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			var player = server.getPlayerList().getPlayers().getFirst();
			int x = (int) player.getX() + 20;
			int z = (int) player.getZ() + 120;
			level.getChunk(x >> 4, z >> 4);
			BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
			BellTower.build(new Builder(level, at, RandomSource.create(13L)));
			level.getEntitiesOfClass(SaltCorpse.class, new AABB(at).inflate(16.0)).forEach(SaltCorpse::discard);
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 400, 4, false, false));
			player.teleportTo(level, at.getX() - 4.5, at.getY() + BellTower.DECK + 1.0, at.getZ() + 0.5, java.util.Set.of(), 270.0F, 0.0F, true);
			return at;
		});
		BlockPos hatch = origin.offset(3, BellTower.DECK, 0);
		context.waitTicks(20);
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			check(level.getBlockState(hatch).is(net.minecraft.world.level.block.Blocks.BARRIER), "up in the belfry, the mist shuts the hatch: " + level.getBlockState(hatch));
			SunkenBell bell = level.getEntitiesOfClass(SunkenBell.class, new AABB(origin).inflate(20.0)).getFirst();
			check(life.shizune.mistinn.keeper.MistWall.shut(bell, level), "its room says it is shut");
			bell.kill(level);
		});
		context.waitTicks(5);
		world.getServer().runOnServer(server -> {
			check(server.overworld().getBlockState(hatch).isAir(), "the keeper fallen, the way down opens: " + server.overworld().getBlockState(hatch));
			server.getPlayerList().getPlayers().getFirst().removeAllEffects();
		});
	}

	/** Stills for the eye: the station from the shore, and the belfry from the top of the stair. */
	private static void photographed(final ClientGameTestContext context, final TestSingleplayerContext world) {
		BlockPos origin = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			var player = server.getPlayerList().getPlayers().getFirst();
			int x = (int) player.getX() - 70;
			int z = (int) player.getZ();
			level.getChunk(x >> 4, z >> 4);
			BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
			BellTower.build(new Builder(level, at, RandomSource.create(3L)));
			level.getEntitiesOfClass(SunkenBell.class, new AABB(at).inflate(20.0)).forEach(bell -> bell.setNoAi(true));
			player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
			player.teleportTo(level, at.getX() + 16.5, at.getY() + 9.0, at.getZ() + 24.5, java.util.Set.of(), 146.0F, 12.0F, true);
			return at;
		});
		context.waitTicks(40);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot("tower-00-outside");
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().teleportTo(server.overworld(),
			origin.getX() + 3.5, origin.getY() + BellTower.DECK + 1.0, origin.getZ() + 5.5, java.util.Set.of(), 150.0F, -12.0F, true));
		context.waitTicks(40);
		context.takeScreenshot("tower-01-belfry");
	}

	/** Whether a hunter (two blocks tall, stepping up one block at a time) can get from outside the door to the belfry floor. */
	private static boolean climbs(final ServerLevel level, final BlockPos origin) {
		BlockPos start = origin.offset(0, 1, 6);
		check(stands(level, start), "there is ground outside the door: " + level.getBlockState(start.below()));
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		open.add(start);
		seen.add(start);
		while (!open.isEmpty()) {
			BlockPos at = open.poll();
			if (at.getY() - origin.getY() == BellTower.DECK + 1) {
				return true;
			}
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos next = at.relative(side);
				// on, a step up (with room to jump), or down a little
				for (int dy : new int[] {0, 1, -1, -2}) {
					BlockPos to = next.above(dy);
					if (Math.abs(to.getX() - origin.getX()) > 9 || Math.abs(to.getZ() - origin.getZ()) > 9 || to.getY() < origin.getY()) {
						continue;
					}
					if (dy == 1 && !level.getBlockState(at.above(2)).isAir()) {
						continue;
					}
					if (stands(level, to) && seen.add(to.immutable())) {
						open.add(to.immutable());
						break;
					}
				}
			}
		}
		return false;
	}

	/** Whether a hunter can stand with their feet at {@code at}: something to stand on, and room for their body. */
	private static boolean stands(final ServerLevel level, final BlockPos at) {
		BlockState floor = level.getBlockState(at.below());
		boolean ground = floor.isFaceSturdy(level, at.below(), Direction.UP) || floor.getBlock() instanceof net.minecraft.world.level.block.StairBlock
			|| floor.getBlock() instanceof net.minecraft.world.level.block.SlabBlock;
		return ground && passable(level, at) && passable(level, at.above());
	}

	private static boolean passable(final ServerLevel level, final BlockPos at) {
		BlockState state = level.getBlockState(at);
		return state.getCollisionShape(level, at).isEmpty();
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
