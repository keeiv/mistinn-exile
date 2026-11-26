package life.shizune.mistinn.test;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.naval.GunShot;
import life.shizune.mistinn.naval.Pirate;
import life.shizune.mistinn.naval.PirateRole;
import life.shizune.mistinn.naval.RaidService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Pirates landing by night: a hunter on the shore after dark is raided by a party rowed in from the
 * sea, boat and all; a gunshot makes a sharpshooter; at dawn what is left of a party goes back to the
 * sea, and one only partly beaten holds nothing; one beaten wholly holds the shore; a raider from a
 * raid the world forgot goes back too; a river bank or a deck is no shore for a landing.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnRaidTest implements FabricClientGameTest {
	private static BlockPos beach;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> s.setSeed("20261126")).create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set midnight");
			world.getServer().runOnServer(MistinnRaidTest::toTheBeach);
			context.waitTicks(100);
			world.getConnection().waitForChunksRender();
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = server.overworld();
				check(RaidService.eligible(level, player), "a hunter on the beach at night may be raided: dark " + level.isDarkOutside()
					+ ", in water " + player.isInWater() + ", at " + player.blockPosition() + " ground " + level.getBlockState(player.blockPosition().below())
					+ ", sea " + level.getSeaLevel());
				RaidService.reset();
				RaidService.certain = true;
			});
			boolean landed = false;
			for (int i = 0; i < 40 && !landed; i++) {
				context.waitTicks(10);
				landed = world.getServer().computeOnServer(server -> !raiders(server).isEmpty());
			}
			check(landed, "a landing party came ashore");
			world.getServer().runOnServer(server -> {
				RaidService.certain = false;
				ServerPlayer player = player(server);
				List<Pirate> party = raiders(server);
				check(party.size() == 3, "two boarders and a marksman: " + party.size());
				check(party.stream().filter(p -> p.role() == PirateRole.MARKSMAN).count() == 1, "one marksman");
				check(party.stream().allMatch(p -> p.getTarget() == player), "all after the hunter");
				check(!server.overworld().getEntitiesOfClass(AbstractBoat.class, player.getBoundingBox().inflate(48.0)).isEmpty(), "their boat at the water's edge");
				check(!RaidService.eligible(server.overworld(), player), "and no second raid on the same hunter");
			});
			context.takeScreenshot("mistinn-raid-landing");
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				List<Pirate> party = raiders(server);
				Pirate first = party.getFirst();
				GunShot.fire(level, player, first.getEyePosition().add(0.0, 0.0, -2.0), first.getBoundingBox().getCenter(), 1000.0F, 0.0, 10.0);
				check(!first.isAlive(), "a shot brings the first down");
				check(AchievementService.has(player, AchievementService.SHARPSHOOTER), "a sharpshooter");
			});
			// the sun comes up on the rest: they go back to the sea, and one down of three holds nothing
			world.getServer().runCommand("time set day");
			context.waitTicks(40);
			check(world.getServer().computeOnServer(server -> raiders(server).stream().allMatch(Pirate::fleeing)), "at dawn they give up");
			context.waitTicks(240);
			check(world.getServer().computeOnServer(server -> raiders(server).isEmpty()), "and are gone back to the sea");
			check(world.getServer().computeOnServer(server -> !AchievementService.has(player(server), AchievementService.SHORE_HELD)),
				"two of three got away: the shore was not held");
			// another night, another party, and every one of it brought down: the shore is held
			world.getServer().runCommand("time set midnight");
			context.waitTicks(5);
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.removeAttached(RaidService.NEXT);
				check(RaidService.land(level, player) >= 0, "a second party lands");
				for (Pirate pirate : raiders(server)) {
					pirate.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
				}
			});
			context.waitTicks(40);
			world.getServer().runOnServer(server -> check(AchievementService.has(player(server), AchievementService.SHORE_HELD), "the shore is held"));
			// a raider whose raid the world has forgotten (closed mid-raid) goes too
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				Pirate stray = (Pirate) life.shizune.mistinn.registry.MistinnEntities.PIRATE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				stray.snapTo(player.getX() + 4.0, player.getY(), player.getZ(), 0.0F, 0.0F);
				stray.enlist(-1, PirateRole.BOARDER, life.shizune.mistinn.naval.PirateLook.CUTLASS_1, player.blockPosition(), 0);
				stray.raid(999);
				level.addFreshEntity(stray);
			});
			context.waitTicks(10);
			check(world.getServer().computeOnServer(server -> raiders(server).stream().allMatch(Pirate::fleeing)), "a raider from a forgotten raid gives up");
			// the same shore made a river's, and then the open sea's: no landing either way
			world.getServer().runCommand("gamerule max_block_modifications 1000000");
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				player.removeAttached(RaidService.NEXT);
				check(RaidService.eligible(level, player), "the beach is still a beach");
				BlockPos at = player.blockPosition();
				BlockPos from = new BlockPos(at.getX() - 48, level.getSeaLevel() - 8, at.getZ() - 48);
				BlockPos to = new BlockPos(at.getX() + 48, level.getSeaLevel() + 12, at.getZ() + 48);
				check(FillBiomeCommand.fill(level, from, to, level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.RIVER)).left().isPresent(),
					"the shore made a river's");
				check(!RaidService.eligible(level, player), "a river bank far from the sea is no shore for pirates");
				check(FillBiomeCommand.fill(level, from, to, level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.OCEAN)).left().isPresent(),
					"and the sea's again");
				check(RaidService.eligible(level, player), "by the sea it is a shore again");
				// every dry place in reach made woodwork: a deck or a pier is no shore for a landing
				for (int dx = -40; dx <= 40; dx++) {
					for (int dz = -40; dz <= 40; dz++) {
						int x = at.getX() + dx;
						int z = at.getZ() + dz;
						BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
						if (level.getFluidState(top).isEmpty() && !level.getBlockState(top).isAir()) {
							level.setBlock(top, Blocks.DARK_OAK_PLANKS.defaultBlockState(), 2);
						}
					}
				}
				check(!RaidService.eligible(level, player), "nor is a deck or a pier");
			});
		} finally {
			RaidService.certain = false;
		}
	}

	private static void toTheBeach(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		BlockPos spawn = level.getRespawnData().pos();
		Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(biome -> biome.is(BiomeTags.IS_BEACH), spawn, 3200, 16, 64);
		check(found != null, "a beach near spawn");
		BlockPos at = found.getFirst();
		// the biome's edge may be in the shallows: the nearest dry sand above the sea
		beach = null;
		for (int r = 0; r <= 40 && beach == null; r += 2) {
			for (int i = 0; i < Math.max(1, r * 4) && beach == null; i++) {
				double angle = Math.PI * 2.0 * i / Math.max(1, r * 4);
				int x = at.getX() + (int) Math.round(Math.cos(angle) * r);
				int z = at.getZ() + (int) Math.round(Math.sin(angle) * r);
				level.getChunk(x >> 4, z >> 4);
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos stand = new BlockPos(x, y, z);
				if (y >= level.getSeaLevel() && level.getFluidState(stand.below()).isEmpty() && !level.getBlockState(stand.below()).isAir()) {
					beach = stand;
				}
			}
		}
		check(beach != null, "dry sand by the beach at " + at);
		ServerPlayer player = player(server);
		player.teleportTo(level, beach.getX() + 0.5, beach.getY(), beach.getZ() + 0.5, java.util.Set.of(), 0.0F, 0.0F, true);
		// hurt but never killed: an invulnerable hunter is no target at all
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 100000, 4, false, false));
	}

	private static List<Pirate> raiders(final MinecraftServer server) {
		ServerPlayer player = player(server);
		return server.overworld().getEntitiesOfClass(Pirate.class, new AABB(player.blockPosition()).inflate(64.0), p -> p.isAlive() && p.raidId() >= 0);
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
