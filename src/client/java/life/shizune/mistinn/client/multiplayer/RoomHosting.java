package life.shizune.mistinn.client.multiplayer;

import org.jspecify.annotations.Nullable;

/** A single-player world opened to friends as a room, with the key its invites carry. */
public interface RoomHosting {
	/** Opens the room on {@code port}; false when the port could not be taken. */
	boolean mistinn$publishRoom(int port);

	/** The open room's key, or null while the room is closed. */
	byte @Nullable [] mistinn$roomKey();

	/** A login that knows whether it is a guest coming in from outside this computer. */
	interface Login {
		/** Whether this login reached the host's world from outside: over TCP or through the relay. */
		boolean mistinn$isGuest();

		/** The name the player asked to log in with. */
		String mistinn$requestedName();

		/** Whether that name is the host's or already in the world (asked on the server thread). */
		boolean mistinn$nameTaken();
	}
}
