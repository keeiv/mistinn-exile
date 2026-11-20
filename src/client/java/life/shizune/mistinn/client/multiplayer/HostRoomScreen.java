package life.shizune.mistinn.client.multiplayer;

import com.google.common.net.HostAndPort;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

/** Opens or closes the current integrated server, with a copyable direct-connection address. */
public final class HostRoomScreen extends Screen {
	private static final int DEFAULT_PORT = 25565;
	private static final int GOLD = 0xFFE8AF69;
	private static final int PAPER = 0xFFECE3D2;
	private static final int ASH = 0xFFB8B0A0;
	private final Screen previous;
	private final IntegratedServer server;
	private EditBox portField;
	private EditBox hostField;
	private Button roomButton;
	private Button copyButton;
	private String host = "";
	private int port = DEFAULT_PORT;
	private int left;
	private int top;
	private int panelWidth;
	private @Nullable Component notice;

	public HostRoomScreen(final Screen previous, final IntegratedServer server) {
		super(Component.translatable("room.mistinn.title"));
		this.previous = previous;
		this.server = server;
		if (server.isPublished()) {
			this.port = server.getPort();
		}
	}

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
		});
	}

	@Override
	protected void init() {
		this.panelWidth = Math.min(340, this.width - 16);
		this.left = (this.width - this.panelWidth) / 2;
		this.top = Math.max(4, (this.height - 188) / 2);
		int x = this.left + 12;
		int inner = this.panelWidth - 24;
		int half = (inner - 6) / 2;
		this.portField = new EditBox(this.font, x, this.top + 55, 68, 20, Component.translatable("room.mistinn.port"));
		this.portField.setMaxLength(5);
		this.portField.setValue(Integer.toString(this.server.isPublished() ? this.server.getPort() : this.port));
		this.addRenderableWidget(this.portField);
		this.hostField = new EditBox(this.font, x + 76, this.top + 55, inner - 76, 20, Component.translatable("room.mistinn.address"));
		this.hostField.setMaxLength(253);
		this.hostField.setValue(this.host);
		this.hostField.setHint(Component.translatable("room.mistinn.address_hint").withColor(ASH));
		this.addRenderableWidget(this.hostField);
		this.roomButton = this.addRenderableWidget(Button.builder(CommonComponents.EMPTY, button -> toggleRoom())
			.bounds(x, this.top + 85, half, 20).build());
		this.copyButton = this.addRenderableWidget(Button.builder(Component.translatable("room.mistinn.copy"), button -> copy())
			.bounds(x + half + 6, this.top + 85, inner - half - 6, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("options.worldOptions.button"), button ->
			this.minecraft.gui.setScreen(new WorldOptionsScreen(this, this.minecraft.level)))
			.bounds(x, this.top + 155, half, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
			.bounds(x + half + 6, this.top + 155, inner - half - 6, 20).build());
		this.portField.setResponder(text -> refresh());
		this.hostField.setResponder(text -> {
			this.host = text;
			refresh();
		});
		refresh();
	}

	private boolean training() {
		return WorldMode.of(this.server) == WorldMode.TRAINING;
	}

	private void refresh() {
		boolean open = this.server.isPublished();
		if (open && !this.portField.getValue().equals(Integer.toString(this.server.getPort()))) {
			this.portField.setValue(Integer.toString(this.server.getPort()));
		}
		this.portField.setEditable(!open);
		this.roomButton.setMessage(Component.translatable(open ? "room.mistinn.close" : "room.mistinn.open"));
		this.roomButton.active = open || (!training() && this.minecraft.allowsMultiplayer() && parsePort(this.portField.getValue()) > 0);
		this.copyButton.active = open && shareAddress(this.host, this.server.getPort()) != null;
	}

	private void toggleRoom() {
		if (this.server.isPublished()) {
			if (this.server.unpublishServer()) {
				this.notice = Component.translatable("room.mistinn.closed");
			}
		} else {
			int requestedPort = parsePort(this.portField.getValue());
			if (requestedPort == 0 || training() || !this.minecraft.allowsMultiplayer()) {
				return;
			}
			this.port = requestedPort;
			this.notice = ((RoomHosting) this.server).mistinn$publishRoom(this.port)
				? Component.translatable("room.mistinn.ready", this.port)
				: Component.translatable("room.mistinn.bind_error", this.port);
		}
		refresh();
	}

	private void copy() {
		String address = this.server.isPublished() ? shareAddress(this.host, this.server.getPort()) : null;
		if (address != null) {
			this.minecraft.keyboardHandler.setClipboard(address);
			this.notice = Component.translatable("room.mistinn.copied");
		}
	}

	public static int parsePort(final String text) {
		if (!text.matches("[0-9]{1,5}")) {
			return 0;
		}
		int port = Integer.parseInt(text);
		return port >= 1024 && port <= 65535 ? port : 0;
	}

	public static @Nullable String shareAddress(final String text, final int port) {
		if (text.isBlank() || port < 1024 || port > 65535) {
			return null;
		}
		try {
			String address = HostAndPort.fromHost(text.strip()).withDefaultPort(port).toString();
			return ServerAddress.isValidAddress(address) ? address : null;
		} catch (IllegalArgumentException invalid) {
			return null;
		}
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float alpha) {
		int x = this.left + 12;
		g.fill(this.left, this.top, this.left + this.panelWidth, this.top + 188, 0xEB101014);
		g.fill(this.left, this.top, this.left + this.panelWidth, this.top + 1, 0xBBA97647);
		g.text(this.font, this.title, x, this.top + 12, GOLD, true);
		g.text(this.font, Component.translatable(this.server.isPublished() ? "room.mistinn.running" : "room.mistinn.stopped"),
			x, this.top + 28, PAPER, true);
		g.text(this.font, Component.translatable("room.mistinn.port"), x, this.top + 44, ASH, false);
		g.text(this.font, Component.translatable("room.mistinn.address"), x + 76, this.top + 44, ASH, false);
		Component line;
		if (training()) {
			line = Component.translatable("room.mistinn.training");
		} else if (!this.server.isPublished() && !this.minecraft.allowsMultiplayer()) {
			line = Component.translatable("room.mistinn.unavailable");
		} else {
			line = this.notice != null ? this.notice : Component.translatable("room.mistinn.hint");
		}
		int y = this.top + 116;
		for (var row : this.font.split(line, this.panelWidth - 24)) {
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
