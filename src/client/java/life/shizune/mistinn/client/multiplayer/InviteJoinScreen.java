package life.shizune.mistinn.client.multiplayer;

import life.shizune.mistinn.room.Invite;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Joins a friend's room from the invite they sent: paste it, see whose room it is, go. */
public final class InviteJoinScreen extends Screen {
	private static final int GOLD = 0xFFE8AF69;
	private static final int ASH = 0xFFB8B0A0;
	private static final int WARN = 0xFFE07A5F;
	private final Screen previous;
	private EditBox codeField;
	private Button joinButton;
	private @Nullable Invite invite;

	public InviteJoinScreen(final Screen previous) {
		super(Component.translatable("room.mistinn.join.title"));
		this.previous = previous;
	}

	@Override
	protected void init() {
		int fieldWidth = Math.min(340, this.width - 40);
		int x = (this.width - fieldWidth) / 2;
		int y = this.height / 2 - 40;
		this.codeField = new EditBox(this.font, x, y, fieldWidth, 20, Component.translatable("room.mistinn.join.title"));
		this.codeField.setMaxLength(200);
		this.codeField.setHint(Component.literal("MIST-…").withColor(ASH));
		this.addRenderableWidget(this.codeField);
		int half = (fieldWidth - 6) / 2;
		this.joinButton = this.addRenderableWidget(Button.builder(Component.translatable("room.mistinn.join.go"), button -> join())
			.bounds(x, y + 56, half, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
			.bounds(x + half + 6, y + 56, fieldWidth - half - 6, 20).build());
		this.codeField.setResponder(text -> refresh());
		if (this.codeField.getValue().isEmpty()) {
			String clipboard = this.minecraft.keyboardHandler.getClipboard();
			if (clipboard != null && clipboard.length() < 200 && Invite.parse(clipboard) != null) {
				this.codeField.setValue(clipboard.strip());
			}
		}
		this.setInitialFocus(this.codeField);
		refresh();
	}

	private void refresh() {
		this.invite = Invite.parse(this.codeField.getValue());
		this.joinButton.active = this.invite != null && this.invite.version().equals(Invites.modVersion());
	}

	/** The pasted text as an invite this copy of the mod can join, or null. */
	public @Nullable Invite invite() {
		return this.joinButton.active ? this.invite : null;
	}

	private void join() {
		Invite target = invite();
		if (target == null) {
			return;
		}
		Invites.remember(target);
		String address = target.address();
		ConnectScreen.startConnecting(this.previous, this.minecraft, ServerAddress.parseString(address),
			new ServerData(Component.translatable("room.mistinn.join.server").getString(), address, ServerData.Type.OTHER), false, null);
	}

	private Component status() {
		String text = this.codeField.getValue().strip();
		if (text.isEmpty()) {
			return Component.translatable("room.mistinn.join.hint");
		}
		if (this.invite == null) {
			return Component.translatable("room.mistinn.join.invalid");
		}
		if (!this.invite.version().equals(Invites.modVersion())) {
			return Component.translatable("room.mistinn.join.version", this.invite.version(), Invites.modVersion());
		}
		return Component.translatable(this.invite.host().endsWith(Invite.RELAY_SUFFIX) ? "room.mistinn.join.relay" : "room.mistinn.join.direct",
			this.invite.address());
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float alpha) {
		int fieldWidth = Math.min(340, this.width - 40);
		int x = (this.width - fieldWidth) / 2;
		int y = this.height / 2 - 40;
		g.centeredText(this.font, this.title, this.width / 2, y - 24, GOLD);
		boolean bad = !this.codeField.getValue().isBlank() && !this.joinButton.active;
		int row = y + 26;
		for (var line : this.font.split(status(), fieldWidth)) {
			g.text(this.font, line, x, row, bad ? WARN : ASH, false);
			row += 10;
		}
		super.extractRenderState(g, mouseX, mouseY, alpha);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.previous);
	}
}
