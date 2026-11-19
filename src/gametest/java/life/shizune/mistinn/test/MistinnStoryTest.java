package life.shizune.mistinn.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Set;
import life.shizune.mistinn.bell.BellService;
import life.shizune.mistinn.client.story.JournalScreen;
import life.shizune.mistinn.client.story.SceneScreen;
import life.shizune.mistinn.client.story.StoryCardScreen;
import life.shizune.mistinn.client.story.StoryClient;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.story.StoryPlaces;
import life.shizune.mistinn.story.StoryProgress;
import life.shizune.mistinn.story.StoryService;
import life.shizune.mistinn.story.StoryStep;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Plays the prologue and the first chapter of a story world end to end: the opening card, each
 * objective, the radio call, the keeper's last scene, the hand-in, and the journal.
 */
public class MistinnStoryTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet") || Boolean.getBoolean("mistinn.medreel")) {
			return;
		}
		boolean[] reloaded = {false};
		context.runOnClient(mc -> {
			mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
			mc.options.languageCode = "zh_tw";
			mc.getLanguageManager().setSelected("zh_tw");
			mc.reloadResourcePacks().thenRun(() -> reloaded[0] = true);
		});
		context.waitFor(mc -> reloaded[0], 1200);
		// the choice made on the mode screen, as the player would make it
		WorldMode.choose(WorldMode.STORY);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			world.getServer().runCommand("difficulty normal");
			world.getServer().runOnServer(server -> {
				check(WorldMode.of(server) == WorldMode.STORY, "the world is a story world");
				player(server).getAbilities().invulnerable = true;
				StoryPlaces places = server.overworld().getAttached(StoryService.PLACES);
				check(places != null, "the story's places were built");
				check(server.overworld().getBlockState(places.radio()).is(MistinnBlocks.RADIO), "the drop station has its radio");
				check(places.station().distSqr(server.overworld().getRespawnData().pos()) > 140.0 * 140.0, "the drop station is a walk from spawn");
			});

			// ── The opening card ─────────────────────────────────────
			context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen, 400);
			context.waitTicks(60);
			context.takeScreenshot("story-card-drop");
			clickThroughCard(context);
			check(step(world) == StoryStep.SALT, "the prologue starts with salt");
			context.waitTicks(20);
			context.takeScreenshot("story-objective");

			// ── Salt, then the station ────────────────────────────────
			world.getServer().runOnServer(server -> SaltService.set(player(server), StoryStep.SALT_GOAL));
			context.waitTicks(20);
			check(step(world) == StoryStep.STATION, "ten salt moves on to the station");
			check(world.getServer().computeOnServer(server -> StoryService.get(player(server)).target().isPresent()), "the minimap points at the station");
			context.waitTicks(10);
			context.takeScreenshot("story-station-goal");
			world.getServer().runOnServer(server -> {
				StoryPlaces places = server.overworld().getAttached(StoryService.PLACES);
				BlockPos stand = places.station().offset(0, 1, -3);
				player(server).teleportTo(server.overworld(), stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Set.of(), -90.0F, 20.0F, true);
			});
			world.getConnection().waitForChunksRender();
			context.waitTicks(20);
			check(step(world) == StoryStep.RADIO, "reaching the station moves on to the radio");

			// ── The radio: broken without redstone, mended with four ──
			useRadio(world, new ItemStack(Items.REDSTONE, 2));
			check(step(world) == StoryStep.RADIO, "two redstone is not enough");
			useRadio(world, new ItemStack(Items.REDSTONE, 4));
			check(world.getServer().computeOnServer(server -> server.overworld().getBlockState(places(server).radio()).getValue(life.shizune.mistinn.story.RadioBlock.LIT)), "the radio is lit");
			check(step(world) == StoryStep.LIGHTHOUSE, "the first call sends the hunter to the lighthouse");
			context.waitTicks(70);
			check(context.computeOnClient(mc -> StoryClient.line() != null), "Helm's call plays as subtitles");
			context.takeScreenshot("story-radio");
			context.runOnClient(mc -> StoryClient.skip());
			context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen, 100);
			context.waitTicks(60);
			context.takeScreenshot("story-card-lighthouse");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(mc -> mc.gui.screen() == null, 100);

			// ── The lighthouse: reach it, rest, climb ─────────────────
			world.getServer().runOnServer(server -> {
				BlockPos tower = places(server).lighthouse();
				player(server).teleportTo(server.overworld(), tower.getX() + 0.5, tower.getY() + 1, tower.getZ() + 9.5, Set.of(), 180.0F, 0.0F, true);
			});
			world.getConnection().waitForChunksRender();
			context.waitTicks(20);
			check(step(world) == StoryStep.BELL, "reaching the lighthouse moves on to the bell");
			world.getServer().runOnServer(server -> BellService.rest(player(server), places(server).lighthouse().offset(0, 1, 7), false));
			check(step(world) == StoryStep.LAMP_ROOM, "resting at the bell moves on to the lamp room");
			world.getServer().runOnServer(server -> {
				BlockPos room = places(server).lampRoom();
				player(server).teleportTo(server.overworld(), room.getX() + 0.5, room.getY(), room.getZ() - 2.5, Set.of(), 0.0F, 10.0F, true);
			});
			world.getConnection().waitForChunksRender();
			context.waitTicks(30);
			check(step(world) == StoryStep.SNUFFER, "the lamp room moves on to the keeper");

			// ── The keeper falls: its last words, in the scene ────────
			world.getServer().runOnServer(server -> {
				Snuffer keeper = server.overworld().getEntitiesOfClass(Snuffer.class, new AABB(places(server).lampRoom()).inflate(16.0)).getFirst();
				keeper.setNoAi(true);
				keeper.hurtServer(server.overworld(), player(server).damageSources().playerAttack(player(server)), 100000.0F);
			});
			context.waitFor(mc -> mc.gui.screen() instanceof SceneScreen, 100);
			check(step(world) == StoryStep.DELIVER, "the fallen keeper moves on to the hand-in");
			context.waitTicks(80);
			context.takeScreenshot("story-scene");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(mc -> mc.gui.screen() == null && !StoryClient.inScene(), 100);

			// ── The salt goes into the pod; no one answers ────────────
			world.getServer().runOnServer(server -> {
				BlockPos stand = places(server).station().offset(0, 1, -3);
				player(server).teleportTo(server.overworld(), stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Set.of(), -90.0F, 20.0F, true);
			});
			world.getConnection().waitForChunksRender();
			context.waitTicks(10);
			useRadio(world, new ItemStack(MistinnItems.RUST_GOD_SALT));
			check(step(world) == StoryStep.WAIT, "handing in the salt ends the chapter");
			context.waitTicks(40);
			context.runOnClient(mc -> StoryClient.skip());
			context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen, 100);
			context.waitTicks(60);
			context.takeScreenshot("story-card-unanswered");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(mc -> mc.gui.screen() == null, 100);

			// ── The journal keeps it all ──────────────────────────────
			context.runOnClient(mc -> mc.gui.setScreen(new JournalScreen()));
			context.waitTicks(5);
			check(world.getServer().computeOnServer(server -> StoryService.get(player(server)).heard().size() == 3), "the journal heard all three sequences");
			context.takeScreenshot("story-journal");
			context.runOnClient(mc -> mc.gui.setScreen(null));
		}
	}

	private static void clickThroughCard(final ClientGameTestContext context) {
		for (int i = 0; i < 20 && context.computeOnClient(mc -> mc.gui.screen() instanceof StoryCardScreen); i++) {
			context.runOnClient(mc -> ((StoryCardScreen) mc.gui.screen()).advance());
			context.waitTicks(3);
		}
		check(context.computeOnClient(mc -> mc.gui.screen() == null), "the card closes after its last line");
	}

	private static void useRadio(final TestSingleplayerContext world, final ItemStack stack) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			BlockPos radio = places(server).radio();
			player.gameMode.useItemOn(player, server.overworld(), stack, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(radio), Direction.WEST, radio, false));
		});
	}

	private static StoryStep step(final TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> {
			StoryProgress progress = StoryService.get(player(server));
			return progress == null ? null : progress.step();
		});
	}

	private static StoryPlaces places(final MinecraftServer server) {
		return server.overworld().getAttached(StoryService.PLACES);
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("check failed: " + what);
		}
	}
}
