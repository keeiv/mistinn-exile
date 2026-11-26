package life.shizune.mistinn.test;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.story.StoryProgress;
import life.shizune.mistinn.story.StoryScript;
import life.shizune.mistinn.story.StoryService;
import life.shizune.mistinn.story.StoryStep;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The story is the team's: one hunter's objective done moves every hunter on and plays the same
 * scene for them (a far one hears the lines, and the card waits until they are out of a fight); a
 * hunter left behind is brought up to the team on arrival. And an achievement is told in the chat,
 * as the game tells its own advancements, unless the game rule says not to.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnTeamStoryTest implements FabricClientGameTest {
	private static final List<Component> HEARD = new CopyOnWriteArrayList<>();
	private static volatile boolean listening;
	private static boolean registered;
	private static Mate mate;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (!registered) {
			registered = true;
			ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
				if (listening) {
					HEARD.add(message);
				}
			});
		}
		Function<MinecraftServer, List<ServerPlayer>> everyone = StoryService.hunters;
		WorldMode.choose(WorldMode.STORY);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("difficulty normal");
			context.waitTicks(5);
			world.getServer().runOnServer(MistinnTeamStoryTest::achievementsAreToldInTheChat);
			world.getServer().runOnServer(MistinnTeamStoryTest::aTeammateComesAlong);
			context.waitTicks(30);
			world.getServer().runOnServer(MistinnTeamStoryTest::theTeamMovesTogether);
			context.waitTicks(10);
			world.getServer().runOnServer(MistinnTeamStoryTest::theCardCameWhenCalm);
		} finally {
			StoryService.hunters = everyone;
			listening = false;
		}
	}

	private static void achievementsAreToldInTheChat(final MinecraftServer server) {
		ServerPlayer player = player(server);
		HEARD.clear();
		listening = true;
		AchievementService.grant(player, Achievement.BELL);
		check(HEARD.stream().anyMatch(MistinnTeamStoryTest::announcement), "earning an achievement is told in the chat: " + HEARD);
		HEARD.clear();
		server.overworld().getGameRules().set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, server);
		AchievementService.grant(player, Achievement.RESIDUE);
		check(HEARD.stream().noneMatch(MistinnTeamStoryTest::announcement), "unless the game rule silences advancement messages: " + HEARD);
		server.overworld().getGameRules().set(GameRules.SHOW_ADVANCEMENT_MESSAGES, true, server);
		listening = false;
	}

	private static boolean announcement(final Component message) {
		return message.getContents() instanceof TranslatableContents text && text.getKey().startsWith("chat.type.advancement.");
	}

	private static void aTeammateComesAlong(final MinecraftServer server) {
		ServerPlayer player = player(server);
		// the opening card would pause this single-player world: it counts as read
		player.setAttached(StoryService.PROGRESS, StoryService.get(player).open());
		mate = new Mate(server.overworld());
		mate.setPos(player.position());
		// long in the world: a hunter who just arrived is not yet counted calm
		mate.tickCount = 1000;
		mate.setAttached(StoryService.PROGRESS, StoryProgress.START);
		StoryService.hunters = s -> {
			List<ServerPlayer> all = new java.util.ArrayList<>(s.getPlayerList().getPlayers());
			all.add(mate);
			return all;
		};
		check(StoryService.get(player).step() == StoryStep.SALT, "the story starts at the first salt");
		SaltService.add(player, StoryStep.SALT_GOAL);
	}

	private static void theTeamMovesTogether(final MinecraftServer server) {
		ServerPlayer player = player(server);
		check(StoryService.get(player).step() == StoryStep.STATION, "the hunter with the salt moves on: " + StoryService.get(player).step());
		check(StoryService.get(mate).step() == StoryStep.STATION, "and the teammate with them: " + StoryService.get(mate).step());
		check(StoryService.teamStep(server) == StoryStep.STATION, "the team is at the station");
		// far off, the teammate hears the call, and the card after it waits for a calm moment
		mate.setPos(player.getX() + 400.0, player.getY(), player.getZ());
		check(StoryService.radioMended(player), "one hunter mends the radio");
		check(StoryService.get(mate).step() == StoryStep.LIGHTHOUSE, "both move on to the lighthouse");
		check(StoryService.get(mate).heard().contains(StoryScript.FIRST_CALL), "the teammate heard the first call too");
		check(StoryService.waiting(mate) == 1, "its card waits for the teammate far off: " + StoryService.waiting(mate));
		// a hunter left behind is brought up to the team when they come in
		mate.setAttached(StoryService.PROGRESS, StoryProgress.START);
		StoryService.catchUp(mate);
		check(StoryService.get(mate).step() == StoryStep.LIGHTHOUSE, "a hunter left behind catches up on arrival: " + StoryService.get(mate).step());
	}

	private static void theCardCameWhenCalm(final MinecraftServer server) {
		check(StoryService.waiting(mate) == 0, "with nothing after them, the waiting card is shown: " + StoryService.waiting(mate));
	}

	/** A second hunter for the team: not in the player list, so the story is told of them by the seam. */
	private static final class Mate extends FakePlayer {
		Mate(final ServerLevel level) {
			super(level, new GameProfile(UUID.nameUUIDFromBytes("story-mate".getBytes()), "mate"));
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
