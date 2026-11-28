package life.shizune.mistinn.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Set;
import life.shizune.mistinn.client.epic.EpicNameScreen;
import life.shizune.mistinn.client.epic.Legacy;
import life.shizune.mistinn.client.epic.LegacyScreen;
import life.shizune.mistinn.client.guide.HunterGuideScreen;
import life.shizune.mistinn.client.story.StoryCardScreen;
import life.shizune.mistinn.epic.EpicService;
import life.shizune.mistinn.epic.EpicState;
import life.shizune.mistinn.epic.EpicStep;
import life.shizune.mistinn.epic.UnsentLetterItem;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.station.StaffRole;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.udasru.Udasru;
import life.shizune.mistinn.udasru.UdasruPlaces;
import life.shizune.mistinn.udasru.UdasruSites;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The epic as a player's own hands play it, not through the test hooks: a tower bell rung with a right-click and a
 * weapon in hand, a letter read by using it, the save closed and opened again on the way (nothing undone, nothing given
 * twice, nothing built twice), the name typed into the Chronicler's screen and written with its button, and the pack
 * sealed from the guide's button. Every button inside the epic clicks with the epic's own sound.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnEpicPlayTest implements FabricClientGameTest {
	private static final UdasruPlaces.Tower FIRST = UdasruPlaces.Tower.ANCHOR_HILLS;
	/** The mouse's buttons, as the game numbers them. */
	private static final int LEFT = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
	private static final int RIGHT = com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		Path pack = Path.of("epic_play_pack.dat").toAbsolutePath();
		context.runOnClient(mc -> {
			Legacy.override = pack;
			Legacy.reread();
		});
		try {
			Files.deleteIfExists(pack);
		} catch (java.io.IOException ignored) {
			// replaced when sealed
		}
		List<String> heard = new CopyOnWriteArrayList<>();
		SoundEventListener listener = (sound, events, range) -> heard.add(sound.getIdentifier().toString());
		context.runOnClient(mc -> mc.getSoundManager().addListener(listener));

		// every use of a block, as early as any handler sees it, on either side
		List<String> uses = new CopyOnWriteArrayList<>();
		net.minecraft.resources.Identifier early = life.shizune.mistinn.Mistinn.id("epic_play_early");
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.addPhaseOrdering(early, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(early, (player, level, hand, hit) -> {
			uses.add((level.isClientSide() ? "client " : "server ") + hand + " " + hit.getBlockPos() + " " + player.getItemInHand(hand).getItem());
			return net.minecraft.world.InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.addPhaseOrdering(early, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(early, (player, level, hand, entity, hit) -> {
			uses.add((level.isClientSide() ? "client " : "server ") + "entity " + entity + " " + player.getItemInHand(hand).getItem());
			return net.minecraft.world.InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.addPhaseOrdering(early, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register(early, (player, level, hand) -> {
			uses.add((level.isClientSide() ? "client " : "server ") + "item " + player.getItemInHand(hand).getItem());
			return net.minecraft.world.InteractionResult.PASS;
		});
		WorldMode.choose(WorldMode.EPIC);
		Hardship.choose(Hardship.HUNTER);
		TestWorldSave save;
		BlockPos[] bell = new BlockPos[1];
		List<String> builtBefore = new ArrayList<>();
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> s.setWorldType(
			new WorldCreationUiState.WorldTypeEntry(s.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(Udasru.PRESET))))
			.create()) {
			save = world.getWorldSave();
			context.waitTicks(100);
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			// out of the inn and the fog (the road's own steps), up to the first tower's belfry
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				EpicService.heard(player, StaffRole.INNKEEPER, "wake");
				go(player, FIRST.x, UdasruPlaces.OUT_OF_THE_FOG_Z - 40);
				EpicService.look(server);
				check(EpicService.state(server).at() == EpicStep.FIRST_TOWER, "out of the fog: " + EpicService.state(server).at());
				build(server, "tower_" + FIRST.id);
				ServerLevel level = server.overworld();
				bell[0] = towerBell(level);
				check(level.getBlockState(bell[0]).is(Blocks.BELL), "the tower's bell hangs: " + bell[0]);
				// a mark left in the belfry, to see the tower is not built over again
				level.setBlockAndUpdate(bell[0].offset(3, -5, 3), Blocks.GOLD_BLOCK.defaultBlockState());
				player.teleportTo(level, bell[0].getX() + 2.5, bell[0].getY() - 4, bell[0].getZ() + 0.5, Set.of(), 90.0F, 0.0F, true);
			});
			// the tower's chunks reach the hunter's own game
			// (a story card pauses a single-player world, chunks and all: the hunter reads it and puts it away first)
			for (int i = 0; i < 60 && !context.computeOnClient(mc -> mc.level.getBlockState(bell[0]).is(Blocks.BELL)); i++) {
				context.runOnClient(mc -> {
					if (mc.gui.screen() != null) {
						mc.gui.screen().onClose();
					}
				});
				context.waitTicks(10);
			}
			String client = context.computeOnClient(mc -> "client at " + mc.player.position() + ", has chunk " + mc.level.hasChunk(bell[0].getX() >> 4, bell[0].getZ() >> 4)
				+ ", bell " + mc.level.getBlockState(bell[0]) + ", floor " + mc.level.getBlockState(bell[0].below(5)) + ", shaft " + mc.level.getBlockState(bell[0].offset(4, -10, 0))
				+ ", ground " + mc.level.getBlockState(bell[0].offset(0, -40, 10)));
			String served = world.getServer().computeOnServer(sv -> "server floor " + sv.overworld().getBlockState(bell[0].below(5)) + ", shaft "
				+ sv.overworld().getBlockState(bell[0].offset(4, -10, 0)) + ", player " + player(sv).position());
			check(context.computeOnClient(mc -> mc.level.getBlockState(bell[0]).is(Blocks.BELL)), "the hunter's game sees the tower: " + client + " / " + served);
			context.waitTicks(10);
			closeScreens(context);
			// a weapon in hand, as 3412 always has, and the bell rung with a right-click
			context.getInput().pressKey(o -> o.keyHotbarSlots[0]);
			context.getInput().lookAt(bell[0]);
			context.waitTicks(2);
			check(context.computeOnClient(mc -> life.shizune.mistinn.weapon.WeaponStyle.of(mc.player.getMainHandItem()) != null), "a weapon in hand");
			String seen = context.computeOnClient(mc -> mc.player.position() + " yaw " + mc.player.getYRot() + " pitch " + mc.player.getXRot() + " sees "
				+ (mc.hitResult instanceof BlockHitResult hit ? mc.level.getBlockState(hit.getBlockPos()) + " at " + hit.getBlockPos() : String.valueOf(mc.hitResult)));
			check(context.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit && mc.level.getBlockState(hit.getBlockPos()).is(Blocks.BELL)),
				"looking at the bell: " + seen);
			String before = context.computeOnClient(mc -> "screen " + mc.gui.screen() + ", using " + mc.player.isUsingItem() + ", aim " + mc.hitResult.getType()
				+ (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult e ? " " + e.getEntity() : "") + ", delay " + mc.player.getAttackStrengthScale(0.0F));
			context.getInput().pressMouse(RIGHT);
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				EpicState state = EpicService.state(server);
				uses.add(before);
				check(state.rungTower(FIRST.bit()) && state.at() == EpicStep.CAPITAL, "the first tower rung by hand: " + state + "; uses " + uses);
				ServerPlayer player = player(server);
				check(player.getRespawnConfig() != null && player.getRespawnConfig().respawnData().pos().distSqr(new BlockPos(FIRST.x, player.getRespawnConfig()
					.respawnData().pos().getY(), FIRST.z)) < 12 * 12, "and 3412 will wake at it: " + player.getRespawnConfig());
				// a letter in hand, read by using it, looking out over the wall
				player.getInventory().setItem(8, UnsentLetterItem.letter(FIRST.ordinal()));
				builtBefore.addAll(UdasruSites.built(server));
			});
			context.waitTicks(5);
			closeScreens(context);
			context.getInput().pressKey(o -> o.keyHotbarSlots[8]);
			context.getInput().lookAt(-90.0F, 0.0F);
			context.waitTicks(2);
			context.getInput().pressMouse(RIGHT);
			context.waitTicks(10);
			world.getServer().runOnServer(server -> check(EpicService.state(server).letters() == 1, "the letter read by using it: " + EpicService.state(server)));
		}

		// the save opened again, half way along the road
		try (TestSingleplayerContext world = save.open()) {
			context.waitTicks(80);
			world.getServer().runOnServer(server -> {
				check(WorldMode.of(server) == WorldMode.EPIC && Hardship.of(server) == Hardship.HUNTER, "still the epic, at the Hunter's hardship");
				EpicState state = EpicService.state(server);
				check(state.at() == EpicStep.CAPITAL && state.rungTower(FIRST.bit()) && state.letters() == 1, "the road kept: " + state);
				ServerPlayer player = player(server);
				check(player.blockPosition().distSqr(bell[0]) < 12 * 12, "3412 opens the save where they stood, not back in bed: " + player.blockPosition());
				int weapons = 0;
				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					if (life.shizune.mistinn.weapon.WeaponStyle.of(player.getInventory().getItem(i)) != null) {
						weapons++;
					}
				}
				check(weapons == 6, "and is not dressed a second time: " + weapons + " weapons");
				check(server.overworld().getBlockState(bell[0].offset(3, -5, 3)).is(Blocks.GOLD_BLOCK), "the tower not built over again");
				check(UdasruSites.built(server).containsAll(builtBefore), "nothing built forgotten");
				check(player.getRespawnConfig() != null && player.getRespawnConfig().respawnData().pos().distSqr(new BlockPos(FIRST.x, player.getRespawnConfig()
					.respawnData().pos().getY(), FIRST.z)) < 12 * 12, "still waking at the rung tower");
				// to the last page: the Chronicler asks for the name
				server.overworld().setAttached(EpicService.STATE, state.at(EpicStep.NAME));
				ServerPlayNetworking.send(player, new EpicService.AskNamePayload(true));
			});
			context.waitFor(mc -> {
				if (mc.gui.screen() instanceof StoryCardScreen card) {
					card.onClose();
				}
				return mc.gui.screen() instanceof EpicNameScreen;
			}, 2400);
			context.getInput().typeChars("小石頭");
			heard.clear();
			click(context, "epic.mistinn.name.write");
			context.waitTicks(10);
			check(context.computeOnClient(mc -> heard.contains(MistinnSounds.EPIC_CLICK.location().toString())), "a button in the epic clicks with its own sound: " + heard);
			world.getServer().runOnServer(server -> {
				EpicState state = EpicService.state(server);
				check(state.at() == EpicStep.ENDED && state.name().equals("小石頭"), "the name typed and written: " + state);
				check(carries(player(server), MistinnItems.LEGACY_SEAL), "the pack seal given, in the pack or the pockets");
			});
			context.waitFor(mc -> {
				if (mc.gui.screen() instanceof StoryCardScreen card) {
					card.onClose();
				}
				EpicState seen = mc.level.getAttached(EpicService.STATE);
				return seen != null && seen.at() == EpicStep.ENDED && mc.gui.screen() == null;
			}, 2400);
			// the pack sealed from the guide's own button
			context.setScreen(HunterGuideScreen::new);
			context.waitTicks(5);
			check(context.computeOnClient(mc -> HunterGuideScreen.pageKey(58).endsWith(".epic") && HunterGuideScreen.pageKey(2).endsWith(".2")),
				"in the epic the guide is the library's copy: Udasru where the station's book says the zone is empty");
			click(context, "epic.mistinn.legacy.title");
			context.waitForScreen(LegacyScreen.class);
			click(context, "epic.mistinn.legacy.seal");
			context.waitTicks(10);
			int sealed = context.computeOnClient(mc -> Legacy.pack().size());
			check(sealed >= 14, "the pack sealed from the guide: " + sealed);
			context.runOnClient(mc -> mc.gui.setScreen(null));
		}
		context.runOnClient(mc -> {
			mc.getSoundManager().removeListener(listener);
			Legacy.override = null;
			Legacy.reread();
		});
	}

	/** Clicks the button on the screen showing this text, with the mouse, where it is drawn. */
	private static void click(final ClientGameTestContext context, final String key) {
		double[] at = context.computeOnClient(mc -> {
			Screen screen = mc.gui.screen();
			check(screen != null, "a screen to click on for " + key);
			for (GuiEventListener child : screen.children()) {
				if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(Component.translatable(key).getString())) {
					check(widget.active && widget.visible, key + " can be pressed");
					check(widget.getY() + widget.getHeight() <= screen.height && widget.getX() + widget.getWidth() <= screen.width, key + " is on the screen");
					double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
					return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
				}
			}
			throw new AssertionError("no button for " + key + " on " + screen);
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(1);
		context.getInput().pressMouse(LEFT);
		context.waitTicks(2);
	}

	/** Closes whatever card or screen the road has put up, so the hands are free. */
	private static void closeScreens(final ClientGameTestContext context) {
		// cards wait in line: put each away until none is left and none comes up a moment later
		for (int quiet = 0; quiet < 10;) {
			boolean clear = context.computeOnClient(mc -> {
				Screen screen = mc.gui.screen();
				if (screen != null) {
					screen.onClose();
				}
				return mc.gui.screen() == null && life.shizune.mistinn.client.story.StoryClient.idle();
			});
			quiet = clear ? quiet + 1 : 0;
			context.waitTicks(1);
		}
	}

	/** Whether the hunter carries one of these, in the pockets or the grid pack. */
	private static boolean carries(final ServerPlayer player, final net.minecraft.world.item.Item item) {
		if (player.getInventory().contains(new ItemStack(item))) {
			return true;
		}
		var grid = life.shizune.mistinn.grid.GridService.get(player);
		for (var area : life.shizune.mistinn.grid.GridService.ORDER) {
			for (var placed : grid.items(area)) {
				if (placed.stack().is(item)) {
					return true;
				}
			}
		}
		return false;
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void go(final ServerPlayer player, final int x, final int z) {
		ServerLevel level = player.level();
		for (int cx = (x >> 4) - 3; cx <= (x >> 4) + 3; cx++) {
			for (int cz = (z >> 4) - 3; cz <= (z >> 4) + 3; cz++) {
				level.getChunk(cx, cz);
			}
		}
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), 0.0F, 0.0F, true);
	}

	private static void build(final MinecraftServer server, final String id) {
		UdasruSites.Site site = UdasruSites.sites().get(id);
		ServerLevel level = server.overworld();
		for (String before : site.after()) {
			build(server, before);
		}
		for (int cx = (site.minX() >> 4) - 1; cx <= (site.maxX() >> 4) + 1; cx++) {
			for (int cz = (site.minZ() >> 4) - 1; cz <= (site.maxZ() >> 4) + 1; cz++) {
				level.getChunk(cx, cz);
			}
		}
		for (int i = 0; i < 4 && !UdasruSites.built(server).contains(id); i++) {
			UdasruSites.tick(server);
		}
		check(UdasruSites.built(server).contains(id), "built " + id);
	}

	private static BlockPos towerBell(final ServerLevel level) {
		for (int y = level.getMaxY(); y > level.getMinY(); y--) {
			BlockPos at = new BlockPos(FIRST.x, y, FIRST.z);
			if (level.getBlockState(at).is(Blocks.BELL)) {
				return at;
			}
		}
		return new BlockPos(FIRST.x, 0, FIRST.z);
	}

	private static void check(final boolean ok, final String what) {
		if (!ok) {
			throw new AssertionError(what);
		}
		life.shizune.mistinn.Mistinn.LOGGER.info("[mistinn epic play] ok: {}", what);
	}
}
