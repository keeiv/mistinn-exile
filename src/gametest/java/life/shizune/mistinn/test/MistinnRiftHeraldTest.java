package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.rift.RiftHerald;
import life.shizune.mistinn.rift.RiftHound;
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
			wrath(context, world, herald[0]);

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

	/** Below half: a roar that throws the hunter back, a line of strikes out to them, and a step through the rift to come out behind them. */
	private static void wrath(final ClientGameTestContext context, final TestSingleplayerContext world, final RiftHerald herald) {
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			// open, level ground round them both
			BlockPos floor = player.blockPosition().below();
			for (int x = -20; x <= 20; x++) {
				for (int z = -20; z <= 20; z++) {
					for (int y = 0; y <= 6; y++) {
						level.setBlockAndUpdate(floor.offset(x, y, z), (y == 0 ? net.minecraft.world.level.block.Blocks.STONE
							: net.minecraft.world.level.block.Blocks.AIR).defaultBlockState());
					}
				}
			}
			player.teleportTo(level, floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			herald.teleportTo(floor.getX() + 3.5, floor.getY() + 1.0, floor.getZ() + 0.5);
			herald.setTarget(player);
			check(!herald.wrathful(), "not yet in its wrath");
			herald.setHealth(herald.getMaxHealth() * 0.4F);
		});
		context.waitTicks(12);
		world.getServer().runOnServer(server -> {
			check(herald.wrathful(), "below half, its wrath comes on it");
			// the throw reaches the hunter's own client, which moves them
			check(player(server).distanceTo(herald) > 4.5F, "and the roar throws the hunter back: " + player(server).distanceTo(herald));
		});

		// a line of strikes out to the hunter, landing one after another
		boolean lined = false;
		for (int i = 0; i < 160 && !lined; i++) {
			context.waitTicks(2);
			lined = world.getServer().computeOnServer(server -> herald.marks().size() >= RiftHerald.LINE - 1);
		}
		check(lined, "in its wrath the rift strikes in a line");
		world.getServer().runOnServer(server -> {
			List<Vec3> marks = herald.marks();
			Vec3 first = marks.getFirst();
			Vec3 last = marks.getLast();
			check(first.distanceTo(herald.position()) < last.distanceTo(herald.position()), "running out from its feet");
		});
		context.waitTicks(RiftHerald.MARKED + RiftHerald.LINE_GAP * 2 + 2);
		world.getServer().runOnServer(server -> check(!herald.marks().isEmpty() && herald.marks().size() < RiftHerald.LINE,
			"ring after ring: " + herald.marks().size()));

		// it steps through the rift and comes out behind the hunter
		boolean shown = false;
		boolean behind = false;
		for (int i = 0; i < 200 && !behind; i++) {
			context.waitTicks(2);
			shown |= world.getServer().computeOnServer(server -> herald.way() != null);
			behind = shown && world.getServer().computeOnServer(server -> herald.way() == null && herald.distanceTo(player(server)) < 6.0F
				&& RiftHound.flanking(player(server), herald.position()));
			world.getServer().runOnServer(server -> {
				// the hunter stands their ground, facing the way they were, out of its reach
				ServerPlayer player = player(server);
				if (player.distanceTo(herald) < 4.0F && herald.way() == null) {
					Vec3 off = player.position().add(0.0, 0.0, -8.0);
					player.teleportTo(server.overworld(), off.x, player.getY(), off.z, Set.of(), 180.0F, 0.0F, true);
				}
			});
		}
		check(shown, "the way out is shown before it steps through");
		check(behind, "and it comes out behind the hunter");
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
