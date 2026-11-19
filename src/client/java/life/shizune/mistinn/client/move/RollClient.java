package life.shizune.mistinn.client.move;

import java.util.HashMap;
import java.util.Map;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.stamina.RollService;
import life.shizune.mistinn.stamina.StaminaService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The client half of rolls and dives: reads the key, moves the hunter (movement is the
 * client's), asks the server for the dodge, and keeps every visible roller's motion for the
 * renderer and the first-person view.
 */
public final class RollClient {
	/** A roll or dive in progress, as the renderer draws it. */
	public record Pose(RollService.Kind kind, float t, float yaw, float leftArm, float rightArm) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "mistinn:roll");

	private record Anim(RollService.Kind kind, long start, float yaw, float leftArm, float rightArm) {
		float t() {
			float seconds = (System.nanoTime() - this.start) / 1.0E9F;
			int ticks = this.kind == RollService.Kind.DIVE ? RollService.DIVE_TICKS : RollService.ROLL_TICKS;
			return seconds * 20.0F / ticks;
		}
	}

	/** For the animation sheet test only: when 0 or more, every roll is drawn frozen at this point. */
	public static float preview = -1.0F;

	private static final Map<Integer, Anim> ANIMS = new HashMap<>();
	private static @Nullable Vec3 push;
	private static long lastRoll;

	private RollClient() {
	}

	public static @Nullable Pose sample(final int entityId) {
		Anim anim = ANIMS.get(entityId);
		if (anim == null) {
			return null;
		}
		float t = preview >= 0.0F ? preview : anim.t();
		if (t >= 1.0F) {
			ANIMS.remove(entityId);
			return null;
		}
		return new Pose(anim.kind(), t, anim.yaw(), anim.leftArm(), anim.rightArm());
	}

	/** The local hunter is mid-roll: no swings until it ends. */
	public static boolean busy() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && sample(player.getId()) != null;
	}

	/** Another player (or the server echoing ours) rolled. */
	public static void received(final RollService.RollAnimPayload payload) {
		Anim existing = ANIMS.get(payload.entity());
		if (existing != null && existing.t() < 0.5F) {
			// our own roll, already started when the key was pressed
			return;
		}
		ANIMS.put(payload.entity(), new Anim(RollService.Kind.values()[payload.kind()], System.nanoTime(), payload.yaw(), payload.leftArm(), payload.rightArm()));
	}

	/** Called every client tick with the roll key. */
	public static void tick(final Minecraft mc, final KeyMapping key) {
		LocalPlayer player = mc.player;
		while (key.consumeClick()) {
			if (player != null) {
				tryRoll(player);
			}
		}
		if (player == null) {
			return;
		}
		Pose pose = sample(player.getId());
		if (pose != null && push != null && pose.t() < 0.65F) {
			// keep the roll travelling against friction, fading out
			double fade = 1.0 - pose.t() / 0.65;
			player.setDeltaMovement(push.x * fade, player.getDeltaMovement().y, push.z * fade);
		}
		if (pose == null) {
			push = null;
		}
	}

	private static void tryRoll(final LocalPlayer player) {
		if (!StaminaService.applies(player) || !player.onGround() || busy() || player.isPassenger()) {
			return;
		}
		if (System.nanoTime() - lastRoll < RollService.COOLDOWN * 50_000_000L) {
			return;
		}
		Body body = BodyService.get(player);
		RollService.Kind kind = RollService.kind(body);
		if (!StaminaService.has(player, RollService.cost(kind))) {
			player.sendOverlayMessage(Component.translatable("message.mistinn.no_stamina"));
			return;
		}
		// the movement keys choose the way; with none held it is a step back
		Vec2 move = player.input.getMoveVector();
		float turn = move.lengthSquared() < 1.0E-4F ? 180.0F : (float) Math.toDegrees(Math.atan2(-move.x, move.y));
		float yaw = player.getYRot() + turn;
		float rad = yaw * Mth.DEG_TO_RAD;
		Vec3 dir = new Vec3(-Mth.sin(rad), 0.0, Mth.cos(rad));
		float speed = kind == RollService.Kind.DIVE ? 0.85F : RollService.rollSpeed(body);
		push = dir.scale(speed);
		player.setDeltaMovement(push.x, kind == RollService.Kind.DIVE ? 0.32 : 0.12, push.z);
		player.setSprinting(false);
		lastRoll = System.nanoTime();
		ANIMS.put(player.getId(), new Anim(kind, System.nanoTime(), yaw, body.get(BodyPart.LEFT_ARM) / BodyPart.MAX, body.get(BodyPart.RIGHT_ARM) / BodyPart.MAX));
		ClientPlayNetworking.send(new RollService.RollPayload(yaw));
	}

	/**
	 * First person, every frame: the view ducks through a roll (leaning to the weaker arm); a
	 * dive pitches it down and jolts on landing. Degrees of yaw, pitch and roll.
	 */
	public static float[] viewOffsets() {
		float[] extra = new float[3];
		LocalPlayer player = Minecraft.getInstance().player;
		Pose pose = player == null ? null : sample(player.getId());
		if (pose == null) {
			return extra;
		}
		float t = pose.t();
		if (pose.kind() == RollService.Kind.ROLL) {
			float lopsided = pose.rightArm() - pose.leftArm();
			extra[1] = 32.0F * Mth.sin(t * Mth.PI);
			extra[2] = (6.0F + 14.0F * Math.abs(lopsided)) * Mth.sign(lopsided == 0.0F ? 1.0F : lopsided) * Mth.sin(t * Mth.PI);
		} else {
			float down = t < 0.5F ? t / 0.5F : 1.0F - (t - 0.5F) / 0.5F;
			extra[1] = 48.0F * down;
			if (t > 0.45F && t < 0.6F) {
				extra[2] = 5.0F * Mth.sin((t - 0.45F) * 120.0F);
			}
		}
		return extra;
	}
}
