package life.shizune.mistinn.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.body.WoundService;
import life.shizune.mistinn.boss.BossMove;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.keeper.Keeper;
import life.shizune.mistinn.registry.MistinnEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Every move of every keeper, run at Rust Anchor's tempo against a hunter standing in front of it: each one ends,
 * and a move whose blow its own script deals (a leap's landing, a hook's pull) still lands where it lands at the
 * tempo it was made for.
 */
public final class MistinnFerocityKeeperTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel")) {
			return;
		}
		Hardship.choose(Hardship.RUST_ANCHOR);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule spawn_mobs false");
			BlockPos stage = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				player.setGameMode(GameType.SURVIVAL);
				ServerLevel level = server.overworld();
				int x = player.getBlockX() + 48;
				int z = player.getBlockZ() + 48;
				BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
				player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 3.5, Set.of(), 180.0F, 0.0F, true);
				return at;
			});
			context.waitTicks(40);
			List<String> problems = new ArrayList<>();
			for (EntityType<? extends Keeper> type : List.of(MistinnEntities.SNUFFER, MistinnEntities.SUNKEN_BELL, MistinnEntities.RUST_WHEEL)) {
				Map<String, Boolean> fierce = run(context, world, stage, type, Hardship.RUST_ANCHOR, false, problems);
				Map<String, Boolean> made = run(context, world, stage, type, Hardship.NORMAL, true, problems);
				for (Map.Entry<String, Boolean> entry : made.entrySet()) {
					if (entry.getValue() && !fierce.getOrDefault(entry.getKey(), false)) {
						problems.add(EntityType.getKey(type).getPath() + " " + entry.getKey() + " lands as made but misses at Rust Anchor's tempo");
					}
				}
				System.out.println("[mistinn ferocity] " + EntityType.getKey(type).getPath() + " scripted blows: made " + made + ", Rust Anchor " + fierce);
			}
			world.getServer().runOnServer(server -> server.overworld().setAttached(Hardship.WORLD, Hardship.RUST_ANCHOR));
			check(problems.isEmpty(), String.join("; ", problems));
		}
	}

	/**
	 * Runs each of a keeper's moves once in a world of this hardship, a fresh hunter in front of it each time. Returns,
	 * for the moves whose blow is scripted, whether it landed. Only those are run when {@code scriptedOnly}.
	 */
	private static Map<String, Boolean> run(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage,
		final EntityType<? extends Keeper> type, final Hardship hardship, final boolean scriptedOnly, final List<String> problems) {
		int[] id = new int[1];
		List<BossMove> moves = world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			level.setAttached(Hardship.WORLD, hardship);
			Keeper keeper = type.create(level, EntitySpawnReason.COMMAND);
			keeper.snapTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5, 0.0F, 0.0F);
			level.addFreshEntity(keeper);
			id[0] = keeper.getId();
			List<BossMove> out = new ArrayList<>();
			for (BossMove move : keeper.moves().moves()) {
				if (move.plannable() && move.kind() != BossMove.Kind.IDLE && (!scriptedOnly || scripted(move))) {
					out.add(move);
				}
			}
			return out;
		});
		context.waitTicks(5);
		Map<String, Boolean> landed = new LinkedHashMap<>();
		for (BossMove move : moves) {
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				BodyService.restoreAll(player);
				WoundService.clearAll(player);
				player.setHealth(player.getMaxHealth());
				player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 3.5, Set.of(), 180.0F, 0.0F, true);
				Keeper keeper = (Keeper) server.overworld().getEntity(id[0]);
				keeper.snapTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5, 0.0F, 0.0F);
				keeper.setDeltaMovement(0.0, 0.0, 0.0);
				keeper.perform(move.id());
			});
			boolean ended = false;
			boolean hit = false;
			for (int tick = 0; tick < 300 && !ended; tick++) {
				context.waitTick();
				Boolean[] state = world.getServer().computeOnServer(server -> {
					Keeper keeper = (Keeper) server.overworld().getEntity(id[0]);
					boolean over = keeper == null || !keeper.isAlive() || keeper.currentMove() != move;
					return new Boolean[] {over, BodyService.get(player(server)).total() < Body.FULL.total()};
				});
				ended = state[0];
				hit = state[1];
			}
			if (!ended) {
				problems.add(EntityType.getKey(type).getPath() + " " + move.id() + " never ends at " + hardship.id);
			}
			if (scripted(move)) {
				landed.put(move.id(), hit);
			}
		}
		world.getServer().runOnServer(server -> {
			var keeper = server.overworld().getEntity(id[0]);
			if (keeper != null) {
				keeper.discard();
			}
			BodyService.restoreAll(player(server));
			WoundService.clearAll(player(server));
		});
		context.waitTicks(5);
		return landed;
	}

	/** Whether the move's script deals one of its blows itself. */
	private static boolean scripted(final BossMove move) {
		return move.strikes().stream().anyMatch(strike -> strike.tick() == BossMove.Strike.SCRIPTED);
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
