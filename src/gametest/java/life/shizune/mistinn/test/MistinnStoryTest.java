package life.shizune.mistinn.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Set;
import life.shizune.mistinn.bell.BellService;
import life.shizune.mistinn.client.story.JournalScreen;
import life.shizune.mistinn.client.story.SceneScreen;
import life.shizune.mistinn.client.story.StoryCardScreen;
import life.shizune.mistinn.client.story.StoryClient;
import life.shizune.mistinn.keeper.RustWheel;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.keeper.SunkenBell;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.story.StoryPlaces;
import life.shizune.mistinn.story.StoryProgress;
import life.shizune.mistinn.story.StoryRoad;
import life.shizune.mistinn.story.StoryScript;
import life.shizune.mistinn.story.StoryService;
import life.shizune.mistinn.story.StoryStep;
import life.shizune.mistinn.story.StoryWorks;
import life.shizune.mistinn.story.StoryEnding;
import life.shizune.mistinn.story.StoryInn;
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
		if (TestFilter.skip(getClass())) {
			return;
		}
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
				ServerPlayer hunter = player(server);
				check(BellService.rest(hunter, tower.offset(0, 1, 7)), "the lighthouse bell starts ready");
				hunter.removeAttached(life.shizune.mistinn.registry.MistinnAttachments.BELL);
				player(server).teleportTo(server.overworld(), tower.getX() + 0.5, tower.getY() + 1, tower.getZ() + 9.5, Set.of(), 180.0F, 0.0F, true);
			});
			world.getConnection().waitForChunksRender();
			context.waitTicks(20);
			check(step(world) == StoryStep.BELL, "reaching the lighthouse moves on to the bell");
			world.getServer().runOnServer(server -> {
				ServerPlayer hunter = player(server);
				check(!BellService.rest(hunter, places(server).lighthouse().offset(0, 1, 7))
					&& hunter.getAttached(life.shizune.mistinn.registry.MistinnAttachments.BELL) == null,
					"approaching a cooling bell does not ring or bind it again");
			});
			check(step(world) == StoryStep.LAMP_ROOM, "a cooling bell still allows a hunter to proceed with the lighthouse objective");
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
			viewIsClear(context, "the Snuffer");
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
			check(step(world) == StoryStep.LISTEN, "handing in the salt ends the chapter; the next waits for the night");
			check(world.getServer().computeOnServer(server -> server.overworld().getAttached(StoryService.ROAD) != null), "the salt road was laid out");
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
			check(world.getServer().computeOnServer(server -> StoryService.get(player(server)).heard().size() == 3), "the journal heard all three sequences of chapter 1");
			context.takeScreenshot("story-journal");
			context.runOnClient(mc -> mc.gui.setScreen(null));

			// A hunter who catches up after the rest of the party needs no second boss or salt block.
			world.getServer().runOnServer(server -> StoryService.advance(player(server), StoryStep.SNUFFER));
			context.waitFor(mc -> mc.player != null && mc.player.getAttached(StoryService.PROGRESS) != null
				&& mc.player.getAttached(StoryService.PROGRESS).step() == StoryStep.LISTEN, 80);
			context.waitTicks(20);
			context.runOnClient(mc -> StoryClient.skip());
			context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen, 200);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(mc -> mc.gui.screen() == null, 100);

			playRoad(context, world);
		}
		playTheAnchorEnding(context);
	}

	/** Chapter 2, the salt road, and chapter 3, the Sunken Bell, to the end of what is written. */
	private static void playRoad(final ClientGameTestContext context, final TestSingleplayerContext world) {
		// ── The bell at night ─────────────────────────────────────────
		world.getServer().runOnServer(server -> {
			StoryPlaces places = places(server);
			StoryRoad road = server.overworld().getAttached(StoryService.ROAD);
			check(road.cairns().size() == 3, "three cairns mark the road: " + road.cairns());
			check(road.bellStation().distSqr(places.lighthouse()) > 96.0 * 96.0, "the fog-bell station stands apart from the lighthouse");
			// out in the open by the drop station, at noon: nothing yet
			stand(server, places.station().offset(10, 0, 0));
		});
		world.getConnection().waitForChunksRender();
		context.waitTicks(30);
		check(step(world) == StoryStep.LISTEN, "by day no bell is heard");
		world.getServer().runCommand("time set midnight");
		context.waitTicks(30);
		check(step(world) == StoryStep.CAIRN_1, "at night the bell is heard, and the road opens");
		check(world.getServer().computeOnServer(server -> StoryService.get(player(server)).target().isPresent()), "the minimap points at the first cairn");
		context.waitTicks(90);
		context.runOnClient(mc -> StoryClient.skip());
		context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen, 200);
		context.waitTicks(60);
		context.takeScreenshot("story-card-salt-road");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.screen() == null, 100);
		world.getServer().runCommand("time set noon");

		// ── Three cairns, three tags ──────────────────────────────────
		StoryStep[] after = {StoryStep.CAIRN_2, StoryStep.CAIRN_3, StoryStep.BELL_STATION};
		for (int cairn = 0; cairn < 3; cairn++) {
			int at = cairn;
			world.getServer().runOnServer(server -> stand(server, server.overworld().getAttached(StoryService.ROAD).cairns().get(at).offset(2, 0, 2)));
			world.getConnection().waitForChunksRender();
			context.waitTicks(30);
			check(step(world) == after[cairn], "cairn " + (cairn + 1) + " moves on to " + after[cairn].id);
			if (cairn == 0) {
				context.waitTicks(20);
				context.takeScreenshot("story-cairn");
			}
			context.runOnClient(mc -> StoryClient.skip());
		}
		world.getServer().runOnServer(server -> {
			java.util.List<ItemStack> tags = new java.util.ArrayList<>();
			for (int slot = 0; slot < player(server).getInventory().getContainerSize(); slot++) {
				if (player(server).getInventory().getItem(slot).is(MistinnItems.HUNTER_TAG)) {
					tags.add(player(server).getInventory().getItem(slot));
				}
			}
			var grid = life.shizune.mistinn.grid.GridService.get(player(server));
			for (var area : life.shizune.mistinn.grid.Area.values()) {
				grid.items(area).stream().filter(placed -> placed.stack().is(MistinnItems.HUNTER_TAG)).forEach(placed -> tags.add(placed.stack()));
			}
			check(tags.size() == 3, "the hunter carries three tags: " + tags.size());
			check(tags.stream().anyMatch(tag -> tag.getHoverName().getString().contains("3409")), "the last bears number 3409: " + tags);
		});

		// ── The fog-bell station ──────────────────────────────────────
		world.getServer().runOnServer(server -> stand(server, server.overworld().getAttached(StoryService.ROAD).bellStation().offset(0, 1, -12)));
		// out over the sea the chunks may take a while to draw; the card does not need them
		context.waitTicks(40);
		for (int i = 0; i < 40 && !context.computeOnClient(mc -> mc.gui.screen() instanceof StoryCardScreen); i++) {
			context.waitTicks(5);
		}
		String seen = context.computeOnClient(mc -> "screen " + mc.gui.screen() + ", playing " + StoryClient.playing() + ", at " + mc.player.blockPosition());
		check(step(world) == StoryStep.BELL_REST, "reaching the fog-bell station opens chapter 3: " + step(world) + ", " + seen + ", station "
			+ world.getServer().computeOnServer(server -> server.overworld().getAttached(StoryService.ROAD).bellStation()));
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof StoryCardScreen), "with its card: " + seen);
		context.waitTicks(60);
		context.takeScreenshot("story-card-bell-station");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.screen() == null, 100);
		world.getServer().runOnServer(server -> StoryService.rested(player(server)));
		check(step(world) == StoryStep.BELFRY, "resting at its bell sends the hunter up to the belfry");
		world.getServer().runOnServer(server -> {
			BlockPos belfry = server.overworld().getAttached(StoryService.ROAD).belfry();
			player(server).teleportTo(server.overworld(), belfry.getX() - 4.5, belfry.getY(), belfry.getZ() + 0.5, Set.of(), -90.0F, 0.0F, true);
		});
		world.getConnection().waitForChunksRender();
		context.waitTicks(20);
		check(step(world) == StoryStep.SUNKEN_BELL, "up in the belfry, the Sunken Bell");
		check(world.getServer().computeOnServer(server -> server.overworld().getEntitiesOfClass(SunkenBell.class,
			new AABB(server.overworld().getAttached(StoryService.ROAD).belfry()).inflate(8.0)).size() == 1), "it waits there, the one the story built");
		context.takeScreenshot("story-belfry");

		// ── It falls, and says what the bell is for ─────────────────────
		world.getServer().runOnServer(server -> {
			BlockPos belfry = server.overworld().getAttached(StoryService.ROAD).belfry();
			SunkenBell bell = server.overworld().getEntitiesOfClass(SunkenBell.class, new AABB(belfry).inflate(8.0)).getFirst();
			bell.setLastHurtByPlayer(player(server), 100);
			bell.kill(server.overworld());
		});
		context.waitTicks(10);
		check(step(world) == StoryStep.RING, "with the Sunken Bell down, the bell is to be rung");
		context.waitTicks(60);
		check(context.computeOnClient(mc -> StoryClient.line() != null), "its last words play as subtitles");
		viewIsClear(context, "the Sunken Bell");
		context.takeScreenshot("story-sunken-bell-end");
		check(world.getServer().computeOnServer(server -> StoryService.get(player(server)).heard().contains(StoryScript.SUNKEN_BELL_END)), "and are kept for the journal");
		context.runOnClient(mc -> StoryClient.skip());
		context.waitTicks(20);

		// ── The great bell, and the answer ──────────────────────────────
		check(world.getServer().computeOnServer(server -> {
			BlockPos belfry = server.overworld().getAttached(StoryService.ROAD).belfry();
			return !StoryService.ring(player(server), belfry.offset(9, 0, 0));
		}), "a click beside the bell does not ring it");
		check(world.getServer().computeOnServer(server -> {
			BlockPos belfry = server.overworld().getAttached(StoryService.ROAD).belfry();
			BlockPos lip = belfry.above(life.shizune.mistinn.world.BellTower.BELL_LIP - life.shizune.mistinn.world.BellTower.DECK - 1).offset(2, 0, 0);
			return StoryService.ring(player(server), lip);
		}), "the great bell rings");
		check(step(world) == StoryStep.POD, "and the answer is a pod coming down");
		context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen, 400);
		context.waitTicks(80);
		context.takeScreenshot("story-card-answer");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.screen() == null, 100);
		context.runOnClient(mc -> mc.gui.setScreen(new JournalScreen()));
		context.waitTicks(5);
		check(world.getServer().computeOnServer(server -> StoryService.get(player(server)).heard().size() == 8), "the journal heard every sequence of the three chapters");
		context.takeScreenshot("story-journal-chapter-3");
		context.runOnClient(mc -> mc.gui.setScreen(null));

		playWorks(context, world);
	}

	/** Chapter 4: the pod that answered, the salt works inland and the Rust Wheel, to the end of what is written. */
	private static void playWorks(final ClientGameTestContext context, final TestSingleplayerContext world) {
		// ── The pod ───────────────────────────────────────────────────
		world.getServer().runOnServer(server -> {
			StoryWorks works = server.overworld().getAttached(StoryService.WORKS);
			check(works != null, "the pod and the salt works were placed when the bell was rung");
			StoryRoad road = server.overworld().getAttached(StoryService.ROAD);
			check(works.works().distSqr(places(server).lighthouse()) > 96.0 * 96.0 && works.works().distSqr(road.bellStation()) > 96.0 * 96.0,
				"the salt works stand apart from the lighthouse and the fog-bell station: " + works);
			check(StoryService.get(player(server)).target().filter(works.pod()::equals).isPresent(), "the minimap points at the pod");
			check(server.overworld().getBlockEntity(works.pod().offset(life.shizune.mistinn.world.Structures.POD_SUPPLY))
				instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity, "the pod brought supplies");
			stand(server, works.pod().offset(0, 0, -5));
		});
		world.getConnection().waitForChunksRender();
		context.waitTicks(30);
		check(step(world) == StoryStep.SALT_WORKS, "at the pod, the station calls and sends the hunter to the salt works");
		context.waitTicks(40);
		check(context.computeOnClient(mc -> StoryClient.line() != null), "the call plays as subtitles");
		context.takeScreenshot("story-pod");
		check(world.getServer().computeOnServer(server -> StoryService.get(player(server)).heard().contains(StoryScript.SUPPLY_CALL)), "and is kept for the journal");
		context.runOnClient(mc -> StoryClient.skip());
		context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen, 200);
		context.waitTicks(60);
		context.takeScreenshot("story-card-salt-works");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.screen() == null, 100);

		// ── The salt works ────────────────────────────────────────────
		world.getServer().runOnServer(server -> stand(server, server.overworld().getAttached(StoryService.WORKS).works().offset(0, 0, life.shizune.mistinn.world.SaltWorks.YARD + 8)));
		world.getConnection().waitForChunksRender();
		context.waitTicks(30);
		check(step(world) == StoryStep.WORKS_BELL, "at the salt works, rest at its bell first");
		world.getServer().runOnServer(server -> StoryService.rested(player(server)));
		check(step(world) == StoryStep.YARD, "resting at its bell sends the hunter into the yard");
		world.getServer().runOnServer(server -> {
			BlockPos middle = server.overworld().getAttached(StoryService.WORKS).works();
			player(server).teleportTo(server.overworld(), middle.getX() + 0.5, middle.getY(), middle.getZ() + 9.5, Set.of(), 180.0F, 0.0F, true);
		});
		world.getConnection().waitForChunksRender();
		context.waitTicks(20);
		check(step(world) == StoryStep.RUST_WHEEL, "inside the walls, the Rust Wheel");
		check(world.getServer().computeOnServer(server -> server.overworld().getEntitiesOfClass(RustWheel.class,
			new AABB(server.overworld().getAttached(StoryService.WORKS).works()).inflate(10.0)).size() == 1), "it waits there, the one the story built");
		context.takeScreenshot("story-yard");

		// ── It stops, and says what the salt is for ──────────────────────
		world.getServer().runOnServer(server -> {
			BlockPos middle = server.overworld().getAttached(StoryService.WORKS).works();
			RustWheel wheel = server.overworld().getEntitiesOfClass(RustWheel.class, new AABB(middle).inflate(10.0)).getFirst();
			wheel.setLastHurtByPlayer(player(server), 100);
			wheel.kill(server.overworld());
		});
		context.waitTicks(10);
		check(step(world) == StoryStep.CALL, "with the Rust Wheel stopped, the station is to be heard from");
		context.waitTicks(60);
		check(context.computeOnClient(mc -> StoryClient.line() != null), "its last words play as subtitles");
		viewIsClear(context, "the Rust Wheel");
		context.takeScreenshot("story-rust-wheel-end");
		check(world.getServer().computeOnServer(server -> StoryService.get(player(server)).heard().contains(StoryScript.RUST_WHEEL_END)), "and are kept for the journal");
		context.runOnClient(mc -> StoryClient.skip());
		context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen, 200);
		context.waitTicks(80);
		context.takeScreenshot("story-card-stopped");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.screen() == null, 100);
		context.runOnClient(mc -> mc.gui.setScreen(new JournalScreen()));
		context.waitTicks(5);
		check(world.getServer().computeOnServer(server -> StoryService.get(player(server)).heard().size() == 10), "the journal heard every sequence of the four chapters");
		context.takeScreenshot("story-journal-chapter-4");
		context.runOnClient(mc -> mc.gui.setScreen(null));

		playChapterFive(context, world);
	}

	/**
	 * Chapter 5: at the radio the station asks for the salt and another voice asks it not to; the salt thrown into the
	 * Mist Inn's hearth ends the story of this world, Mist Inn. After it the station takes no salt.
	 */
	private static void playChapterFive(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			StoryInn inn = server.overworld().getAttached(StoryService.INN);
			check(inn != null, "the Mist Inn was built when the wheel stopped");
			check(server.overworld().getBlockState(inn.hearth()).is(net.minecraft.world.level.block.Blocks.CAMPFIRE), "its hearth burns: "
				+ server.overworld().getBlockState(inn.hearth()));
			check(StoryService.get(player(server)).target().filter(places(server).radio()::equals).isPresent(), "the minimap points at the drop station's radio");
		});
		// ── The call ────────────────────────────────────────────────
		useRadio(world, ItemStack.EMPTY);
		check(step(world) == StoryStep.CHOOSE, "at the radio the station asks for the salt, and another voice asks it not to");
		context.waitTicks(40);
		check(context.computeOnClient(mc -> StoryClient.line() != null), "the call plays as subtitles");
		context.takeScreenshot("story-call");
		world.getServer().runOnServer(server -> {
			check(StoryService.get(player(server)).heard().contains(StoryScript.STATION_CALL), "and is kept for the journal");
			StoryInn inn = server.overworld().getAttached(StoryService.INN);
			check(StoryService.get(player(server)).target().filter(inn.inn()::equals).isPresent(), "the minimap points at the Mist Inn");
		});
		context.runOnClient(mc -> StoryClient.skip());
		context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen, 200);
		context.waitTicks(60);
		context.takeScreenshot("story-card-the-call");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.screen() == null, 100);

		// ── The Mist Inn: the salt into its fire ─────────────────────
		world.getServer().runOnServer(server -> {
			StoryInn inn = server.overworld().getAttached(StoryService.INN);
			// off to the south of its door, looking back at it
			stand(server, inn.inn().offset(5, 0, 12), 160.0F);
		});
		world.getConnection().waitForChunksRender();
		context.waitTicks(30);
		context.takeScreenshot("story-inn");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			StoryInn inn = server.overworld().getAttached(StoryService.INN);
			ItemStack salt = new ItemStack(MistinnItems.RUST_GOD_SALT);
			player.setItemInHand(InteractionHand.MAIN_HAND, salt);
			player.gameMode.useItemOn(player, server.overworld(), salt, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(inn.hearth()), Direction.UP, inn.hearth(), false));
		});
		check(step(world) == StoryStep.ENDED, "thrown into the Mist Inn's fire, the salt ends the story");
		world.getServer().runOnServer(server -> {
			StoryInn inn = server.overworld().getAttached(StoryService.INN);
			check(StoryService.ending(server) == StoryEnding.INN, "the ending is Mist Inn");
			check(server.overworld().getBlockState(inn.hearth()).is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE), "the fire burns blue");
			check(player(server).getMainHandItem().isEmpty(), "the salt is gone into it");
			check(life.shizune.mistinn.achievement.AchievementService.has(player(server), life.shizune.mistinn.achievement.AchievementService.ENDING_INN),
				"and the ending is an achievement");
		});
		context.waitTicks(40);
		check(context.computeOnClient(mc -> StoryClient.line() != null), "the man from tag 3401 speaks, and the night goes on");
		context.runOnClient(mc -> StoryClient.skip());
		context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen, 400);
		context.waitTicks(80);
		context.takeScreenshot("story-card-inn");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.screen() == null, 100);
		// the other way is shut now
		useRadio(world, new ItemStack(MistinnItems.RUST_GOD_SALT));
		check(world.getServer().computeOnServer(server -> player(server).getMainHandItem().is(MistinnItems.RUST_GOD_SALT)), "after the ending the station takes no salt");
		context.runOnClient(mc -> mc.gui.setScreen(new JournalScreen()));
		context.waitTicks(5);
		check(world.getServer().computeOnServer(server -> StoryService.get(player(server)).heard().size() == 12), "the journal heard every sequence, the ending's too");
		context.takeScreenshot("story-journal-chapter-5");
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	/** The other ending, in a world of its own: chapter 5 reached at once, and the salt sent up from the drop station's radio. */
	private static void playTheAnchorEnding(final ClientGameTestContext context) {
		WorldMode.choose(WorldMode.STORY);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			// the opening card first, out of the way
			context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen, 400);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(mc -> mc.gui.screen() == null, 100);
			world.getServer().runOnServer(server -> {
				player(server).getAbilities().invulnerable = true;
				check(StoryService.ensureRoad(server) != null && StoryService.ensureWorks(server) != null && StoryService.ensureInn(server) != null,
					"the later places can be built at once");
				StoryService.advance(player(server), StoryStep.CHOOSE);
				// the radio mended long ago, as it would be by now
				BlockPos radio = places(server).radio();
				server.overworld().setBlockAndUpdate(radio, server.overworld().getBlockState(radio).setValue(life.shizune.mistinn.story.RadioBlock.LIT, true));
			});
			useRadio(world, new ItemStack(MistinnItems.RUST_GOD_SALT));
			check(step(world) == StoryStep.ENDED, "sent up from the radio, the salt ends the story");
			world.getServer().runOnServer(server -> {
				check(StoryService.ending(server) == StoryEnding.ANCHOR, "the ending is The Anchor");
				check(player(server).getMainHandItem().isEmpty(), "the salt went up in the pod");
				check(life.shizune.mistinn.achievement.AchievementService.has(player(server), life.shizune.mistinn.achievement.AchievementService.ENDING_ANCHOR),
					"and the ending is an achievement");
			});
			context.waitTicks(40);
			check(context.computeOnClient(mc -> StoryClient.line() != null), "Helm thanks the hunter");
			context.runOnClient(mc -> StoryClient.skip());
			context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen, 400);
			context.waitTicks(80);
			context.takeScreenshot("story-card-anchor");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitFor(mc -> mc.gui.screen() == null, 100);
			// the Mist Inn's fire takes nothing now
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				StoryInn inn = server.overworld().getAttached(StoryService.INN);
				ItemStack salt = new ItemStack(MistinnItems.RUST_GOD_SALT);
				player.setItemInHand(InteractionHand.MAIN_HAND, salt);
				check(!StoryService.burn(player, inn.hearth(), salt), "after the ending the Mist Inn's fire takes no salt");
				check(server.overworld().getBlockState(inn.hearth()).is(net.minecraft.world.level.block.Blocks.CAMPFIRE), "and stays as it was");
			});
		}
	}

	/** Nothing stands between a scene's view and what it watches: no wall, and not the hunter. */
	private static void viewIsClear(final ClientGameTestContext context, final String what) {
		String trouble = context.computeOnClient(mc -> {
			SceneScreen scene = SceneScreen.current();
			Vec3 eye = scene == null ? null : scene.eye(1.0F);
			if (eye == null) {
				return "no scene";
			}
			Vec3 target = scene.target();
			net.minecraft.world.phys.HitResult wall = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, target,
				net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
			if (wall.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
				return "a block stands between, at " + wall.getLocation() + " (view " + eye + ", on " + target + ")";
			}
			if (mc.player.getBoundingBox().inflate(0.3).clip(eye, target).isPresent() || mc.player.getEyePosition().distanceTo(eye) < 2.5) {
				return "the hunter stands in it (view " + eye + ", hunter " + mc.player.getEyePosition() + ")";
			}
			return null;
		});
		check(trouble == null, "the scene over " + what + " sees clearly: " + trouble);
	}

	/** Puts the hunter on the ground at (x, z) of {@code at}, under the open sky. */
	private static void stand(final MinecraftServer server, final BlockPos at) {
		var level = server.overworld();
		level.getChunk(at.getX() >> 4, at.getZ() >> 4);
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		player(server).teleportTo(level, at.getX() + 0.5, y, at.getZ() + 0.5, Set.of(), 0.0F, 10.0F, true);
	}

	/** Puts the hunter on the ground at (x, z) of {@code at}, looking along {@code yaw}, a little up. */
	private static void stand(final MinecraftServer server, final BlockPos at, final float yaw) {
		stand(server, at);
		ServerPlayer player = player(server);
		player.teleportTo(server.overworld(), player.getX(), player.getY(), player.getZ(), Set.of(), yaw, -5.0F, true);
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
