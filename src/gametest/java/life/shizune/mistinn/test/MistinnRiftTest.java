package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.rift.RiftHerald;
import life.shizune.mistinn.rift.RiftService;
import life.shizune.mistinn.rift.RiftShaper;
import life.shizune.mistinn.salt.SaltService;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Sky rifts: one torn open a way off sears the ground under it (but not round someone's chest),
 * turns its biome, and every client hears of it; a hunter who comes close brings down three waves,
 * the last led by the herald; when the last is down the rift seals, the ground stays scarred, and
 * the hunters there are paid and remembered.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnRiftTest implements FabricClientGameTest {
	private static final int OFF = 64;

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
			world.getServer().runOnServer(server -> {
				player(server).addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4, false, false));
				DomainService.pace = 8;
			});
			context.waitTicks(20);

			// someone's chest stands in the way, a little off the middle
			BlockPos[] centre = new BlockPos[1];
			BlockPos[] chest = new BlockPos[1];
			BlockState[] underChest = new BlockState[1];
			int[] id = new int[1];
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				BlockPos column = player.blockPosition().offset(OFF, 0, 0);
				level.getChunk(column.getX() >> 4, column.getZ() >> 4);
				chest[0] = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.offset(0, 0, 12));
				level.setBlock(chest[0], Blocks.CHEST.defaultBlockState(), 3);
				underChest[0] = level.getBlockState(chest[0].below());
				Domain domain = RiftService.tearAt(level, column);
				centre[0] = domain.centre();
				id[0] = domain.id();
				check(domain.phase() == Domain.Phase.OPEN, "the rift opens");
			});

			// it spreads: the ground is seared and the biome turned, round the chest nothing
			for (int i = 0; i < 60 && !spread(world, id[0]); i++) {
				context.waitTicks(10);
			}
			context.waitTicks(60);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				check(level.getBiome(centre[0]).is(RiftShaper.OPEN), "the middle is rift ground now");
				int seared = 0;
				int columns = 0;
				for (int dx = -20; dx <= 20; dx += 4) {
					for (int dz = -20; dz <= 20; dz += 4) {
						BlockPos column = centre[0].offset(dx, 0, dz);
						if (Math.abs(column.getZ() - chest[0].getZ()) <= 8 && Math.abs(column.getX() - chest[0].getX()) <= 8) {
							continue;
						}
						columns++;
						BlockState top = level.getBlockState(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below());
						if (rift(top)) {
							seared++;
						}
					}
				}
				check(seared * 3 >= columns, "most of the ground is seared: " + seared + " of " + columns);
				check(level.getBlockState(chest[0]).is(Blocks.CHEST), "the chest is still there");
				check(level.getBlockState(chest[0].below()).equals(underChest[0]), "and the ground under it untouched: " + level.getBlockState(chest[0].below()));
			});
			check(context.computeOnClient(mc -> mc.level.getBiome(centre[0]).is(RiftShaper.OPEN)), "the client sees the rift's biome");
			check(context.computeOnClient(mc -> music(mc, centre[0]) == life.shizune.mistinn.registry.MistinnSounds.MUSIC_RIFT), "an open rift has its own music");
			check(context.computeOnClient(mc -> {
				List<Domain> view = mc.level.getAttached(DomainService.VIEW);
				return view != null && view.stream().anyMatch(d -> d.id() == id[0] && d.open());
			}), "the client knows the rift is open");

			// seen from a way off: the tear in the sky
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.teleportTo(server.overworld(), player.getX() - 40, player.getY() + 2, player.getZ(), Set.of(), -90.0F, -38.0F, true);
			});
			context.waitTicks(40);
			context.takeScreenshot(TestScreenshotOptions.of("rift-afar").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("rift")));

			// a hunter walks in: three waves come down, the last with the herald
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				BlockPos in = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre[0].offset(-14, 0, 3));
				player(server).teleportTo(level, in.getX() + 0.5, in.getY() + 0.1, in.getZ() + 0.5, Set.of(), -90.0F, -20.0F, true);
			});
			int before = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
			boolean[] heraldSeen = new boolean[1];
			for (int wave = 1; wave <= RiftService.WAVES; wave++) {
				List<UUID> guardians = null;
				for (int i = 0; i < 40; i++) {
					context.waitTicks(10);
					int expect = wave;
					guardians = world.getServer().computeOnServer(server -> RiftService.battle(server)
						.filter(b -> b.wave() == expect && !b.guardians().isEmpty()).map(RiftService.Battle::guardians).orElse(null));
					if (guardians != null) {
						break;
					}
				}
				check(guardians != null, "wave " + wave + " comes down");
				if (wave >= 2) {
					List<UUID> came = guardians;
					long hounds = world.getServer().computeOnServer(server -> came.stream().map(server.overworld()::getEntity)
						.filter(e -> e instanceof life.shizune.mistinn.rift.RiftHound).count());
					check(hounds >= 2, "a pack of hounds comes down with wave " + wave + ": " + hounds);
				}
				List<UUID> down = guardians;
				if (wave == 1 || wave == RiftService.WAVES) {
					// face to face with one of them, held still for the picture
					boolean herald = wave == RiftService.WAVES;
					context.waitTicks(30);
					world.getServer().runOnServer(server -> closeUp(server, down, herald));
					context.waitTicks(10);
					context.takeScreenshot(TestScreenshotOptions.of(herald ? "rift-herald" : "rift-walker").disableCounterPrefix()
						.withDestinationDir(java.nio.file.Path.of("rift")));
				}
				world.getServer().runOnServer(server -> {
					ServerLevel level = server.overworld();
					ServerPlayer player = player(server);
					for (UUID uuid : down) {
						Entity guardian = level.getEntity(uuid);
						if (guardian instanceof RiftHerald) {
							heraldSeen[0] = true;
						}
						if (guardian instanceof LivingEntity living && living.isAlive()) {
							living.hurtServer(level, level.damageSources().playerAttack(player), 10000.0F);
						}
					}
				});
			}
			check(heraldSeen[0], "the herald came down with the last wave");

			// the last is down: sealed, scarred for good, the hunter paid
			boolean sealed = false;
			for (int i = 0; i < 30 && !sealed; i++) {
				context.waitTicks(10);
				sealed = world.getServer().computeOnServer(server -> {
					Domain domain = DomainService.get(server, id[0]);
					return domain != null && domain.phase() == Domain.Phase.SCAR;
				});
			}
			check(sealed, "the rift seals");
			context.waitTicks(40);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				check(server.overworld().getBiome(centre[0]).is(RiftShaper.SCAR), "the ground stays a scar");
				int gained = SaltService.get(player) - before;
				check(gained >= RiftService.REWARD + RiftService.HERALD_BONUS, "sealing and the herald pay: " + gained);
				check(AchievementService.has(player, Achievement.RIFT_SEALED), "the sky sewn shut is remembered");
				check(RiftService.battle(server).isEmpty(), "and the fight is over");
			});
			context.waitTicks((int) (RiftService.CLOSING / 2));
			context.takeScreenshot(TestScreenshotOptions.of("rift-closing").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("rift")));
			context.waitTicks((int) RiftService.CLOSING);
			check(context.computeOnClient(mc -> music(mc, centre[0]) == life.shizune.mistinn.registry.MistinnSounds.MUSIC_RIFT_SCAR), "and the scar it leaves, its own");
			world.getServer().runOnServer(server -> player(server).setYRot(0.0F));
			context.takeScreenshot(TestScreenshotOptions.of("rift-scar").disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("rift")));
		} finally {
			DomainService.pace = 1;
		}
	}

	/** The music the biome at a place would play, as the client has it. */
	private static net.minecraft.sounds.SoundEvent music(final net.minecraft.client.Minecraft mc, final BlockPos at) {
		return mc.level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.BACKGROUND_MUSIC,
			net.minecraft.world.phys.Vec3.atCenterOf(at.above(2))).defaultMusic().map(m -> m.sound().value()).orElse(null);
	}

	/** Stills one guardian (the herald, or the first walker) and stands the hunter a few steps in front of it, looking at it. */
	private static void closeUp(final MinecraftServer server, final List<UUID> guardians, final boolean herald) {
		ServerLevel level = server.overworld();
		net.minecraft.world.entity.Mob subject = null;
		for (UUID uuid : guardians) {
			Entity entity = level.getEntity(uuid);
			if (entity instanceof net.minecraft.world.entity.Mob mob && (herald == (mob instanceof RiftHerald))) {
				subject = mob;
				break;
			}
		}
		check(subject != null, herald ? "the herald is there to see" : "a walker is there to see");
		for (UUID uuid : guardians) {
			if (level.getEntity(uuid) instanceof net.minecraft.world.entity.Mob mob) {
				mob.setNoAi(true);
			}
		}
		double reach = herald ? 6.5 : 4.0;
		double yaw = Math.toRadians(subject.getYRot());
		net.minecraft.world.phys.Vec3 at = subject.position().add(-Math.sin(yaw) * reach, 0.0, Math.cos(yaw) * reach);
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
		double dx = subject.getX() - at.x;
		double dz = subject.getZ() - at.z;
		double dy = subject.getEyeY() - (ground.getY() + 1.62);
		float lookYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float lookPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
		player(server).teleportTo(level, at.x, ground.getY() + 0.05, at.z, Set.of(), lookYaw, lookPitch, true);
	}

	private static boolean spread(final TestSingleplayerContext world, final int id) {
		return world.getServer().computeOnServer(server -> {
			Domain domain = DomainService.get(server, id);
			return domain != null && domain.spread() >= domain.radius() + DomainService.EDGE;
		});
	}

	private static boolean rift(final BlockState state) {
		return state.is(MistinnBlocks.RIFT_TURF) || state.is(MistinnBlocks.RIFT_SOIL) || state.is(MistinnBlocks.SCORCHED_STONE)
			|| state.is(MistinnBlocks.RIFT_CRYSTAL_BLOCK) || state.is(Blocks.RED_SAND) || state.is(Blocks.WATER) || state.is(MistinnBlocks.CRIMSON_MOSS)
			|| state.is(MistinnBlocks.CRIMSON_MOSS_CARPET) || state.is(MistinnBlocks.RIFT_CRYSTAL_CLUSTER) || state.is(MistinnBlocks.RIFT_CRYSTAL_BUD);
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
