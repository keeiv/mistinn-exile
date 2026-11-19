package life.shizune.mistinn.client.map;

import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.residue.DeathMark;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * The hunter's minimap in the top right corner: terrain, the player's heading, the golden bell
 * they are bound to and the gravestone holding their residue. Markers outside the map sit on its
 * edge, pointing the way. Below it: the salt they carry and how far the residue is.
 */
public final class MinimapHud implements HudElement {
	private static final int MARGIN = 6;
	private static final int FRAME = 0xE00D0F12;
	private static final int FRAME_LINE = 0xFF6B5B3A;
	private static final int TEXT = 0xFFE9E6EA;
	private static final int SALT_TEXT = 0xFFF0DC8C;
	private static final int RESIDUE = 0xFFFFFFFF;
	private static final int RESIDUE_EDGE = 0xFF94461F;
	private static final int BELL = 0xFFF0DC8C;
	private static final int BELL_EDGE = 0xFF7A6326;
	/** Created on first draw: item stacks cannot exist before the registries are bound. */
	private ItemStack saltIcon;

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		if (life.shizune.mistinn.client.body.BodyHud.headDestroyed()) {
			// a destroyed head cannot read the map
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.screen() != null || minecraft.getDebugOverlay().showDebugScreen() || !MinimapTexture.ready()) {
			return;
		}
		int size = MinimapTexture.SIZE;
		int x = g.guiWidth() - size - MARGIN;
		int y = MARGIN;
		int cx = x + size / 2;
		int cy = y + size / 2;

		g.fill(x - 3, y - 3, x + size + 3, y + size + 3, FRAME);
		g.outline(x - 2, y - 2, size + 4, size + 4, FRAME_LINE);
		g.blit(RenderPipelines.GUI_TEXTURED, MinimapTexture.ID, x, y, 0, 0, size, size, size, size);

		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		double px = Mth.lerp(partial, player.xo, player.getX());
		double pz = Mth.lerp(partial, player.zo, player.getZ());
		int scale = Math.max(1, MinimapTexture.scale());
		// the texture was drawn around a whole block; keep markers aligned with it
		double originX = MinimapTexture.centreX() + 0.5;
		double originZ = MinimapTexture.centreZ() + 0.5;

		GlobalPos bell = player.getAttached(MistinnAttachments.BELL);
		if (bell != null && bell.dimension() == player.level().dimension()) {
			int[] at = place(bell.pos().getX() + 0.5 - originX, bell.pos().getZ() + 0.5 - originZ, scale, cx, cy, size);
			g.fill(at[0] - 2, at[1] - 2, at[0] + 3, at[1] + 3, BELL_EDGE);
			g.fill(at[0] - 1, at[1] - 1, at[0] + 2, at[1] + 2, BELL);
		}

		// the story's next place: an amber diamond, pinned to the edge when it is far
		life.shizune.mistinn.story.StoryProgress story = player.getAttached(life.shizune.mistinn.story.StoryService.PROGRESS);
		if (story != null && story.target().isPresent() && player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
			net.minecraft.core.BlockPos goal = story.target().get();
			int[] at = place(goal.getX() + 0.5 - originX, goal.getZ() + 0.5 - originZ, scale, cx, cy, size);
			drawGoal(g, at[0], at[1]);
		}

		DeathMark mark = player.getAttached(MistinnAttachments.DEATH_MARK);
		Font font = minecraft.font;
		int textY = y + size + 6;
		if (mark != null && mark.pos().dimension() == player.level().dimension()) {
			double dx = mark.pos().pos().getX() + 0.5 - originX;
			double dz = mark.pos().pos().getZ() + 0.5 - originZ;
			int[] at = place(dx, dz, scale, cx, cy, size);
			boolean blink = (player.tickCount / 8) % 2 == 0;
			drawResidue(g, at[0], at[1], blink || at[2] == 0);
			int distance = (int) Math.round(Math.sqrt(Math.pow(mark.pos().pos().getX() + 0.5 - px, 2) + Math.pow(mark.pos().pos().getZ() + 0.5 - pz, 2)));
			Component line = Component.translatable("hud.mistinn.residue", mark.salt(), distance);
			g.text(font, line, x + size - font.width(line), textY + 12, TEXT, true);
		}

