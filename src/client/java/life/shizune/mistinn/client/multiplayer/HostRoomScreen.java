package life.shizune.mistinn.client.multiplayer;

import com.google.common.net.HostAndPort;
import java.util.concurrent.ThreadLocalRandom;
import life.shizune.mistinn.room.Invite;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

/**
 * Opens or closes the current world as a room and hands out its invite. With the relay mod the
 * room is reachable from anywhere with no router settings; without it, or when the relay fails,
 * the host types an address friends can reach and the invite carries that instead.
 */
public final class HostRoomScreen extends Screen {
	private static final int GOLD = 0xFFE8AF69;
	private static final int PAPER = 0xFFECE3D2;
	private static final int ASH = 0xFFB8B0A0;
	private static final int PANEL_HEIGHT = 206;
	private static String host = "";
	private static int port = Invite.DEFAULT_PORT;
	private final Screen previous;
	private final IntegratedServer server;
	private EditBox portField;
	private EditBox hostField;
	private Button roomButton;
	private Button copyButton;
	private int left;
	private int top;
	private int panelWidth;
	private @Nullable Component notice;

	public HostRoomScreen(final Screen previous, final IntegratedServer server) {
		super(Component.translatable("room.mistinn.title"));
		this.previous = previous;
		this.server = server;
	}

