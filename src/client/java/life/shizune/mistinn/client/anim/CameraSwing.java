package life.shizune.mistinn.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The first-person view follows the swing: it is pulled round with the wind-up, thrown along
 * with the strike and carried past it, as if the weapon's weight dragged the head with it.
 * A hit jolts the view. The roll and dive add their own motion through {@link #extra}.
 * Hunters can turn the swing motion off; the setting is kept in the config folder.
 */
public final class CameraSwing {
	private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("mistinn-client.properties");
	private static boolean enabled = true;
	private static boolean loaded;
	/** Time of the last hit, for the jolt. */
	private static long hitAt = Long.MIN_VALUE;

	/** Extra view motion from moves other than swings (the roll), in degrees: yaw, pitch, roll. */
	public static float[] extra = new float[3];

	private CameraSwing() {
	}

	private record View(WeaponSwings.Channel yaw, WeaponSwings.Channel pitch, WeaponSwings.Channel roll) {
	}

	private static View view(final WeaponStyle style) {
		return switch (style) {
			case SLASH -> new View(new WeaponSwings.Channel(6.0F, -9.0F, -3.0F), new WeaponSwings.Channel(-4.0F, 6.0F, 2.0F), new WeaponSwings.Channel(6.0F, -8.0F, -3.0F));
			case CLEAVE -> new View(new WeaponSwings.Channel(0.0F, 0.0F, 0.0F), new WeaponSwings.Channel(-9.0F, 13.0F, 4.0F), new WeaponSwings.Channel(2.0F, -3.0F, -1.0F));
			case THRUST -> new View(new WeaponSwings.Channel(3.0F, -4.0F, -1.0F), new WeaponSwings.Channel(2.0F, -3.0F, -1.0F), new WeaponSwings.Channel(2.0F, -3.0F, -1.0F));
			case FLURRY -> new View(new WeaponSwings.Channel(3.0F, -5.0F, -1.5F), new WeaponSwings.Channel(-1.0F, 2.0F, 0.5F), new WeaponSwings.Channel(3.0F, -4.0F, -1.5F));
			case SMASH -> new View(new WeaponSwings.Channel(12.0F, -16.0F, -6.0F), new WeaponSwings.Channel(-2.0F, 4.0F, 1.5F), new WeaponSwings.Channel(6.0F, -9.0F, -4.0F));
		};
	}

	public static boolean enabled() {
		load();
		return enabled;
	}

	public static void toggle() {
		load();
		enabled = !enabled;
		Properties properties = new Properties();
		properties.setProperty("swing_camera", Boolean.toString(enabled));
		try {
			Files.createDirectories(CONFIG.getParent());
			try (var out = Files.newBufferedWriter(CONFIG)) {
				properties.store(out, "Mistinn: Exile client settings");
			}
		} catch (IOException ignored) {
			// the setting still holds for this session
		}
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		if (Files.isRegularFile(CONFIG)) {
			Properties properties = new Properties();
			try (var in = Files.newBufferedReader(CONFIG)) {
				properties.load(in);
				enabled = Boolean.parseBoolean(properties.getProperty("swing_camera", "true"));
			} catch (IOException ignored) {
				enabled = true;
			}
		}
	}

	/** Called when the local player's blow lands. */
	public static void hit() {
		hitAt = System.nanoTime();
	}

	/** Adds the swing and jolt to the view; called while vanilla tilts the view for damage. */
	public static void apply(final PoseStack pose, final float partialTicks) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		float yaw = extra[0];
		float pitch = extra[1];
		float roll = extra[2];
		WeaponStyle style = WeaponStyle.of(player.getMainHandItem());
		float p = WeaponSwings.progress(player.getSwingAnimation(partialTicks));
		if (enabled() && style != null && p > 0.0F) {
			int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
			float local = p;
			if (style == WeaponStyle.FLURRY) {
				local = p < 0.5F ? p * 2.0F : p * 2.0F - 1.0F;
				side = p < 0.5F ? side : -side;
			}
			WeaponSwings.Beat beat = WeaponSwings.beat(style);
			View view = view(style);
			yaw += side * view.yaw().at(beat, local, 0.0F);
			pitch += view.pitch().at(beat, local, 0.0F);
			roll += side * view.roll().at(beat, local, 0.0F);
		}
		float since = (System.nanoTime() - hitAt) / 1.0E9F;
		if (enabled() && since >= 0.0F && since < 0.3F) {
			// a sharp jolt that dies away fast
			float k = (1.0F - since / 0.3F);
			k *= k;
			pitch += 2.6F * k * Mth.sin(since * 70.0F);
			roll += 1.8F * k * Mth.cos(since * 55.0F);
		}
		if (yaw != 0.0F || pitch != 0.0F || roll != 0.0F) {
			pose.rotateDegrees(Axis.ZP, roll);
			pose.rotateDegrees(Axis.XP, pitch);
			pose.rotateDegrees(Axis.YP, yaw);
		}
	}
}
