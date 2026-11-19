package life.shizune.mistinn.client.story;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An in-world scene: black bars close in, the view leaves the hunter and holds on one entity,
 * circling it slowly, while its lines play beneath. The hunter stands still meanwhile; Esc ends
 * the scene early.
 */
public class SceneScreen extends Screen {
	/** Height of each black bar, as a share of the screen. */
	private static final float BAR = 0.13F;
	/** How far the view stands from the entity, and how high above its middle. */
	private static final double DISTANCE = 4.6;
	private static final double RISE = 1.4;
	/** Degrees the view circles per tick. */
	private static final float ORBIT = 0.35F;

	private static @Nullable SceneScreen current;

	private final int focus;
	private int ticks;
	private @Nullable CameraType camera;
	private boolean hudWasHidden;
	/** The direction (degrees) the view first stood in from the entity: towards the hunter. */
	private float baseYaw = Float.NaN;
	private Vec3 target = Vec3.ZERO;

	public SceneScreen(final int focus) {
		super(Component.empty());
		this.focus = focus;
	}

	/** The open scene, for the camera. */
	public static @Nullable SceneScreen current() {
		return current;
	}

	@Override
	protected void init() {
		if (current != this) {
			current = this;
			Minecraft mc = Minecraft.getInstance();
			this.camera = mc.options.getCameraType();
			// third person: no hand in the frame, and the hunter's body is drawn
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
		StoryClient.skip();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		this.ticks++;
		Minecraft mc = Minecraft.getInstance();
		Entity entity = mc.level == null ? null : mc.level.getEntity(this.focus);
		if (entity != null) {
			this.target = entity.position().add(0.0, entity.getBbHeight() * 0.45, 0.0);
			if (Float.isNaN(this.baseYaw) && mc.player != null) {
				double dx = mc.player.getX() - entity.getX();
				double dz = mc.player.getZ() - entity.getZ();
				this.baseYaw = dx * dx + dz * dz < 0.01 ? entity.getYRot() : (float) Math.toDegrees(Math.atan2(dz, dx));
			}
		}
		if (mc.player != null && this.target == Vec3.ZERO) {
			this.target = mc.player.position().add(0.0, 1.0, 0.0);
		}
	}

	/** Where the view stands now, or null before the entity is known. */
	public @Nullable Vec3 eye(final float partial) {
		if (Float.isNaN(this.baseYaw)) {
			return null;
		}
		double angle = Math.toRadians(this.baseYaw + (this.ticks + partial) * ORBIT);
		return this.target.add(Math.cos(angle) * DISTANCE, RISE, Math.sin(angle) * DISTANCE);
	}

	/** What the view looks at. */
	public Vec3 target() {
		return this.target;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		// the world shows through: no blur, no dimming
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		float close = Mth.clamp((this.ticks + a) / 16.0F, 0.0F, 1.0F);
		close = close * close * (3.0F - 2.0F * close);
		int bar = Math.round(this.height * BAR * close);
		g.fill(0, 0, this.width, bar, 0xFF000000);
		g.fill(0, this.height - bar, this.width, this.height, 0xFF000000);
		if (close >= 1.0F) {
			Subtitles.draw(g, this.width, this.height - bar / 2, false);
		}
	}

	@Override
	public boolean keyPressed(final net.minecraft.client.input.KeyEvent event) {
		if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN) {
			StoryClient.advanceLine();
			return true;
		}
		return super.keyPressed(event);
	}
}
