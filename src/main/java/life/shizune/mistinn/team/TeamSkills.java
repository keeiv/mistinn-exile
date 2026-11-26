package life.shizune.mistinn.team;

import com.mojang.serialization.Codec;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.skill.SkillTree;
import life.shizune.mistinn.skill.Skills;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Major skills belong to the whole team: a rust god salt spent by one hunter teaches every hunter
 * in the world, those who come later included. Minor skills and salt stay each hunter's own. The
 * world keeps the list; each hunter's own skills get a copy, so everything that asks a hunter's
 * skills works as before.
 */
public final class TeamSkills {
	/** The major nodes the team has learned, kept on the overworld. */
	public static final AttachmentType<List<String>> MAJORS = AttachmentRegistry.<List<String>>builder()
		.persistent(Codec.STRING.listOf())
		.buildAndRegister(Mistinn.id("team_majors"));

	private TeamSkills() {
	}

	public static Set<String> majors(final MinecraftServer server) {
		return new LinkedHashSet<>(server.overworld().getAttachedOrElse(MAJORS, List.of()));
	}

	private static void setMajors(final MinecraftServer server, final Set<String> majors) {
		server.overworld().setAttached(MAJORS, List.copyOf(majors));
	}

	/** Forgets what the team has learned (for tests). */
	public static void clear(final MinecraftServer server) {
		server.overworld().removeAttached(MAJORS);
	}

	/** After {@code learner} unlocks a major node: the team keeps it, and every other hunter here learns it too. */
	public static void share(final ServerPlayer learner, final String id) {
		MinecraftServer server = learner.level().getServer();
		Set<String> majors = majors(server);
		if (majors.add(id)) {
			setMajors(server, majors);
		}
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other != learner && teach(other, Set.of(id))) {
				other.sendSystemMessage(Component.translatable("message.mistinn.team.shared", learner.getDisplayName(),
					Component.translatable("skill.mistinn." + id)));
			}
		}
	}

	/**
	 * A hunter arrives: the team's major skills are theirs, and the ones they bring (an old world,
	 * from before skills were shared) become the team's, for everyone here.
	 */
	public static void joined(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Set<String> majors = majors(server);
		Set<String> brought = new LinkedHashSet<>();
		for (String id : SkillService.get(player).unlocked()) {
			SkillTree.Node node = SkillTree.node(id);
			if (node != null && node.major() && majors.add(id)) {
				brought.add(id);
			}
		}
		if (!brought.isEmpty()) {
			setMajors(server, majors);
			for (ServerPlayer other : server.getPlayerList().getPlayers()) {
				if (other != player && teach(other, brought)) {
					for (String id : brought) {
						other.sendSystemMessage(Component.translatable("message.mistinn.team.shared", player.getDisplayName(),
							Component.translatable("skill.mistinn." + id)));
					}
				}
			}
		}
		teach(player, majors);
	}

	/** Gives the hunter every node of {@code ids} they lack; true when there was any. */
	private static boolean teach(final ServerPlayer player, final Set<String> ids) {
		Skills skills = SkillService.get(player);
		Skills taught = skills;
		for (String id : ids) {
			if (!taught.has(id)) {
				taught = taught.with(id);
			}
		}
		if (taught == skills) {
			return false;
		}
		SkillService.set(player, taught);
		life.shizune.mistinn.achievement.AchievementService.learned(player);
		return true;
	}
}