		drawPlayer(g, cx + (float) ((px - originX) / scale), cy + (float) ((pz - originZ) / scale), player.getYRot(partial));

		int salt = player.getAttachedOrElse(MistinnAttachments.SALT, 0);
		String amount = String.format("%,d", salt);
		if (this.saltIcon == null) {
			this.saltIcon = new ItemStack(MistinnItems.SALT);
		}
		g.item(this.saltIcon, x + size - font.width(amount) - 18, textY - 5);
		g.text(font, amount, x + size - font.width(amount), textY, SALT_TEXT, true);
	}

	/** Map position of an offset in blocks; clamped onto the edge when it is off the map. [x, y, inside] */
	private static int[] place(final double dx, final double dz, final int scale, final int cx, final int cy, final int size) {
		double mx = dx / scale;
		double mz = dz / scale;
		double limit = size / 2.0 - 3;
		boolean inside = Math.abs(mx) <= limit && Math.abs(mz) <= limit;
		if (!inside) {
			double k = limit / Math.max(Math.abs(mx), Math.abs(mz));
			mx *= k;
			mz *= k;
		}
		return new int[] {cx + (int) Math.round(mx), cy + (int) Math.round(mz), inside ? 1 : 0};
	}

	/** The story's goal: a small amber diamond with a dark rim. */
	private static void drawGoal(final GuiGraphicsExtractor g, final int x, final int y) {
		for (int i = 0; i <= 3; i++) {
			g.fill(x - (3 - i) - 1, y - i - 1, x + (3 - i) + 2, y - i + 1, 0xFF2A1E0E);
			g.fill(x - (3 - i) - 1, y + i - 1, x + (3 - i) + 2, y + i + 1, 0xFF2A1E0E);
		}
		for (int i = 0; i <= 2; i++) {
			g.fill(x - (2 - i), y - i, x + (2 - i) + 1, y - i + 1, 0xFFE0A050);
			g.fill(x - (2 - i), y + i, x + (2 - i) + 1, y + i + 1, 0xFFE0A050);
		}
	}

	/** The residue marker: a small salt-white X with a rust rim. */
	private static void drawResidue(final GuiGraphicsExtractor g, final int x, final int y, final boolean bright) {
		int core = bright ? RESIDUE : 0xFFC7C4CC;
		for (int i = -2; i <= 2; i++) {
			g.fill(x + i - 1, y + i - 1, x + i + 2, y + i + 2, RESIDUE_EDGE);
			g.fill(x + i - 1, y - i - 1, x + i + 2, y - i + 2, RESIDUE_EDGE);
		}
		for (int i = -2; i <= 2; i++) {
			g.fill(x + i, y + i, x + i + 1, y + i + 1, core);
			g.fill(x + i, y - i, x + i + 1, y - i + 1, core);
		}
	}

	/** The player: an arrow pointing where they face (map north is up). */
	private static void drawPlayer(final GuiGraphicsExtractor g, final float x, final float y, final float yaw) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.rotate((float) Math.toRadians(yaw + 180.0F));
		g.fill(-1, -4, 1, -3, 0xFF000000);
		g.fill(-2, -3, 2, -1, 0xFF000000);
		g.fill(-3, -1, 3, 3, 0xFF000000);
		g.fill(0, -3, 1, -2, 0xFFFFFFFF);
		g.fill(-1, -2, 1, 0, 0xFFFFFFFF);
		g.fill(-2, 0, 2, 2, 0xFFFFFFFF);
		g.fill(-1, 1, 1, 2, 0xFF94461F);
		pose.popMatrix();
	}
}