	/** Renames the pause menu's world-options button and adds the invite button to the server list. */
	public static void register() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof PauseScreen && minecraft.getSingleplayerServer() != null) {
				for (var widget : Screens.getWidgets(screen)) {
					if (widget.getMessage().getContents() instanceof TranslatableContents text
						&& text.getKey().equals("options.worldOptions.button")) {
						widget.setMessage(Component.translatable("room.mistinn.menu"));
					}
				}
			}
			if (screen instanceof JoinMultiplayerScreen) {
				Screens.getWidgets(screen).add(Button.builder(Component.translatable("room.mistinn.join.button"),
					button -> minecraft.gui.setScreen(new InviteJoinScreen(screen)))
					.bounds(width - 128, 6, 120, 20).build());
			}
		});
	}

	@Override
	protected void init() {
		this.panelWidth = Math.min(360, this.width - 16);
		this.left = (this.width - this.panelWidth) / 2;
		this.top = Math.max(4, (this.height - PANEL_HEIGHT) / 2);
		int x = this.left + 12;
		int inner = this.panelWidth - 24;
		int half = (inner - 6) / 2;
		this.portField = new EditBox(this.font, x, this.top + 92, 68, 20, Component.translatable("room.mistinn.port"));
		this.portField.setMaxLength(5);
		this.portField.setValue(Integer.toString(this.server.isPublished() ? this.server.getPort() : port));
		this.addRenderableWidget(this.portField);
		this.hostField = new EditBox(this.font, x + 76, this.top + 92, inner - 76, 20, Component.translatable("room.mistinn.address"));
		this.hostField.setMaxLength(253);
		this.hostField.setValue(host);
		this.hostField.setHint(Component.translatable("room.mistinn.address_hint").withColor(ASH));
		this.addRenderableWidget(this.hostField);
		this.roomButton = this.addRenderableWidget(Button.builder(CommonComponents.EMPTY, button -> toggleRoom())
			.bounds(x, this.top + 120, half, 20).build());
		this.copyButton = this.addRenderableWidget(Button.builder(Component.translatable("room.mistinn.copy"), button -> copy())
			.bounds(x + half + 6, this.top + 120, inner - half - 6, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("options.worldOptions.button"), button ->
			this.minecraft.gui.setScreen(new WorldOptionsScreen(this, this.minecraft.level)))
			.bounds(x, this.top + PANEL_HEIGHT - 28, half, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
			.bounds(x + half + 6, this.top + PANEL_HEIGHT - 28, inner - half - 6, 20).build());
		this.portField.setResponder(text -> {
			// kept across a resize, like the address
			if (!this.server.isPublished() && parsePort(text) > 0) {
				port = parsePort(text);
			}
			refresh();
		});
		this.hostField.setResponder(text -> {
			host = text;
			refresh();
		});
		refresh();
	}

	private boolean training() {
		return WorldMode.of(this.server) == WorldMode.TRAINING;
	}

	/** Whether the room is reached by a typed address: no relay mod, or the relay failed. */
	private static boolean direct() {
		return !RelayWatch.available() || RelayWatch.state() == RelayWatch.State.FAILED;
	}

	@Override
	public void tick() {
		refresh();
	}

	private void refresh() {
		boolean open = this.server.isPublished();
		boolean typed = direct();
		this.portField.visible = typed;
		this.hostField.visible = typed;
		if (open && !this.portField.getValue().equals(Integer.toString(this.server.getPort()))) {
			this.portField.setValue(Integer.toString(this.server.getPort()));
		}
		this.portField.setEditable(!open);
		this.roomButton.setMessage(Component.translatable(open ? "room.mistinn.close" : "room.mistinn.open"));
		this.roomButton.active = open || !training() && this.minecraft.allowsMultiplayer()
			&& (!typed || parsePort(this.portField.getValue()) > 0);
		this.copyButton.active = currentInvite(this.minecraft) != null;
	}

	private void toggleRoom() {
		if (this.server.isPublished()) {
			if (this.server.unpublishServer()) {
				this.notice = Component.translatable("room.mistinn.closed");
			}
		} else {
			if (training() || !this.minecraft.allowsMultiplayer()) {
				return;
			}
			int wanted = direct() ? parsePort(this.portField.getValue()) : Invite.DEFAULT_PORT;
			if (wanted == 0) {
				return;
			}
			RoomHosting room = (RoomHosting) this.server;
			boolean opened = room.mistinn$publishRoom(wanted);
			// Through the relay the port is never seen, so any free one will do.
			for (int tries = 0; !opened && !direct() && tries < 6; tries++) {
				opened = room.mistinn$publishRoom(ThreadLocalRandom.current().nextInt(49152, 65536));
			}
			if (opened) {
				RelayWatch.opened();
				this.notice = null;
			} else {
				this.notice = Component.translatable("room.mistinn.bind_error", wanted);
			}
		}
		refresh();
	}

	private void copy() {
		Invite invite = currentInvite(this.minecraft);
		if (invite != null) {
			this.minecraft.keyboardHandler.setClipboard(invite.code());
			this.notice = Component.translatable("room.mistinn.copied");
		}
	}

	/** The open room's invite: through the relay, or to the typed address; null while there is none yet. */
	public static @Nullable Invite currentInvite(final Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null || !server.isPublished() || !(server instanceof RoomHosting room)) {
			return null;
		}
		byte[] key = room.mistinn$roomKey();
		if (key == null) {
			return null;
		}
		if (direct()) {
			String address = shareAddress(host, server.getPort());
			if (address == null) {
				return null;
			}
			ServerAddress parsed = ServerAddress.parseString(address);
			return new Invite(parsed.getHost(), parsed.getPort(), key, Invites.modVersion());
		}
		String domain = RelayWatch.domain();
		return domain == null ? null : new Invite(domain, Invite.DEFAULT_PORT, key, Invites.modVersion());
	}

	public static int parsePort(final String text) {
		if (!text.matches("[0-9]{1,5}")) {
			return 0;
		}
		int value = Integer.parseInt(text);
		return value >= 1024 && value <= 65535 ? value : 0;
	}

	public static @Nullable String shareAddress(final String text, final int portNumber) {
		if (text.isBlank() || portNumber < 1024 || portNumber > 65535) {
			return null;
		}
		try {
			String address = HostAndPort.fromHost(text.strip()).withDefaultPort(portNumber).toString();
			return ServerAddress.isValidAddress(address) ? address : null;
		} catch (IllegalArgumentException invalid) {
			return null;
		}
	}

	private Component status() {
		if (!this.server.isPublished()) {
			return Component.translatable("room.mistinn.stopped");
		}
		return switch (RelayWatch.state()) {
			case WAITING -> Component.translatable("room.mistinn.relay_waiting");
			case READY -> Component.translatable("room.mistinn.running");
			default -> Component.translatable("room.mistinn.running_direct");
		};
	}

	private Component line() {
		if (training()) {
			return Component.translatable("room.mistinn.training");
		}
		if (!this.server.isPublished() && !this.minecraft.allowsMultiplayer()) {
			return Component.translatable("room.mistinn.unavailable");
		}
		if (this.notice != null) {
			return this.notice;
		}
		if (this.server.isPublished() && RelayWatch.available() && RelayWatch.state() == RelayWatch.State.FAILED) {
			return Component.translatable("room.mistinn.relay_failed");
		}
		if (direct()) {
			return Component.translatable(RelayWatch.available() ? "room.mistinn.hint" : "room.mistinn.hint_no_relay");
		}
		return Component.translatable("room.mistinn.hint_relay");
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float alpha) {
		int x = this.left + 12;
		int inner = this.panelWidth - 24;
		g.fill(this.left, this.top, this.left + this.panelWidth, this.top + PANEL_HEIGHT, 0xEB101014);
		g.fill(this.left, this.top, this.left + this.panelWidth, this.top + 1, 0xBBA97647);
		g.text(this.font, this.title, x, this.top + 12, GOLD, true);
		g.text(this.font, status(), x, this.top + 28, PAPER, true);
		g.text(this.font, Component.translatable("room.mistinn.invite"), x, this.top + 46, ASH, false);
		Invite invite = currentInvite(this.minecraft);
		String waiting = direct() ? "room.mistinn.invite_need_address" : "room.mistinn.invite_pending";
		Component code = invite != null ? Component.literal(invite.code())
			: Component.translatable(this.server.isPublished() ? waiting : "room.mistinn.invite_closed");
		int y = this.top + 58;
		for (var row : this.font.split(code, inner)) {
			g.text(this.font, row, x, y, invite != null ? GOLD : ASH, invite != null);
			y += 10;
		}
		if (this.portField.visible) {
			g.text(this.font, Component.translatable("room.mistinn.port"), x, this.top + 81, ASH, false);
			g.text(this.font, Component.translatable("room.mistinn.address"), x + 76, this.top + 81, ASH, false);
		}
		y = this.top + 148;
		for (var row : this.font.split(line(), inner)) {
			g.text(this.font, row, x, y, ASH, false);
			y += 10;
		}
		super.extractRenderState(g, mouseX, mouseY, alpha);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.previous);
	}
}
