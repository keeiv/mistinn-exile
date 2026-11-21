package life.shizune.mistinn.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.boss.BossMove;
import life.shizune.mistinn.boss.PlayerModel;
import life.shizune.mistinn.boss.Response;
import life.shizune.mistinn.keeper.LampStandBlock;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 實機影片: a hunter played by real key presses fights the Snuffer in a lamp room for about a minute, frame by
 * frame. The hunter keeps a distance, backs off now and then (inviting the slide, the rush, the hook and the
 * leap), always rolls to the left early when a tell begins (so the keeper can be seen learning it), and strikes
 * into the keeper's recoveries. Halfway the keeper goes dark; near the end the hunter lights the four lamps and it
 * staggers. Each frame's keeper move and what the keeper believes of the hunter are written beside the frames for
 * the captions. Runs only with -Dmistinn.bossreel=true (build.gradle: -PbossReel); writes to bossreel/.
 */
@SuppressWarnings("UnstableApiUsage")
public class MistinnBossReelTest implements FabricClientGameTest {
	private static final Path OUT = Path.of("bossreel");
	private static final String TAG = "mistinn.reel_keeper";
	private static final int LENGTH = 1300;
	private static final int WAKE = 90;
	private static final int DARK = 720;
	private static final int LAMPS = 1040;
	/** The family of the keeper's latest tell, whose left-roll belief the caption shows. */
	private String lastFamily = "";

