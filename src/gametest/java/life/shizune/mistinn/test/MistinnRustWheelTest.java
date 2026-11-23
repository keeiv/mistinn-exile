package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.boss.BossMove;
import life.shizune.mistinn.boss.Planner;
import life.shizune.mistinn.boss.PlayerAction;
import life.shizune.mistinn.boss.PlayerModel;
import life.shizune.mistinn.keeper.RustWheel;
import life.shizune.mistinn.keeper.WheelMoves;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.world.Builder;
import life.shizune.mistinn.world.SaltWorks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 鏽輪 and its salt works: the yard is built with its pillars, bell and stores; its iron front turns most of a blow aside
 * and its back takes more; a run into a pillar leaves it stuck, and a full heavy blow then staggers it at once; it turns
 * slowly, and a hunter who keeps to its back gets the chain; below half it breaks loose, its front softens, and its slam
 * spills brine that slows; it falls and leaves the Rust God's salt.
 */
public class MistinnRustWheelTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		movesKeepFaith();
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("time set noon");
			BlockPos centre = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = server.overworld();
				BlockPos floor = player.blockPosition().offset(0, -1, 40);
				BlockPos middle = SaltWorks.build(new Builder(level, floor, RandomSource.create(7L)));
				player.setGameMode(GameType.SURVIVAL);
				player.getAbilities().invulnerable = true;
				return middle;
			});
			context.waitTicks(10);
			theYardStands(world, centre);
			photographed(context, world, centre);
			frontAndBack(world, centre);
			runIntoAPillar(context, world, centre);
			runsOverAHunter(context, world, centre);
			turnsSlowlyAndLashesItsBack(context, world, centre);
			breaksLooseAtHalf(context, world, centre);
			fallsAndLeavesItsSalt(context, world, centre);
		}
	}

	// ── The move set ──────────────────────────────────────────────

	private static void movesKeepFaith() {
		int feints = 0;
		for (BossMove move : WheelMoves.SET.moves()) {
			if (move.kind() == BossMove.Kind.REACTION) {
				continue;
			}
			check(move.tell() != null, move.id() + " has a sound for its tell");
			check(move.windup() >= move.weight().minWindup, move.id() + " winds up for at least " + move.weight().minWindup + " ticks, has " + move.windup());
			if (move.kind() == BossMove.Kind.FEINT) {
				feints++;
				BossMove real = WheelMoves.SET.get(move.imitates());
				check(move.tell() == real.tell() && move.windup() == real.windup(), move.id() + " shows " + real.id() + "'s tell and length");
				check(WheelMoves.SET.links().stream().anyMatch(link -> link.from().equals(move.id())), move.id() + " turns into something");
			}
		}
		check(feints >= 1, "the wheel has its feint");
		check(WheelMoves.CIRCUIT.in(RustWheel.BROKEN) && !WheelMoves.CIRCUIT.in(RustWheel.WHOLE), "only the broken wheel runs the circuit");
		check(WheelMoves.SET.get("stuck") != null && WheelMoves.SET.get("stagger") != null, "it can be stuck and staggered");
		// what the planner may choose keeps to that: the circuit once broken, the back roll only out of a ram
		check(!Planner.allowedIn(WheelMoves.CIRCUIT, RustWheel.WHOLE) && Planner.allowedIn(WheelMoves.CIRCUIT, RustWheel.BROKEN),
			"the planner keeps the circuit for the broken wheel");
		Planner planner = new Planner(WheelMoves.SET, RandomSource.create(9L));
		Vec3[] hunters = {new Vec3(0.0, 0.0, -3.0), new Vec3(0.0, 0.0, 3.0), new Vec3(0.0, 0.0, -8.0), new Vec3(6.0, 0.0, 6.0)};
		java.util.List<String> loneRoll = null;
		java.util.List<String> circuit = null;
		for (int i = 0; i < 200; i++) {
			Planner.Plan plan = planner.plan(new Planner.Situation(Vec3.ZERO, 0.0F, RustWheel.WHOLE, java.util.Map.of(), null, 0, hunters[i % hunters.length],
				Vec3.ZERO, PlayerAction.IDLE, 100.0F, new PlayerModel(), null, 0, Vec3.ZERO, SaltWorks.YARD), 3_000_000L);
			java.util.List<String> ids = plan.steps().stream().map(BossMove::id).toList();
			for (int s = 0; s < ids.size(); s++) {
				if ("back_roll".equals(ids.get(s)) && (s == 0 || !"ram".equals(ids.get(s - 1)))) {
					loneRoll = ids;
				}
				if ("circuit".equals(ids.get(s))) {
					circuit = ids;
				}
			}
		}
		check(loneRoll == null, "it rolls back only out of a ram: " + loneRoll);
		check(circuit == null, "the whole wheel never plans the circuit: " + circuit);
	}

	// ── The salt works ────────────────────────────────────────────

	private static void theYardStands(final TestSingleplayerContext world, final BlockPos centre) {
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			for (int[] pillar : SaltWorks.PILLARS) {
				check(level.getBlockState(centre.offset(pillar[0], 1, pillar[1])).isSolidRender(), "a pillar stands at " + pillar[0] + ", " + pillar[1]);
			}
			check(level.getBlockState(centre.offset(0, 1, SaltWorks.YARD + 1)).isAir(), "the gate is open to the south");
			boolean bell = false;
			boolean chest = false;
			for (BlockPos at : BlockPos.betweenClosed(centre.offset(-14, -2, SaltWorks.YARD), centre.offset(14, 3, SaltWorks.YARD + 12))) {
				bell |= level.getBlockState(at).is(MistinnBlocks.GOLDEN_BELL);
				chest |= level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.CHEST);
			}
			check(bell, "a golden bell stands outside the gate");
			check(chest, "the boiling house keeps its stores");
			check(wheel(server, centre) != null, "the Rust Wheel stands in its yard");
		});
	}

	private static void photographed(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos centre) {
		world.getServer().runOnServer(server -> {
			RustWheel wheel = wheel(server, centre);
			wheel.setYRot(0.0F);
			wheel.setYBodyRot(0.0F);
			// in front of it and to one side, looking at its iron front and the rim
			player(server).setGameMode(GameType.SPECTATOR);
			player(server).teleportTo(server.overworld(), centre.getX() + 5.5, centre.getY() + 1.5, centre.getZ() + 5.5, Set.of(), 135.0F, 15.0F, true);
		});
		world.getConnection().waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("wheel-00-front");
		// behind it and to the other side: the yoke and the man chained across it
		world.getServer().runOnServer(server -> player(server).teleportTo(server.overworld(), centre.getX() - 3.5, centre.getY() + 1.5, centre.getZ() - 3.5, Set.of(),
			-45.0F, 20.0F, true));
		context.waitTicks(20);
		context.takeScreenshot("wheel-01-back");
		world.getServer().runOnServer(server -> {
			player(server).setGameMode(GameType.SURVIVAL);
			player(server).getAbilities().invulnerable = true;
		});
	}

	// ── Its sides ─────────────────────────────────────────────────

	private static void frontAndBack(final TestSingleplayerContext world, final BlockPos centre) {
		world.getServer().runOnServer(server -> {
			RustWheel wheel = wheel(server, centre);
			wheel.setYRot(0.0F);
			Vec3 at = wheel.position();
			// facing yaw 0 is south, +z
			check(Math.abs(wheel.struckFrom(at.add(0.0, 0.0, 4.0)) - RustWheel.FRONT_WHOLE) < 1.0E-6, "a blow on its iron front does a third of its weight");
			check(Math.abs(wheel.struckFrom(at.add(0.0, 0.0, -4.0)) - RustWheel.BACK) < 1.0E-6, "a blow on its back does half again");
			check(Math.abs(wheel.struckFrom(at.add(4.0, 0.0, 0.0)) - 1.0F) < 1.0E-6, "a blow on its side does its weight");
			check(RustWheel.behind(wheel, at.add(0.0, 0.0, -4.0)) && !RustWheel.behind(wheel, at.add(0.0, 0.0, 4.0)), "it knows its back from its front");
		});
	}

	// ── Stuck ─────────────────────────────────────────────────────

	/** Run at a hunter with a pillar between them, it runs into the pillar and is stuck; a heavy blow then staggers it at once. */
	private static void runIntoAPillar(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos centre) {
		world.getServer().runOnServer(server -> {
			RustWheel wheel = wheel(server, centre);
			wheel.teleportTo(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
			// beyond the north-east pillar, on the line from the middle through it
			int[] pillar = SaltWorks.PILLARS[0];
			player(server).teleportTo(server.overworld(), centre.getX() + pillar[0] * 1.3 + 0.5, centre.getY(), centre.getZ() + pillar[1] * 1.3 + 0.5, Set.of(), 0.0F, 0.0F,
				true);
			wheel.setTarget(player(server));
		});
		context.waitTicks(5);
		world.getServer().runOnServer(server -> {
			// facing the pillar too, so the run goes that way whoever it has in mind
			face(wheel(server, centre), -45.0F);
			wheel(server, centre).perform("ram");
		});
		boolean stuck = false;
		for (int i = 0; i < 30 && !stuck; i++) {
			context.waitTicks(3);
			stuck = world.getServer().computeOnServer(server -> wheel(server, centre).stuck());
		}
		check(stuck, "the run ends against the pillar, the wheel stuck there");
		world.getServer().runOnServer(server -> {
			player(server).setGameMode(GameType.SPECTATOR);
			player(server).teleportTo(server.overworld(), centre.getX() + 0.5, centre.getY() + 2.5, centre.getZ() + 0.5, Set.of(), -45.0F, 20.0F, true);
		});
		context.waitTicks(5);
		context.takeScreenshot("wheel-02-stuck");
		world.getServer().runOnServer(server -> {
			player(server).setGameMode(GameType.SURVIVAL);
			player(server).getAbilities().invulnerable = true;
		});
		world.getServer().runOnServer(server -> {
			RustWheel wheel = wheel(server, centre);
			check(wheel.momentum() == 0, "its momentum is gone with the crash");
			wheel.takeHeavyBlow(server.overworld());
			check(wheel.staggered(), "stuck, one full heavy blow throws it off balance");
		});
		context.waitTicks(RustWheel.STAGGER_TICKS + 10);
	}

	/** On open floor it runs over a hunter in its path and throws them aside. */
	private static void runsOverAHunter(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos centre) {
		float before = world.getServer().computeOnServer(server -> {
			RustWheel wheel = wheel(server, centre);
			wheel.teleportTo(centre.getX() + 0.5, centre.getY(), centre.getZ() - 6.5);
			player(server).teleportTo(server.overworld(), centre.getX() + 0.5, centre.getY(), centre.getZ() + 1.5, Set.of(), 180.0F, 0.0F, true);
			BodyService.restoreAll(player(server));
			wheel.setTarget(player(server));
			return BodyService.get(player(server)).total();
		});
		context.waitTicks(5);
		String aimed = world.getServer().computeOnServer(server -> {
			player(server).getAbilities().invulnerable = false;
			// facing the hunter, to the south
			face(wheel(server, centre), 0.0F);
			wheel(server, centre).perform("ram");
			RustWheel w = wheel(server, centre);
			return "target " + w.getTarget() + ", brain " + w.brain().target() + ", hunts " + life.shizune.mistinn.boss.BossBrain.hunts(player(server))
				+ ", room " + w.room() + ", hunter " + player(server).position() + ", mode " + player(server).gameMode() + ", noAi " + w.isNoAi();
		});
		context.waitTicks(WheelMoves.RAM_SPIN + WheelMoves.RAM_ROLL + 5);
		float after = world.getServer().computeOnServer(server -> {
			player(server).getAbilities().invulnerable = true;
			return BodyService.get(player(server)).total();
		});
		String where = world.getServer().computeOnServer(server -> "wheel at " + wheel(server, centre).position() + ", hunter at " + player(server).position());
		check(after < before, "the wheel ran the hunter over: " + before + " -> " + after + "; " + aimed + "; " + where);
		System.out.println("[mistinn rust wheel] aim: " + aimed);
	}

	// ── Its back ──────────────────────────────────────────────────

	private static void turnsSlowlyAndLashesItsBack(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos centre) {
		// whatever it was still doing (a roll back out of the last ram) runs out first, with no one to hunt
		world.getServer().runOnServer(server -> player(server).setGameMode(GameType.SPECTATOR));
		for (int i = 0; i < 40 && world.getServer().computeOnServer(server -> wheel(server, centre).currentMove() != null); i++) {
			context.waitTicks(3);
		}
		float before = world.getServer().computeOnServer(server -> {
			player(server).setGameMode(GameType.SURVIVAL);
			RustWheel wheel = wheel(server, centre);
			wheel.teleportTo(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
			wheel.setYRot(0.0F);
			wheel.setYBodyRot(0.0F);
			wheel.setYHeadRot(0.0F);
			// right at its back, to the north
			player(server).teleportTo(server.overworld(), centre.getX() + 0.5, centre.getY(), centre.getZ() - 3.0, Set.of(), 0.0F, 0.0F, true);
			BodyService.restoreAll(player(server));
			player(server).getAbilities().invulnerable = false;
			return BodyService.get(player(server)).total();
		});
		context.waitTicks(10);
		float turned = world.getServer().computeOnServer(server -> Math.abs(net.minecraft.util.Mth.wrapDegrees(wheel(server, centre).getYRot())));
		check(turned <= 10.0F * RustWheel.TURN + 1.0F, "it comes round slowly: " + turned + " degrees in ten ticks");
		boolean lashed = false;
		java.util.List<String> seen = new java.util.ArrayList<>();
		for (int i = 0; i < 20 && !lashed; i++) {
			context.waitTicks(3);
			String now = world.getServer().computeOnServer(server -> {
				BossMove move = wheel(server, centre).currentMove();
				return move == null ? "-" : move.id();
			});
			seen.add(now);
			lashed = "chain_lash".equals(now);
		}
		check(lashed, "a hunter kept at its back gets the chain: " + seen);
		context.waitTicks(WheelMoves.CHAIN_LASH.length() + 2);
		float after = world.getServer().computeOnServer(server -> {
			player(server).getAbilities().invulnerable = true;
			return BodyService.get(player(server)).total();
		});
		check(after < before, "and the chain lands: " + before + " -> " + after);
	}

	// ── Broken ────────────────────────────────────────────────────

	private static void breaksLooseAtHalf(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos centre) {
		world.getServer().runOnServer(server -> {
			RustWheel wheel = wheel(server, centre);
			wheel.setHealth(wheel.getMaxHealth() * 0.49F);
			wheel.teleportTo(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
			player(server).teleportTo(server.overworld(), centre.getX() + 2.5, centre.getY(), centre.getZ() + 0.5, Set.of(), 90.0F, 0.0F, true);
		});
		context.waitTicks(5);
		world.getServer().runOnServer(server -> {
			RustWheel wheel = wheel(server, centre);
			check(wheel.broken(), "below half the man tears a chain loose");
			wheel.setYRot(0.0F);
			check(Math.abs(wheel.struckFrom(wheel.position().add(0.0, 0.0, 4.0)) - RustWheel.FRONT_BROKEN) < 1.0E-6, "and its front gives more");
			wheel.perform("hop_slam");
		});
		context.waitTicks(WheelMoves.HOP_AIR + 10);
		world.getServer().runOnServer(server -> check(!wheel(server, centre).brine().isEmpty(), "the broken wheel spills brine where it slams down"));
		world.getServer().runOnServer(server -> {
			Vec3 pool = wheel(server, centre).brine().getFirst().at();
			player(server).teleportTo(server.overworld(), pool.x, pool.y, pool.z, Set.of(), 0.0F, 0.0F, true);
		});
		context.waitTicks(10);
		check(world.getServer().computeOnServer(server -> player(server).hasEffect(MobEffects.SLOWNESS)), "wading in the brine slows a hunter");
		world.getServer().runOnServer(server -> player(server).teleportTo(server.overworld(), centre.getX() + 0.5, centre.getY(), centre.getZ() + 9.5, Set.of(), 180.0F,
			10.0F, true));
		context.waitTicks(20);
		context.takeScreenshot("wheel-03-broken");
	}

	private static void fallsAndLeavesItsSalt(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos centre) {
		world.getServer().runOnServer(server -> {
			RustWheel wheel = wheel(server, centre);
			wheel.setLastHurtByPlayer(player(server), 100);
			wheel.kill(server.overworld());
		});
		context.waitTicks(30);
		world.getServer().runOnServer(server -> {
			boolean salt = server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(centre).inflate(16.0)).stream()
				.anyMatch(item -> item.getItem().is(MistinnItems.RUST_GOD_SALT));
			check(salt, "it leaves the Rust God's salt");
		});
	}

	// ── Helpers ───────────────────────────────────────────────────

	private static void face(final RustWheel wheel, final float yaw) {
		wheel.setYRot(yaw);
		wheel.setYBodyRot(yaw);
		wheel.setYHeadRot(yaw);
	}

	private static RustWheel wheel(final MinecraftServer server, final BlockPos centre) {
		var found = server.overworld().getEntitiesOfClass(RustWheel.class, new AABB(centre).inflate(20.0));
		return found.isEmpty() ? null : found.getFirst();
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
		System.out.println("[mistinn rust wheel] ok: " + message);
	}
}
