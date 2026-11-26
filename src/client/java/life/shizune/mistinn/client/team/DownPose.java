package life.shizune.mistinn.client.team;

import com.mojang.blaze3d.vertex.PoseStack;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.anim.Poser;
import life.shizune.mistinn.client.death.DeathScene;
import life.shizune.mistinn.team.DownService;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * How a downed hunter lies: the fall of a hunter struck down (the death scene's kneel), buckling onto
 * a knee with a hand at the wound and toppling onto the side, held there breathing, so teammates tell
 * them from a legless hunter crawling.
 */
public final class DownPose {
	/** Seconds since the hunter went down, or absent when they are not down. */
	public static final RenderStateDataKey<Float> KEY = RenderStateDataKey.create(() -> "mistinn:down");
	/** Where in the fall the body comes to rest on its side. */
	private static final float REST = 0.95F;
	private static final String CLIP = "death/kneel";

	private DownPose() {
	}

	/** Seconds since the entity went down, or null when it is not. */
	public static @Nullable Float sample(final Avatar entity, final float partialTicks) {
		if (!(entity instanceof Player player)) {
			return null;
		}
		DownService.Down down = DownService.get(player);
		if (down == null) {
			return null;
		}
		// a hunter seen going down falls; one who was already lying when they came into view just lies there
		float seconds = down.since() <= 0L ? DeathScene.GONE : (entity.level().getGameTime() - down.since() + partialTicks) / 20.0F;
		return Math.max(0.0F, seconds);
	}

	private static float progress(final float seconds) {
		return Mth.clamp(seconds / DeathScene.GONE, 0.0F, REST);
	}

	/** The whole body's turn and drop. */
	public static void root(final float seconds, final PoseStack pose, final float scale) {
		Poser.root(Clip.get(CLIP), progress(seconds), pose, 1, scale);
	}

	/** The limbs, with a slow breath through the chest once lying. */
	public static void limbs(final float seconds, final HumanoidModel<?> model) {
		Clip clip = Clip.get(CLIP);
		float p = progress(seconds);
		Poser.limbs(clip, p, model, 1, clip.weightIn(p));
		if (p >= REST) {
			float breath = Mth.sin(seconds * 2.4F) * 0.05F;
			model.rightArm.xRot += breath;
			model.leftArm.xRot -= breath * 0.6F;
			model.head.xRot += breath * 0.4F;
		}
		model.head.yRot = clip.has("limbs.head_y") ? model.head.yRot : 0.0F;
	}
}
