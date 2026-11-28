package life.shizune.mistinn.test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.elite.EliteService;
import life.shizune.mistinn.expedition.DamGround;
import life.shizune.mistinn.expedition.Expedition;
import life.shizune.mistinn.expedition.Grounds;
import life.shizune.mistinn.terra.CrystalDrake;
import life.shizune.mistinn.terra.CrystalLizard;
import life.shizune.mistinn.terra.Settlements;
import life.shizune.mistinn.terra.TerraService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * 泰拉水壩: built in steps while the party gathers, and a build cut short and done again comes out whole; the party waits
 * by the hub until it is done. Built, its water stands still. A hunter walking from where the party comes in reaches both
 * ways out and the control room, and nowhere walks or falls off the edge of the world. Pictures from along the way.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnDamTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("time set 6000");
			context.waitTicks(20);
			long[] began = new long[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				server.setDifficulty(Difficulty.NORMAL, true);
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(180, 0, 0));
				Domain domain = TerraService.settleAt(level, centre, 240);
				Settlements.Settlement built = Settlements.buildNow(level, domain);
				check(Settlements.light(level, built.hub(), player), "a homestead is woken");
				player.getAbilities().mayfly = true;
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				player.teleportTo(level, built.hub().getX() + 2.5, built.hub().getY() + 1.0, built.hub().getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
				// half a build, as if the server had stopped partway: the next starts over and comes out whole
				ServerLevel dam = server.getLevel(Grounds.DAM);
				Expedition.buildNow(dam, new DamGround().plan().size() / 2);
				check(!Expedition.ground(server, Grounds.DAM).built(), "half a build is not a build");
				check(Expedition.muster(player, Grounds.DAM) == Expedition.Result.MUSTERING, "a party sets out for the dam");
				check(Expedition.building(Grounds.DAM), "and the dam is built while it gathers");
				began[0] = level.getGameTime();
			});
			boolean in = false;
			for (int i = 0; i < 90 && !in; i++) {
				context.waitTicks(20);
				in = world.getServer().computeOnServer(server -> player(server).level().dimension() == Grounds.DAM);
			}
			check(in, "the party gets to the dam");
			world.getServer().runOnServer(server -> {
				long took = server.overworld().getGameTime() - began[0];
				check(Expedition.ground(server, Grounds.DAM).built(), "built, in " + took + " ticks from setting out (the count is " + Expedition.musterTicks + ")");
				ServerPlayer player = player(server);
				check(player.blockPosition().equals(DamGround.STARTS.getFirst()), "the party comes in by the hut: " + player.blockPosition());
				// the test looks at the place, not the fight: what lives there leaves a creative hunter be
				player.setGameMode(GameType.CREATIVE);
				// counted while the ground is still held loaded about the party coming in
				// stocked: the creatures, stronger than outside, the guards elites; what is to be found
				AABB all = AABB.of(new DamGround().bounds());
				ServerLevel dam = server.getLevel(Grounds.DAM);
				List<CrystalLizard> lizards = dam.getEntitiesOfClass(CrystalLizard.class, all);
				check(lizards.size() == DamGround.LIZARDS.size() + DamGround.GUARDS.size(), "the lizards lie in wait: " + lizards.size());
				check(lizards.stream().allMatch(l -> l.getMaxHealth() >= 23.0F && l.isPersistenceRequired()), "stronger than outside, and they stay");
				List<CrystalLizard> guards = lizards.stream().filter(EliteService::isElite).toList();
				check(guards.size() == DamGround.GUARDS.size(), "the control room's guards are elites: " + guards.size());
				check(dam.getEntitiesOfClass(CrystalDrake.class, all).size() == DamGround.DRAKES.size(), "drakes keep the crest");
				CrystalLizard fallen = guards.getFirst();
				fallen.kill(dam);
				check(fallen.isDeadOrDying(), "an elite lizard, ticked a while, falls like any other");
				for (BlockPos at : List.of(DamGround.CRATES.getFirst(), DamGround.LOCKERS.getFirst(), DamGround.SAFE, DamGround.HUT_BARREL)) {
					life.shizune.mistinn.expedition.Cache cache = dam.getBlockEntity(at) == null ? null
						: dam.getBlockEntity(at).getAttached(life.shizune.mistinn.expedition.Cache.CACHE);
					check(cache != null && !cache.entries().isEmpty() && cache.next() == 0, "something to be found (by searching) at " + at);
				}
			});
			context.waitTicks(100);
			world.getServer().runOnServer(server -> {
				ServerLevel dam = server.getLevel(Grounds.DAM);
				BoundingBox box = new DamGround().bounds();
				int flowing = 0;
				BlockPos first = null;
				for (int x = box.minX(); x <= box.maxX(); x++) {
					for (int z = box.minZ(); z <= box.maxZ(); z++) {
						for (int y = DamGround.BED - 4; y <= DamGround.WATER + 1; y++) {
							FluidState fluid = dam.getFluidState(new BlockPos(x, y, z));
							if (!fluid.isEmpty() && !fluid.isSource()) {
								flowing++;
								first = first == null ? new BlockPos(x, y, z) : first;
							}
						}
					}
				}
				check(flowing == 0, "the water stands still: " + flowing + " flowing, first at " + first);
				Walk walk = walk(dam, DamGround.STARTS.getFirst(), box);
				check(walk.leak == null, "nowhere walks or falls off the edge: " + walk.leak);
				check(walk.reached.contains(DamGround.SPILLWAY.centre().asLong()), "the spillway's way out is reached on foot");
				check(walk.reached.contains(DamGround.CONTROL.asLong()), "so is the control room");
				check(walk.reached.contains(DamGround.TOWER.centre().asLong()), "and the tower's roof, the other way out (" + walk.reached.size() + " places)");
			});
			// a wave at the spillway: lizards come up out of the ground a little way off
			world.getServer().runOnServer(server -> player(server).teleportTo(server.getLevel(Grounds.DAM), 0.5, DamGround.BED + 1.0, 24.5,
				Set.of(), 0.0F, 0.0F, true));
			context.waitTicks(60);
			world.getServer().runOnServer(server -> {
				ServerLevel dam = server.getLevel(Grounds.DAM);
				AABB about = new AABB(DamGround.SPILLWAY.centre()).inflate(16.0);
				int before = dam.getEntitiesOfClass(CrystalLizard.class, about).size();
				new DamGround().wave(dam, DamGround.SPILLWAY, 1, dam.getRandom());
				List<CrystalLizard> after = dam.getEntitiesOfClass(CrystalLizard.class, about);
				check(after.size() - before == 3, "the second wave is three lizards: " + before + " then " + after.size());
				check(after.stream().noneMatch(l -> DamGround.SPILLWAY.holds(l)), "none of them comes up in the way out itself");
				check(after.stream().allMatch(l -> l.getMaxHealth() >= 23.0F), "and they are as strong as the rest");
				// nothing in the pictures but the place
				for (net.minecraft.world.entity.Entity entity : dam.getEntities((net.minecraft.world.entity.Entity) null, AABB.of(new DamGround().bounds()),
					e -> !(e instanceof ServerPlayer))) {
					entity.discard();
				}
			});

			// pictures along the way, with nothing in the way of them
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			look(context, world, new BlockPos(10, 44, 132), new BlockPos(0, 80, 0), "dam-riverbed");
			look(context, world, new BlockPos(16, 43, 40), new BlockPos(0, 78, 4), "dam-face");
			look(context, world, new BlockPos(3, 42, 18), new BlockPos(0, 44, 2), "dam-spillway");
			look(context, world, new BlockPos(-24, 56, -3), new BlockPos(24, 57, -3), "dam-corridor");
			look(context, world, new BlockPos(7, 88, -3), new BlockPos(-5, 89, -10), "dam-control");
			look(context, world, new BlockPos(-40, 109, -8), new BlockPos(20, 108, -8), "dam-crest");
			look(context, world, new BlockPos(0, 110, -5), new BlockPos(0, 41, 90), "dam-valley");
			look(context, world, new BlockPos(3, 119, -46), new BlockPos(0, 108, -8), "dam-tower");
			look(context, world, new BlockPos(30, 120, -70), new BlockPos(0, 104, -20), "dam-reservoir");
			context.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			world.getServer().runOnServer(server -> {
				player(server).setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				player(server).removeAttached(Expedition.AWAY);
			});
		}
	}

	/** Where a hunter's feet can be got to on foot (and swimming) from {@code start}; and the first place that falls off the world, if any. */
	private record Walk(Set<Long> reached, BlockPos leak) {
	}

	private static Walk walk(final ServerLevel level, final BlockPos start, final BoundingBox box) {
		Set<Long> reached = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		reached.add(start.asLong());
		queue.add(start);
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			boolean swimming = !level.getFluidState(at).isEmpty();
			for (Direction way : Direction.Plane.HORIZONTAL) {
				BlockPos next = at.relative(way);
				BlockPos to = null;
				if (stand(level, next)) {
					to = next;
				} else if (stand(level, next.above()) && clear(level, at.above(2))) {
					to = next.above();
				} else if (clear(level, next) && clear(level, next.above())) {
					// off an edge: down until something holds them up
					BlockPos down = next;
					while (down.getY() > box.minY() && !stand(level, down)) {
						down = down.below();
					}
					if (!stand(level, down)) {
						return new Walk(reached, next);
					}
					to = down;
				}
				if (to != null) {
					if (!box.isInside(to)) {
						return new Walk(reached, to);
					}
					if (reached.add(to.asLong())) {
						queue.add(to);
					}
				}
			}
			if (swimming && stand(level, at.above()) && reached.add(at.above().asLong())) {
				queue.add(at.above());
			}
		}
		return new Walk(reached, null);
	}

	private static boolean clear(final ServerLevel level, final BlockPos at) {
		return level.getBlockState(at).getCollisionShape(level, at).isEmpty();
	}

	/** Whether a hunter's feet can be here: room for them, and ground under them (or water about them). */
	private static boolean stand(final ServerLevel level, final BlockPos at) {
		return clear(level, at) && clear(level, at.above())
			&& (!clear(level, at.below()) || !level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty());
	}

	private static void look(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos from, final BlockPos at, final String name) {
		world.getServer().runOnServer(server -> {
			double dx = at.getX() - from.getX();
			double dz = at.getZ() - from.getZ();
			double dy = at.getY() + 0.5 - (from.getY() + 1.62);
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
			ServerPlayer player = player(server);
			player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
			player.teleportTo(server.getLevel(Grounds.DAM), from.getX() + 0.5, from.getY(), from.getZ() + 0.5, Set.of(), yaw, pitch, true);
		});
		context.waitTicks(60);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("dam")));
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn dam] ok: " + what);
	}
}
