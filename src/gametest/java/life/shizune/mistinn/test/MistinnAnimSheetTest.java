package life.shizune.mistinn.test;

import java.nio.file.Path;
import life.shizune.mistinn.client.anim.WeaponSwings;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.world.InteractionHand;

/**
 * Freezes each weapon branch's swing at its beats (top of the wind-up, the strike, the
 * overshoot, the recovery) and screenshots them in first and third person, so the animation
 * can be judged frame by frame. Runs only with -Dmistinn.animsheet=true; writes to animsheet/.
 */
@SuppressWarnings("UnstableApiUsage")
public class MistinnAnimSheetTest implements FabricClientGameTest {
	private static final String[] WEAPONS = {"iron_blade", "iron_greatsword", "iron_spear", "iron_dagger", "iron_hammer"};

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (!Boolean.getBoolean("mistinn.animsheet")) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamerule advance_time false");
			for (String weapon : WEAPONS) {
				world.getServer().runCommand("item replace entity @a weapon.mainhand with mistinn:" + weapon);
				// let the new item finish rising into the hand first
				context.waitTicks(25);
				WeaponStyle style = context.computeOnClient(mc -> WeaponStyle.of(mc.player.getMainHandItem()));
				WeaponSwings.Beat beat = WeaponSwings.beat(style);
				float[] beats = {
					beat.windEnd() * 0.98F,
					beat.windEnd() + (beat.strikeEnd() - beat.windEnd()) * 0.75F,
					beat.strikeEnd() + (beat.overEnd() - beat.strikeEnd()) * 0.5F,
					beat.overEnd() + (1.0F - beat.overEnd()) * 0.4F
				};
				if (style == WeaponStyle.FLURRY) {
					for (int i = 0; i < beats.length; i++) {
						beats[i] = beats[i] / 2.0F;
					}
				}
				for (CameraType camera : new CameraType[] {CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_BACK}) {
					context.runOnClient(mc -> mc.options.setCameraType(camera));
					for (int i = 0; i < beats.length; i++) {
						float at = beats[i];
						context.runOnClient(mc -> {
							mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false);
							WeaponSwings.preview = at;
						});
						context.waitTicks(2);
						String view = camera == CameraType.FIRST_PERSON ? "1p" : "3p";
						context.takeScreenshot(TestScreenshotOptions.of(weapon + "-" + view + "-" + i).disableCounterPrefix().withDestinationDir(Path.of("animsheet")));
						context.runOnClient(mc -> WeaponSwings.preview = -1.0F);
						context.waitTicks(25);
					}
				}
			}
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		}
	}
}
