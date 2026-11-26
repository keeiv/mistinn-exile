package life.shizune.mistinn.team;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

/**
 * Hunters play together, three at most. Each one gets a colour of their own (a scoreboard team, so
 * the name tag, the chat and the player list all show it, and the minimap's dot matches), hunters
 * do not hurt each other unless the host turns that back on, and every hunter is told where the
 * others are.
 */
public final class TeamService {
	/** Hunters a world takes at once: the station's rules and its balance are made for three. */
	public static final int MAX_HUNTERS = 3;
	/** The hunters' colours, in the order they are handed out. */
	public static final List<TeamColor> PALETTE = List.of(TeamColor.AQUA, TeamColor.GREEN, TeamColor.LIGHT_PURPLE,
		TeamColor.YELLOW, TeamColor.GOLD, TeamColor.BLUE);
	/** Scoreboard teams this mod owns are named this, then the colour's index. */
	public static final String TEAM_PREFIX = "mistinn_hunter_";
	/** Ticks between the reports of where everyone is. */
	public static final int REPORT_TICKS = 10;

	/** Whether this world has had friendly fire turned off once; after that the host's choice stands. */
	public static final AttachmentType<Boolean> PVP_SETTLED = AttachmentRegistry.<Boolean>builder()
		.persistent(Codec.BOOL)
		.buildAndRegister(Mistinn.id("pvp_settled"));

	/** Lowered by tests to try the limit with the one hunter a test world has. */
	public static int limit = MAX_HUNTERS;

	private TeamService() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TeamPayload.TYPE, TeamPayload.CODEC);
		ServerLifecycleEvents.SERVER_STARTED.register(TeamService::settlePvp);
		ServerPlayerEvents.JOIN.register(player -> {
			assignColour(player);
			TeamSkills.joined(player);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % REPORT_TICKS == 0) {
				report(server);
			}
		});
	}

	// ── How many ──────────────────────────────────────────────────

	/** Whether a login is turned away: the world is full, and the one logging in is not already here. */
	public static boolean refuses(final int online, final boolean alreadyHere) {
		return !alreadyHere && online >= limit;
	}

	/** What a hunter turned away by {@link #refuses} reads. */
	public static Component fullReason() {
		return Component.translatableWithFallback("room.mistinn.login.full",
			"房間已滿，最多三位追獵者。 / The room is full: three hunters at most.", MAX_HUNTERS);
	}

	// ── Friendly fire ─────────────────────────────────────────────

	/** Turns friendly fire off the first time a world is loaded with this mod, old worlds as well; never again after that. */
	public static void settlePvp(final MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		if (overworld.getAttachedOrElse(PVP_SETTLED, false)) {
			return;
		}
		overworld.getGameRules().set(GameRules.PVP, false, server);
		overworld.setAttached(PVP_SETTLED, true);
	}

	// ── Colours ───────────────────────────────────────────────────

	/**
	 * Puts the hunter in a colour no one else here wears. A hunter who comes back keeps the colour
	 * they had, unless someone here has it now; a hunter the host put in a team of their own is left there.
	 */
	public static void assignColour(final ServerPlayer player) {
		Scoreboard scoreboard = player.level().getServer().getScoreboard();
		String name = player.getScoreboardName();
		PlayerTeam current = scoreboard.getPlayersTeam(name);
		if (current != null && !current.getName().startsWith(TEAM_PREFIX)) {
			return;
		}
		if (current != null && !takenByAnother(player, current.getName())) {
			return;
		}
		for (int i = 0; i < PALETTE.size(); i++) {
			String team = TEAM_PREFIX + i;
			if (takenByAnother(player, team)) {
				continue;
			}
			PlayerTeam colour = scoreboard.getPlayerTeam(team);
			if (colour == null) {
				colour = scoreboard.addPlayerTeam(team);
			}
			colour.setColor(Optional.of(PALETTE.get(i)));
			scoreboard.addPlayerToTeam(name, colour);
			return;
		}
	}

	private static boolean takenByAnother(final ServerPlayer player, final String team) {
		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			if (other != player && other.getTeam() != null && other.getTeam().getName().equals(team)) {
				return true;
			}
		}
		return false;
	}

	/** The hunter's colour as RGB: their team's, or white without one. */
	public static int colour(final Player player) {
		PlayerTeam team = player.getTeam();
		return team != null && team.getColor().isPresent() ? team.getColor().get().rgb() : 0xFFFFFF;
	}

	// ── Where everyone is ─────────────────────────────────────────

	/** Everyone but {@code viewer}: what their minimap shows. */
	public static List<TeamPayload.Member> others(final MinecraftServer server, final ServerPlayer viewer) {
		List<TeamPayload.Member> members = new ArrayList<>();
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other == viewer || other.isSpectator()) {
				continue;
			}
			members.add(new TeamPayload.Member(other.getUUID(), other.getScoreboardName(), colour(other),
				other.level().dimension().identifier(), other.getX(), other.getZ(), other.getYRot(), false));
		}
		return members;
	}

	private static void report(final MinecraftServer server) {
		// sent even to a hunter alone, so a minimap forgets those who have left
		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(viewer, TeamPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, new TeamPayload(others(server, viewer)));
			}
		}
	}
}
