package life.shizune.mistinn.client.view;

import life.shizune.mistinn.client.anim.HeavyClient;
import life.shizune.mistinn.client.move.RollClient;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import org.jspecify.annotations.Nullable;

/**
 * 自己的身體: in one's own view the hunter has a body. Looking down they see their chest, their legs walking and
 * their arms hanging at their sides, whatever they carry in their hands; the head is not drawn, and the body stands a
 * little behind the eye so the view is never inside it. The arms only come up in front of the eye when they are
 * doing something there: a swing, a heavy blow drawn back, a bow, food, a block being broken, a wound treated. Then it
 * is the body's own arms that reach up from its shoulders, gripping what they hold ({@link OwnArms}), and the body
 * steps up under the eye so they reach far enough.
 */
public final class OwnBody {
	/** Set on the local hunter's render state while the world is drawn through their own eyes. */
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "mistinn:own_body");
	/**
	 * Set with {@link #KEY} while a map is held up in front of the eye: the game draws it with its own hands there, so
	 * the body leaves those arms, and what they hold, out instead of drawing them twice.
	 */
	public static final RenderStateDataKey<Hands> HANDS_UP = RenderStateDataKey.create(() -> "mistinn:own_body_hands_up");
	/** Set with {@link #KEY}: how far the arms are raised to their work in front of the eye, 0 to 1. */
	public static final RenderStateDataKey<Float> RAISED = RenderStateDataKey.create(() -> "mistinn:own_body_raised");

	/** Which of the body's arms the game draws in front of the eye instead. */
	public record Hands(boolean main, boolean off) {
	}

	/** The arms the game draws itself now, holding a map up to be read; null while the body's own arms do everything. */
	public static @Nullable Hands handsUp(final LocalPlayer player, final float partial) {
		if (raised(partial) <= 0.0F) {
			return null;
		}
		boolean map = player.getMainHandItem().has(net.minecraft.core.component.DataComponents.MAP_ID)
			|| player.getOffhandItem().has(net.minecraft.core.component.DataComponents.MAP_ID);
		return map ? new Hands(true, true) : null;
	}

	/** How far behind the eye the body stands, in blocks. */
	public static final float BACK = 0.28F;
	/**
	 * How far it steps up under the eye while the arms work in front of it, so they reach out far enough to be seen; only
	 * while the hunter looks ahead, since looking down they would see their own shoulders from too near.
	 */
	public static final float STEP = 0.22F;
	/** How fast the hands come down again once they are done, a share a tick; they come up at once. */
	private static final float LOWER = 0.2F;
	/** How far below the view the lowered hands are, in the hand pass's own units. */
	public static final float DROP = 0.9F;

	/** True only while the world's entities are being read for drawing (not the inventory's doll). */
	private static boolean world;
	private static float raised;
	private static float raisedO;

	private OwnBody() {
	}

	public static void world(final boolean extracting) {
		world = extracting;
	}

	public static boolean inWorldPass() {
		return world;
	}

	/** Whether the hunter's own body is drawn in their view now. */
	public static boolean active() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.getCameraEntity() != player || !mc.options.getCameraType().isFirstPerson()) {
			return false;
		}
		// lying on the ground the hunter sees the sky, not their own body under the eye
		if (player.isSpectator() || player.isSleeping() || !player.isAlive() || player.isInvisible() || life.shizune.mistinn.team.DownService.isDown(player)) {
			return false;
		}
		// lying, swimming, gliding or tumbling, the eye would be inside the body: it is left out
		if ((player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING) || RollClient.sample(player.getId()) != null) {
			return false;
		}
		return life.shizune.mistinn.client.story.SceneScreen.current() == null
			&& life.shizune.mistinn.client.training.DropCinematic.view(mc, 1.0F) == null;
	}

	/** Whether the hands have work in front of the eye now (the guard is work in front of it). */
	private static boolean working(final Minecraft mc, final LocalPlayer player) {
		if (life.shizune.mistinn.client.medical.TreatmentClient.sample(player.getId()) != null) {
			// a wound is treated with the hands up in front of the eye; a plate is mended on the body, in sight below it
			return life.shizune.mistinn.client.medical.TreatmentClient.inHands(player) != null;
		}
		return player.isSwinging() || player.isUsingItem() || HeavyClient.sample(player, 1.0F) != null
			|| life.shizune.mistinn.client.combat.ParryClient.sample(player.getId()) != null
			|| (mc.gameMode != null && mc.gameMode.isDestroying());
	}

	public static void tick(final Minecraft mc) {
		raisedO = raised;
		LocalPlayer player = mc.player;
		if (player == null) {
			raised = 0.0F;
			return;
		}
		raised = working(mc, player) ? 1.0F : Math.max(0.0F, raised - LOWER);
	}

	/** How far the hands are up in front of the eye, 0 (hanging at the sides) to 1. */
	public static float raised(final float partial) {
		return Mth.lerp(partial, raisedO, raised);
	}

	/**
	 * How far behind the eye the body stands now, in blocks, for arms {@code raised} that far and a hunter looking
	 * {@code pitch} degrees down.
	 */
	public static float back(final float raised, final float pitch) {
		float ahead = 1.0F - Mth.clamp((pitch - 15.0F) / 40.0F, 0.0F, 1.0F);
		return BACK - STEP * raised * ahead * ahead * (3.0F - 2.0F * ahead);
	}

	/** How far below their working place the hands are drawn while they are lowered, in the hand pass's own units. */
	public static float lowered(final float partial) {
		float down = 1.0F - raised(partial);
		return down * down * DROP;
	}
}
