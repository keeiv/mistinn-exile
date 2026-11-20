package life.shizune.mistinn.client.multiplayer;

import io.netty.util.AttributeKey;

/** Carries the room's account policy from its TCP listener to the guest login. */
public interface RoomHosting {
	AttributeKey<Boolean> VERIFIED_GUEST = AttributeKey.valueOf("mistinn:verified_guest");

	boolean mistinn$publishRoom(int port);

	boolean mistinn$isPublishingRoom();

	interface Login {
		boolean mistinn$requiresVerifiedGuest();
	}
}
