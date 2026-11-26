package life.shizune.mistinn.test;

import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.client.tide.TideClient;
import life.shizune.mistinn.elite.EliteService;
import life.shizune.mistinn.tide.TideService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * The fog tide: a night weighed as a tide night raises it (the hunter there a witness, their client
 * drawing the fog in, elites twice as likely); creatures walk out of the fog round the hunter, but
 * not with mob spawning turned off; at dawn it ebbs and the witness has come through it.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnTideTest implements FabricClientGameTest {
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
			world.getServer().runCommand("time set midnight");
			world.getServer().runOnServer(server -> {
				player(server).addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4, false, false));
				TideService.certain = true;
			});
			context.waitTicks(45);

			// the night was weighed and the tide is up, the hunter there a witness
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(TideService.up(server), "the fog tide is up");
				check(TideService.tide(server).witnesses().contains(player.getUUID()), "the hunter was there when it rose");
				check(player.getAttachedOrElse(TideService.FOG, false), "and is under it");
				ServerLevel level = server.overworld();
				float chance = EliteService.chance(level, level.getRespawnData().pos());
				check(Math.abs(chance - 0.10F) < 1.0E-4F, "elites twice as likely by night: " + chance);
			});
			context.waitTicks(220);
			check(context.computeOnClient(mc -> TideClient.haze() > 0.9F), "the fog has rolled in on the hunter's screen: " + context.computeOnClient(mc -> TideClient.haze()));
			world.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ ~180 0");
			context.waitTicks(5);
			context.takeScreenshot("mistinn-fog-tide");

			// with mob spawning off nothing walks out of it; with it on, creatures do
			int[] crowd = new int[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				crowd[0] = hostile(level, player(server));
				for (int i = 0; i < 5; i++) {
					TideService.waves(level);
				}
				check(hostile(level, player(server)) == crowd[0], "nothing walks out with mob spawning off");
			});
			world.getServer().runCommand("gamerule spawn_mobs true");
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				int before = hostile(level, player(server));
				for (int i = 0; i < 5; i++) {
					TideService.waves(level);
				}
				int after = hostile(level, player(server));
				check(after > before, "creatures walk out of the fog: " + before + " to " + after);
			});
			world.getServer().runCommand("gamerule spawn_mobs false");

			// dawn: the fog ebbs and the witness has come through
			world.getServer().runCommand("time set day");
			context.waitTicks(45);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(!TideService.up(server), "at dawn the tide is down");
				check(!player.getAttachedOrElse(TideService.FOG, false), "and the hunter is out from under it");
				check(AchievementService.has(player, Achievement.TIDE), "having come through the fog tide");
			});
			context.waitTicks(40);
			check(context.computeOnClient(mc -> TideClient.haze() < 0.9F), "and the fog ebbs on screen");
		} finally {
			TideService.certain = false;
		}
	}

	/** Hostile creatures within 48 blocks of the hunter. */
	private static int hostile(final ServerLevel level, final ServerPlayer player) {
		return level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48.0), mob -> mob instanceof Enemy && mob.isAlive()).size();
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
