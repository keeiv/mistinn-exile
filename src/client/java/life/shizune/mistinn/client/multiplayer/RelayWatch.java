package life.shizune.mistinn.client.multiplayer;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.room.Invite;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

/**
 * Follows the relay that makes a room reachable from anywhere. The relay mod (e4mc) connects by
 * itself whenever the world starts listening, and writes the public address it was given to chat;
 * this reads that address from the line, shows our own line with the invite instead, and gives up
 * after a while so the room screen can offer a direct address.
 */
public final class RelayWatch {
	/** How long the relay may take to hand out an address. */
	public static final long PATIENCE_MS = 25_000;
	private static final String ASSIGNED = "text.e4mc_minecraft.domainAssigned";
	private static final String ERROR = "text.e4mc_minecraft.error";
	private static volatile State state = State.OFF;
	private static volatile @Nullable String domain;
	private static volatile long since;

	/** Where the relay is. */
	public enum State {
		/** No room, or no relay mod. */
		OFF,
		/** The room is open and the relay has not answered yet. */
		WAITING,
		/** The relay gave the room a public address. */
		READY,
		/** The relay failed or took too long. */
		FAILED
	}

	private RelayWatch() {
	}

	/** Whether the relay mod is installed. */
	public static boolean available() {
		return FabricLoader.getInstance().isModLoaded("e4mc");
	}

	/** The room just opened: wait for the relay's address. */
	public static void opened() {
		domain = null;
		since = System.currentTimeMillis();
		state = available() ? State.WAITING : State.OFF;
	}

	/** The room closed: stop the relay too (closing the listener alone leaves it connected). */
	public static void closed() {
		state = State.OFF;
		domain = null;
		stopRelay();
	}

	/** The relay's state, which turns to failed once the wait is over. */
	public static State state() {
		if (state == State.WAITING && System.currentTimeMillis() - since > PATIENCE_MS) {
			state = State.FAILED;
		}
		return state;
	}

	/** The room's public address through the relay, once ready. */
	public static @Nullable String domain() {
		return state() == State.READY ? domain : null;
	}

	/**
	 * Reads a line the client is about to put in chat. Returns true when it is the relay's address,
	 * which our own line replaces (the relay's has a stop link the room screen would not know of).
	 */
	public static boolean onChat(final Component message) {
		if (!(message.getContents() instanceof TranslatableContents text)) {
			return false;
		}
		if (ERROR.equals(text.getKey())) {
			if (state == State.WAITING) {
				state = State.FAILED;
			}
			return false;
		}
		if (!ASSIGNED.equals(text.getKey()) || text.getArgs().length == 0) {
			return false;
		}
		Object arg = text.getArgs()[0];
		String found = (arg instanceof Component component ? component.getString() : String.valueOf(arg)).strip();
		if (found.isEmpty() || found.indexOf('.') < 0 || found.indexOf(' ') >= 0) {
			// The relay was told to hide its address: nothing to build an invite from.
			state = State.FAILED;
			return false;
		}
		domain = found;
		state = State.READY;
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.gui.screen() instanceof HostRoomScreen)) {
			Invite invite = HostRoomScreen.currentInvite(mc);
			if (invite != null) {
				announce(mc, invite);
			}
		}
		return true;
	}

	/** Puts the invite in chat, ready to copy with a click. */
	public static void announce(final Minecraft mc, final Invite invite) {
		String code = invite.code();
		Component link = Component.literal(code).withStyle(style -> style.withColor(ChatFormatting.GOLD).withUnderlined(true)
			.withClickEvent(new ClickEvent.CopyToClipboard(code))
			.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.copy.click"))));
		mc.gui.hud.getChat().addClientSystemMessage(Component.translatable("room.mistinn.chat_invite", link));
	}

	private static void stopRelay() {
		if (!available()) {
			return;
		}
		try {
			var field = Class.forName("link.e4mc.E4mcClient").getField("session");
			Object session = field.get(null);
			if (session != null) {
				session.getClass().getMethod("stop").invoke(session);
				field.set(null, null);
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError unknown) {
			Mistinn.LOGGER.warn("Could not stop the relay", unknown);
		}
	}
}
