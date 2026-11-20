package life.shizune.mistinn.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import life.shizune.mistinn.client.multiplayer.HostRoomScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

/** Exercises the actual pause menu, room screen and integrated server's TCP listener. */
public final class MistinnRoomTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel")) {
			return;
		}
		check(HostRoomScreen.parsePort("65536") == 0 && HostRoomScreen.parsePort("25565") == 25565, "the port stays in TCP range");
		check("example.net:25565".equals(HostRoomScreen.shareAddress("example.net", 25565)), "hostnames work");
		check("[2001:db8::1]:25565".equals(HostRoomScreen.shareAddress("2001:db8::1", 25565)), "IPv6 is bracketed");
		check(HostRoomScreen.shareAddress("example.net:1234", 25565) == null, "the address field takes a host, not a second port");
		InetAddress loopback = InetAddress.getLoopbackAddress();
		int port;
		try (ServerSocket listener = new ServerSocket(0, 1, loopback)) {
			port = listener.getLocalPort();
		} catch (IOException error) {
			throw new AssertionError("could not find a free port", error);
		}
		try (var world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			context.setScreen(() -> new PauseScreen(true));
			click(context, "room.mistinn.menu");
			context.waitForScreen(HostRoomScreen.class);
			context.runOnClient(mc -> {
				var field = mc.gui.screen().children().stream().filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
					.map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
				field.setValue(Integer.toString(port));
			});
			click(context, "room.mistinn.open");
			context.waitFor(mc -> mc.getSingleplayerServer().isPublished() && mc.getSingleplayerServer().getPort() == port, 100);
			try (Socket guest = new Socket()) {
				guest.connect(new InetSocketAddress(loopback, port), 2000);
			} catch (IOException error) {
				throw new AssertionError("the joined host port did not accept a connection", error);
			}
			context.takeScreenshot("multiplayer-host-room");
			click(context, "room.mistinn.close");
			context.waitFor(mc -> !mc.getSingleplayerServer().isPublished(), 100);
			boolean refused = false;
			try (Socket guest = new Socket()) {
				guest.connect(new InetSocketAddress(loopback, port), 1200);
			} catch (IOException closed) {
				refused = true;
			}
			check(refused, "closing the room stops the TCP listener");
			check(context.computeOnClient(mc -> mc.player != null && mc.player.isAlive()), "closing the room keeps the host's world open");
			context.setScreen(() -> null);
		}
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
