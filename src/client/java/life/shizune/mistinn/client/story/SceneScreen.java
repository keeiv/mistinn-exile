package life.shizune.mistinn.client.story;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
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
	/** Degrees off the hunter's side the view begins, circling on away from them: the hunter is never in the way. */
	private static final float ASIDE = 45.0F;

	private static @Nullable SceneScreen current;

	private final int focus;
	private int ticks;
	private @Nullable CameraType camera;
	private boolean hudWasHidden;
	/** The direction (degrees) the view first stands in from the entity: a little aside of the hunter. */
	private float baseYaw = Float.NaN;
	private Vec3 target = Vec3.ZERO;
	/** Where the orbit stopped (in ticks), or NaN while it circles. */
	private float held = Float.NaN;

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
		// skipped before its last words were over: the wrong does not happen this time
		if (life.shizune.mistinn.client.ledger.SnufferScare.phase() == life.shizune.mistinn.client.ledger.SnufferScare.Phase.WORDS) {
			life.shizune.mistinn.client.ledger.SnufferScare.forget();
		}
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
				// the entity's facing turned into the same measure as the orbit's (yaw 0 faces +z, the orbit's 0 is +x)
				float toward = dx * dx + dz * dz < 0.01 ? entity.getYRot() + 90.0F : (float) Math.toDegrees(Math.atan2(dz, dx));
				this.baseYaw = toward + ASIDE;
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
		// the orbit gathers pace over its first second and a half instead of starting at speed; the one time it looks back,
		// the view stops where it is
		if (life.shizune.mistinn.client.ledger.SnufferScare.viewHeld(StoryClient.lineIndex())) {
			if (Float.isNaN(this.held)) {
				this.held = this.ticks + partial;
			}
		} else {
			this.held = Float.NaN;
		}
		float time = Float.isNaN(this.held) ? this.ticks + partial : this.held;
		float ramp = 30.0F;
		float travelled = time < ramp ? time * time * time / (ramp * ramp) - time * time * time * time / (2.0F * ramp * ramp * ramp) : time - ramp / 2.0F;
		double angle = Math.toRadians(this.baseYaw + travelled * ORBIT);
		Vec3 wanted = this.target.add(Math.cos(angle) * DISTANCE, RISE, Math.sin(angle) * DISTANCE);
		// the one time it looks back, the view comes in close to its head
		float closing = life.shizune.mistinn.client.ledger.SnufferScare.closing(partial);
		if (closing > 0.0F) {
			float c = closing * closing * (3.0F - 2.0F * closing);
			wanted = wanted.lerp(this.head(), 0.62 * c);
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return wanted;
		}
		// a wall in the way: the view comes in to stand in front of it, as the game's own third-person view does
		HitResult wall = mc.level.clip(new ClipContext(this.target, wanted, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
		return wall.getType() == HitResult.Type.MISS ? wanted : wall.getLocation().lerp(this.target, 0.14);
	}

	/** What the view looks at: the entity's middle, or its head as the view comes in to it. */
	public Vec3 target() {
		float closing = life.shizune.mistinn.client.ledger.SnufferScare.closing(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
		return closing > 0.0F ? this.target.lerp(this.head(), Math.min(1.0F, closing * 1.5F)) : this.target;
	}

	/** The head of the entity the scene holds on. */
	private Vec3 head() {
		Minecraft mc = Minecraft.getInstance();
		Entity entity = mc.level == null ? null : mc.level.getEntity(this.focus);
		return entity == null ? this.target : entity.getEyePosition();
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
		// the one time it looks back: its face over everything for a moment, then dark
		switch (life.shizune.mistinn.client.ledger.SnufferScare.phase()) {
			case FACE -> g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, life.shizune.mistinn.client.ledger.SnufferScare.FACE_PICTURE, 0, 0, 0.0F, 0.0F,
				this.width, this.height, 320, 180, 320, 180);
			case DARK -> g.fill(0, 0, this.width, this.height, 0xFF000000);
			default -> {
			}
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		// after its last words, Esc does nothing
		return !life.shizune.mistinn.client.ledger.SnufferScare.holding(StoryClient.lineIndex());
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
