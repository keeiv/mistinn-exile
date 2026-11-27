package life.shizune.mistinn.test;

import java.nio.file.Path;
import life.shizune.mistinn.client.path.RustApostleRenderer;
import life.shizune.mistinn.path.ApostleMove;
import life.shizune.mistinn.path.RustApostle;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * 3412's choreography, frame by frame: two of them on a platform in the sky, one seen from the weapon side and one
 * from the front, held at each tick of each move (and in each stance), shot for the contact sheets the moves are
 * judged on. Not a pass or fail: the pictures are the result.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnApostleReelTest implements FabricClientGameTest {
	/** Ticks between frames. */
	private static final int STEP = 2;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 6000");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("weather clear");
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos floor = player.blockPosition().above(30);
				for (BlockPos stone : BlockPos.betweenClosed(floor.offset(-2, -1, -6), floor.offset(10, -1, 6))) {
					level.setBlockAndUpdate(stone, Blocks.SMOOTH_STONE.defaultBlockState());
				}
				// one seen from the weapon side, one from the front
				spawn(level, floor.getX() + 5.5, floor.getY(), floor.getZ() - 1.4, 0.0F);
				spawn(level, floor.getX() + 5.5, floor.getY(), floor.getZ() + 1.9, 62.0F);
				player.setGameMode(GameType.SPECTATOR);
				player.teleportTo(level, floor.getX() + 0.5, floor.getY() + 1.15, floor.getZ() + 0.3, java.util.Set.of(), -90.0F, 4.0F, true);
			});
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			context.waitTicks(40);

			if (Boolean.parseBoolean(System.getProperty("mistinn.reel.calibrate", "false"))) {
				// how each branch sits in the hand: the arm at four angles, the weapon turned six ways
				for (WeaponStyle style : new WeaponStyle[] {WeaponStyle.SLASH, WeaponStyle.CLEAVE, WeaponStyle.THRUST, WeaponStyle.FLURRY, WeaponStyle.REAP}) {
					hold(context, world, style);
					for (int arm : new int[] {-20, -50, -90, -130}) {
						for (int item : new int[] {-150, -90, -45, 0, 45, 90}) {
							float[] arms = new float[12];
							arms[0] = arm;
							arms[3] = 0.0F;
							arms[10] = item;
							context.runOnClient(mc -> {
								RustApostleRenderer.previewMove = null;
								RustApostleRenderer.previewStyle = style;
								RustApostleRenderer.previewArms = arms;
							});
							context.waitTicks(1);
							shot(context, "cal_" + style.id + "_a" + (-arm) + "_i" + (item + 200));
						}
					}
				}
				context.runOnClient(mc -> RustApostleRenderer.previewArms = null);
			}
			for (WeaponStyle style : new WeaponStyle[] {WeaponStyle.SLASH, WeaponStyle.CLEAVE, WeaponStyle.THRUST, WeaponStyle.FLURRY, WeaponStyle.REAP}) {
				hold(context, world, style);
				context.runOnClient(mc -> {
					RustApostleRenderer.previewMove = null;
					RustApostleRenderer.previewStyle = style;
				});
				context.waitTicks(3);
				shot(context, "ready_" + style.id + "_00");
			}
			if (Boolean.getBoolean("mistinn.reel.short")) {
				return;
			}
			for (ApostleMove move : ApostleMove.values()) {
				WeaponStyle style = move.style != null ? move.style : WeaponStyle.SLASH;
				hold(context, world, style);
				for (int t = 0; t <= move.length; t += STEP) {
					int tick = t;
					context.runOnClient(mc -> {
						RustApostleRenderer.previewStyle = style;
						RustApostleRenderer.previewMove = move;
						RustApostleRenderer.previewTicks = tick;
					});
					context.waitTicks(1);
					shot(context, move.id + "_" + String.format(java.util.Locale.ROOT, "%02d", tick));
				}
			}
			context.runOnClient(mc -> {
				RustApostleRenderer.previewMove = null;
				RustApostleRenderer.previewStyle = null;
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
	}

	/** Puts the branch in every apostle's hand. */
	private static void hold(final ClientGameTestContext context, final TestSingleplayerContext world, final WeaponStyle style) {
		world.getServer().runOnServer(server -> {
			for (RustApostle hunter : server.overworld().getEntitiesOfClass(RustApostle.class,
				server.getPlayerList().getPlayers().getFirst().getBoundingBox().inflate(32.0))) {
				hunter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
					new net.minecraft.world.item.ItemStack(life.shizune.mistinn.registry.MistinnItems.WEAPONS.get(style).get(life.shizune.mistinn.weapon.WeaponMaterial.NETHERITE)));
			}
		});
		context.waitTicks(2);
	}

	private static void spawn(final ServerLevel level, final double x, final double y, final double z, final float yaw) {
		RustApostle hunter = MistinnEntities.RUST_APOSTLE.create(level, EntitySpawnReason.COMMAND);
		hunter.setNoAi(true);
		hunter.setPermanentlyInvulnerable(true);
		hunter.snapTo(x, y, z, yaw, 0.0F);
		hunter.setYHeadRot(yaw);
		hunter.setYBodyRot(yaw);
		level.addFreshEntity(hunter);
	}

	private static void shot(final ClientGameTestContext context, final String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(Path.of("photos", "apostle_reel")));
	}
}
