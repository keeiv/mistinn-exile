package life.shizune.mistinn.client.guide;

import java.util.List;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.supply.SupplyService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The guide's supply page: what the anchor station sells, five offers on each page, with what
 * the hunter has to spend and how their last order stands. An offer is chosen with one click
 * and ordered with the second, like a skill.
 */
final class GuideSupplyPage {
	private static final int PER_PAGE = 5;
	private static final int ROW = 24;

	private static @Nullable String armed;
	private static @Nullable Component notice;
	private static boolean noticeBad;
	private static long noticeAt;

	private GuideSupplyPage() {
	}

	/** Top-left of an offer's row, relative to the spread. */
	private static int[] rowAt(final int index) {
		return index < PER_PAGE ? new int[] {20, 46 + index * ROW} : new int[] {172, 18 + (index - PER_PAGE) * ROW};
	}

	/** Centre of an offer's row, in GUI pixels. */
	static double[] rowCentre(final int left, final int top, final int index) {
		int[] at = rowAt(index);
		return new double[] {left + at[0] + 64.0, top + at[1] + 10.0};
	}

	static void disarm() {
		armed = null;
	}

	static boolean clicked(final int left, final int top, final double mx, final double my) {
		for (int i = 0; i < SupplyService.OFFERS.size(); i++) {
			int[] at = rowAt(i);
			if (HunterGuideScreen.inside(mx, my, left + at[0] - 2, top + at[1], 132, ROW - 2)) {
				String id = SupplyService.OFFERS.get(i).id();
				if (id.equals(armed)) {
					// the second click orders it
					ClientPlayNetworking.send(new SupplyService.OrderPayload(id));
					armed = null;
				} else {
					armed = id;
					Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.8F));
				}
				return true;
			}
		}
		armed = null;
		return false;
	}

	/** The station answered an order. */
	static void answered(final SupplyService.OrderResultPayload payload) {
		SupplyService.Result result = SupplyService.Result.byId(payload.result());
		noticeAt = System.currentTimeMillis();
		noticeBad = result != SupplyService.Result.ORDERED;
		notice = Component.translatable("gui.mistinn.guide.supply.result." + result.name().toLowerCase(java.util.Locale.ROOT));
		if (!noticeBad) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0F, 0.9F));
		}
	}

	static void draw(final HunterGuideScreen screen, final GuiGraphicsExtractor g, final int x, final int y, final int mouseX, final int mouseY) {
		Font font = screen.guideFont();
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		int salt = player.getAttachedOrElse(MistinnAttachments.SALT, 0);
		g.text(font, Component.translatable("gui.mistinn.guide.supply.title"), x + 20, y + 18, HunterGuideScreen.INK, false);
		g.text(font, Component.translatable("gui.mistinn.guide.salt", salt), x + 20, y + 30, HunterGuideScreen.FADED, false);
		g.fill(x + 20, y + 41, x + 148, y + 42, HunterGuideScreen.LINE_DARK);
		boolean pending = player.hasAttached(SupplyService.ORDER);
		for (int i = 0; i < SupplyService.OFFERS.size(); i++) {
			row(g, font, SupplyService.OFFERS.get(i), x + rowAt(i)[0], y + rowAt(i)[1], salt, pending, mouseX, mouseY);
		}
		// how the last order stands, or the answer just given
		g.fill(x + 172, y + 140, x + 300, y + 141, HunterGuideScreen.LINE_DARK);
		Component status;
		int colour = HunterGuideScreen.FADED;
		if (notice != null && System.currentTimeMillis() - noticeAt < 4000) {
			status = notice;
			colour = noticeBad ? HunterGuideScreen.RED : HunterGuideScreen.INK;
		} else {
			status = status(player);
		}
		int sy = y + 145;
		List<FormattedCharSequence> rows = font.split(status, HunterGuideScreen.PAGE_WIDTH);
		for (int i = 0; i < Math.min(4, rows.size()); i++) {
			g.text(font, rows.get(i), x + 172, sy + i * 10, colour, false);
		}
	}

	private static Component status(final LocalPlayer player) {
		SupplyService.Order order = player.getAttached(SupplyService.ORDER);
		long now = player.level().getGameTime();
		if (order != null) {
			return Component.translatable("gui.mistinn.guide.supply.coming", SupplyService.secondsLeft(order, now));
		}
		SupplyService.Drop drop = player.getAttached(SupplyService.DROP);
		if (drop != null) {
			BlockPos at = drop.at();
			int distance = (int) Math.round(Math.hypot(at.getX() + 0.5 - player.getX(), at.getZ() + 0.5 - player.getZ()));
			return Component.translatable("gui.mistinn.guide.supply.lies", direction(player, at), distance);
		}
		return Component.translatable("gui.mistinn.guide.supply.hint");
	}

	private static Component direction(final LocalPlayer player, final BlockPos at) {
		double angle = Math.toDegrees(Math.atan2(at.getX() + 0.5 - player.getX(), -(at.getZ() + 0.5 - player.getZ())));
		int point = Math.floorMod(Math.round((float) angle / 45.0F), 8);
		return Component.translatable("direction.mistinn." + new String[] {"n", "ne", "e", "se", "s", "sw", "w", "nw"}[point]);
	}

	/** One offer: its item, its name and how many, and its price (red when the hunter cannot pay). */
	private static void row(final GuiGraphicsExtractor g, final Font font, final SupplyService.Offer offer, final int rx, final int ry,
		final int salt, final boolean pending, final int mouseX, final int mouseY) {
		boolean chosen = offer.id().equals(armed);
		if (chosen || HunterGuideScreen.inside(mouseX, mouseY, rx - 2, ry, 132, ROW - 2)) {
			g.fill(rx - 2, ry, rx + 130, ry + ROW - 2, chosen ? 0x30A0382A : 0x18000000);
		}
		ItemStack stack = offer.stack();
		g.item(stack, rx, ry + 3);
		String name = Component.translatable("gui.mistinn.guide.supply.count", stack.getHoverName(), offer.count()).getString();
		int room = 108;
		if (font.width(name) > room) {
			name = font.plainSubstrByWidth(name, room - font.width("…")) + "…";
		}
		g.text(font, name, rx + 20, ry + 2, chosen ? HunterGuideScreen.RED : HunterGuideScreen.INK, false);
		Component price;
		int colour;
		if (chosen) {
			price = Component.translatable(pending ? "gui.mistinn.guide.supply.wait" : "gui.mistinn.guide.supply.again", offer.price());
			colour = HunterGuideScreen.RED;
		} else {
			price = Component.translatable("gui.mistinn.guide.supply.price", offer.price());
			colour = salt < offer.price() ? HunterGuideScreen.RED : HunterGuideScreen.FADED;
		}
		g.text(font, price, rx + 20, ry + 12, colour, false);
	}
}
