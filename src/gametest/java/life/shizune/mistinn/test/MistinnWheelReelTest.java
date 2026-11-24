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
import life.shizune.mistinn.keeper.RustWheel;
import life.shizune.mistinn.keeper.WheelMoves;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.shore.SaltCorpse;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import life.shizune.mistinn.world.Builder;
import life.shizune.mistinn.world.SaltWorks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 鏽輪實機影片: a hunter played by real key presses fights the Rust Wheel in its salt works for about a minute, frame by
 * frame. The hunter steps into the fight as the mist closes the gate, then baits the wheel from in front of a pillar and
 * rolls aside the moment its run is aimed, so the wheel runs on into the pillar and sticks. The moment it sticks the
 * hunter charges a heavy blow on the way round to its back and lets it go full (stuck, it is thrown off balance at once),
 * strikes into the recovery of each move it spends, and lingers at its back long enough to get the chain. Halfway the man tears a chain loose and the plates fall; near the end it goes
 * down. Each frame's move, momentum and state, the blows it takes and the hunter's swings are written beside the frames
 * for the captions and the sound. Runs only with -Dmistinn.wheelreel=true (build.gradle: -PwheelReel); writes to
 * wheelreel/.
 */
@SuppressWarnings("UnstableApiUsage")
public class MistinnWheelReelTest implements FabricClientGameTest {
	private static final Path OUT = Path.of("wheelreel");
	private static final int LENGTH = 1300;
	/** When the hunter steps into the fight. */
	private static final int ENTER = 40;
	/** When the man tears his chain loose: the wheel is put just under half. */
	private static final int BREAK = 700;
	/** When the next blow is made to bring it down. */
	private static final int FALL = 1180;
	/** How far in front of a pillar, toward the middle, the hunter waits for the run. */
	private static final double BAIT = 2.4;
	/** How far behind the stuck wheel the hunter stands to strike its back. */
	private static final double BEHIND = 2.3;

	private record Frame(int tick, String move, String kind, int moveTicks, boolean stuck, boolean broken, boolean staggered, int momentum,
		double distance, String hit, boolean swing) {
	}

