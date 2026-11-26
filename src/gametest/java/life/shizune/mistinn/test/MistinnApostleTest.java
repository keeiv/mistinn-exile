package life.shizune.mistinn.test;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.achievement.ApostleService;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.skill.Stat;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 鏽錨使徒: hidden until earned; never in an ordinary world, nor for a hunter who has died; in a Rust
 * Anchor world the last keeper's fall, with no death, makes the apostle: the salt, the mark on their
 * own client, two more of every stat past the cap, and a little less from every blow. A hunter who
 * arrives already the anchor's is known for it again.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnApostleTest implements FabricClientGameTest {
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
			context.waitTicks(5);

			// hidden on the tab until it is earned
			world.getServer().runOnServer(server -> {
				AdvancementHolder holder = server.getAdvancements().get(Mistinn.id("hunt/apostle"));
				check(holder != null && holder.value().display().orElseThrow().hidden(), "the apostle is a hidden achievement");
			});

			// an ordinary world: all three keepers down is not enough
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				AchievementService.grant(player, Achievement.SNUFFER);
				AchievementService.grant(player, Achievement.SUNKEN_BELL);
				AchievementService.grant(player, Achievement.RUST_WHEEL);
				check(!AchievementService.has(player, Achievement.APOSTLE) && !ApostleService.is(player), "no apostle in an ordinary world");
			});

			// a Rust Anchor world, but the hunter has died in it once
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				server.overworld().setAttached(Hardship.WORLD, Hardship.RUST_ANCHOR);
				player.getStats().setValue(player, Stats.CUSTOM.get(Stats.DEATHS), 1);
				again(server, player);
				check(!AchievementService.has(player, Achievement.APOSTLE), "a hunter who has died is no apostle");
			});

			// never died: the last keeper's fall makes the apostle
			int[] before = new int[3];
			float[] torso = new float[1];
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.getStats().setValue(player, Stats.CUSTOM.get(Stats.DEATHS), 0);
				before[0] = SkillService.stat(player, Stat.STRENGTH);
				before[1] = SaltService.get(player);
				before[2] = (int) Math.round(player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1000.0);
				again(server, player);
				check(AchievementService.has(player, Achievement.APOSTLE) && ApostleService.is(player), "the Rust Anchor's apostle");
				check(SaltService.get(player) - before[1] == Achievement.RUST_WHEEL.reward + Achievement.APOSTLE.reward,
					"the keeper's salt and the apostle's 5000: " + (SaltService.get(player) - before[1]));
				check(SkillService.stat(player, Stat.STRENGTH) == before[0] + ApostleService.BLESSING, "two more strength than was bought");
				check(Math.round(player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1000.0) > before[2], "and the blows hit harder for it");
				torso[0] = BodyService.get(player).get(BodyPart.TORSO);
				player.setInvulnerableTime(0);
				player.hurtServer(server.overworld(), player.damageSources().generic(), 4.0F);
				float lost = torso[0] - BodyService.get(player).get(BodyPart.TORSO);
				float expected = 4.0F * BodyService.DAMAGE_SCALE * Hardship.RUST_ANCHOR.damageTaken * ApostleService.TAKEN;
				check(Math.abs(lost - expected) < 0.01F, "a 4-point hit takes " + expected + " from an apostle's torso: " + lost);
				BodyService.restoreAll(player);
			});
			context.waitTicks(5);
			check(context.computeOnClient(mc -> ApostleService.is(mc.player) && SkillService.stat(mc.player, Stat.SPEED) >= ApostleService.BLESSING),
				"the hunter's own client knows the blessing");
			context.waitTicks(20);
			context.takeScreenshot("mistinn-apostle");

			// arriving again: the mark comes back from the advancement
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.removeAttached(ApostleService.APOSTLE);
				AchievementService.joined(player);
				check(ApostleService.is(player), "an apostle arriving is known for one again");
			});
		}
	}

	/** Takes the last keeper's achievement back and earns it again, as its fall would. */
	private static void again(final MinecraftServer server, final ServerPlayer player) {
		AdvancementHolder wheel = server.getAdvancements().get(Mistinn.id("hunt/" + AchievementService.RUST_WHEEL));
		for (String criterion : wheel.value().criteria().keySet()) {
			player.getAdvancements().revoke(wheel, criterion);
		}
		AchievementService.grant(player, Achievement.RUST_WHEEL);
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
