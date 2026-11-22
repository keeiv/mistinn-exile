package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.boss.BossMove;
import life.shizune.mistinn.boss.Planner;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.keeper.SunkenBellModel;
import life.shizune.mistinn.client.mist.ShoreMistClient;
import life.shizune.mistinn.keeper.BellMoves;
import life.shizune.mistinn.keeper.SunkenBell;
import life.shizune.mistinn.registry.MistinnEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 沉鐘, the second keeper: every move has its own clip and tell; a toll rolls out along the floor and takes the head of a
 * hunter standing on it, but not one in the air; each toll thickens the mist a layer, the mist thins again with time,
 * and a full heavy blow rings the bell back; at the fourth layer the deep toll; below half the bell cracks.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnSunkenBellTest implements FabricClientGameTest {
	private static final String TAG = "mistinn.test_bell";

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		context.runOnClient(mc -> everyMoveHasItsClipAndTell());
		feintsAndSignalsKeepFaith();
		ringRollsAlongTheFloor();
		phasesKeepTheirMoves();
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("time set noon");
			BlockPos stage = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				BlockPos pos = player.blockPosition().above(12).immutable();
				for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-14, -1, -14), pos.offset(14, -1, 14))) {
					player.level().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
				}
				for (BlockPos air : BlockPos.betweenClosed(pos.offset(-14, 0, -14), pos.offset(14, 8, 14))) {
					player.level().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
				}
				player.setGameMode(GameType.SURVIVAL);
				return pos;
			});
			context.waitTicks(10);
			drawnOnItsOwnFrame(context);
			clapperStrikesTheCrown(context);
			photographed(context, world, stage);
			tollTakesTheHead(context, world, stage);
			tollThickensTheMist(context, world, stage);
			mistThinsWithTime(context, world, stage);
			heavyBlowsRingItBack(context, world, stage);
			deepTollAtTheFourthLayer(context, world, stage);
			cracksAtHalf(context, world, stage);
			dropShutsAHunterIn(context, world, stage);
			leavesItsClapper(context, world, stage);
			tollsAloneAtNight(context, world, stage);
			fightsAStandingHunter(context, world, stage);
		}
	}

	// ── The move set ──────────────────────────────────────────────

	private static void everyMoveHasItsClipAndTell() {
		for (BossMove move : BellMoves.SET.moves()) {
			check(Clip.find("bell/" + move.id()) != null, move.id() + " has its clip");
			if (move.kind() == BossMove.Kind.REACTION) {
				continue;
			}
			check(move.tell() != null, move.id() + " has a sound for its tell");
			check(move.windup() >= move.weight().minWindup, move.id() + " winds up for at least " + move.weight().minWindup + " ticks, has " + move.windup());
		}
		for (String idle : SunkenBellModel.IDLES) {
			check(Clip.find("bell/" + idle) != null, idle + " has its clip");
		}
	}

	private static void feintsAndSignalsKeepFaith() {
		int feints = 0;
		for (BossMove move : BellMoves.SET.moves()) {
			if (move.kind() == BossMove.Kind.FEINT) {
				feints++;
				BossMove real = BellMoves.SET.get(move.imitates());
				check(move.tell() == real.tell() && move.windup() == real.windup(), move.id() + " shows " + real.id() + "'s tell, sound and length");
				check(BellMoves.SET.links().stream().anyMatch(link -> link.from().equals(move.id())), move.id() + " turns into something");
			}
			if (move.kind() == BossMove.Kind.SIGNAL) {
				check(BellMoves.SET.moves().stream().anyMatch(m -> m.kind() == BossMove.Kind.ATTACK && m.in(move.signals())),
					move.id() + " promises " + move.signals() + ", and there is such a blow");
			}
		}
		check(feints >= 1, "the bell has its feint");
	}

	/** The ring takes whoever stands on the floor where it passes; not one in the air, nor one it has already passed. */
	private static void ringRollsAlongTheFloor() {
		Vec3 bell = Vec3.ZERO;
		check(BellMoves.ringHits(bell, 6.0, new Vec3(6.0, 0.0, 0.0), 0.3, true), "a hunter on the floor six blocks out is taken as the ring reaches six");
		check(!BellMoves.ringHits(bell, 6.0, new Vec3(6.0, 1.2, 0.0), 0.3, false), "one in the air above it is not");
		check(!BellMoves.ringHits(bell, 6.0, new Vec3(3.0, 0.0, 0.0), 0.3, true), "nor one the ring has already passed");
		check(!BellMoves.ringHits(bell, 6.0, new Vec3(9.0, 0.0, 0.0), 0.3, true), "nor one it has not reached yet");
	}

	/** Whole, it tolls once; cracked, its tolls come double, and the single toll is gone. */
	private static void phasesKeepTheirMoves() {
		check(Planner.allowedIn(BellMoves.SET.get("toll"), SunkenBell.WHOLE) && !Planner.allowedIn(BellMoves.SET.get("toll"), SunkenBell.CRACKED),
			"the single toll belongs to the whole bell");
		check(Planner.allowedIn(BellMoves.SET.get("double_toll"), SunkenBell.CRACKED) && !Planner.allowedIn(BellMoves.SET.get("double_toll"), SunkenBell.WHOLE),
			"the double toll to the cracked one");
		check(Planner.allowedIn(BellMoves.SET.get("clapper_sweep"), SunkenBell.WHOLE) && Planner.allowedIn(BellMoves.SET.get("clapper_sweep"), SunkenBell.CRACKED),
			"the rest to both");
	}

	// ── The body ──────────────────────────────────────────────────

	/** Drawn on its own frame: a bell over its shoulders, taller than a man, nothing sunk into the floor, the clapper out in front. */
	private static void drawnOnItsOwnFrame(final ClientGameTestContext context) {
		String[] lowest = {""};
		float[] shape = context.computeOnClient(mc -> {
			SunkenBell entity = MistinnEntities.SUNKEN_BELL.create(mc.level, EntitySpawnReason.COMMAND);
			entity.setId(-4243);
			check(((LivingEntityRenderer<?, ?, ?>) mc.getEntityRenderDispatcher().getRenderer(entity)).getModel() instanceof SunkenBellModel,
				"it is drawn on its own frame");
			List<ClipProbe.Box> rest = ClipProbe.boxes(mc, entity, Vec3.ZERO, 0.0F, state -> { }, true, null, null);
			float clapper = rest.stream().filter(box -> box.path().endsWith("/clapper")).map(box -> box.center().z).max(Float::compare).orElse(-9.0F);
			float low = Float.MAX_VALUE;
			for (ClipProbe.Box box : rest) {
				float bottom = ClipProbe.reach(List.of(box), -1.0F);
				if (bottom < low) {
					low = bottom;
					lowest[0] = box.path();
				}
			}
			return new float[] {ClipProbe.reach(rest, -1.0F), ClipProbe.reach(rest, 1.0F), clapper};
		});
		check(shape[0] > -0.06F, "nothing of it sinks into the floor: lowest " + shape[0] + " (" + lowest[0] + ")");
		check(shape[1] > 2.6F && shape[1] < 3.2F, "a bell over its shoulders, taller than a man: top " + shape[1]);
		check(shape[2] > 0.3F, "the clapper rests out in front of it: " + shape[2]);
	}

	/** Stills for the eye: standing, the clapper raised to toll, and the stroke. */
	private static void photographed(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			clear(server, stage);
			ServerPlayer player = player(server);
			player.setGameMode(GameType.CREATIVE);
			player.teleportTo(server.overworld(), stage.getX() + 2.5, stage.getY(), stage.getZ() + 6.5, Set.of(), 160.0F, -8.0F, true);
			bell(server, stage);
		});
		context.waitTicks(20);
		context.takeScreenshot("bell-00-standing");
		world.getServer().runOnServer(server -> bell(server, stage).perform("toll"));
		context.waitTicks(17);
		context.takeScreenshot("bell-01-raised");
		context.waitTicks(6);
		context.takeScreenshot("bell-02-stroke");
		world.getServer().runOnServer(server -> {
			clear(server, stage);
			player(server).setGameMode(GameType.SURVIVAL);
		});
		context.waitFor(mc -> ShoreMistClient.lastEnd() < 0.0F, 300);
	}

	/** At the stroke of a toll the clapper's bulb comes down on the crown of the bell; standing, it is nowhere near it. */
	private static void clapperStrikesTheCrown(final ClientGameTestContext context) {
		float[] got = context.computeOnClient(mc -> {
			SunkenBell entity = MistinnEntities.SUNKEN_BELL.create(mc.level, EntitySpawnReason.COMMAND);
			entity.setId(-4244);
			List<ClipProbe.Box> stroke = ClipProbe.boxes(mc, entity, Vec3.ZERO, 0.0F, state -> {
				if (state instanceof life.shizune.mistinn.client.keeper.SunkenBellRenderState bell) {
					bell.move = "toll";
					bell.moveTicks = 20.0F;
					bell.moveLength = BellMoves.TOLL.length();
				}
			}, true, null, null);
			List<ClipProbe.Box> rest = ClipProbe.boxes(mc, entity, Vec3.ZERO, 0.0F, state -> { }, true, null, null);
			return new float[] {gap(stroke), gap(rest)};
		});
		check(got[0] < 0.3F, "at the stroke the clapper's bulb comes down on the crown of the bell: " + got[0] + " blocks off");
		check(got[1] > 1.0F, "standing, it is nowhere near: " + got[1]);
	}

	/** How far the clapper's bulb (the biggest box of the clapper) is from the crown (the highest box of the bell), in blocks. */
	private static float gap(final List<ClipProbe.Box> boxes) {
		ClipProbe.Box bulb = null;
		ClipProbe.Box crown = null;
		for (ClipProbe.Box box : boxes) {
			if (box.path().endsWith("/clapper") && (bulb == null || volume(box) > volume(bulb))) {
				bulb = box;
			}
			if (box.path().startsWith("/head") && (crown == null || box.center().y > crown.center().y)) {
				crown = box;
			}
		}
		check(bulb != null && crown != null, "the clapper and the bell are both there");
		return bulb.center().distance(crown.center());
	}

	private static float volume(final ClipProbe.Box box) {
		return box.half()[0] * box.half()[1] * box.half()[2];
	}

	// ── The toll ──────────────────────────────────────────────────

	private static void tollTakesTheHead(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			clear(server, stage);
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 6.5, Set.of(), 180.0F, 0.0F, true);
			bell(server, stage).perform("toll");
		});
		context.waitTicks(40);
		world.getServer().runOnServer(server -> {
			var body = BodyService.get(player(server));
			check(body.get(BodyPart.HEAD) < BodyPart.MAX, "the ring takes the head of a hunter standing six blocks out: " + body);
			check(body.get(BodyPart.LEFT_LEG) == BodyPart.MAX && body.get(BodyPart.TORSO) == BodyPart.MAX, "and nothing else: " + body);
			clear(server, stage);
		});
	}

	private static void tollThickensTheMist(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			clear(server, stage);
			player(server).teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 4.5, Set.of(), 180.0F, 0.0F, true);
			SunkenBell bell = bell(server, stage);
			check(bell.layers() == 0, "it begins with no mist of its own: " + bell.layers());
			bell.perform("toll");
		});
		context.waitTicks(25);
		world.getServer().runOnServer(server -> check(bell(server, stage).layers() == 1, "one toll, one layer: " + bell(server, stage).layers()));
		// the hunter sees it: the world closes in to the first layer's reach
		context.waitFor(mc -> ShoreMistClient.lastEnd() > 0.0F && ShoreMistClient.lastEnd() < SunkenBell.SIGHT[1] + 0.5F, 200);
		world.getServer().runOnServer(server -> clear(server, stage));
		context.waitFor(mc -> ShoreMistClient.lastEnd() < 0.0F, 300);
	}

	private static void mistThinsWithTime(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			clear(server, stage);
			bell(server, stage).setLayers(2);
		});
		context.waitTicks(SunkenBell.FADE_WHOLE + 10);
		world.getServer().runOnServer(server -> {
			check(bell(server, stage).layers() == 1, "fifteen quiet seconds and a layer thins: " + bell(server, stage).layers());
			clear(server, stage);
		});
	}

	private static void heavyBlowsRingItBack(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			clear(server, stage);
			SunkenBell bell = bell(server, stage);
			bell.setLayers(4);
			ServerLevel level = server.overworld();
			bell.takeHeavyBlow(level);
			check(bell.layers() == 3, "a full heavy blow rings the bell back: a layer thins: " + bell.layers());
			check(!bell.staggered(), "and it keeps its feet");
			bell.setLayers(4);
			bell.takeHeavyBlow(level);
			bell.takeHeavyBlow(level);
			check(bell.staggered(), "the third in a row throws it off balance");
			// the third blow rings it back a layer like the others, and off balance it loses two more
			check(bell.layers() == 0, "and the mist thins by two more: " + bell.layers());
			clear(server, stage);
		});
	}

	private static void deepTollAtTheFourthLayer(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			clear(server, stage);
			player(server).teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 5.5, Set.of(), 180.0F, 0.0F, true);
			bell(server, stage).setLayers(4);
		});
		boolean began = false;
		for (int i = 0; i < 40 && !began; i++) {
			context.waitTicks(1);
			began = world.getServer().computeOnServer(server -> {
				BossMove move = bell(server, stage).currentMove();
				return move != null && move.id().equals("deep_toll");
			});
		}
		check(began, "at the fourth layer it gives the deep toll");
		for (int i = 0; i < 120; i++) {
			context.waitTicks(1);
			if (world.getServer().computeOnServer(server -> bell(server, stage).currentMove() == null)) {
				break;
			}
		}
		world.getServer().runOnServer(server -> {
			check(bell(server, stage).layers() == 1, "after it, the mist falls back to one layer: " + bell(server, stage).layers());
			check(BodyService.get(player(server)).get(BodyPart.HEAD) < BodyPart.MAX, "and a hunter who stood still took it: " + BodyService.get(player(server)));
			clear(server, stage);
		});
	}

	private static void cracksAtHalf(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			clear(server, stage);
			SunkenBell bell = bell(server, stage);
			check(!bell.cracked(), "it begins whole");
			bell.setHealth(bell.getMaxHealth() * 0.49F);
		});
		context.waitTicks(3);
		world.getServer().runOnServer(server -> {
			check(bell(server, stage).cracked(), "below half the bell cracks");
			clear(server, stage);
		});
	}

	/** 罩鐘: it leaps and comes down over a hunter who stood still; they are shut in under it, in the dark, and it tolls. */
	private static void dropShutsAHunterIn(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		check(!Planner.allowedIn(BellMoves.BELL_DROP, SunkenBell.WHOLE) && Planner.allowedIn(BellMoves.BELL_DROP, SunkenBell.CRACKED), "only the cracked bell drops");
		world.getServer().runOnServer(server -> {
			clear(server, stage);
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 4.5, Set.of(), 180.0F, 0.0F, true);
			SunkenBell bell = bell(server, stage);
			bell.setTarget(player);
			bell.perform("bell_drop");
		});
		boolean caught = false;
		for (int i = 0; i < 50 && !caught; i++) {
			context.waitTicks(1);
			caught = world.getServer().computeOnServer(server -> player(server).hasEffect(MobEffects.BLINDNESS));
		}
		check(caught, "a hunter who stood still is caught under the bell, in the dark");
		context.waitTicks(5);
		world.getServer().runOnServer(server -> check(player(server).distanceTo(bell(server, stage)) < 1.0F,
			"and held there under it: " + player(server).distanceTo(bell(server, stage))));
		for (int i = 0; i < 40; i++) {
			context.waitTicks(1);
			if (world.getServer().computeOnServer(server -> BodyService.get(player(server)).get(BodyPart.HEAD) < BodyPart.MAX)) {
				break;
			}
		}
		world.getServer().runOnServer(server -> {
			check(BodyService.get(player(server)).get(BodyPart.HEAD) < BodyPart.MAX, "it tolls with them inside: " + BodyService.get(player(server)));
			player(server).removeAllEffects();
			clear(server, stage);
		});
	}

	/** Brought down, it leaves its salt block and its clapper, for a hunter strong enough to swing it. */
	private static void leavesItsClapper(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			clear(server, stage);
			bell(server, stage).kill(server.overworld());
		});
		context.waitTicks(5);
		world.getServer().runOnServer(server -> {
			var items = server.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(stage).inflate(8.0));
			check(items.stream().anyMatch(item -> item.getItem().is(life.shizune.mistinn.registry.MistinnItems.BELL_CLAPPER)), "it leaves its clapper");
			check(items.stream().anyMatch(item -> item.getItem().is(life.shizune.mistinn.registry.MistinnItems.RUST_GOD_SALT)), "and the Rust God's salt");
			items.forEach(net.minecraft.world.entity.Entity::discard);
		});
	}

	/** Left alone at night, now and then it tolls its bell by itself: far off, harmless, and no mist comes of it. By day it is still. */
	private static void tollsAloneAtNight(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runCommand("time set midnight");
		world.getServer().runOnServer(server -> {
			clear(server, stage);
			player(server).setGameMode(GameType.CREATIVE);
			SunkenBell bell = bell(server, stage);
			check(bell.idleTolls() == 0, "it has not tolled yet");
			bell.nextIdleToll(5);
		});
		context.waitTicks(15);
		world.getServer().runOnServer(server -> {
			SunkenBell bell = bell(server, stage);
			check(bell.idleTolls() == 1, "alone at night, it tolls by itself: " + bell.idleTolls());
			check(bell.layers() == 0, "and no mist comes of it: " + bell.layers());
			check(BodyService.get(player(server)).total() == life.shizune.mistinn.body.Body.FULL.total(), "nor any harm");
		});
		world.getServer().runCommand("time set noon");
		world.getServer().runOnServer(server -> bell(server, stage).nextIdleToll(5));
		context.waitTicks(15);
		world.getServer().runOnServer(server -> {
			check(bell(server, stage).idleTolls() == 1, "by day it is still: " + bell(server, stage).idleTolls());
			player(server).setGameMode(GameType.SURVIVAL);
			clear(server, stage);
		});
	}

	/**
	 * Its own mind, left to fight: against a hunter who stands six blocks off and never moves, over twenty seconds it
	 * picks several different moves, tolls (the mist rises), and lands blows. The hunter is mended now and then so the
	 * fight goes on.
	 */
	private static void fightsAStandingHunter(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> {
			clear(server, stage);
			ServerPlayer player = player(server);
			player.setGameMode(GameType.SURVIVAL);
			player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 6.5, Set.of(), 180.0F, 0.0F, true);
			bell(server, stage).getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(40.0);
		});
		java.util.Set<String> moves = new java.util.LinkedHashSet<>();
		int[] hits = {0};
		int[] mist = {0};
		for (int i = 0; i < 400; i++) {
			context.waitTicks(1);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				SunkenBell bell = bell(server, stage);
				BossMove move = bell.currentMove();
				if (move != null) {
					moves.add(move.id());
				}
				mist[0] = Math.max(mist[0], bell.layers());
				if (BodyService.get(player).total() < life.shizune.mistinn.body.Body.FULL.total()) {
					hits[0]++;
					BodyService.restoreAll(player);
					life.shizune.mistinn.body.WoundService.clearAll(player);
				}
				// the hunter stands where they are, whatever pulls or throws them (put back only once they have been moved)
				if (player.distanceToSqr(stage.getX() + 0.5, stage.getY(), stage.getZ() + 6.5) > 1.0 && player.onGround()) {
					player.teleportTo(server.overworld(), stage.getX() + 0.5, stage.getY(), stage.getZ() + 6.5, Set.of(), player.getYRot(), player.getXRot(), true);
				}
			});
		}
		System.out.println("[bellfight] moves " + moves + ", blows landed " + hits[0] + ", deepest mist " + mist[0]);
		check(moves.size() >= 4, "it fights with more than one or two moves: " + moves);
		check(moves.stream().anyMatch(id -> BellMoves.SET.get(id).in("toll")) && mist[0] >= 1, "it tolls, and the mist rises: " + moves + ", " + mist[0] + " layers");
		check(hits[0] >= 2, "and its blows land: " + hits[0] + " times, with " + moves);
		world.getServer().runOnServer(server -> clear(server, stage));
	}

	// ── Helpers ───────────────────────────────────────────────────

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** The stage's bell keeper, made if there is none: it faces south (+z) and picks no fights of its own. */
	private static SunkenBell bell(final MinecraftServer server, final BlockPos stage) {
		ServerLevel level = server.overworld();
		var found = level.getEntitiesOfClass(SunkenBell.class, new AABB(stage).inflate(16), keeper -> keeper.entityTags().contains(TAG) && keeper.isAlive());
		if (!found.isEmpty()) {
			return found.getFirst();
		}
		SunkenBell bell = MistinnEntities.SUNKEN_BELL.create(level, EntitySpawnReason.COMMAND);
		bell.snapTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5, 0.0F, 0.0F);
		bell.setYHeadRot(0.0F);
		bell.setYBodyRot(0.0F);
		bell.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(0.0);
		bell.addTag(TAG);
		level.addFreshEntity(bell);
		return bell;
	}

	private static void clear(final MinecraftServer server, final BlockPos stage) {
		server.overworld().getEntitiesOfClass(SunkenBell.class, new AABB(stage).inflate(40), keeper -> keeper.entityTags().contains(TAG))
			.forEach(net.minecraft.world.entity.Entity::discard);
		BodyService.restoreAll(player(server));
		life.shizune.mistinn.body.WoundService.clearAll(player(server));
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
