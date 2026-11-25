package life.shizune.mistinn.test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.Inflater;
import life.shizune.mistinn.client.multiplayer.HostRoomScreen;
import life.shizune.mistinn.client.multiplayer.InviteJoinScreen;
import life.shizune.mistinn.client.multiplayer.Invites;
import life.shizune.mistinn.client.multiplayer.RelayWatch;
import life.shizune.mistinn.client.multiplayer.RoomHosting;
import life.shizune.mistinn.room.Invite;
import life.shizune.mistinn.room.RoomKey;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

/**
 * The room from both sides: the invite format, the actual pause menu and room screen, the
 * integrated server's TCP listener, and guests logging in over a raw connection with no invite,
 * a wrong one, a taken name, a client without the mod, and the right invite.
 */
public final class MistinnRoomTest implements FabricClientGameTest {
	private static final String KEY_CHANNEL = "mistinn:room_key";

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel")) {
			return;
		}
		checkAddresses();
		checkInvites();
		Invites.clear();
		// screenshots in Traditional Chinese, as players see the room
		if (!context.computeOnClient(mc -> mc.options.languageCode.equals("zh_tw"))) {
			boolean[] reloaded = {false};
			context.runOnClient(mc -> {
				mc.options.languageCode = "zh_tw";
				mc.getLanguageManager().setSelected("zh_tw");
				mc.reloadResourcePacks().thenRun(() -> reloaded[0] = true);
			});
			context.waitFor(mc -> reloaded[0], 1200);
			// the loading overlay fades out first, taking clicks meanwhile
			context.waitTicks(40);
		}
		InetAddress loopback = InetAddress.getLoopbackAddress();
		int port;
		try (ServerSocket listener = new ServerSocket(0, 1, loopback)) {
			port = listener.getLocalPort();
		} catch (IOException error) {
			throw new AssertionError("could not find a free port", error);
		}
		String hostName = context.computeOnClient(mc -> mc.getUser().getName());
		try (var world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.setScreen(() -> new PauseScreen(true));
			click(context, "room.mistinn.menu");
			context.waitForScreen(HostRoomScreen.class);
			boolean relay = context.computeOnClient(mc -> RelayWatch.available());
			if (!relay) {
				context.runOnClient(mc -> {
					var fields = mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();
					fields.get(0).setValue(Integer.toString(port));
					fields.get(1).setValue("127.0.0.1");
				});
			}
			click(context, "room.mistinn.open");
			try {
				context.waitFor(mc -> mc.getSingleplayerServer().isPublished(), 100);
			} catch (AssertionError notOpened) {
				context.takeScreenshot("multiplayer-room-not-opened");
				throw notOpened;
			}
			int opened = context.computeOnClient(mc -> mc.getSingleplayerServer().getPort());
			check(relay || opened == port, "a direct room listens on the typed port");
			if (relay) {
				// Only when the relay mod is in the test's mods folder: the room goes public through it.
				context.waitFor(mc -> RelayWatch.state() != RelayWatch.State.WAITING, 800);
				check(context.computeOnClient(mc -> RelayWatch.state()) == RelayWatch.State.READY, "the relay hands the room an address");
			}
			byte[] key = context.computeOnClient(mc -> ((RoomHosting) mc.getSingleplayerServer()).mistinn$roomKey());
			check(key != null && key.length == Invite.KEY_LENGTH, "an open room has a key");
			Invite invite = context.computeOnClient(HostRoomScreen::currentInvite);
			check(invite != null && Arrays.equals(invite.key(), key), "the invite carries the room's key: " + invite);
			check(relay ? invite.host().endsWith(Invite.RELAY_SUFFIX) : invite.address().equals("127.0.0.1:" + port),
				"the invite leads to the relay or the typed address: " + invite);
			click(context, "room.mistinn.copy");
			check(context.computeOnClient(mc -> mc.keyboardHandler.getClipboard()).equals(invite.code()), "copying puts the invite on the clipboard");
			context.waitTicks(2);
			String copyLabel = context.computeOnClient(mc -> Component.translatable("room.mistinn.copy").getString());
			check(context.computeOnClient(mc -> mc.gui.screen().children().stream()
				.filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast)
				.anyMatch(b -> b.getMessage().getString().equals(copyLabel) && b.active)), "the copy button stays usable while there is an invite");
			context.takeScreenshot(relay ? "multiplayer-host-relay" : "multiplayer-host-room");

			String host = invite.host();
			int to = invite.port();
			check(login(context, host, to, "Stranger", null, false).contains("room.mistinn.login.no_mod"), "a client without the mod is told it needs the mod");
			check(login(context, host, to, "Stranger", new byte[0], true).contains("room.mistinn.login.no_invite"), "a guest with no invite is asked for one");
			check(login(context, host, to, "Stranger", new byte[Invite.KEY_LENGTH], true).contains("room.mistinn.login.wrong_invite"),
				"a guest with another room's key is turned away");
			check(login(context, host, to, hostName, key, true).contains("room.mistinn.login.same_name"), "a guest cannot take the host's name");
			check(login(context, host, to, "Friend", key, true).equals("finished"), "a guest with the invite gets through the login, offline account and all");

			if (!relay) {
				check(context.computeOnClient(mc -> RelayWatch.onChat(
					Component.translatable("text.e4mc_minecraft.domainAssigned", Component.literal("quiet-otter.ap.e4mc.link"))))
					&& "quiet-otter.ap.e4mc.link".equals(context.computeOnClient(mc -> RelayWatch.domain())),
					"the relay's address is read from its chat line, which is kept out of chat");
				check(!context.computeOnClient(mc -> RelayWatch.onChat(Component.translatable("chat.type.text", "a", "b"))),
					"other chat lines pass through");
				context.runOnClient(mc -> RelayWatch.closed());
			}

			click(context, "room.mistinn.close");
			context.waitFor(mc -> !mc.getSingleplayerServer().isPublished(), 100);
			check(context.computeOnClient(mc -> ((RoomHosting) mc.getSingleplayerServer()).mistinn$roomKey()) == null, "a closed room forgets its key");
			check(context.computeOnClient(mc -> RelayWatch.state()) == RelayWatch.State.OFF, "closing the room lets go of the relay");
			boolean refused = false;
			try (Socket guest = new Socket()) {
				guest.connect(new InetSocketAddress(loopback, opened), 1200);
			} catch (IOException closed) {
				refused = true;
			}
			check(refused, "closing the room stops the TCP listener");
			check(context.computeOnClient(mc -> mc.player != null && mc.player.isAlive()), "closing the room keeps the host's world open");
			context.setScreen(() -> null);
		}
		checkJoinScreen(context);
		Invites.clear();
	}

	private static void checkAddresses() {
		check(HostRoomScreen.parsePort("65536") == 0 && HostRoomScreen.parsePort("25565") == 25565, "the port stays in TCP range");
		check("example.net:25565".equals(HostRoomScreen.shareAddress("example.net", 25565)), "hostnames work");
		check("[2001:db8::1]:25565".equals(HostRoomScreen.shareAddress("2001:db8::1", 25565)), "IPv6 is bracketed");
		check(HostRoomScreen.shareAddress("example.net:1234", 25565) == null, "the address field takes a host, not a second port");
	}

	private static void checkInvites() {
		byte[] key = {1, 2, 3, 4, 5, 6, 7, 8};
		Invite relay = new Invite("Quiet-Otter.ap.e4mc.link", 25565, key, "0.1.1");
		String code = relay.code();
		check(code.startsWith("MIST-") && Invite.parse(code) != null && Invite.parse(code).equals(relay), "a relay invite reads back: " + code);
		check(Invite.parse(code).host().equals("quiet-otter.ap.e4mc.link"), "the host is kept lowercase with the relay's domain");
		check(Invite.parse(code.toLowerCase().replace("-", " ")) != null, "case, spaces and dashes do not matter");
		Invite plain = new Invite("example.net", 25565, key, "0.1.1");
		check(code.length() < new Invite("quiet-otter.ap.e4mc.linx", 25565, key, "0.1.1").code().length(),
			"the relay's domain is left out of the code");
		char last = code.charAt(code.length() - 1);
		String typo = code.substring(0, code.length() - 1) + (last == 'A' ? 'B' : 'A');
		check(Invite.parse(typo) == null, "a mistyped invite is caught by its checksum");
		check(Invite.parse(code.substring(0, code.length() - 5)) == null, "a cut-off invite is not read");
		check(Invite.parse("MIST-HELLO") == null && Invite.parse("example.net:25565") == null, "other text is not an invite");
		Invite six = new Invite("[2001:db8::1]", 40000, key, "0.1.1");
		check(six.address().equals("[2001:db8::1]:40000") && six.equals(Invite.parse(six.code())), "an IPv6 host keeps its brackets in the address");
		check(plain.address().equals("example.net:25565"), "the address joins host and port");
		// The launcher reads the same codes: this one is checked there too (src-tauri/src/invite.rs).
		check(code.equals("MIST-040G4-0R40M-30E20-10MR2-WC9E6-4772X-B9CNT-2TVVM-EHJQ4-BK1E1-HXVJC-Y"),
			"the shared example invite is unchanged: " + code);
		byte[] challenge = new byte[RoomKey.CHALLENGE_LENGTH];
		byte[] answer = RoomKey.answer(key, challenge, "Friend");
		check(RoomKey.accepts(key, challenge, "Friend", answer) && !RoomKey.accepts(key, challenge, "Other", answer),
			"the room key's answer is bound to the name");
	}

	private static void checkJoinScreen(final ClientGameTestContext context) {
		context.setScreen(() -> new JoinMultiplayerScreen(net.minecraft.client.Minecraft.getInstance().gui.screen()));
		context.waitTicks(5);
		context.takeScreenshot("multiplayer-server-list");
		click(context, "room.mistinn.join.button");
		context.waitForScreen(InviteJoinScreen.class);
		String version = context.computeOnClient(mc -> Invites.modVersion());
		byte[] key = {9, 9, 9, 9, 9, 9, 9, 9};
		String older = new Invite("quiet-otter.ap.e4mc.link", 25565, key, "0.0.9").code();
		String same = new Invite("quiet-otter.ap.e4mc.link", 25565, key, version).code();
		setCode(context, older);
		check(context.computeOnClient(mc -> ((InviteJoinScreen) mc.gui.screen()).invite()) == null, "an invite from another version is not joined");
		context.takeScreenshot("multiplayer-join-version");
		setCode(context, "MIST-12345");
		check(context.computeOnClient(mc -> ((InviteJoinScreen) mc.gui.screen()).invite()) == null, "a broken invite is not joined");
		setCode(context, same);
		check(context.computeOnClient(mc -> ((InviteJoinScreen) mc.gui.screen()).invite()) != null, "a good invite can be joined");
		context.takeScreenshot("multiplayer-join-invite");
		context.setScreen(() -> null);
	}

	private static void setCode(final ClientGameTestContext context, final String code) {
		context.runOnClient(mc -> mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
			.findFirst().orElseThrow().setValue(code));
	}

	/** Runs {@link #login(String, int, String, byte[], boolean)} beside the game, which only ticks while the test waits. */
	private static String login(final ClientGameTestContext context, final String host, final int port, final String name,
		final byte[] key, final boolean understood) {
		var result = java.util.concurrent.CompletableFuture.supplyAsync(() -> login(host, port, name, key, understood));
		context.waitFor(mc -> result.isDone(), 400);
		return result.join();
	}

	/**
	 * Logs in to the room as a guest over a raw connection, answering the room's key question with
	 * {@code key}: null answers "not understood" (a client without the mod), an empty array answers
	 * with nothing. Returns "finished" once the login completes, or the bytes of the refusal.
	 */
	private static String login(final String host, final int port, final String name, final byte[] key, final boolean understood) {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(host, port), 5000);
			socket.setSoTimeout(15000);
			Wire wire = new Wire(socket.getInputStream(), socket.getOutputStream());
			ByteArrayOutputStream hello = new ByteArrayOutputStream();
			writeVarInt(hello, SharedConstants.getProtocolVersion());
			writeString(hello, host);
			hello.write(port >> 8 & 0xFF);
			hello.write(port & 0xFF);
			writeVarInt(hello, 2);
			wire.send(0x00, hello.toByteArray());
			ByteArrayOutputStream start = new ByteArrayOutputStream();
			writeString(start, name);
			UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
			for (long half : new long[] {id.getMostSignificantBits(), id.getLeastSignificantBits()}) {
				for (int shift = 56; shift >= 0; shift -= 8) {
					start.write((int) (half >> shift) & 0xFF);
				}
			}
			wire.send(0x00, start.toByteArray());
			StringBuilder seen = new StringBuilder();
			while (true) {
				byte[] packet;
				try {
					packet = wire.read();
				} catch (IOException broken) {
					throw new IOException("after packets [" + seen + "]", broken);
				}
				DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(packet));
				int type = readVarInt(in);
				seen.append(type).append(' ');
				switch (type) {
					case 0x00 -> {
						return new String(packet, StandardCharsets.ISO_8859_1);
					}
					case 0x01 -> throw new AssertionError("a guest was asked to prove a Microsoft account");
					case 0x02 -> {
						return "finished";
					}
					case 0x03 -> wire.threshold = readVarInt(in);
					case 0x04 -> {
						int transaction = readVarInt(in);
						String channel = readString(in);
						byte[] data = in.readAllBytes();
						ByteArrayOutputStream reply = new ByteArrayOutputStream();
						writeVarInt(reply, transaction);
						boolean ours = channel.equals(KEY_CHANNEL);
						if (ours && understood) {
							reply.write(1);
							if (key != null && key.length > 0) {
								byte[] challenge = Arrays.copyOf(data, RoomKey.CHALLENGE_LENGTH);
								reply.writeBytes(RoomKey.answer(key, challenge, name));
							}
						} else {
							reply.write(0);
						}
						wire.send(0x02, reply.toByteArray());
					}
					default -> {
						// cookies and the like: not part of this login
					}
				}
			}
		} catch (IOException error) {
			throw new AssertionError("the guest's login broke off for " + name, error);
		}
	}

	/** Length-prefixed packets, compressed once the server says so. */
	private static final class Wire {
		private final DataInputStream in;
		private final OutputStream out;
		private int threshold = -1;

		Wire(final InputStream in, final OutputStream out) {
			this.in = new DataInputStream(in);
			this.out = out;
		}

		void send(final int type, final byte[] body) throws IOException {
			ByteArrayOutputStream packet = new ByteArrayOutputStream();
			if (this.threshold >= 0) {
				writeVarInt(packet, 0);
			}
			writeVarInt(packet, type);
			packet.writeBytes(body);
			ByteArrayOutputStream frame = new ByteArrayOutputStream();
			writeVarInt(frame, packet.size());
			packet.writeTo(frame);
			this.out.write(frame.toByteArray());
			this.out.flush();
		}

		byte[] read() throws IOException {
			byte[] frame = new byte[readVarInt(this.in)];
			this.in.readFully(frame);
			if (this.threshold < 0) {
				return frame;
			}
			DataInputStream body = new DataInputStream(new java.io.ByteArrayInputStream(frame));
			int size = readVarInt(body);
			byte[] rest = body.readAllBytes();
			if (size == 0) {
				return rest;
			}
			Inflater inflater = new Inflater();
			inflater.setInput(rest);
			byte[] plain = new byte[size];
			try {
				inflater.inflate(plain);
			} catch (java.util.zip.DataFormatException broken) {
				throw new IOException(broken);
			} finally {
				inflater.end();
			}
			return plain;
		}
	}

	private static void writeVarInt(final ByteArrayOutputStream out, final int value) {
		int v = value;
		while ((v & ~0x7F) != 0) {
			out.write(v & 0x7F | 0x80);
			v >>>= 7;
		}
		out.write(v);
	}

	private static int readVarInt(final DataInputStream in) throws IOException {
		int value = 0;
		for (int shift = 0; shift < 35; shift += 7) {
			int b = in.readUnsignedByte();
			value |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				return value;
			}
		}
		throw new IOException("varint too long");
	}

	private static void writeString(final ByteArrayOutputStream out, final String text) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		writeVarInt(out, bytes.length);
		out.writeBytes(bytes);
	}

	private static String readString(final DataInputStream in) throws IOException {
		byte[] bytes = new byte[readVarInt(in)];
		in.readFully(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static void click(final ClientGameTestContext context, final String key) {
		context.clickScreenButton(context.computeOnClient(mc -> Component.translatable(key).getString()));
	}

	private static void check(final boolean result, final String message) {
		if (!result) {
			throw new AssertionError(message);
		}
	}
}
