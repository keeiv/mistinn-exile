package life.shizune.mistinn.room;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import life.shizune.mistinn.Mistinn;
import net.minecraft.resources.Identifier;

/**
 * The room's key check at login: the host sends a random challenge, a guest who holds the invite
 * answers with an HMAC of it and their name. Nobody without the invite gets in, whatever account
 * they use.
 */
public final class RoomKey {
	/** The login query's channel. */
	public static final Identifier CHANNEL = Mistinn.id("room_key");
	/** The challenge's length in bytes. */
	public static final int CHALLENGE_LENGTH = 16;
	private static final SecureRandom RANDOM = new SecureRandom();

	private RoomKey() {
	}

	/** A new room key. */
	public static byte[] newKey() {
		byte[] key = new byte[Invite.KEY_LENGTH];
		RANDOM.nextBytes(key);
		return key;
	}

	/** A new login challenge. */
	public static byte[] challenge() {
		byte[] challenge = new byte[CHALLENGE_LENGTH];
		RANDOM.nextBytes(challenge);
		return challenge;
	}

	/** The answer a guest holding {@code key} gives to {@code challenge} when logging in as {@code name}. */
	public static byte[] answer(final byte[] key, final byte[] challenge, final String name) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			mac.update(challenge);
			mac.update(name.getBytes(StandardCharsets.UTF_8));
			return mac.doFinal();
		} catch (GeneralSecurityException broken) {
			throw new IllegalStateException(broken);
		}
	}

	/** Whether {@code answer} is the right one, compared in constant time. */
	public static boolean accepts(final byte[] key, final byte[] challenge, final String name, final byte[] answer) {
		return MessageDigest.isEqual(answer(key, challenge, name), answer);
	}
}
