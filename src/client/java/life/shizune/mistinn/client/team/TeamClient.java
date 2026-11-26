package life.shizune.mistinn.client.team;

import java.util.List;
import life.shizune.mistinn.team.TeamPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The other hunters as the server last reported them: who, their colour, and where. */
public final class TeamClient {
	private static volatile List<TeamPayload.Member> members = List.of();

	private TeamClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(TeamPayload.TYPE, (payload, context) -> members = List.copyOf(payload.members()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> members = List.of());
	}

	public static List<TeamPayload.Member> members() {
		return members;
	}
}
