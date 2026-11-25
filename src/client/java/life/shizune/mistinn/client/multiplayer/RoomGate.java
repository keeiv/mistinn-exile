package life.shizune.mistinn.client.multiplayer;

import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import life.shizune.mistinn.client.mixin.HandshakeServerDataAccessor;
import life.shizune.mistinn.room.Invite;
import life.shizune.mistinn.room.RoomKey;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.jspecify.annotations.Nullable;

/**
 * The door of a room: every guest is asked for the room key during login, and answers with the
 * invite they joined with ({@link RoomKey}). Strangers, closed rooms, old invites and clients
 * without the mod each get a plain reason.
 */
public final class RoomGate {
	private static final Map<ServerLoginPacketListenerImpl, byte[]> CHALLENGES = Collections.synchronizedMap(new WeakHashMap<>());

	private RoomGate() {
	}

	/** Registers both sides: the host's question and the guest's answer. */
	public static void register() {
		ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
			if (!((RoomHosting.Login) handler).mistinn$isGuest()) {
				return;
			}
			RoomHosting.Login login = (RoomHosting.Login) handler;
			if (login.mistinn$nameTaken()) {
				handler.disconnect(reason("same_name", login.mistinn$requestedName()));
				return;
			}
			if (key(server) == null) {
				handler.disconnect(reason("closed"));
				return;
			}
			byte[] challenge = RoomKey.challenge();
			CHALLENGES.put(handler, challenge);
			FriendlyByteBuf question = new FriendlyByteBuf(Unpooled.buffer());
			question.writeBytes(challenge);
			sender.sendPacket(RoomKey.CHANNEL, question);
		});
		ServerLoginNetworking.registerGlobalReceiver(RoomKey.CHANNEL, (server, handler, understood, buf, synchronizer, responseSender) -> {
			byte[] challenge = CHALLENGES.remove(handler);
			if (challenge == null) {
				return;
			}
			String problem = check(key(server), challenge, ((RoomHosting.Login) handler).mistinn$requestedName(), understood, buf);
			if (problem != null) {
				handler.disconnect(reason(problem));
			}
		});
		ClientLoginNetworking.registerGlobalReceiver(RoomKey.CHANNEL, (client, handler, buf, listenerAdder) -> {
			FriendlyByteBuf answer = new FriendlyByteBuf(Unpooled.buffer());
			if (buf.readableBytes() >= RoomKey.CHALLENGE_LENGTH) {
				byte[] challenge = new byte[RoomKey.CHALLENGE_LENGTH];
				buf.readBytes(challenge);
				ServerData target = ((HandshakeServerDataAccessor) handler).mistinn$serverData();
				@Nullable Invite invite = target == null ? null : Invites.forAddress(target.ip);
				if (invite != null) {
					answer.writeBytes(RoomKey.answer(invite.key(), challenge, client.getUser().getName()));
				}
			}
			return CompletableFuture.completedFuture(answer);
		});
	}

	/**
	 * What is wrong with a guest's answer, as a reason key, or null when they may come in.
	 *
	 * @param key the open room's key, null when the room has been closed meanwhile
	 * @param understood whether the guest's client knew the question at all (it has the mod)
	 */
	public static @Nullable String check(final byte @Nullable [] key, final byte[] challenge, final String name, final boolean understood, final FriendlyByteBuf buf) {
		if (!understood) {
			return "no_mod";
		}
		if (key == null) {
			return "closed";
		}
		if (buf.readableBytes() == 0) {
			return "no_invite";
		}
		byte[] answer = new byte[Math.min(64, buf.readableBytes())];
		buf.readBytes(answer);
		return RoomKey.accepts(key, challenge, name, answer) ? null : "wrong_invite";
	}

	private static byte @Nullable [] key(final MinecraftServer server) {
		return server instanceof RoomHosting room ? room.mistinn$roomKey() : null;
	}

	/** A login refusal: a guest with the mod reads it in their language, one without it in both. */
	public static Component reason(final String key, final Object... args) {
		String fallback = switch (key) {
			case "no_mod" -> "需要霧宿模組才能加入。 / The Mistinn mod is needed to join.";
			case "same_name" -> "房裡已經有這個名字的人了。 / Someone with this name is already here.";
			case "closed" -> "房間已經關閉。 / The room has been closed.";
			default -> "請用房主給你的邀請碼加入。 / Join with the invite code from the host.";
		};
		return Component.translatableWithFallback("room.mistinn.login." + key, fallback, args);
	}
}
