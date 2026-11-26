package life.shizune.mistinn.test;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import life.shizune.mistinn.client.team.AidPose;
import life.shizune.mistinn.client.team.DownPose;
import life.shizune.mistinn.client.team.DragPose;
import life.shizune.mistinn.team.DownService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * The downed poses as teammates see them, on hunters drawn only on this client: one lying there
 * waiting, one still going down (on the knees), a rescuer dragging a third, and one kneeling to bring
 * the first back where they lie. Each is its own motion (not the death scene's), the dragged one lies
 * head toward the rescuer, the rescuer bends into the pull, and the one kneeling is down at the side
 * with the hands on the wound while the one lying there turns their head to them.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnDownPoseTest implements FabricClientGameTest {
	private static final int LYING = 900001;
	private static final int RESCUER = 900002;
	private static final int DRAGGED = 900003;
	private static final int AIDER = 900004;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set noon");
			BlockPos[] centre = new BlockPos[1];
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos c = player.blockPosition().above(40).immutable();
				for (BlockPos floor : BlockPos.betweenClosed(c.offset(-8, -1, -8), c.offset(8, -1, 8))) {
					player.level().setBlockAndUpdate(floor, Blocks.SMOOTH_STONE.defaultBlockState());
				}
				// the camera: off to the east, looking west across the three of them
				player.teleportTo(server.overworld(), c.getX() + 4.5, c.getY(), c.getZ() + 0.5, Set.of(), 90.0F, 18.0F, true);
				centre[0] = c;
			});
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				ClientLevel level = mc.level;
				long now = level.getGameTime();
				BlockPos c = centre[0];
				RemotePlayer lying = remote(level, LYING, "lying", c.getX() + 0.5, c.getY(), c.getZ() - 3.0, 0.0F);
				lying.setAttached(DownService.DOWN, new DownService.Down(1200, now - 120, 0L, -1, 0, false));
				RemotePlayer rescuer = remote(level, RESCUER, "rescuer", c.getX() + 0.5, c.getY(), c.getZ() + 1.5, 180.0F);
				RemotePlayer dragged = remote(level, DRAGGED, "dragged", c.getX() + 0.5, c.getY(), c.getZ() + 1.5 + DownService.DRAG_BEHIND, 0.0F);
				dragged.setAttached(DownService.DOWN, new DownService.Down(1200, now - 200, 0L, rescuer.getId(), 10, true));
			});
			context.waitTicks(15);
			String seen = context.computeOnClient(mc -> {
				net.minecraft.world.entity.Entity entity = mc.level.getEntity(LYING);
				if (!(entity instanceof RemotePlayer remote)) {
					return "no hunter " + LYING + ": " + entity;
				}
				DownPose.State lying = DownPose.sample(remote, 0.0F);
				return lying == null ? "not down: " + remote.getAttached(DownService.DOWN) : lying.seconds() > DownPose.FALL && lying.drag() == 0.0F ? "ok" : lying.toString();
			});
			check("ok".equals(seen), "the one lying there has finished falling and is not dragged: " + seen);
			check(context.computeOnClient(mc -> {
				DownPose.State dragged = DownPose.sample((RemotePlayer) mc.level.getEntity(DRAGGED), 0.0F);
				return dragged != null && dragged.drag() > 0.99F && Math.abs(net.minecraft.util.Mth.wrapDegrees(dragged.facing())) < 1.0F;
			}), "the dragged one is wholly dragged, turned to face away from the rescuer (south)");
			check(context.computeOnClient(mc -> DragPose.dragging((RemotePlayer) mc.level.getEntity(RESCUER))
				&& !DragPose.dragging((RemotePlayer) mc.level.getEntity(LYING))), "the rescuer is dragging, no one else");
			// the hud hidden, as with F1: only the hunters
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			context.takeScreenshot("mistinn-down-poses");
			// closer, from above: the drag (hands meeting), then the one lying there
			world.getServer().runCommand("gamemode spectator @a");
			look(world, centre[0], 2.8, 2.4, 2.1, 90.0F, 38.0F);
			context.waitTicks(3);
			context.takeScreenshot("mistinn-down-drag");
			look(world, centre[0], -2.0, 2.2, -0.5, -45.0F, 34.0F);
			context.waitTicks(3);
			context.takeScreenshot("mistinn-down-drag-behind");
			look(world, centre[0], 2.6, 2.2, -3.0, 90.0F, 40.0F);
			context.waitTicks(3);
			context.takeScreenshot("mistinn-down-lying");
			// a teammate kneels by the one lying there to bring them back where they lie: at their side,
			// hands on the wound, and the one lying there turns their head to them
			context.runOnClient(mc -> {
				BlockPos c = centre[0];
				RemotePlayer aider = remote(mc.level, AIDER, "aider", c.getX() - 1.0, c.getY(), c.getZ() - 3.6, -90.0F);
				RemotePlayer lying = (RemotePlayer) mc.level.getEntity(LYING);
				DownService.Down down = lying.getAttached(DownService.DOWN);
				lying.setAttached(DownService.DOWN, new DownService.Down(down.left(), down.since(), down.lastHit(), aider.getId(), 20, false));
			});
			context.waitTicks(10);
			String aided = context.computeOnClient(mc -> {
				AidPose.State aid = AidPose.sample((RemotePlayer) mc.level.getEntity(AIDER), 0.0F);
				DownPose.State lying = DownPose.sample((RemotePlayer) mc.level.getEntity(LYING), 0.0F);
				return aid != null && aid.weight() > 0.99F && lying != null && lying.look() > 0.0F ? "ok" : aid + " / " + lying;
			});
			check("ok".equals(aided), "the aider is down on the knees by the one lying there, who turns their head to them (west): " + aided);
			context.takeScreenshot("mistinn-down-aid");
			look(world, centre[0], -3.2, 1.0, -3.2, -90.0F, 20.0F);
			context.waitTicks(3);
			context.takeScreenshot("mistinn-down-aid-side");
			look(world, centre[0], 0.0, 1.4, -0.6, 180.0F, 30.0F);
			context.waitTicks(3);
			context.takeScreenshot("mistinn-down-aid-front");
			look(world, centre[0], 2.6, 2.2, -3.0, 90.0F, 40.0F);
			// let go: the one lying there alone again
			context.runOnClient(mc -> {
				RemotePlayer lying = (RemotePlayer) mc.level.getEntity(LYING);
				DownService.Down down = lying.getAttached(DownService.DOWN);
				lying.setAttached(DownService.DOWN, new DownService.Down(down.left(), down.since(), down.lastHit(), -1, 0, false));
				mc.level.removeEntity(AIDER, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
			});
			context.waitTicks(2);
			check(context.computeOnClient(mc -> Float.isNaN(DownPose.sample((RemotePlayer) mc.level.getEntity(LYING), 0.0F).look())),
				"with no one kneeling there, nobody to turn to");
			look(world, centre[0], 4.5, 0.0, 0.5, 90.0F, 18.0F);
			context.waitTicks(3);
			// a moment of the fall: down on both knees, hunched over the wound
			context.runOnClient(mc -> {
				RemotePlayer lying = (RemotePlayer) mc.level.getEntity(LYING);
				lying.setAttached(DownService.DOWN, new DownService.Down(1200, mc.level.getGameTime() - 17, 0L, -1, 0, false));
			});
			context.waitTicks(1);
			context.takeScreenshot("mistinn-down-kneel");
			context.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			context.runOnClient(mc -> {
				for (int id : new int[] {LYING, RESCUER, DRAGGED}) {
					mc.level.removeEntity(id, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
				}
			});
		}
	}

	/** Puts the camera (the local hunter, a spectator) {@code dx, dy, dz} from the floor's centre, turned so. */
	private static void look(final TestSingleplayerContext world, final BlockPos centre, final double dx, final double dy, final double dz,
		final float yaw, final float pitch) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.teleportTo(server.overworld(), centre.getX() + 0.5 + dx, centre.getY() + dy, centre.getZ() + dz, Set.of(), yaw, pitch, true);
		});
	}

	/** A hunter drawn only on this client. */
	private static RemotePlayer remote(final ClientLevel level, final int id, final String name, final double x, final double y, final double z,
		final float yaw) {
		RemotePlayer player = new RemotePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
		player.setId(id);
		player.snapTo(x, y, z, yaw, 0.0F);
		player.setYHeadRot(yaw);
		player.yBodyRot = yaw;
		player.yBodyRotO = yaw;
		player.yHeadRotO = yaw;
		level.addEntity(player);
		return player;
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
	}
}
