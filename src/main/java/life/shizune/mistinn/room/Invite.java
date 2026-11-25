package life.shizune.mistinn.room;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * An invitation to a friend's room: where it is, the room's key and the mod version the host runs.
 * Written as {@code MIST-} and Crockford base32 in groups of five, so it survives being typed or
 * pasted into a chat. The launcher reads the same format.
 *
 * <p>Bytes (format 1): format, key (8), flags (bit 0: the host had {@code .e4mc.link} cut off),
 * mod version and host (each a length byte and UTF-8), port (2), and the first two bytes of the
 * SHA-256 of everything before them.
 */
public record Invite(String host, int port, byte[] key, String version) {
	/** How every invite starts. */
	public static final String PREFIX = "MIST";
	/** The relay's domain, left out of the code to keep it short. */
	public static final String RELAY_SUFFIX = ".e4mc.link";
	/** The port a host listens on unless it says otherwise. */
	public static final int DEFAULT_PORT = 25565;
	/** The room key's length in bytes. */
	public static final int KEY_LENGTH = 8;
	private static final int FORMAT = 1;
	private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

	/** Keeps the host lowercase and the key a copy. */
	public Invite {
		host = host.strip().toLowerCase(Locale.ROOT);
		key = key.clone();
	}

	@Override
	public byte[] key() {
		return this.key.clone();
	}

	/** {@code host:port} as the multiplayer screen and the login look it up. */
	public String address() {
		return address(this.host, this.port);
	}

	/** {@code host:port}, lowercase, with an IPv6 host in brackets. */
	public static String address(final String host, final int port) {
		String h = host.strip().toLowerCase(Locale.ROOT);
		if (h.indexOf(':') >= 0 && !h.startsWith("[")) {
			h = "[" + h + "]";
		}
		return h + ":" + port;
	}

	/** The text to hand to friends. */
	public String code() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(FORMAT);
		out.writeBytes(this.key);
		boolean relay = this.host.endsWith(RELAY_SUFFIX) && this.host.length() > RELAY_SUFFIX.length();
		out.write(relay ? 1 : 0);
		writeText(out, this.version);
		writeText(out, relay ? this.host.substring(0, this.host.length() - RELAY_SUFFIX.length()) : this.host);
		out.write(this.port >> 8 & 0xFF);
		out.write(this.port & 0xFF);
		byte[] body = out.toByteArray();
		byte[] sum = sha256(body);
		out.write(sum[0]);
		out.write(sum[1]);
		String text = base32(out.toByteArray());
		StringBuilder grouped = new StringBuilder(PREFIX);
		for (int i = 0; i < text.length(); i += 5) {
			grouped.append('-').append(text, i, Math.min(text.length(), i + 5));
		}
		return grouped.toString();
	}

	/** Reads an invite back, or null when the text is not one (or was cut or mistyped). */
	public static @Nullable Invite parse(final String text) {
		String clean = text.strip().toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
		if (!clean.startsWith(PREFIX)) {
			return null;
		}
		byte[] bytes = unbase32(clean.substring(PREFIX.length()));
		if (bytes == null || bytes.length < 1 + KEY_LENGTH + 1 + 1 + 1 + 2 + 2 || bytes[0] != FORMAT) {
			return null;
		}
		byte[] body = Arrays.copyOf(bytes, bytes.length - 2);
		byte[] sum = sha256(body);
		if (sum[0] != bytes[bytes.length - 2] || sum[1] != bytes[bytes.length - 1]) {
			return null;
		}
		int at = 1;
		byte[] key = Arrays.copyOfRange(body, at, at + KEY_LENGTH);
		at += KEY_LENGTH;
		boolean relay = (body[at++] & 1) != 0;
		int versionLength = body[at++] & 0xFF;
		if (at + versionLength + 1 > body.length) {
			return null;
		}
		String version = new String(body, at, versionLength, StandardCharsets.UTF_8);
		at += versionLength;
		int hostLength = body[at++] & 0xFF;
		if (hostLength == 0 || at + hostLength + 2 != body.length) {
			return null;
		}
		String host = new String(body, at, hostLength, StandardCharsets.UTF_8);
		at += hostLength;
		int port = (body[at] & 0xFF) << 8 | body[at + 1] & 0xFF;
		if (port == 0) {
			return null;
		}
		return new Invite(relay ? host + RELAY_SUFFIX : host, port, key, version);
	}

	private static void writeText(final ByteArrayOutputStream out, final String text) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		int length = Math.min(255, bytes.length);
		out.write(length);
		out.write(bytes, 0, length);
	}

	static byte[] sha256(final byte[] data) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(data);
		} catch (NoSuchAlgorithmException missing) {
			throw new IllegalStateException(missing);
		}
	}

	private static String base32(final byte[] data) {
		StringBuilder out = new StringBuilder();
		int buffer = 0;
		int bits = 0;
		for (byte b : data) {
			buffer = buffer << 8 | b & 0xFF;
			bits += 8;
			while (bits >= 5) {
				out.append(ALPHABET.charAt(buffer >> bits - 5 & 31));
				bits -= 5;
			}
		}
		if (bits > 0) {
			out.append(ALPHABET.charAt(buffer << 5 - bits & 31));
		}
		return out.toString();
	}

	private static byte @Nullable [] unbase32(final String text) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int buffer = 0;
		int bits = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == 'O') {
				c = '0';
			} else if (c == 'I' || c == 'L') {
				c = '1';
			}
			int value = ALPHABET.indexOf(c);
			if (value < 0) {
				return null;
			}
			buffer = buffer << 5 | value;
			bits += 5;
			if (bits >= 8) {
				out.write(buffer >> bits - 8 & 0xFF);
				bits -= 8;
			}
		}
		return out.toByteArray();
	}

	@Override
	public boolean equals(final Object other) {
		return other instanceof Invite invite && invite.host.equals(this.host) && invite.port == this.port
			&& Arrays.equals(invite.key, this.key) && invite.version.equals(this.version);
	}

	@Override
	public int hashCode() {
		return (this.host.hashCode() * 31 + this.port) * 31 + Arrays.hashCode(this.key);
	}

	@Override
	public String toString() {
		return "Invite[" + address() + ", " + this.version + "]";
	}
}
