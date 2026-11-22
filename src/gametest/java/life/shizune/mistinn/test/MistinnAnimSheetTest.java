package life.shizune.mistinn.test;

import java.nio.file.Path;
import life.shizune.mistinn.client.anim.WeaponSwings;
import life.shizune.mistinn.client.move.RollClient;
import life.shizune.mistinn.stamina.RollService;
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
		if (TestFilter.skip(getClass())) {
			return;
		}
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
				// the top of the anticipation, the contact, mid follow-through, the recovery
				life.shizune.mistinn.client.anim.Clip clip = WeaponSwings.clip(style);
				float[] beats = {
					clip.key(1),
					clip.key(2),
					(clip.key(2) + clip.key(3)) / 2.0F,
					(clip.key(clip.keyCount() - 2) + 1.0F) / 2.0F
				};
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
			// every tier's full set, mask included, front and back
			world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
			world.getServer().runCommand("tp @a ~ ~ ~12");
			context.waitTicks(20);
			for (int tier = 0; tier < 7; tier++) {
				int t = tier;
				world.getServer().runOnServer(server -> {
					var player = server.getPlayerList().getPlayers().getFirst();
					player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(life.shizune.mistinn.registry.MistinnItems.HELMETS.get(t)));
					player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(life.shizune.mistinn.registry.MistinnItems.ARMORS.get(t)));
					boolean legs = t < life.shizune.mistinn.registry.MistinnItems.LEGGINGS.size();
					player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, legs
						? new net.minecraft.world.item.ItemStack(life.shizune.mistinn.registry.MistinnItems.LEGGINGS.get(t)) : net.minecraft.world.item.ItemStack.EMPTY);
					player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, legs ? new net.minecraft.world.item.ItemStack(life.shizune.mistinn.registry.MistinnItems.BOOTS.get(t))
						: net.minecraft.world.item.ItemStack.EMPTY);
					life.shizune.mistinn.grid.GridService.set(player,
						life.shizune.mistinn.grid.GridService.get(player).withMask(new net.minecraft.world.item.ItemStack(life.shizune.mistinn.registry.MistinnItems.MASKS.get(t))));
				});
				for (CameraType camera : new CameraType[] {CameraType.THIRD_PERSON_FRONT, CameraType.THIRD_PERSON_BACK}) {
					context.runOnClient(mc -> mc.options.setCameraType(camera));
					context.waitTicks(6);
					String view = camera == CameraType.THIRD_PERSON_FRONT ? "front" : "back";
					context.takeScreenshot(TestScreenshotOptions.of("gear-" + t + "-" + view).disableCounterPrefix().withDestinationDir(Path.of("animsheet")));
				}
			}
			world.getServer().runCommand("tp @a ~ ~ ~-12");
			context.waitTicks(10);

			// rolls: whole arms, a destroyed right arm, both arms destroyed; and the dive
			world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
			// out in the open, clear of the test house, so the camera sees the whole body
			world.getServer().runCommand("tp @a ~ ~ ~12");
			context.waitTicks(20);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			float[][] arms = {{1.0F, 1.0F}, {1.0F, 0.0F}, {0.0F, 0.0F}};
			String[] names = {"roll", "roll-right-arm-gone", "roll-armless", "dive"};
			for (int n = 0; n < names.length; n++) {
				int kind = n == 3 ? 1 : 0;
				float[] a = n == 3 ? arms[0] : arms[n];
				for (int i = 0; i < 3; i++) {
					float at = 0.25F * (i + 1);
					context.runOnClient(mc -> {
						RollClient.received(new RollService.RollAnimPayload(mc.player.getId(), kind, mc.player.getYRot() + 90.0F, a[0], a[1]));
						RollClient.preview = at;
					});
					context.waitTicks(2);
					context.takeScreenshot(TestScreenshotOptions.of(names[n] + "-" + i).disableCounterPrefix().withDestinationDir(Path.of("animsheet")));
					context.runOnClient(mc -> RollClient.preview = -1.0F);
					context.waitTicks(20);
				}
			}
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		}
	}
}
