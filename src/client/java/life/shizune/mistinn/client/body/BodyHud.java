package life.shizune.mistinn.client.body;

import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Takes the place of the heart row for hunters: a small figure beside the hotbar, each part
 * coloured by its health, destroyed parts dark. A destroyed head also closes in the view.
 */
public final class BodyHud implements HudElement {
	/** Where each part sits in the 18 x 28 figure: x, y, width, height. Seen from behind. */
	private static final int[][] SHAPE = {
		{6, 0, 6, 6},   // head
		{5, 7, 8, 10},  // torso
		{1, 7, 3, 10},  // left arm
		{14, 7, 3, 10}, // right arm
		{5, 18, 3, 10}, // left leg
		{10, 18, 3, 10} // right leg
	};
	public static final int WIDTH = 18;
	public static final int HEIGHT = 28;
	/** How far vanilla's off-hand slot reaches out from the side of the hotbar. */
	private static final int OFFHAND_SLOT = 29;
	private static final int OUTLINE = 0xC0101010;
	private static final int DEAD = 0xFF4A1414;
	/** Wound marks on the figure. */
	private static final int BLEED = 0xFFB0201A;
	private static final int CRACK = 0xFFF2EEE4;
	private static final int CRACK_HELD = 0xFF9C9488;
	private static final int SPLINT = 0xFFB88A52;
	private static final int STRAP = 0xFF1A1714;
	private static final int STRAP_LATE = 0xFFE0A050;

	private final HudElement vanilla;

	public BodyHud(final HudElement vanilla) {
		this.vanilla = vanilla;
	}

	public static boolean active(final LocalPlayer player) {
		return player != null && BodyService.applies(player);
	}

	public static boolean headDestroyed() {
		LocalPlayer player = Minecraft.getInstance().player;
		return active(player) && BodyService.get(player).destroyed(BodyPart.HEAD);
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!active(player)) {
			this.vanilla.extractRenderState(g, deltaTracker);
			return;
		}
		Body body = BodyService.get(player);
		if (body.destroyed(BodyPart.HEAD)) {
			vignette(g);
		}
		// left of the hotbar, resting on the bottom edge, and clear of the off-hand slot when that is on the left
		int offhandSlot = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? OFFHAND_SLOT : 0;
		int x = g.guiWidth() / 2 - 91 - offhandSlot - WIDTH - 6;
		int y = g.guiHeight() - HEIGHT - 3;
		figure(g, body, life.shizune.mistinn.body.WoundService.get(player), player.level().getGameTime(), x, y, 1);
	}

	/** Draws the figure at (x, y), each figure pixel {@code scale} screen pixels wide. */
	public static void figure(final GuiGraphicsExtractor g, final Body body, final int x, final int y, final int scale) {
		figure(g, body, life.shizune.mistinn.body.Wounds.NONE, 0L, x, y, scale);
	}

	/**
	 * The figure with its wounds: a bleeding part flashes red, a broken limb has a pale crack
	 * across it, a tourniquet is a dark band at the top of the part (amber once it has been on
	 * too long), a splint two wooden slats down its sides.
	 */
	public static void figure(final GuiGraphicsExtractor g, final Body body, final life.shizune.mistinn.body.Wounds wounds, final long now, final int x, final int y, final int scale) {
		for (BodyPart part : BodyPart.values()) {
			int[] s = SHAPE[part.ordinal()];
			int px = x + s[0] * scale;
			int py = y + s[1] * scale;
			int w = s[2] * scale;
			int h = s[3] * scale;
			g.fill(px - 1, py - 1, px + w + 1, py + h + 1, OUTLINE);
			life.shizune.mistinn.body.Wounds.Wound wound = wounds.get(part);
			boolean flash = wound.bleeding() && !wound.tied() && !body.destroyed(part) && (now / 8) % 2 == 0;
			g.fill(px, py, px + w, py + h, flash ? BLEED : colour(body.get(part)));
			if (body.destroyed(part)) {
				continue;
			}
			if (wound.fractured()) {
				// a pale crack corner to corner
				int steps = Math.max(w, h);
				for (int i = 0; i < steps; i++) {
					int cx = px + i * w / steps;
					int cy = py + i * h / steps;
					g.fill(cx, cy, cx + Math.max(1, scale / 2), cy + Math.max(1, scale / 2), wound.splinted(now) ? CRACK_HELD : CRACK);
				}
			}
			if (wound.splinted(now)) {
				g.fill(px, py, px + Math.max(1, scale / 2), py + h, SPLINT);
				g.fill(px + w - Math.max(1, scale / 2), py, px + w, py + h, SPLINT);
			}
			if (wound.tied()) {
				boolean starving = now - wound.tourniquet() > life.shizune.mistinn.body.WoundService.TOURNIQUET_SAFE;
				g.fill(px, py, px + w, py + Math.max(1, scale), starving ? STRAP_LATE : STRAP);
			}
		}
	}

	/** Pale green when whole, through amber, to red; a dead part is near black red. */
	public static int colour(final float health) {
		if (health <= 0.0F) {
			return DEAD;
		}
		// against the hunter's own ceiling, which constitution raises
		LocalPlayer player = Minecraft.getInstance().player;
		float share = Mth.clamp(health / (player != null ? BodyService.max(player) : BodyPart.MAX), 0.0F, 1.0F);
		int r;
		int gr;
		if (share > 0.5F) {
			float t = (share - 0.5F) * 2.0F;
			r = (int) Mth.lerp(t, 0xE0, 0x9C);
			gr = (int) Mth.lerp(t, 0xB0, 0xD8);
		} else {
			float t = share * 2.0F;
			r = (int) Mth.lerp(t, 0xD8, 0xE0);
			gr = (int) Mth.lerp(t, 0x3A, 0xB0);
		}
		return 0xFF000000 | r << 16 | gr << 8 | 0x48;
	}

	/** A dark frame closing in from all four edges; where two meet, the corners are darkest. */
	private static void vignette(final GuiGraphicsExtractor g) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		int depth = Math.min(w, h) * 2 / 5;
		int dark = 0xE0000000;
		g.fillGradient(0, 0, w, depth, dark, 0);
		g.fillGradient(0, h - depth, w, h, 0, dark);
		// fillGradient only fades downwards: turn it a quarter for each side
		org.joml.Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(0.0F, h);
		pose.rotate((float) (-Math.PI / 2));
		g.fillGradient(0, 0, h, depth, dark, 0);
		pose.popMatrix();
		pose.pushMatrix();
		pose.translate(w, 0.0F);
		pose.rotate((float) (Math.PI / 2));
		g.fillGradient(0, 0, h, depth, dark, 0);
		pose.popMatrix();
	}
}
