package life.shizune.mistinn.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import life.shizune.mistinn.client.move.RollClient;
import life.shizune.mistinn.stamina.StaminaService;
import life.shizune.mistinn.weapon.HeavyService;
import life.shizune.mistinn.weapon.ParryService;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The client half of the guard: reads the key, raises the weapon at once (the server keeps the
 * guard's time), and keeps every visible hunter's guard for the renderer: the weapon across the
 * body, a snap outward when a blow is parried, a jolt when one is only blocked.
 */
public final class ParryClient {
	/** A guard as the renderer draws it: what it met, how far up it is (0 to 1), and the snap of a blow met (1 fading to 0). */
	public record Pose(int kind, float weight, float kick) {
	}

	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "mistinn:guard");
	/** Ticks the weapon takes to come up, and to come down again after the guard. */
	private static final float UP = 2.0F;
	private static final float DOWN = 6.0F;
	/** Ticks the snap of a met blow lasts. */
	private static final float SNAP = 5.0F;

	private record Anim(int kind, long start, long met) {
		float ticks() {
			return (System.nanoTime() - this.start) / 1.0E9F * 20.0F;
		}
	}

	/** For the guard's picture in the tests only: when set, every guard is drawn held up, with this snap. */
	public static float preview = -1.0F;

	private static final Map<Integer, Anim> ANIMS = new HashMap<>();
	private static long last = Long.MIN_VALUE / 2;

	private ParryClient() {
	}

	public static @Nullable Pose sample(final int entityId) {
		if (preview >= 0.0F) {
			return new Pose(ParryService.PARRIED, 1.0F, preview);
		}
		Anim anim = ANIMS.get(entityId);
		if (anim == null) {
			return null;
		}
		float ticks = anim.ticks();
		if (ticks >= ParryService.GUARD + DOWN) {
			ANIMS.remove(entityId);
			return null;
		}
		float weight = ticks < UP ? ticks / UP : ticks < ParryService.GUARD ? 1.0F : 1.0F - (ticks - ParryService.GUARD) / DOWN;
		float kick = 0.0F;
		if (anim.met() != 0L) {
			float since = (System.nanoTime() - anim.met()) / 1.0E9F * 20.0F;
			kick = since < SNAP ? 1.0F - since / SNAP : 0.0F;
		}
		return new Pose(anim.kind(), Mth.clamp(weight, 0.0F, 1.0F), kick);
	}

	/** Called every client tick with the guard key. */
	public static void tick(final Minecraft mc, final KeyMapping key) {
		while (key.consumeClick()) {
			if (mc.player != null && mc.gui.screen() == null) {
				raise(mc.player);
			}
		}
	}

	private static void raise(final LocalPlayer player) {
		if (!StaminaService.applies(player) || WeaponStyle.of(player.getMainHandItem()) == null || RollClient.busy() || player.isPassenger()) {
			return;
		}
		if (System.nanoTime() - last < ParryService.COOLDOWN * 50_000_000L) {
			return;
		}
		if (!StaminaService.has(player, ParryService.COST)) {
			player.sendOverlayMessage(Component.translatable("message.mistinn.no_stamina"));
			return;
		}
		last = System.nanoTime();
		HeavyService.breakCharge(player);
		ANIMS.put(player.getId(), new Anim(ParryService.RAISED, System.nanoTime(), 0L));
		ClientPlayNetworking.send(new ParryService.RaisePayload());
	}

	/** The server showed a guard: raised (ours, or another hunter's), or what it met. */
	public static void received(final ParryService.GuardPayload payload) {
		Anim existing = ANIMS.get(payload.entity());
		if (payload.kind() == ParryService.RAISED) {
			if (existing != null && existing.ticks() < ParryService.GUARD / 2.0F) {
				// our own guard, already up since the key was pressed
				return;
			}
			ANIMS.put(payload.entity(), new Anim(ParryService.RAISED, System.nanoTime(), 0L));
			return;
		}
		// a blow met: the guard stays where it is and shows it
		long start = existing != null ? existing.start() : System.nanoTime();
		ANIMS.put(payload.entity(), new Anim(payload.kind(), start, System.nanoTime()));
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && payload.entity() == player.getId()) {
			life.shizune.mistinn.client.anim.CameraSwing.met(payload.kind() == ParryService.PARRIED);
		}
	}

	public static void forgetAll() {
		ANIMS.clear();
		last = Long.MIN_VALUE / 2;
	}

	/**
	 * Seen from outside: the weapon arm comes up and across in front of the chest, the other arm
	 * braced behind it; a parry snaps the weapon arm out and up.
	 */
	public static void thirdPerson(final Pose pose, final ModelPart arm, final ModelPart other, final int side) {
		float w = pose.weight();
		float snap = pose.kind() == ParryService.PARRIED ? pose.kick() : -0.4F * pose.kick();
		arm.xRot = Mth.lerp(w, arm.xRot, -1.45F - 0.35F * snap);
		arm.yRot = Mth.lerp(w, arm.yRot, -0.55F * side + 0.25F * side * snap);
		arm.zRot = Mth.lerp(w, arm.zRot, 0.0F);
		other.xRot = Mth.lerp(w, other.xRot, -1.15F);
		other.yRot = Mth.lerp(w, other.yRot, 0.6F * side);
		other.zRot = Mth.lerp(w, other.zRot, 0.0F);
	}

	/** In one's own view: the weapon comes up and lies across the view, edge out; a parry knocks it out and up, a block back. */
	public static void firstPerson(final Pose pose, final PoseStack stack, final int side) {
		float w = pose.weight();
		float snap = pose.kind() == ParryService.PARRIED ? pose.kick() : -0.5F * pose.kick();
		stack.translate(-0.10F * side * w, (-0.04F + 0.05F * snap) * w, (-0.06F * snap) * w);
		stack.rotateDegrees(Axis.YP, -25.0F * side * w);
		stack.rotateDegrees(Axis.ZP, (68.0F + 8.0F * snap) * side * w);
	}
}
