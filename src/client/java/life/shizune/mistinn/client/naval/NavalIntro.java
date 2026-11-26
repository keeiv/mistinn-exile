package life.shizune.mistinn.client.naval;

import life.shizune.mistinn.naval.NavalIntroPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * 海盜船的登場: black bars close in, the view leaves the hunter and drops low over the water beside
 * them, looking out into the fog. The fog stands close; out of it, slowly, a black-sailed ship comes
 * on, lanterns first, as the view drifts toward it and the fog draws back. Its name comes up large,
 * with whose it is under it. As it heaves to the view cuts back to the hunter and the bars open:
 * then the first gun. Esc, space or enter skips it (for this hunter only; the ship comes regardless).
 */
public class NavalIntro extends Screen {
	/** Height of each black bar, as a share of the screen. */
	private static final float BAR = 0.12F;
	/** When the name comes up and goes, in ticks from the start; how long each fade takes. */
	private static final int TITLE_FROM = 70;
	private static final int TITLE_TO = 178;
	private static final int FADE = 16;
	/** How long before the ship is there the view comes back to the hunter. */
	private static final int CUT_BACK = 14;
	/** How far the view drifts toward the ship. */
	private static final double DRIFT = 10.0;
	/** Where the fog stands at first, and how far it draws back. */
	private static final float FOG_NEAR = 26.0F;
	private static final float FOG_BACK = 80.0F;

	private static @Nullable NavalIntro current;

	private final int entity;
	private final int name;
	private final int length;
	private int ticks;
	private @Nullable CameraType camera;
	private boolean hudWasHidden;
	private @Nullable Vec3 base;
	private Vec3 toward = new Vec3(1.0, 0.0, 0.0);
	private Vec3 target = Vec3.ZERO;

	public NavalIntro(final NavalIntroPayload payload) {
		super(Component.empty());
		this.entity = payload.entity();
		this.name = payload.name();
		this.length = Math.max(40, payload.ticks());
	}

	public static void begin(final NavalIntroPayload payload) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		mc.gui.setScreen(new NavalIntro(payload));
	}

	/** The entrance playing now, for the camera, the fog and the music. */
	public static @Nullable NavalIntro current() {
		return current;
	}

	/** How far into the entrance, 0 to 1. */
	public float progress(final float partial) {
		return Mth.clamp((this.ticks + partial) / this.length, 0.0F, 1.0F);
	}

	@Override
	protected void init() {
		if (current != this) {
			current = this;
			Minecraft mc = Minecraft.getInstance();
			this.camera = mc.options.getCameraType();
			// the hunter in the boat is in the picture, not in the view
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			this.hudWasHidden = mc.gui.hud.isHidden();
			if (!this.hudWasHidden) {
				mc.gui.hud.toggle();
			}
		}
	}

	@Override
	public void removed() {
		current = null;
		Minecraft mc = Minecraft.getInstance();
		if (this.camera != null) {
			mc.options.setCameraType(this.camera);
		}
		if (!this.hudWasHidden && mc.gui.hud.isHidden()) {
			mc.gui.hud.toggle();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		this.ticks++;
		Minecraft mc = Minecraft.getInstance();
		if (this.ticks >= this.length || mc.player == null || mc.level == null) {
			this.onClose();
			return;
		}
		Entity ship = mc.level.getEntity(this.entity);
		if (ship != null) {
			this.target = ship.position().add(0.0, 7.0, 0.0);
		}
		if (this.base == null && mc.player != null) {
			Vec3 eye = mc.player.getEyePosition();
			Vec3 at = ship != null ? ship.position() : eye.add(this.toward.scale(60.0));
			Vec3 flat = at.subtract(eye).multiply(1.0, 0.0, 1.0);
			this.toward = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
			Vec3 side = new Vec3(-this.toward.z, 0.0, this.toward.x);
			double low = Math.max(eye.y - 0.9, mc.level.getSeaLevel() + 0.7);
			this.base = new Vec3(eye.x, low, eye.z).add(side.scale(2.5));
			if (this.target == Vec3.ZERO) {
				this.target = at.add(0.0, 7.0, 0.0);
			}
		}
	}

	/** Where the view stands now, or null when it is the hunter's own (before it is set, and for the cut back). */
	public @Nullable Vec3 eye(final float partial) {
		if (this.base == null || this.ticks >= this.length - CUT_BACK) {
			return null;
		}
		float t = this.progress(partial);
		double eased = t * t * (3.0 - 2.0 * t);
		double bob = Math.sin((this.ticks + partial) * 0.09) * 0.08;
		return this.base.add(this.toward.scale(DRIFT * eased)).add(0.0, bob, 0.0);
	}

	public Vec3 target() {
		return this.target;
	}

	/** Where the fog stands: close at first, drawing back as the ship comes out of it. */
	public float fogEnd(final float partial) {
		float t = this.progress(partial);
		return FOG_NEAR + FOG_BACK * t * t;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		// the world shows through: no blur, no dimming
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		float time = this.ticks + a;
		float close = Mth.clamp(time / 16.0F, 0.0F, 1.0F);
		float open = Mth.clamp((this.length - CUT_BACK - time) / 10.0F, 0.0F, 1.0F);
		float bars = Math.min(close, open);
		bars = bars * bars * (3.0F - 2.0F * bars);
		int bar = Math.round(this.height * BAR * bars);
		g.fill(0, 0, this.width, bar, 0xFF000000);
		g.fill(0, this.height - bar, this.width, this.height, 0xFF000000);
		float shown = Mth.clamp(Math.min((time - TITLE_FROM) / FADE, (TITLE_TO - time) / FADE), 0.0F, 1.0F);
		if (shown <= 0.01F) {
			return;
		}
		int alpha = Math.max(4, Math.round(shown * 255.0F));
		Font font = this.font;
		int cx = this.width / 2;
		int y = Math.round(this.height * 0.71F);
		Component ship = Component.translatable("naval.mistinn.ship." + this.name);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, y);
		pose.scale(3.0F, 3.0F);
		g.text(font, ship, -font.width(ship) / 2, 0, alpha << 24 | 0xE8E2D6, true);
		pose.popMatrix();
		// a line under the name that draws out from the middle
		int line = Math.round(font.width(ship) * 1.8F * shown);
		g.fill(cx - line, y + 32, cx + line, y + 33, alpha << 24 | 0x9A2A22);
		Component under = Component.translatable("hud.mistinn.naval.raiders", Component.translatable("naval.mistinn.captain." + this.name));
		g.text(font, under, cx - font.width(under) / 2, y + 40, alpha << 24 | 0xB8B0A2, true);
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}
}
