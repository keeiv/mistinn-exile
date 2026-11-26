package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.bounty.BountyService;
import life.shizune.mistinn.elite.EliteService;
import life.shizune.mistinn.salt.SaltService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

/**
 * Bounties: a morning weighed as a bounty morning posts one, marked on the hunter's minimap; the
 * creature is out when the hunter comes near (three affixes, twice the life); brought down by the
 * hunter, it pays, earns the achievement and clears the board and the mark; one left standing is
 * withdrawn after three days.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnBountyTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set day");
			world.getServer().runOnServer(server -> {
				player(server).addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4, false, false));
				BountyService.certain = true;
			});
			context.waitTicks(45);

			// a morning weighed: a bounty posted far off, and the hunter's minimap shows it
			BlockPos[] at = new BlockPos[1];
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				BountyService.Board board = BountyService.board(server);
				check(board.bounty().isPresent(), "a bounty is posted at dawn");
				BountyService.Bounty bounty = board.bounty().get();
				double distance = Math.hypot(bounty.at().getX() - player.getX(), bounty.at().getZ() - player.getZ());
				check(distance >= BountyService.NEAR - 2 && distance <= BountyService.FAR + 2, "a long walk off: " + distance);
				check(bounty.affixes().size() == 3 && bounty.creature().isEmpty(), "three affixes, and nothing there yet");
				check(bounty.at().equals(player.getAttached(BountyService.MARK)), "the hunter's minimap marks it");
				at[0] = bounty.at();
			});
			context.waitTicks(5);
			check(context.computeOnClient(mc -> at[0].equals(mc.player.getAttached(BountyService.MARK))), "the client knows where");

			// walking there brings it out
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.teleportTo(server.overworld(), at[0].getX() + 20.5, 120.0, at[0].getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0, false, false));
			});
			Mob[] wanted = new Mob[1];
			for (int i = 0; i < 30 && wanted[0] == null; i++) {
				context.waitTicks(10);
				wanted[0] = world.getServer().computeOnServer(server -> {
					List<Mob> found = server.overworld().getEntitiesOfClass(Mob.class, new AABB(at[0]).inflate(8.0, 400.0, 8.0),
						m -> m.getAttachedOrElse(BountyService.WANTED, false));
					return found.isEmpty() ? null : found.getFirst();
				});
			}
			check(wanted[0] != null, "near enough, the bounty is out");
			world.getServer().runOnServer(server -> {
				Mob mob = wanted[0];
				check(EliteService.affixes(mob).size() == 3, "an elite of three affixes");
				check(mob.getMaxHealth() >= 59.0F, "with twice an elite's life: " + mob.getMaxHealth());
				check(BountyService.board(server).bounty().orElseThrow().creature().isPresent(), "the board knows it is out");
			});

			// brought down: paid, remembered, and the board and the mark cleared
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				int before = SaltService.get(player);
				wanted[0].hurtServer(level, level.damageSources().playerAttack(player), 10000.0F);
				check(!wanted[0].isAlive(), "the bounty falls");
				check(SaltService.get(player) - before >= BountyService.REWARD, "the hunter is paid: " + (SaltService.get(player) - before));
				check(AchievementService.has(player, Achievement.BOUNTY), "a bounty hunter");
				check(BountyService.board(server).bounty().isEmpty(), "the board is clear");
			});
			context.waitTicks(25);
			check(world.getServer().computeOnServer(server -> player(server).getAttached(BountyService.MARK) == null), "and the mark gone");

			// another, left standing three days: withdrawn
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				BountyService.post(server.overworld(), player.blockPosition().offset(200, 0, 0));
				BountyService.age(server, BountyService.LASTS + 20);
			});
			context.waitTicks(25);
			check(world.getServer().computeOnServer(server -> BountyService.board(server).bounty().isEmpty()), "after three days the bounty is withdrawn");
		} finally {
			BountyService.certain = false;
		}
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
	}
}
