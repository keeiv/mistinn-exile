package life.shizune.mistinn.test;

import com.mojang.authlib.GameProfile;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.skill.SkillTree;
import life.shizune.mistinn.skill.Skills;
import life.shizune.mistinn.team.TeamService;
import life.shizune.mistinn.team.TeamSkills;
import life.shizune.mistinn.team.UpdateNotice;
import life.shizune.mistinn.weapon.HeavyService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

/** Hunters together: three at most, a colour each, no friendly fire unless the host wants it, and major skills shared. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnTeamTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		limitAsWritten();
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty easy");
			context.waitTicks(10);
			world.getServer().runOnServer(MistinnTeamTest::friendlyFireIsOffUntilTheHostSaysSo);
			world.getServer().runOnServer(MistinnTeamTest::eachHunterHasAColour);
			world.getServer().runOnServer(MistinnTeamTest::majorSkillsAreTheTeams);
			world.getServer().runOnServer(MistinnTeamTest::anOldWorldsSkillsAreShared);
			world.getServer().runOnServer(MistinnTeamTest::theFourthIsTurnedAway);
			world.getServer().runOnServer(MistinnTeamTest::oldHuntersReadWhatChangedOnce);
		} finally {
			TeamService.limit = TeamService.MAX_HUNTERS;
		}
	}

	private static void limitAsWritten() {
		check(TeamService.MAX_HUNTERS == 3, "three hunters at most");
		check(!TeamService.refuses(0, false) && !TeamService.refuses(2, false), "the first three come in");
		check(TeamService.refuses(3, false), "the fourth is turned away");
		check(!TeamService.refuses(3, true), "one already here logging in again is not a fourth");
	}

	private static void friendlyFireIsOffUntilTheHostSaysSo(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		Mate mate = new Mate(level);
		check(!level.getGameRules().get(GameRules.PVP), "a world starts with friendly fire off");
		check(!player.canHarmPlayer(mate), "a hunter's light hit cannot hurt a teammate");
		check(!HeavyService.attackable(player, mate), "nor a heavy blow, the rim's roll or the lantern's burst");
		level.getGameRules().set(GameRules.PVP, true, server);
		check(player.canHarmPlayer(mate) && HeavyService.attackable(player, mate), "with friendly fire on, the same teammate can be hit");
		TeamService.settlePvp(server);
		check(level.getGameRules().get(GameRules.PVP), "the host's choice stands: the world is not set back again");
		level.getGameRules().set(GameRules.PVP, false, server);
	}

	private static void eachHunterHasAColour(final MinecraftServer server) {
		ServerPlayer player = player(server);
		check(player.getTeam() != null && player.getTeam().getName().equals(TeamService.TEAM_PREFIX + "0"), "the first hunter wears the first colour");
		check(TeamService.colour(player) == TeamService.PALETTE.getFirst().rgb(), "and the minimap draws them in it");
		TeamService.assignColour(player);
		check(player.getTeam().getName().equals(TeamService.TEAM_PREFIX + "0"), "a hunter keeps their colour when it is still free");
		check(TeamService.others(server, player).isEmpty(), "a hunter alone sees no one else on the minimap");
	}

	private static void majorSkillsAreTheTeams(final MinecraftServer server) {
		ServerPlayer player = player(server);
		TeamSkills.clear(server);
		SkillService.set(player, Skills.EMPTY);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.RUST_GOD_SALT));
		check(SkillService.unlock(player, SkillTree.ROOT) == SkillService.Result.UNLOCKED, "the slide is learned");
		check(TeamSkills.majors(server).contains(SkillTree.ROOT), "the team keeps it");
		player.setAttached(MistinnAttachments.SALT, 1000);
		check(SkillService.unlock(player, "strength_1") == SkillService.Result.UNLOCKED, "a minor node is learned");
		check(!TeamSkills.majors(server).contains("strength_1"), "a minor skill stays the hunter's own");
		SkillService.set(player, Skills.EMPTY);
		TeamSkills.joined(player);
		check(SkillService.get(player).has(SkillTree.ROOT), "a hunter who arrives learns the team's major skills");
		check(!SkillService.get(player).has("strength_1"), "but not another's minor ones");
	}

	private static void anOldWorldsSkillsAreShared(final MinecraftServer server) {
		ServerPlayer player = player(server);
		TeamSkills.clear(server);
		SkillService.set(player, new Skills(Set.of(SkillTree.ROOT, "speed_1")));
		TeamSkills.joined(player);
		check(TeamSkills.majors(server).equals(Set.of(SkillTree.ROOT)), "a slide learned before skills were shared becomes the team's: " + TeamSkills.majors(server));
	}

	private static void theFourthIsTurnedAway(final MinecraftServer server) {
		ServerPlayer player = player(server);
		InetSocketAddress address = new InetSocketAddress("127.0.0.1", 25565);
		NameAndId stranger = new NameAndId(UUID.nameUUIDFromBytes("stranger".getBytes()), "stranger");
		check(server.getPlayerList().canPlayerLogin(address, stranger) == null, "with room to spare a hunter comes in");
		TeamService.limit = 1;
		Component refusal = server.getPlayerList().canPlayerLogin(address, stranger);
		check(refusal != null && refusal.getString().contains("3"), "a full world turns the next away, saying how many it takes: " + refusal);
		// (the world's owner logging in twice is refused by the game itself, for the name; just not as a fourth hunter)
		Component again = server.getPlayerList().canPlayerLogin(address, new NameAndId(player.getUUID(), player.getScoreboardName()));
		check(again == null || !again.getString().equals(TeamService.fullReason().getString()), "a hunter already here is not counted as a fourth: " + again);
		TeamService.limit = TeamService.MAX_HUNTERS;
	}

	private static void oldHuntersReadWhatChangedOnce(final MinecraftServer server) {
		ServerPlayer player = player(server);
		check(player.getAttached(UpdateNotice.SEEN) != null && player.getAttached(UpdateNotice.SEEN).contains("coop"),
			"a hunter new to the world has nothing to unlearn: every notice counts as read");
		player.removeAttached(UpdateNotice.SEEN);
		UpdateNotice.joined(player);
		check(player.getAttached(UpdateNotice.SEEN).equals(List.of("coop")), "one who played before reads the notice once");
		UpdateNotice.joined(player);
		check(player.getAttached(UpdateNotice.SEEN).equals(List.of("coop")), "and not again");
	}

	/** A second hunter for the friendly-fire checks: Fabric's fake player is never hurt at all, this one can be. */
	private static final class Mate extends FakePlayer {
		Mate(final ServerLevel level) {
			super(level, new GameProfile(UUID.nameUUIDFromBytes("mate".getBytes()), "mate"));
		}

		@Override
		public boolean isInvulnerableTo(final ServerLevel level, final DamageSource source) {
			return false;
		}
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
