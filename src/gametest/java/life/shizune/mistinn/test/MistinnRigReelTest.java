package life.shizune.mistinn.test;

import java.nio.file.Path;
import java.util.Set;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * 胸掛: films every tier of chest rig worn, on a row of mannequins from the front, the side, the back and the front
 * quarter, bare and over a chestplate, then through the wearer's own eyes looking down. Nothing is asserted; the frames
 * in {@code rigs/} are for judging the rigs by eye. Runs only when picked with {@code -PonlyTest=MistinnRigReelTest}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnRigReelTest implements FabricClientGameTest {
	private static final Path OUT = Path.of("rigs");
	private static final int TIERS = 7;
	/** How far apart the mannequins stand, in blocks. */
	private static final double GAP = 2.6;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (!getClass().getSimpleName().equals(System.getProperty("mistinn.only"))) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule spawn_mobs false");
			BlockPos stage = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos pos = player.blockPosition().above(14).immutable();
				for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-4, -1, -7), pos.offset(21, -1, 7))) {
					player.level().setBlockAndUpdate(floor, Blocks.SMOOTH_STONE.defaultBlockState());
				}
				player.setGameMode(GameType.CREATIVE);
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				return pos;
			});
			// the mannequins face +z, toward the camera in front of them
			for (int t = 0; t < TIERS; t++) {
				double x = stage.getX() + 0.5 + t * GAP;
				world.getServer().runCommand(String.format(java.util.Locale.ROOT, "summon minecraft:mannequin %.2f %d %.2f {Rotation:[0.0f,0.0f],Tags:[\"rig%d\"]}",
					x, stage.getY(), stage.getZ() + 0.5, t));
			}
			context.waitTicks(10);
			wear(world, false);
			context.waitTicks(10);
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			double middle = stage.getX() + 0.5 + (TIERS - 1) * GAP / 2.0;
			for (boolean armour : new boolean[] {false, true}) {
				wear(world, armour);
				context.waitTicks(10);
				String tag = armour ? "armour" : "bare";
				view(context, world, middle, stage.getY() + 0.8, stage.getZ() + 9.5, 180.0F, 4.0F);
				shot(context, tag + "-front");
				view(context, world, middle, stage.getY() + 0.8, stage.getZ() - 8.5, 0.0F, 4.0F);
				shot(context, tag + "-back");
				// close by each one from the side and the front quarter, two at a time
				for (int t = 0; t < TIERS; t++) {
					double x = stage.getX() + 0.5 + t * GAP;
					view(context, world, x - 2.2, stage.getY() + 0.7, stage.getZ() + 0.5, -90.0F, 8.0F);
					shot(context, tag + "-side-" + t);
					view(context, world, x - 1.1, stage.getY() + 0.75, stage.getZ() + 1.9, 210.0F, 12.0F);
					shot(context, tag + "-quarter-" + t);
				}
			}

			// through the wearer's own eyes, looking down at the chest
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.setGameMode(GameType.SURVIVAL);
				player.getInventory().clearContent();
				player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() - 3.5, Set.of(), 0.0F, 62.0F, true);
			});
			context.waitTicks(20);
			for (int t = 0; t < TIERS; t++) {
				int tier = t;
				world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setAttached(MistinnAttachments.WORN_RIG, tier));
				context.waitTicks(6);
				context.takeScreenshot(TestScreenshotOptions.of("own-" + t).disableCounterPrefix().withDestinationDir(OUT));
			}
		}
	}

	/** Puts each mannequin's rig on it, over a chestplate or not. */
	private static void wear(final TestSingleplayerContext world, final boolean armour) {
		world.getServer().runOnServer(server -> {
			for (int t = 0; t < TIERS; t++) {
				int tier = t;
				Entity mannequin = server.overworld().getEntities(net.minecraft.world.entity.EntityTypes.MANNEQUIN, e -> e.entityTags().contains("rig" + tier)).getFirst();
				mannequin.setAttached(MistinnAttachments.WORN_RIG, tier);
				((net.minecraft.world.entity.LivingEntity) mannequin).setItemSlot(EquipmentSlot.CHEST, armour ? new ItemStack(MistinnItems.ARMORS.get(3)) : ItemStack.EMPTY);
			}
		});
	}

	private static void view(final ClientGameTestContext context, final TestSingleplayerContext world, final double x, final double y, final double z, final float yaw,
		final float pitch) {
		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().teleportTo(server.overworld(), x, y, z, Set.of(), yaw, pitch, true));
		context.waitTicks(4);
	}

	private static void shot(final ClientGameTestContext context, final String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(OUT));
	}
}