	private record Frame(int tick, String move, String kind, int moveTicks, boolean dark, boolean staggered, double leftRoll, double distance) {
	}

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass()) || !Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		context.getInput().resizeWindow(1280, 720);
		// the game in the player's own language
		context.runOnClient(mc -> {
			mc.options.languageCode = "zh_tw";
			mc.getLanguageManager().setSelected("zh_tw");
			mc.reloadResourcePacks();
		});
		context.waitTicks(200);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			for (String command : List.of("time set 14000", "gamerule advance_time false", "weather clear", "gamerule advance_weather false",
				"gamerule spawn_mobs false", "difficulty normal", "gamemode survival @a")) {
				world.getServer().runCommand(command);
			}
			BlockPos centre = world.getServer().computeOnServer(MistinnBossReelTest::buildRoom);
			context.waitTicks(20);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.teleportTo(server.overworld(), centre.getX() + 0.5, centre.getY(), centre.getZ() + 11.5, Set.of(), 180.0F, 5.0F, true);
				BodyService.restoreAll(player);
				player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(life.shizune.mistinn.registry.MistinnItems.MIST_SPEAR));
				player.getInventory().setSelectedSlot(0);
				Snuffer keeper = MistinnEntities.SNUFFER.create(server.overworld(), EntitySpawnReason.COMMAND);
				keeper.snapTo(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, 0.0F, 0.0F);
				keeper.setYHeadRot(0.0F);
				keeper.setYBodyRot(0.0F);
				keeper.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(0.0);
				keeper.addTag(TAG);
				server.overworld().addFreshEntity(keeper);
			});
			world.getServer().runCommand("effect give @a minecraft:resistance infinite 2 true");
			world.getServer().runCommand("effect give @a minecraft:saturation infinite 0 true");
			// what the hunter sees: first person, the keeper in front of them
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(30);
			List<Frame> frames = new ArrayList<>();
			int rollAt = -100;
			int attackAt = -100;
			int down = -1;
			for (int tick = 0; tick < LENGTH && (down < 0 || tick < down + 50); tick++) {
				int t = tick;
				if (t == 40) {
					// before it wakes it shows what it will do: the spear spun once, a thrust is coming
					world.getServer().runOnServer(server -> keeper(server).perform("twirl"));
				}
				if (t == WAKE) {
					world.getServer().runOnServer(server -> keeper(server).getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(40.0));
				}
				if (t == DARK) {
					world.getServer().runOnServer(server -> keeper(server).setHealth(Snuffer.MAX_HEALTH * 0.48F));
				}
				if (t == LAMPS && down < 0) {
					world.getServer().runOnServer(server -> {
						Snuffer keeper = keeper(server);
						for (BlockPos lamp : keeper.lamps(server.overworld())) {
							server.overworld().setBlockAndUpdate(lamp, server.overworld().getBlockState(lamp).setValue(LampStandBlock.LIT, true));
						}
					});
				}
				if (t % 200 == 0) {
					// the hunter is made to last the minute: their wounds closed now and then
					world.getServer().runOnServer(server -> {
						ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
						BodyService.restoreAll(player);
						life.shizune.mistinn.body.WoundService.clearAll(player);
						player.setHealth(player.getMaxHealth());
					});
				}
				Frame frame = world.getServer().computeOnServer(server -> this.frame(server, t));
				frames.add(frame);
				if ("DOWN".equals(frame.kind())) {
					down = down < 0 ? t : down;
					context.waitTick();
					context.takeScreenshot(TestScreenshotOptions.of(String.format("f_%05d", t)).withSize(1280, 720).disableCounterPrefix().withDestinationDir(OUT));
					continue;
				}
				// the hunter's hands
				var input = context.getInput();
				Vec3[] where = world.getServer().computeOnServer(server -> new Vec3[] {
					server.getPlayerList().getPlayers().getFirst().getEyePosition(), keeper(server).position().add(0.0, 1.8, 0.0)});
				Vec3 to = where[1].subtract(where[0]);
				float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
				float pitch = (float) -Math.toDegrees(Math.atan2(to.y, to.horizontalDistance()));
				input.lookAt(yaw, Mth.clamp(pitch, -30.0F, 40.0F));
				double keep = t < WAKE ? 7.0 : (t / 160) % 3 == 2 ? 9.5 : 4.0;
				// while rolling, only the left key: the roll goes where the keys point
				boolean rolling = t - rollAt <= 8;
				hold(input, InputConstants.KEY_W, !rolling && (frame.distance() > keep + 1.0 && !frame.staggered() || frame.staggered() && frame.distance() > 2.5));
				hold(input, InputConstants.KEY_S, !rolling && frame.distance() < keep - 1.0 && !frame.staggered());
				BossMove move = frame.move().isEmpty() ? null : life.shizune.mistinn.keeper.SnufferMoves.SET.get(frame.move());
				boolean tell = move != null && (move.kind() == BossMove.Kind.ATTACK || move.kind() == BossMove.Kind.FEINT || !move.strikes().isEmpty());
				if (tell && t >= WAKE && frame.moveTicks() == Math.max(1, move.windup() - 10) && t - rollAt > 20) {
					// always to the left, and always early: the habit the keeper is meant to read (left held first, the roll the tick after)
					input.holdKeyFor(InputConstants.KEY_A, 6);
					rollAt = t;
				}
				if (t == rollAt + 1) {
					input.pressKey(InputConstants.KEY_LALT);
				}
				boolean opening = move != null && frame.moveTicks() > move.windup() + move.active() + 2 || frame.staggered();
				if (opening && frame.distance() < 3.8 && t - attackAt > 12) {
					input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
					attackAt = t;
				}
				context.waitTick();
				context.takeScreenshot(TestScreenshotOptions.of(String.format("f_%05d", t)).withSize(1280, 720).disableCounterPrefix().withDestinationDir(OUT));
			}
			hold(context.getInput(), InputConstants.KEY_W, false);
			hold(context.getInput(), InputConstants.KEY_S, false);
			write(frames);
		}
	}

	private static void hold(final net.fabricmc.fabric.api.client.gametest.v1.TestInput input, final int key, final boolean down) {
		if (down) {
			input.holdKey(key);
		} else {
			input.releaseKey(key);
		}
	}

	private Frame frame(final MinecraftServer server, final int tick) {
		Snuffer keeper = keeper(server);
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		if (keeper == null) {
			// brought down: the fight is over
			return new Frame(tick, "", "DOWN", 0, true, false, 0.0, 99.0);
		}
		BossMove move = keeper.currentMove();
		if (move != null && (move.kind() == BossMove.Kind.ATTACK || move.kind() == BossMove.Kind.FEINT || !move.strikes().isEmpty())) {
			BossMove shown = move.imitates() != null ? life.shizune.mistinn.keeper.SnufferMoves.SET.get(move.imitates()) : move;
			this.lastFamily = shown.families().isEmpty() ? shown.id() : shown.families().iterator().next();
		}
		double left = 0.0;
		PlayerModel model = keeper.brain().model(player.getUUID());
		if (model != null && !this.lastFamily.isEmpty()) {
			left = model.responses(this.lastFamily).get(Response.ROLL_LEFT);
		}
		return new Frame(tick, move == null ? "" : move.id(), move == null ? "" : move.kind().name(), keeper.moveTicks(), keeper.dark(), keeper.staggered(), left,
			keeper.position().subtract(player.position()).horizontalDistance());
	}

	private static Snuffer keeper(final MinecraftServer server) {
		var found = server.overworld().getEntitiesOfClass(Snuffer.class, new AABB(BlockPos.ZERO).inflate(30_000_000.0), k -> k.entityTags().contains(TAG));
		return found.isEmpty() ? null : found.getFirst();
	}

	/** A lamp room: a tiled floor, walls of deepslate bricks, four lamp stands lit at its corners, a few lanterns on the walls. */
	private static BlockPos buildRoom(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		BlockPos centre = player.blockPosition().offset(0, 0, 30).immutable();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-14, -1, -14), centre.offset(14, 8, 14))) {
			int dx = Math.abs(pos.getX() - centre.getX());
			int dz = Math.abs(pos.getZ() - centre.getZ());
			int dy = pos.getY() - centre.getY();
			if (dy == -1) {
				level.setBlockAndUpdate(pos, (dx + dz) % 2 == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState());
			} else if (dx == 14 || dz == 14) {
				level.setBlockAndUpdate(pos, dy < 6 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
			} else {
				level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			}
		}
		for (int[] corner : new int[][] {{-8, -8}, {8, -8}, {-8, 8}, {8, 8}}) {
			level.setBlockAndUpdate(centre.offset(corner[0], 0, corner[1]), MistinnBlocks.LAMP_STAND.defaultBlockState().setValue(LampStandBlock.LIT, true));
		}
		// a dim, even light high over the room, unseen, so the fight can be followed once the lamps are out
		for (int x = -12; x <= 12; x += 6) {
			for (int z = -12; z <= 12; z += 6) {
				level.setBlockAndUpdate(centre.offset(x, 6, z), Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 10));
			}
		}
		for (int[] wall : new int[][] {{0, -13}, {-13, 0}, {13, 0}, {-6, 13}, {6, 13}}) {
			level.setBlockAndUpdate(centre.offset(wall[0], 4, wall[1]), Blocks.DEEPSLATE_BRICKS.defaultBlockState());
			level.setBlockAndUpdate(centre.offset(wall[0], 3, wall[1]), Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
		}
		return centre;
	}

	private static void write(final List<Frame> frames) {
		StringBuilder out = new StringBuilder("tick,move,kind,move_ticks,dark,staggered,left_roll,distance\n");
		for (Frame f : frames) {
			out.append(f.tick()).append(',').append(f.move()).append(',').append(f.kind()).append(',').append(f.moveTicks()).append(',')
				.append(f.dark()).append(',').append(f.staggered()).append(',').append(String.format("%.3f", f.leftRoll())).append(',')
				.append(String.format("%.2f", f.distance())).append('\n');
		}
		try {
			Files.createDirectories(OUT);
			Files.writeString(OUT.resolve("frames.csv"), out.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
