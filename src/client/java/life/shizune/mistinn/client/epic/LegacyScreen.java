package life.shizune.mistinn.client.epic;

import java.util.List;
import life.shizune.mistinn.epic.EpicState;
import life.shizune.mistinn.epic.EpicStep;
import life.shizune.mistinn.epic.LegacyService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 史詩的行囊, opened from the guide. In the epic once it is written: seal what you wear and carry. Anywhere else: what the
 * pack holds, a piece at a time, each with its button to take it out.
 */
public class LegacyScreen extends Screen {
	private static final int ROW = 22;
	private final @Nullable Screen parent;
	private @Nullable Component notice;

	public LegacyScreen(final @Nullable Screen parent) {
		super(Component.translatable("epic.mistinn.legacy.title"));
		this.parent = parent;
	}

	private boolean sealing() {
		EpicState state = EpicClient.state(this.minecraft);
		return state != null && state.at() == EpicStep.ENDED;
	}

	@Override
	protected void init() {
		this.clearWidgets();
		int cx = this.width / 2;
		int top = this.height / 2 - 90;
		if (EpicClient.inEpic()) {
			Button seal = Button.builder(Component.translatable("epic.mistinn.legacy.seal"), b -> ClientPlayNetworking.send(new LegacyService.SealPayload(true)))
				.bounds(cx - 70, top + 26, 140, 20).build();
			seal.active = this.sealing();
			this.addRenderableWidget(seal);
		} else {
			List<ItemStack> pack = Legacy.pack();
			for (int i = 0; i < pack.size() && i < 14; i++) {
				int index = i;
				int x = cx + (i % 2 == 0 ? -150 : 6);
				int y = top + 26 + (i / 2) * ROW;
				this.addRenderableWidget(Button.builder(Component.translatable("epic.mistinn.legacy.take"), b -> Legacy.claim(index))
					.bounds(x + 104, y, 40, 18).build());
			}
		}
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose()).bounds(cx - 50, this.height / 2 + 96, 100, 20).build());
	}

	@Override
	public void tick() {
		Component said = Legacy.notice();
		if (said != null) {
			this.notice = said;
			this.init();
		}
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			this.minecraft.gui.setScreen(this.parent);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		g.fill(0, 0, this.width, this.height, 0xD8100D0A);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int cx = this.width / 2;
		int top = this.height / 2 - 90;
		Component title = this.getTitle();
		g.text(this.font, title, cx - this.font.width(title) / 2, top, 0xFFF2EAD8, true);
		Component hint = Component.translatable(EpicClient.inEpic() ? (this.sealing() ? "epic.mistinn.legacy.seal_hint" : "epic.mistinn.legacy.not_yet")
			: Legacy.pack().isEmpty() ? "epic.mistinn.legacy.empty" : "epic.mistinn.legacy.take_hint");
		g.text(this.font, hint, cx - this.font.width(hint) / 2, top + 12, 0xFFB8B0A0, false);
		if (!EpicClient.inEpic()) {
			List<ItemStack> pack = Legacy.pack();
			for (int i = 0; i < pack.size() && i < 14; i++) {
				int x = cx + (i % 2 == 0 ? -150 : 6);
				int y = top + 26 + (i / 2) * ROW;
				g.item(pack.get(i), x, y + 1);
				String name = pack.get(i).getHoverName().getString();
				if (this.font.width(name) > 80) {
					name = this.font.plainSubstrByWidth(name, 76) + "…";
				}
				g.text(this.font, name, x + 20, y + 5, 0xFFE8DCC4, false);
			}
		}
		if (this.notice != null) {
			g.text(this.font, this.notice, cx - this.font.width(this.notice) / 2, this.height / 2 + 82, 0xFFD8B070, false);
		}
	}
}
