package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.epic.EpicService;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.udasru.Udasru;
import life.shizune.mistinn.udasru.UdasruPlaces;
import life.shizune.mistinn.udasru.UdasruSites;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Pictures of Udasru, to look at what was built: the Mist Inn, a bell tower, the capital from above and in its streets,
 * the four beasts' grounds, the anchor's crown and its chain, and the land from the fog wall and across its countries.
 * Only with -Dmistinn.epicphotos (a run for looking, not a check); the shots go to the run folder's epic_photos.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnEpicPhotoTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass()) || !Boolean.getBoolean("mistinn.epicphotos")) {
			return;
		}
		WorldMode.choose(WorldMode.EPIC);
		Hardship.choose(Hardship.HUNTER);
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> s.setWorldType(
			new WorldCreationUiState.WorldTypeEntry(s.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(Udasru.PRESET))))
			.create()) {
			context.waitTicks(100);
			context.runOnClient(mc -> {
				mc.options.renderDistance().set(16);
				// an empty hand: the pictures are of the land, not the blade
				mc.player.getInventory().setSelectedSlot(8);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
				server.overworld().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, false, server);
				server.overworld().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER, false, server);
			});
			world.getServer().runCommand("time set 2000");
			BlockPos inn = world.getServer().computeOnServer(server -> server.overworld().getAttached(EpicService.INN));
			look(context, world, inn.offset(16, 9, 18), inn.above(4), "inn");
			look(context, world, inn.offset(3, 6, 2), inn.offset(-5, 6, -3), "inn-upstairs");
			look(context, world, inn.offset(0, 2, 0), inn.offset(-6, 2, 0), "inn-hall");

			UdasruPlaces.Tower tower = UdasruPlaces.Tower.ANCHOR_HILLS;
			build(context, world, "tower_" + tower.id);
			look(context, world, ground(context, world, tower.x + 26, tower.z + 22, 6), ground(context, world, tower.x, tower.z, 18), "tower");
			// the land from the fog wall: what the epic's first card promises
			look(context, world, ground(context, world, 100, 1330, 14), ground(context, world, 100, 950, 20), "fog-wall-north");
			look(context, world, ground(context, world, tower.x, tower.z, 34), ground(context, world, 0, 0, 40), "tower-top-north");

			for (String id : UdasruSites.sites().keySet()) {
				if (id.startsWith("capital_")) {
					build(context, world, id);
				}
			}
			look(context, world, new BlockPos(0, 190, 170), new BlockPos(0, 80, 0), "capital-air");
			look(context, world, ground(context, world, 0, 60, 3), new BlockPos(0, 110, 0), "capital-avenue");
			look(context, world, ground(context, world, 0, 132, 6), ground(context, world, 0, 108, 6), "capital-gate");
			look(context, world, ground(context, world, 40, 66, 3), ground(context, world, 40, 52, 2), "capital-tea");
			look(context, world, ground(context, world, 62, 16, 3), ground(context, world, 62, 0, 5), "capital-library");
			look(context, world, ground(context, world, 0, -40, 3), ground(context, world, 0, -62, 6), "capital-palace");
			look(context, world, ground(context, world, -62, 24, 4), ground(context, world, -62, 0, 2), "capital-market");

			for (UdasruPlaces.GreatBell bell : UdasruPlaces.GreatBell.values()) {
				build(context, world, "arena_" + bell.id);
				build(context, world, "shrine_" + bell.id);
				look(context, world, ground(context, world, bell.x + 30, bell.z + 30, 18), ground(context, world, bell.x, bell.z, 8), "arena-" + bell.id);
			}
			for (String town : new String[] {"salt_town", "bell_port", "woods_village", "frost_gate", "forge_town"}) {
				for (String id : UdasruSites.sites().keySet()) {
					if (id.startsWith(town + "_")) {
						build(context, world, id);
					}
				}
				UdasruSites.Site square = UdasruSites.sites().get(town + "_square");
				look(context, world, ground(context, world, square.x() + 34, square.z() + 34, 16), ground(context, world, square.x(), square.z(), 2), "town-" + town);
			}
			world.getServer().runOnServer(server -> server.overworld().setAttached(EpicService.STATE,
				EpicService.state(server).with(life.shizune.mistinn.epic.EpicState.BRIDGE, true)));
			build(context, world, "crown");
			build(context, world, "chain");
			BlockPos crown = UdasruPlaces.ANCHOR_CROWN;
			look(context, world, new BlockPos(crown.getX() + 40, 100, crown.getZ() - 60), new BlockPos(crown.getX(), 90, crown.getZ()), "crown");
			// the countries
			look(context, world, ground(context, world, -1250, 300, 30), ground(context, world, -1580, -150, 0), "land-salt-flats");
			look(context, world, ground(context, world, 900, -800, 40), ground(context, world, 1400, -1150, 10), "land-rust-wastes");
			look(context, world, ground(context, world, 0, -1200, 40), ground(context, world, 0, -1650, 60), "land-frost-ridge");
			look(context, world, ground(context, world, 1100, 300, 30), ground(context, world, 1620, 460, 20), "land-crystal");
			look(context, world, ground(context, world, -900, -800, 30), ground(context, world, -1200, -1200, 10), "land-old-woods");
			look(context, world, ground(context, world, 200, -200, 40), ground(context, world, 0, -400, 0), "land-mirror-lake");
		}
	}

	/** A place above the ground at (x, z), made if it is not yet. */
	private static BlockPos ground(final ClientGameTestContext context, final TestSingleplayerContext world, final int x, final int z, final int above) {
		return world.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			level.getChunk(x >> 4, z >> 4);
			return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + above, z);
		});
	}

	private static void build(final ClientGameTestContext context, final TestSingleplayerContext world, final String id) {
		world.getServer().runOnServer(server -> build(server, id));
	}

	private static void build(final MinecraftServer server, final String id) {
		UdasruSites.Site site = UdasruSites.sites().get(id);
		if (site == null || UdasruSites.built(server).contains(id)) {
			return;
		}
		for (String before : site.after()) {
			build(server, before);
		}
		ServerLevel level = server.overworld();
		for (int cx = (site.minX() >> 4) - 1; cx <= (site.maxX() >> 4) + 1; cx++) {
			for (int cz = (site.minZ() >> 4) - 1; cz <= (site.maxZ() >> 4) + 1; cz++) {
				level.getChunk(cx, cz);
			}
		}
		for (int i = 0; i < 3 && !UdasruSites.built(server).contains(id); i++) {
			UdasruSites.tick(server);
		}
	}

	private static void look(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos from, final BlockPos at, final String name) {
		world.getServer().runOnServer(server -> {
			double dx = at.getX() - from.getX();
			double dz = at.getZ() - from.getZ();
			double dy = at.getY() + 0.5 - (from.getY() + 1.62);
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			player.teleportTo(server.overworld(), from.getX() + 0.5, from.getY(), from.getZ() + 0.5, Set.of(), yaw, pitch, true);
		});
		context.waitTicks(120);
		// a story card the road brought up (the first morning, the land) is read already: off the screen
		context.runOnClient(mc -> mc.gui.setScreen(null));
		context.waitTicks(3);
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(java.nio.file.Path.of("epic_photos")));
	}
}