	private BlockPos middle = BlockPos.ZERO;
	private float lastHealth = -1.0F;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass()) || !Boolean.getBoolean("mistinn.wheelreel")) {
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
			for (String command : List.of("time set 12400", "gamerule advance_time false", "weather clear", "gamerule advance_weather false",
				"gamerule spawn_mobs false", "difficulty normal", "gamemode survival @a")) {
				world.getServer().runCommand(command);
			}
			this.middle = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				BlockPos floor = player.blockPosition().offset(0, -1, 40);
				BlockPos built = SaltWorks.build(new Builder(server.overworld(), floor, RandomSource.create(11L)));
				// the dead lying in the yard would wake and walk into the fight
				for (SaltCorpse corpse : server.overworld().getEntitiesOfClass(SaltCorpse.class, new AABB(built).inflate(40.0))) {
					corpse.discard();
				}
				// in front of the south-east pillar, the wheel ahead in the middle of the yard; watching (as a spectator, whom
				// no keeper hunts) until the reel begins, so nothing starts before the first frame
				player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
				Vec3 bait = Vec3.atBottomCenterOf(built).add(7.0, 0.0, 7.0).add(new Vec3(-1.0, 0.0, -1.0).normalize().scale(BAIT));
				player.teleportTo(server.overworld(), bait.x, bait.y, bait.z, Set.of(), 135.0F, 5.0F, true);
				BodyService.restoreAll(player);
				player.getInventory().setItem(0, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.CLEAVE).get(WeaponMaterial.NETHERITE)));
				player.getInventory().setSelectedSlot(0);
				return built;
			});
			clearFrames();
			world.getServer().runCommand("effect give @a minecraft:resistance infinite 2 true");
			world.getServer().runCommand("effect give @a minecraft:saturation infinite 0 true");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			world.getConnection().waitForChunksRender();
			context.waitTicks(40);
			this.fight(context, world);
		}
	}

	private void fight(final ClientGameTestContext context, final TestSingleplayerContext world) {
		TestInput input = context.getInput();
		List<Frame> frames = new ArrayList<>();
		int rollAt = -100;
		int attackAt = -100;
		int heavyUntil = -1;
		boolean heavyThisStick = false;
		int down = -1;
		for (int tick = 0; tick < LENGTH && (down < 0 || tick < down + 60); tick++) {
			int t = tick;
			if (t == ENTER) {
				// the hunter steps into the fight: the mist closes the gate
				world.getServer().runOnServer(server -> player(server).setGameMode(net.minecraft.world.level.GameType.SURVIVAL));
			}
			if (t == BREAK) {
				world.getServer().runOnServer(server -> wheel(server).setHealth(RustWheel.MAX_HEALTH * 0.49F));
			}
			if (t == FALL) {
				world.getServer().runOnServer(server -> wheel(server).setHealth(3.0F));
			}
			if (t == FALL + 60) {
				// no blow came in time: it goes down anyway
				world.getServer().runOnServer(server -> {
					RustWheel wheel = wheel(server);
					if (wheel != null) {
						wheel.hurtServer(server.overworld(), player(server).damageSources().playerAttack(player(server)), 50.0F);
					}
				});
			}
			if (t % 200 == 0) {
				// the hunter is made to last the minute: their wounds closed now and then
				world.getServer().runOnServer(server -> {
					ServerPlayer player = player(server);
					BodyService.restoreAll(player);
					life.shizune.mistinn.body.WoundService.clearAll(player);
					player.setHealth(player.getMaxHealth());
				});
			}
			boolean swung = t == attackAt || t == heavyUntil;
			Frame frame = world.getServer().computeOnServer(server -> this.frame(server, t, swung));
			frames.add(frame);
			if ("DOWN".equals(frame.kind())) {
				down = down < 0 ? t : down;
				release(input);
				context.waitTick();
				shoot(context, t);
				continue;
			}
			Vec3[] where = world.getServer().computeOnServer(server -> {
				RustWheel wheel = wheel(server);
				Vec3 facing = Vec3.directionFromRotation(0.0F, wheel.getYRot());
				return new Vec3[] {player(server).position(), player(server).getEyePosition(), wheel.position(), facing};
			});
			Vec3 hunter = where[0];
			Vec3 wheelAt = where[2];
			Vec3 toWheel = wheelAt.add(0.0, 1.4, 0.0).subtract(where[1]);
			float yaw = (float) Math.toDegrees(Math.atan2(-toWheel.x, toWheel.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(toWheel.y, toWheel.horizontalDistance()));
			input.lookAt(yaw, Mth.clamp(pitch, -30.0F, 40.0F));
			BossMove move = frame.move().isEmpty() ? null : WheelMoves.SET.get(frame.move());
			boolean rolling = t - rollAt <= 8;
			boolean charging = t < heavyUntil;
			Vec3 goal;
			if (t < ENTER) {
				goal = hunter;
			} else if (frame.stuck() || frame.staggered()) {
				// round behind it, where the man is
				goal = wheelAt.subtract(where[3].scale(BEHIND));
				if (!frame.stuck()) {
					heavyThisStick = false;
				}
			} else {
				heavyThisStick = false;
				goal = this.bait(hunter);
			}
			Vec3 way = goal.subtract(hunter).multiply(1.0, 0.0, 1.0);
			if (rolling || charging && !frame.stuck()) {
				steer(input, yaw, Vec3.ZERO);
			} else {
				steer(input, yaw, way.length() > 0.7 ? way : Vec3.ZERO);
			}
			boolean ram = move != null && ("ram".equals(move.id()) || "fake_ram".equals(move.id()) || "circuit".equals(move.id()));
			boolean tell = move != null && (move.kind() == BossMove.Kind.ATTACK || move.kind() == BossMove.Kind.FEINT || !move.strikes().isEmpty());
			// a run is dodged the moment it is aimed (it keeps its line, into the pillar behind); anything else a little early
			int dodgeAt = ram ? WheelMoves.RAM_SPIN + 2 : move == null ? -1 : Math.max(1, move.windup() - 8);
			if (t >= ENTER && tell && !frame.stuck() && frame.moveTicks() == dodgeAt && t - rollAt > 20 && !charging) {
				input.holdKeyFor(InputConstants.KEY_A, 6);
				rollAt = t;
			}
			if (t == rollAt + 1) {
				input.pressKey(InputConstants.KEY_LALT);
			}
			boolean atBack = (frame.stuck() || frame.staggered()) && way.length() <= 1.0;
			// into its recovery, as soon as a move is spent and it is in reach
			boolean recovering = move != null && move.kind() == BossMove.Kind.ATTACK && frame.moveTicks() > move.windup() + move.active() + 1;
			if (frame.stuck() && !heavyThisStick && !charging && !rolling && frame.distance() < 7.0) {
				// it has stuck: a heavy blow, charged on the way round to its back, and let go full; stuck, it is thrown off balance at once
				input.holdMouseFor(InputConstants.MOUSE_BUTTON_RIGHT, 28);
				heavyUntil = t + 28;
				heavyThisStick = true;
			} else if ((atBack || recovering || t >= FALL) && frame.distance() < 3.6 && !charging && !rolling && t - attackAt > 12) {
				input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
				attackAt = t;
			}
			context.waitTick();
			shoot(context, t);
		}
		release(input);
		write(frames);
	}

	/** The frames of an earlier take go first: a shorter take would leave the old ones behind it. */
	private static void clearFrames() {
		try {
			Files.createDirectories(OUT);
			try (var old = Files.list(OUT)) {
				for (Path file : old.toList()) {
					Files.delete(file);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/** In front of the pillar nearest the hunter, toward the middle: a run aimed at the hunter goes on into the pillar. */
	private Vec3 bait(final Vec3 hunter) {
		Vec3 centre = Vec3.atBottomCenterOf(this.middle);
		Vec3 best = centre;
		double nearest = Double.MAX_VALUE;
		for (int[] pillar : SaltWorks.PILLARS) {
			Vec3 at = centre.add(pillar[0], 0.0, pillar[1]);
			Vec3 spot = at.add(centre.subtract(at).normalize().scale(BAIT));
			double d = spot.distanceToSqr(hunter);
			if (d < nearest) {
				nearest = d;
				best = spot;
			}
		}
		return best;
	}

	/** Holds the movement keys that carry the hunter along {@code way} while they look along {@code yaw}. */
	private static void steer(final TestInput input, final float yaw, final Vec3 way) {
		Vec3 forward = Vec3.directionFromRotation(0.0F, yaw);
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		double f = way.lengthSqr() < 1.0E-4 ? 0.0 : way.normalize().dot(forward);
		double r = way.lengthSqr() < 1.0E-4 ? 0.0 : way.normalize().dot(right);
		hold(input, InputConstants.KEY_W, f > 0.35);
		hold(input, InputConstants.KEY_S, f < -0.35);
		hold(input, InputConstants.KEY_D, r > 0.35);
		hold(input, InputConstants.KEY_A, r < -0.35);
	}

	private static void release(final TestInput input) {
		for (int key : new int[] {InputConstants.KEY_W, InputConstants.KEY_S, InputConstants.KEY_A, InputConstants.KEY_D}) {
			input.releaseKey(key);
		}
	}

	private static void hold(final TestInput input, final int key, final boolean down) {
		if (down) {
			input.holdKey(key);
		} else {
			input.releaseKey(key);
		}
	}

	private static void shoot(final ClientGameTestContext context, final int t) {
		context.takeScreenshot(TestScreenshotOptions.of(String.format("f_%05d", t)).withSize(1280, 720).disableCounterPrefix().withDestinationDir(OUT));
	}

	private Frame frame(final MinecraftServer server, final int tick, final boolean swing) {
		RustWheel wheel = wheel(server);
		ServerPlayer player = player(server);
		if (wheel == null || !wheel.isAlive()) {
			return new Frame(tick, "", "DOWN", 0, false, true, false, 0, 99.0, "", false);
		}
		String hit = "";
		if (this.lastHealth >= 0.0F && wheel.getHealth() < this.lastHealth - 0.01F) {
			hit = RustWheel.behind(wheel, player.position()) ? "back" : RustWheel.front(wheel, player.position()) ? "front" : "side";
		}
		this.lastHealth = wheel.getHealth();
		BossMove move = wheel.currentMove();
		return new Frame(tick, move == null ? "" : move.id(), move == null ? "" : move.kind().name(), wheel.moveTicks(), wheel.stuck(), wheel.broken(),
			wheel.staggered(), wheel.momentum(), wheel.position().subtract(player.position()).horizontalDistance(), hit, swing);
	}

	private RustWheel wheel(final MinecraftServer server) {
		var found = server.overworld().getEntitiesOfClass(RustWheel.class, new AABB(this.middle).inflate(40.0));
		return found.isEmpty() ? null : found.getFirst();
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void write(final List<Frame> frames) {
		StringBuilder out = new StringBuilder("tick,move,kind,move_ticks,stuck,broken,staggered,momentum,distance,hit,swing\n");
		for (Frame f : frames) {
			out.append(f.tick()).append(',').append(f.move()).append(',').append(f.kind()).append(',').append(f.moveTicks()).append(',')
				.append(f.stuck()).append(',').append(f.broken()).append(',').append(f.staggered()).append(',').append(f.momentum()).append(',')
				.append(String.format("%.2f", f.distance())).append(',').append(f.hit()).append(',').append(f.swing()).append('\n');
		}
		try {
			Files.createDirectories(OUT);
			Files.writeString(OUT.resolve("frames.csv"), out.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
