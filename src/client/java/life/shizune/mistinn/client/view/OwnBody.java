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
 * little behind the eye so the view is never inside it. The hands only come up in front of the eye when they are
 * doing something there: a swing, a heavy blow drawn back, a bow, food, a block being broken. Up there each hand is
 * drawn with its arm, gripping what it holds ({@link OwnHand}), so it is the hunter's hand that does the work.
 */
public final class OwnBody {
	/** Set on the local hunter's render state while the world is drawn through their own eyes. */
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "mistinn:own_body");
	/**
	 * Set with {@link #KEY} while the hands are up in front of the eye: the arms they belong to, and what those hold, are
	 * drawn there by the hand pass, so the body leaves them out instead of drawing them twice.
	 */
	public static final RenderStateDataKey<Hands> HANDS_UP = RenderStateDataKey.create(() -> "mistinn:own_body_hands_up");

	/**
	 * Which of the body's arms are up in front of the eye. The main arm always is while the hands work; the other one
	 * only when it holds something, since the hand pass never draws an empty off hand.
	 */
	public record Hands(boolean main, boolean off) {
	}

	/** The arms that are up now; null while both hang at the hunter's sides. */
	public static @Nullable Hands handsUp(final LocalPlayer player, final float partial) {
		if (raised(partial) <= 0.0F) {
			return null;
		}
		return new Hands(true, !player.getOffhandItem().isEmpty());
	}

	/** How far behind the eye the body stands, in blocks. */
	public static final float BACK = 0.28F;
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
		if (player.isSpectator() || player.isSleeping() || !player.isAlive() || player.isInvisible()) {
			return false;
		}
		// lying, swimming, gliding or tumbling, the eye would be inside the body: it is left out
		if ((player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING) || RollClient.sample(player.getId()) != null) {
			return false;
		}
		return life.shizune.mistinn.client.story.SceneScreen.current() == null
			&& life.shizune.mistinn.client.training.DropCinematic.view(mc, 1.0F) == null;
	}

	/** Whether the hands have work in front of the eye now. */
	private static boolean working(final Minecraft mc, final LocalPlayer player) {
		if (life.shizune.mistinn.client.medical.TreatmentClient.sample(player.getId()) != null) {
			// treating a wound or mending a plate is done with the body's own hands, in sight below the eye
			return false;
		}
		return player.isSwinging() || player.isUsingItem() || HeavyClient.sample(player, 1.0F) != null
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

	/** How far below their working place the hands are drawn while they are lowered, in the hand pass's own units. */
	public static float lowered(final float partial) {
		float down = 1.0F - raised(partial);
		return down * down * DROP;
	}
}
