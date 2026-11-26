package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.rift.RiftHerald;
import life.shizune.mistinn.rift.RiftWalker;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The rift's creatures in their own models: the herald drags a hunter who keeps their distance in
 * and calls the rift down round them, and shows it (its cast is what the client poses); a picture of
 * each, the herald with its hands raised.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnRiftHeraldTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set 13000");
			context.waitTicks(20);

			RiftHerald[] herald = new RiftHerald[1];
			RiftWalker[] walker = new RiftWalker[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 3, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100000, 3, false, false));
				BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(10, 0, 0));
				herald[0] = MistinnEntities.RIFT_HERALD.spawn(level, at, EntitySpawnReason.COMMAND);
				herald[0].setTarget(player);
				BlockPos off = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(-40, 0, 20));
				walker[0] = MistinnEntities.RIFT_WALKER.spawn(level, off, EntitySpawnReason.COMMAND);
				walker[0].setNoAi(true);
			});

			// it drags the hunter in, and calls the rift down: each shown as a cast
			boolean dragged = false;
			boolean marked = false;
			for (int i = 0; i < 120 && !(dragged && marked); i++) {
				context.waitTicks(2);
				RiftHerald.Cast cast = world.getServer().computeOnServer(server -> {
					// the hunter keeps their distance, so it has someone to drag
					ServerPlayer player = player(server);
					if (player.distanceTo(herald[0]) < 8.0F) {
						Vec3 back = player.position().add(player.position().subtract(herald[0].position()).normalize().scale(4.0));
						player.teleportTo(server.overworld(), back.x, player.getY(), back.z, Set.of(), player.getYRot(), player.getXRot(), true);
					}
					return herald[0].cast();
				});
				dragged |= cast == RiftHerald.Cast.DRAG;
				if (cast == RiftHerald.Cast.MARK && !marked) {
					marked = true;
					check(world.getServer().computeOnServer(server -> !herald[0].marks().isEmpty()), "calling the rift down marks the ground");
					photo(context, world, herald[0], 4.2, 1.5, "herald-cast");
				}
			}
			check(dragged, "the herald drags the hunter in, and shows it");
			check(marked, "the herald calls the rift down, and shows it");

			// the walker, held still for its picture
			photo(context, world, walker[0], 2.6, 0.9, "walker");
		}
	}

	/** Holds a creature still and looks at it from {@code reach} blocks off, the eye at {@code high} blocks over its feet. */
	private static void photo(final ClientGameTestContext context, final TestSingleplayerContext world, final Mob subject, final double reach, final double high,
		final String name) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			subject.setNoAi(true);
			Vec3 at = subject.position();
			float facing = subject.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD;
			// in front of it and a little to its side
			Vec3 from = at.add(-Math.sin(facing + 0.5) * reach, high - 1.62, Math.cos(facing + 0.5) * reach);
			double dx = at.x - from.x;
			double dz = at.z - from.z;
			double dy = at.y + subject.getBbHeight() * 0.6 - (from.y + 1.62);
			player.teleportTo(server.overworld(), from.x, from.y, from.z, Set.of(), (float) Math.toDegrees(Math.atan2(-dx, dz)),
				(float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))), true);
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
		});
		context.waitTicks(6);
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("rift")));
		world.getServer().runOnServer(server -> subject.setNoAi(false));
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
	}
}
