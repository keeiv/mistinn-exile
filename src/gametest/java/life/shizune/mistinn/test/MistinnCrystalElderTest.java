package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.terra.CrystalDrake;
import life.shizune.mistinn.terra.CrystalElder;
import life.shizune.mistinn.terra.Elders;
import life.shizune.mistinn.terra.TerraService;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The crown elder: it takes the air when a hunter comes near a highland's heart; it calls the
 * lattice up under its quarry; badly hurt it calls two drakes; brought down it leaves crystal
 * cores enough to wake a hub, is remembered, and the crown grows another later. Below half its
 * strength it comes down into its nest: it sweeps a hunter close by, a charge that ends against
 * stone stuns it and leaves it hurt harder, it takes the air again in a while, and coming back to
 * the world it still knows it has called for help.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnCrystalElderTest implements FabricClientGameTest {
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
			world.getServer().runCommand("time set day");
			int[] id = new int[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 2, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100000, 3, false, false));
				BlockPos centre = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(30, 0, 0));
				id[0] = TerraService.settleAt(level, centre, 60).id();
			});

			// a hunter near the heart: the elder takes the air
			CrystalElder[] elder = new CrystalElder[1];
			for (int i = 0; i < 40 && elder[0] == null; i++) {
				context.waitTicks(20);
				elder[0] = world.getServer().computeOnServer(server -> {
					List<CrystalElder> found = server.overworld().getEntitiesOfClass(CrystalElder.class, new AABB(player(server).blockPosition()).inflate(120.0));
					return found.isEmpty() ? null : found.getFirst();
				});
			}
			check(elder[0] != null, "the crown elder takes the air");
			check(world.getServer().computeOnServer(server -> Elders.crown(server, id[0]).out()), "and the crown knows it is out");
			context.waitTicks(40);
			photo(context, world, elder[0], "elder");

			// it calls the lattice up under its quarry
			boolean ringed = false;
			for (int i = 0; i < 60 && !ringed; i++) {
				context.waitTicks(10);
				ringed = world.getServer().computeOnServer(server -> !elder[0].rings().isEmpty());
			}
			check(ringed, "rings of light mark where the lattice will erupt");
			context.waitTicks(CrystalElder.ERUPT_WARNING + 5);
			check(world.getServer().computeOnServer(server -> elder[0].rings().isEmpty()), "and it erupts");

			// badly hurt, it calls two drakes
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				elder[0].hurtServer(level, level.damageSources().playerAttack(player(server)), elder[0].getMaxHealth() * 0.6F);
			});
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				check(elder[0].called(), "badly hurt, it calls for help");
				long drakes = server.overworld().getEntitiesOfClass(CrystalDrake.class, new AABB(elder[0].blockPosition()).inflate(20.0),
					d -> !(d instanceof CrystalElder)).size();
				check(drakes >= 2, "two drakes come: " + drakes);
				// out of the way of what follows
				server.overworld().getEntitiesOfClass(CrystalDrake.class, new AABB(elder[0].blockPosition()).inflate(40.0),
					d -> !(d instanceof CrystalElder)).forEach(d -> d.discard());
			});
			inTheNest(context, world, elder[0]);

			// brought down: cores, remembered, and the crown will grow another
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				elder[0].hurtServer(level, level.damageSources().playerAttack(player), 100000.0F);
				check(!elder[0].isAlive(), "the elder falls");
				check(AchievementService.has(player, Achievement.ELDER_SLAIN), "the crown is remembered");
				Elders.Crown crown = Elders.crown(server, id[0]);
				check(!crown.out() && crown.next() > level.getGameTime(), "and another will grow later");
			});
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				int cores = server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(elder[0].blockPosition()).inflate(8.0),
					e -> e.getItem().is(MistinnItems.CRYSTAL_CORE)).stream().mapToInt(e -> e.getItem().getCount()).sum();
				check(cores >= 3, "it leaves cores enough to wake a hub: " + cores);
			});
		}
	}

	/** Below half its strength: down into the nest, a sweep, a charge into stone, up again, and remembered across a reload. */
	private static void inTheNest(final ClientGameTestContext context, final TestSingleplayerContext world, final CrystalElder elder) {
		boolean down = false;
		for (int i = 0; i < 40 && !down; i++) {
			context.waitTicks(10);
			down = world.getServer().computeOnServer(server -> elder.stance() == CrystalElder.Stance.GROUND);
		}
		check(down, "below half its strength it comes down");
		world.getServer().runOnServer(server -> {
			BlockPos nest = server.overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				life.shizune.mistinn.domain.DomainService.at(server, elder.getX(), elder.getZ()).centre());
			double across = Math.hypot(elder.getX() - (nest.getX() + 0.5), elder.getZ() - (nest.getZ() + 0.5));
			check(across < 5.0, "into its nest: " + across + " from the heart");
			check(!elder.isAloft(), "and stands on the ground");
		});
		context.waitTicks(25);

		// a hunter close by is swept
		long[] before = new long[1];
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Vec3 at = elder.position().add(5.0, 0.0, 0.0);
			player.teleportTo(server.overworld(), at.x, elder.getY() + 0.5, at.z, Set.of(), 90.0F, 0.0F, true);
			elder.setTarget(player);
			before[0] = player.getLastHurtByMobTimestamp();
		});
		boolean coiled = false;
		boolean swept = false;
		for (int i = 0; i < 120 && !swept; i++) {
			context.waitTicks(1);
			coiled |= world.getServer().computeOnServer(server -> elder.move() == CrystalElder.Move.SWEEP_WINDUP);
			swept = world.getServer().computeOnServer(server -> player(server).getLastHurtByMob() == elder && player(server).getLastHurtByMobTimestamp() != before[0]);
		}
		check(coiled, "it coils before it sweeps");
		check(swept, "and the sweep strikes the hunter close by");

		// a hunter far off, before stone: the charge ends against it, stunned
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			ServerPlayer player = player(server);
			Vec3 from = elder.position();
			Vec3 at = from.add(0.0, 0.0, 20.0);
			int floor = (int) Math.floor(from.y);
			for (int x = -6; x <= 6; x++) {
				for (int z = -4; z <= 24; z++) {
					for (int y = 0; y <= 6; y++) {
						BlockPos pos = BlockPos.containing(from.x + x, floor + y, from.z + z);
						level.setBlockAndUpdate(pos, z >= 23 && y <= 5 ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
							: y == 0 ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
					}
				}
			}
			elder.teleportTo(from.x, floor + 1.0, from.z);
			player.teleportTo(level, at.x, floor + 1.0, at.z, Set.of(), 180.0F, 0.0F, true);
			elder.setTarget(player);
		});
		boolean lowered = false;
		boolean stunned = false;
		for (int i = 0; i < 200 && !stunned; i++) {
			context.waitTicks(1);
			lowered |= world.getServer().computeOnServer(server -> elder.move() == CrystalElder.Move.CHARGE_WINDUP);
			stunned = world.getServer().computeOnServer(server -> elder.stunned());
			if (i % 20 == 0) {
				world.getServer().runOnServer(server -> {
					// keep the hunter before the stone, whatever the charge did to them
					ServerPlayer player = player(server);
					if (player.getZ() < elder.getZ() + 12.0) {
						player.teleportTo(server.overworld(), elder.getX(), elder.getY(), elder.getZ() + 18.0, Set.of(), 180.0F, 0.0F, true);
					}
				});
			}
		}
		check(lowered, "it lowers its head before it charges");
		check(stunned, "a charge that ends against stone stuns it");
		check(world.getServer().computeOnServer(server -> AchievementService.has(player(server), Achievement.ELDER_STUNNED)), "and the hunter who saw it remembers it");
		context.waitTicks(8);
		world.getServer().runOnServer(server -> {
			// from down the cleared run behind it, looking along its charge
			elder.setNoAi(true);
			ServerPlayer player = player(server);
			Vec3 at = elder.position();
			Vec3 from = at.add(4.0, 2.5, -9.0);
			double dx = at.x - from.x;
			double dz = at.z - from.z;
			double dy = at.y + 1.5 - (from.y + 1.62);
			player.teleportTo(server.overworld(), from.x, from.y, from.z, Set.of(), (float) Math.toDegrees(Math.atan2(-dx, dz)),
				(float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))), true);
			player.setNoGravity(true);
			player.setDeltaMovement(Vec3.ZERO);
		});
		context.waitTicks(100);
		context.takeScreenshot(TestScreenshotOptions.of("elder-stunned").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("drake")));
		world.getServer().runOnServer(server -> {
			player(server).setNoGravity(false);
			elder.setNoAi(false);
		});
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			float health = elder.getHealth();
			elder.damageCooldownTime = 0;
			elder.hurtServer(level, level.damageSources().magic(), 10.0F);
			float lost = health - elder.getHealth();
			check(lost > 14.0F, "stunned, it is hurt harder: " + lost);
		});

		// in a while it takes the air again
		boolean up = false;
		for (int i = 0; i < 60 && !up; i++) {
			context.waitTicks(10);
			up = world.getServer().computeOnServer(server -> elder.stance() == CrystalElder.Stance.SKY);
		}
		check(up, "after a while on the ground it takes the air again");

		// back in the world, it still knows it has called, and comes down again later
		world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			net.minecraft.world.level.storage.TagValueOutput out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
				net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
			elder.saveWithoutId(out);
			CrystalElder again = life.shizune.mistinn.registry.MistinnEntities.CRYSTAL_ELDER.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
			again.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
			check(again.called(), "reloaded, it remembers it has called for help");
			check(again.landed() && again.stance() == CrystalElder.Stance.SKY, "and takes the air before it comes down again");
			again.discard();
		});
	}

	private static void photo(final ClientGameTestContext context, final TestSingleplayerContext world, final CrystalElder elder, final String name) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			elder.setNoAi(true);
			Vec3 at = elder.position();
			Vec3 from = new Vec3(at.x - 14.0, at.y + 1.0, at.z - 10.0);
			double dx = at.x - from.x;
			double dz = at.z - from.z;
			double dy = at.y + 2.0 - (from.y + 1.62);
			player.teleportTo(server.overworld(), from.x, from.y, from.z, Set.of(), (float) Math.toDegrees(Math.atan2(-dx, dz)),
				(float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))), true);
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
		});
		context.waitTicks(20);
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("drake")));
		world.getServer().runOnServer(server -> {
			elder.setNoAi(false);
			ServerPlayer player = player(server);
			BlockPos ground = server.overworld().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition());
			player.teleportTo(server.overworld(), ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
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
