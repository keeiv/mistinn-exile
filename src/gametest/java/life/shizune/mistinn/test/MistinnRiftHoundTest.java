package life.shizune.mistinn.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.rift.RiftHound;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The rift hounds: a pack rings its quarry a few blocks off, each in its own place round it; only a
 * hound at the quarry's side or back may lunge, never the one it faces; and they do lunge, and bite.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnRiftHoundTest implements FabricClientGameTest {
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
			world.getServer().runCommand("time set night");
			context.waitTicks(20);

			// where a hound may lunge from: never in front of where its quarry looks
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.teleportTo(server.overworld(), player.getX(), player.getY(), player.getZ(), Set.of(), 0.0F, 0.0F, true);
				Vec3 at = player.position();
				Vec3 look = player.getViewVector(1.0F).multiply(1.0, 0.0, 1.0).normalize();
				Vec3 side = new Vec3(-look.z, 0.0, look.x);
				check(!RiftHound.flanking(player, at.add(look.scale(5.0))), "the one in front does not lunge");
				check(!RiftHound.flanking(player, at.add(look.scale(5.0)).add(side.scale(1.5))), "nor one just off to the front");
				check(RiftHound.flanking(player, at.add(side.scale(5.0))), "one at the side may");
				check(RiftHound.flanking(player, at.add(look.scale(-5.0))), "one behind may");
			});

			// a pack of three on a hunter
			List<RiftHound> pack = new ArrayList<>();
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 2, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100000, 3, false, false));
				for (int i = 0; i < 3; i++) {
					double angle = i * Math.PI * 2.0 / 3.0;
					BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
						player.blockPosition().offset((int) Math.round(Math.cos(angle) * 10.0), 0, (int) Math.round(Math.sin(angle) * 10.0)));
					RiftHound hound = MistinnEntities.RIFT_HOUND.spawn(level, at, EntitySpawnReason.COMMAND);
					hound.setPersistenceRequired();
					hound.setTarget(player);
					pack.add(hound);
				}
			});

			// they ring the hunter, each in its own place; one at the side or back lunges and bites
			double nearest = Double.MAX_VALUE;
			double farthest = 0.0;
			double widest = 0.0;
			boolean lunged = false;
			boolean bitten = false;
			for (int i = 0; i < 80 && !(bitten && lunged && widest > 1.5 && i >= 16); i++) {
				context.waitTicks(5);
				double[] seen = world.getServer().computeOnServer(server -> {
					ServerPlayer player = player(server);
					double near = Double.MAX_VALUE;
					double far = 0.0;
					List<Double> angles = new ArrayList<>();
					boolean up = false;
					for (RiftHound hound : pack) {
						double d = hound.distanceTo(player);
						near = Math.min(near, d);
						far = Math.max(far, d);
						angles.add(Math.atan2(hound.getZ() - player.getZ(), hound.getX() - player.getX()));
						up |= hound.isLunging() || hound.isCrouching();
					}
					double spread = 0.0;
					for (double a : angles) {
						for (double b : angles) {
							spread = Math.max(spread, Math.abs(Math.atan2(Math.sin(a - b), Math.cos(a - b))));
						}
					}
					boolean hit = player.getLastHurtByMob() instanceof RiftHound;
					return new double[] {near, far, spread, up ? 1 : 0, hit ? 1 : 0};
				});
				if (i >= 16) {
					nearest = Math.min(nearest, seen[0]);
					farthest = Math.max(farthest, seen[1]);
				}
				widest = Math.max(widest, seen[2]);
				lunged |= seen[3] > 0;
				bitten |= seen[4] > 0;
			}
			check(farthest < 12.0, "the pack keeps close round the hunter: " + farthest);
			check(widest > 1.5, "each in its own place round the hunter: " + widest);
			check(lunged, "one crouches and lunges");
			check(bitten, "and bites");
			alpha(context, world, pack);

			// the hound, held still for the picture
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.removeEffect(MobEffects.RESISTANCE);
				for (RiftHound hound : pack) {
					hound.setNoAi(true);
				}
				// out on open ground, turned three-quarters towards the camera
				RiftHound hound = pack.stream().filter(RiftHound::isAlive).findFirst().orElseThrow();
				hound.scatterFor(0);
				ServerLevel level = server.overworld();
				BlockPos open = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(-30, 0, 12));
				hound.teleportTo(open.getX() + 0.5, open.getY(), open.getZ() + 0.5);
				Vec3 at = hound.position();
				Vec3 from = new Vec3(at.x - 3.4, at.y + 0.5, at.z - 2.2);
				double dx = at.x - from.x;
				double dz = at.z - from.z;
				float facing = (float) Math.toDegrees(Math.atan2(dx, -dz)) - 35.0F;
				hound.setYRot(facing);
				hound.setYHeadRot(facing);
				hound.yBodyRot = facing;
				double dy = at.y + 0.6 - (from.y + 1.62);
				player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
				player.teleportTo(server.overworld(), from.x, from.y, from.z, Set.of(), (float) Math.toDegrees(Math.atan2(-dx, dz)),
					(float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))), true);
			});
			world.getServer().runCommand("time set 12800");
			context.waitTicks(20);
			context.takeScreenshot(TestScreenshotOptions.of("hound").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("rift")));
		}
	}

	/** One of the pack is made its alpha: bigger and red-named; it howls, two leap at once; brought down, the pack scatters and it leaves shards. */
	private static void alpha(final ClientGameTestContext context, final TestSingleplayerContext world, final List<RiftHound> pack) {
		RiftHound[] alpha = new RiftHound[1];
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			// open, level ground round the hunter, so that every hound can see them
			BlockPos floor = player.blockPosition().below();
			for (int x = -16; x <= 16; x++) {
				for (int z = -16; z <= 16; z++) {
					for (int y = 0; y <= 5; y++) {
						level.setBlockAndUpdate(floor.offset(x, y, z), (y == 0 ? net.minecraft.world.level.block.Blocks.STONE
							: net.minecraft.world.level.block.Blocks.AIR).defaultBlockState());
					}
				}
			}
			player.teleportTo(level, floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			for (int i = 0; i < pack.size(); i++) {
				double angle = i * Math.PI * 2.0 / pack.size();
				pack.get(i).teleportTo(floor.getX() + 0.5 + Math.cos(angle) * 6.0, floor.getY() + 1.0, floor.getZ() + 0.5 + Math.sin(angle) * 6.0);
				pack.get(i).setTarget(player);
			}
			alpha[0] = pack.getFirst();
			float before = alpha[0].getScale();
			alpha[0].lead();
			check(alpha[0].isAlpha() && alpha[0].getScale() > before * 1.3F, "the alpha is bigger: " + alpha[0].getScale());
			check(alpha[0].getMaxHealth() >= 70.0F && alpha[0].getHealth() >= 70.0F, "and harder to bring down: " + alpha[0].getMaxHealth());
			check(alpha[0].hasCustomName(), "and named");
			BlockPos at = player.blockPosition().offset(0, 0, -8);
			RiftHound more = MistinnEntities.RIFT_HOUND.spawn(level, at, EntitySpawnReason.COMMAND);
			more.setPersistenceRequired();
			more.setTarget(player);
			pack.add(more);
		});
		boolean howled = false;
		boolean pincer = false;
		int since = 1000;
		for (int i = 0; i < 400 && !pincer; i++) {
			context.waitTicks(1);
			boolean howling = world.getServer().computeOnServer(server -> alpha[0].isHowling());
			if (howling && !howled) {
				howled = true;
				since = 0;
			}
			since++;
			int leaping = world.getServer().computeOnServer(server -> (int) pack.stream()
				.filter(h -> h != alpha[0] && h.isAlive() && (h.isCrouching() || h.isLunging())).count());
			pincer = howled && since < 40 && leaping >= 2;
		}
		check(howled, "the alpha howls its pack on");
		check(pincer, "and two of the pack leap at once");

		// brought down: the pack scatters, and it leaves rift shards
		double[] near = new double[1];
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			near[0] = pack.stream().filter(h -> h != alpha[0] && h.isAlive()).mapToDouble(h -> h.distanceTo(player)).average().orElse(0.0);
			alpha[0].hurtServer(level, level.damageSources().playerAttack(player), 10000.0F);
			check(!alpha[0].isAlive(), "the alpha falls");
		});
		context.waitTicks(5);
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			for (RiftHound hound : pack) {
				if (hound != alpha[0] && hound.isAlive()) {
					check(hound.scattering() && hound.getTarget() == null, "its pack breaks off the hunt");
				}
			}
			int shards = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(alpha[0].blockPosition()).inflate(6.0),
				e -> e.getItem().is(life.shizune.mistinn.registry.MistinnItems.RIFT_SHARD)).stream().mapToInt(e -> e.getItem().getCount()).sum();
			check(shards >= 2, "the alpha leaves rift shards: " + shards);
		});
		context.waitTicks(80);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			double now = pack.stream().filter(h -> h != alpha[0] && h.isAlive()).mapToDouble(h -> h.distanceTo(player)).average().orElse(0.0);
			check(now > near[0] + 3.0, "and runs: " + near[0] + " -> " + now);
		});
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
