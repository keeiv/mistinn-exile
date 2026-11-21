package life.shizune.mistinn.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import life.shizune.mistinn.weapon.HeavyService;
import life.shizune.mistinn.weapon.WeaponItem;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * 蓄力重擊, as drawn: while the use key is held the weapon is drawn back into the branch's charge
 * stance ({@code anim/heavy/<branch>_charge.json}, trembling once full); on letting go the strike
 * ({@code anim/heavy/<branch>.json}) plays from that stance. The charge is read from the hunter's
 * item use, which every client sees; the strike starts at once for the hunter who let go and on
 * the server's word for everyone watching.
 */
public final class HeavyClient {
	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "Mistinn heavy blow");

	public enum Phase {
		CHARGE, STRIKE
	}

	/**
	 * One frame of a heavy blow: {@code progress} through the stance (charge share, 0..1) or the
	 * strike, {@code charge} how full the blow is, {@code time} in ticks for the tremble.
	 */
	public record Pose(Phase phase, WeaponStyle style, float progress, float charge, float time) {
	}

	private record Strike(WeaponStyle style, float charge, long start) {
	}

	private static final Map<Integer, Strike> STRIKES = new HashMap<>();

	/** For the frame sheets only: when set, the local hunter is drawn frozen in this pose. */
	public static @Nullable Pose preview;

	private HeavyClient() {
	}

	public static Clip chargeClip(final WeaponStyle style) {
		return Clip.get("heavy/" + style.id + "_charge");
	}

	public static Clip strikeClip(final WeaponStyle style) {
		return Clip.get("heavy/" + style.id);
	}

	public static Clip clip(final Pose pose) {
		return pose.phase() == Phase.CHARGE ? chargeClip(pose.style()) : strikeClip(pose.style());
	}

	public static int strikeTicks(final WeaponStyle style) {
		return HeavyService.shape(style, null).strikeTicks();
	}

	private static long now() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0L : mc.level.getGameTime();
	}

	/** Someone else let a heavy blow go. */
	public static void received(final HeavyService.HeavyAnimPayload payload) {
		WeaponStyle[] styles = WeaponStyle.values();
		if (payload.style() < 0 || payload.style() >= styles.length) {
			return;
		}
		STRIKES.put(payload.entity(), new Strike(styles[payload.style()], payload.charge(), now()));
	}

	/** This hunter let go after {@code held} ticks: strike at once if the server will. */
	public static void released(final Player player, final int held) {
		WeaponStyle style = WeaponStyle.of(player.getMainHandItem());
		if (style == null) {
			return;
		}
		float charge = HeavyService.charge(player, style, held);
		// the same question the server asks; a roll or slide has already ended the charge on both sides
		if (charge < HeavyService.MIN_CHARGE || !HeavyService.canLetGo(player, player.getMainHandItem())) {
			return;
		}
		STRIKES.put(player.getId(), new Strike(style, charge, now()));
		CameraSwing.heavy(style);
	}

	/** Leaving a world: no strike carries over to whoever has the same id in the next. */
	public static void forgetAll() {
		STRIKES.clear();
		preview = null;
	}

	/** Test hook: starts a strike on any entity as if its hunter had let go. */
	public static void strike(final int entity, final WeaponStyle style, final float charge) {
		STRIKES.put(entity, new Strike(style, charge, now()));
	}

	/** The heavy blow this entity is in now, or null. */
	public static @Nullable Pose sample(final Entity entity, final float partial) {
		if (preview != null && entity == Minecraft.getInstance().player) {
			return preview;
		}
		long now = now();
		Strike strike = STRIKES.get(entity.getId());
		if (strike != null) {
			float t = (now - strike.start() + partial) / strikeTicks(strike.style());
			if (t >= 0.0F && t < 1.0F) {
				return new Pose(Phase.STRIKE, strike.style(), t, strike.charge(), now + partial);
			}
			if (t >= 1.0F || t < 0.0F) {
				STRIKES.remove(entity.getId());
			}
		}
		if (entity instanceof LivingEntity living && living.isUsingItem() && living.getUseItem().getItem() instanceof WeaponItem) {
			WeaponStyle style = WeaponStyle.of(living.getUseItem());
			if (style != null) {
				float held = living.getTicksUsingItem() + partial;
				int full = entity instanceof Player player ? HeavyService.chargeTicks(player, style) : style.chargeTicks;
				float charge = Mth.clamp(held / full, 0.0F, 1.0F);
				return new Pose(Phase.CHARGE, style, charge, charge, now + partial);
			}
		}
		return null;
	}

	/** Seconds of tremble once the charge is full: a few hundredths of a radian. */
	private static float tremble(final Pose pose) {
		if (pose.phase() != Phase.CHARGE || pose.progress() < 1.0F) {
			return 0.0F;
		}
		return Mth.sin(pose.time() * 2.7F) * 0.025F;
	}

	public static void thirdPerson(final Pose pose, final HumanoidModel<?> model, final ModelPart arm, final ModelPart other, final int side, final float ageScale) {
		Clip clip = clip(pose);
		float w = pose.phase() == Phase.CHARGE ? clip.weightIn(pose.progress()) : clip.weightOut(pose.progress());
		WeaponSwings.thirdPerson(clip, pose.progress(), w, model, arm, other, side, ageScale);
		arm.xRot += tremble(pose);
	}

	public static void thirdPersonItem(final Pose pose, final PoseStack stack) {
		WeaponSwings.thirdPersonItem(clip(pose), pose.progress(), stack);
	}

	public static void firstPerson(final Pose pose, final PoseStack stack, final int side) {
		WeaponSwings.firstPerson(clip(pose), pose.progress(), stack, side);
		float shake = tremble(pose);
		if (shake != 0.0F) {
			stack.translate(shake * 0.3F, shake * 0.2F, 0.0F);
		}
	}

	public static void camera(final Pose pose, final int side, final float[] out) {
		WeaponSwings.camera(clip(pose), pose.progress(), side, out);
	}
}
