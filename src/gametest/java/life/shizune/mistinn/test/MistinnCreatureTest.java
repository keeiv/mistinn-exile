package life.shizune.mistinn.test;

import java.util.Arrays;
import java.util.List;
import life.shizune.mistinn.client.anim.Clip;
import life.shizune.mistinn.client.enemy.EnemyMotion;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.body.WoundService;
import life.shizune.mistinn.enemy.EnemyRig;
import life.shizune.mistinn.enemy.WindupService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Vanilla creatures: attack windups, hunting stance, twitches and voices. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnCreatureTest implements FabricClientGameTest {
	private static final String TAG = "mistinn.test_creature";

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel")) {
			return;
		}
		rigsCarrySpecWindups();
		context.runOnClient(mc -> {
			everyVoiceEventResolves(mc);
			twitchIsDeterministic();
			creatureClipsLoad();
			executionClipsMatchServerTimeline();
		});
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("difficulty normal");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			BlockPos stage = world.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				BlockPos pos = player.blockPosition().above(9).immutable();
				for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-8, -1, -8), pos.offset(8, -1, 8))) {
					player.level().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
				}
				for (BlockPos air : BlockPos.betweenClosed(pos.offset(-8, 0, -8), pos.offset(8, 6, 8))) {
					player.level().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
				}
				return pos;
			});
			context.waitTicks(10);
			windupDelaysTheBlow(context, world, stage);
			rollDodgesTheRelease(context, world, stage);
			backingOffIsChased(context, world, stage);
			sprintingAwayEscapesRavager(context, world, stage);
			huntingSampleFollowsAggression(context, world, stage);
			creatureSheet(context, world, stage);
			// last: the hunter dies, and the death scene's smoke would cloud later shots
			windupCancelsWhenTargetDies(context, world, stage);
		}
	}

	// ── Voices ────────────────────────────────────────────────

	/**
	 * The windup cries and contact sounds resolve with a subtitle in both languages, the vanilla voices they
	 * replace point at the mod's files, and every voice file opens in the game's own Ogg decoder as 32 kHz mono.
	 */
	private static void everyVoiceEventResolves(final net.minecraft.client.Minecraft mc) {
		java.util.List<net.minecraft.resources.Identifier> added = new java.util.ArrayList<>();
		for (String creature : life.shizune.mistinn.registry.VanillaCreatureSounds.windupCreatures()) {
			added.add(life.shizune.mistinn.Mistinn.id("entity." + creature + ".windup"));
		}
		for (life.shizune.mistinn.enemy.ExecutionMove.Hit hit : life.shizune.mistinn.enemy.ExecutionMove.Hit.values()) {
			added.add(life.shizune.mistinn.registry.VanillaCreatureSounds.execution(hit).location());
		}
		java.util.Map<String, com.google.gson.JsonObject> languages = new java.util.HashMap<>();
		for (String language : new String[] {"zh_tw", "en_us"}) {
			try (java.io.Reader reader = mc.getResourceManager().openAsReader(life.shizune.mistinn.Mistinn.id("lang/" + language + ".json"))) {
				languages.put(language, com.google.gson.JsonParser.parseReader(reader).getAsJsonObject());
			} catch (java.io.IOException e) {
				throw new AssertionError("lang " + language, e);
			}
		}
		for (net.minecraft.resources.Identifier id : added) {
			net.minecraft.client.sounds.WeighedSoundEvents sounds = mc.getSoundManager().getSoundEvent(id);
			check(sounds != null && sounds.getWeight() >= 2, id + " has its takes");
			check(sounds.getSubtitle() != null, id + " has a subtitle");
			String key = ((net.minecraft.network.chat.contents.TranslatableContents) sounds.getSubtitle().getContents()).getKey();
			for (var language : languages.entrySet()) {
				check(language.getValue().has(key) && !language.getValue().get(key).getAsString().isBlank(), key + " is written in " + language.getKey());
			}
		}
		// the vanilla voices taken over: each draws from the mod's own files
		net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(7L);
		for (String event : new String[] {"entity.zombie.ambient", "entity.zombie.hurt", "entity.warden.roar", "entity.wolf_big.growl", "entity.polar_bear.warning",
			"entity.enderman.stare", "entity.spider.ambient", "entity.ghast.scream", "entity.drowned.ambient_water", "entity.elder_guardian.curse"}) {
			net.minecraft.client.sounds.WeighedSoundEvents sounds = mc.getSoundManager().getSoundEvent(net.minecraft.resources.Identifier.withDefaultNamespace(event));
			check(sounds != null, event + " resolves");
			for (int i = 0; i < 6; i++) {
				net.minecraft.resources.Identifier file = sounds.getSound(random).getPath();
				check(file.getNamespace().equals(life.shizune.mistinn.Mistinn.MOD_ID) && mc.getResourceManager().getResource(file).isPresent(),
					event + " plays the mod's " + file);
			}
		}
		var files = mc.getResourceManager().listResources("sounds", path -> path.getNamespace().equals(life.shizune.mistinn.Mistinn.MOD_ID)
			&& path.getPath().endsWith(".ogg") && (path.getPath().startsWith("sounds/entity/vanilla/") || path.getPath().startsWith("sounds/execution/")));
		check(files.size() > 400, "the voice files are packed: " + files.size());
		for (var file : files.entrySet()) {
			try (java.io.InputStream in = file.getValue().open();
				net.minecraft.client.sounds.JOrbisAudioStream stream = new net.minecraft.client.sounds.JOrbisAudioStream(in)) {
				javax.sound.sampled.AudioFormat format = stream.getFormat();
				check(format.getSampleRate() == 32000.0F && format.getChannels() == 1, file.getKey() + " is 32 kHz mono, got " + format);
				int[] samples = {0};
				while (stream.readChunk(value -> samples[0]++)) {
					// counting
				}
				check(samples[0] > 32000 / 10, file.getKey() + " has sound in it");
			} catch (java.io.IOException e) {
				throw new AssertionError(file.getKey() + " opens in the game's decoder", e);
			}
		}
	}

	// ── Stance and twitches ───────────────────────────────────

	private static void twitchIsDeterministic() {
		for (int id = 0; id < 40; id++) {
			for (float time = 0; time < 400; time += 3.5F) {
				check(EnemyMotion.twitch(id, time, false).equals(EnemyMotion.twitch(id, time, false)), "a twitch is the same for every viewer");
			}
		}
		int starts = 0;
		boolean was = false;
		for (int time = 0; time < 10_000; time++) {
			boolean now = EnemyMotion.twitch(7, time, false).amount() > 0.0F;
			if (now && !was) {
				starts++;
			}
			was = now;
		}
		check(starts >= 10_000 / 120 && starts <= 10_000 / 40, "an idle creature twitches every 2 to 6 seconds, got " + starts);
		int hunting = 0;
		was = false;
		for (int time = 0; time < 10_000; time++) {
			boolean now = EnemyMotion.twitch(7, time, true).amount() > 0.0F;
			if (now && !was) {
				hunting++;
			}
			was = now;
		}
		check(hunting > starts * 1.8, "a hunting creature twitches more often: " + hunting + " vs " + starts);
	}

	private static void creatureClipsLoad() {
		for (String name : EnemyMotion.clipNames()) {
			Clip clip = Clip.get(name);
			check(clip.keyCount() > 2, "the creature clip " + name + " is present and has keys");
		}
	}

	/** The two animations of every finisher keep the server's clock: a key on each contact and on the fatal blow. */
	private static void executionClipsMatchServerTimeline() {
		for (life.shizune.mistinn.enemy.ExecutionMove move : life.shizune.mistinn.enemy.ExecutionMove.values()) {
			if (move.ranged()) {
				continue;
			}
			for (String side : new String[] {"striker", "victim"}) {
				Clip clip = Clip.get("execution/" + move.id() + "/" + side);
				check(clip.number("length", 0.0F) == move.length(), move.id() + "/" + side + " runs " + move.length() + " ticks");
				List<Float> marks = new java.util.ArrayList<>();
				move.beats().forEach(beat -> marks.add(beat.tick() / (float) move.length()));
				marks.add(move.kill() / (float) move.length());
				for (float mark : marks) {
					boolean keyed = false;
					for (int i = 0; i < clip.keyCount(); i++) {
						keyed |= Math.abs(clip.key(i) - mark) < 0.005F;
					}
					check(keyed, move.id() + "/" + side + " has a key at " + mark);
				}
			}
		}
	}

	private static void huntingSampleFollowsAggression(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		int id = world.getServer().computeOnServer(server -> {
			Mob mob = spawn(player(server), stage, EntityTypes.HUSK, 3.0);
			mob.setNoAi(true);
			return mob.getId();
		});
		context.waitTicks(10);
		check(context.computeOnClient(mc -> EnemyMotion.hunting((LivingEntity) mc.level.getEntity(id), 0.0F)) < 0.05F,
			"a creature at rest does not take the hunting stance");
		world.getServer().runOnServer(server -> testMob(server).setAggressive(true));
		context.waitTicks(12);
		check(context.computeOnClient(mc -> EnemyMotion.hunting((LivingEntity) mc.level.getEntity(id), 0.0F)) > 0.9F,
			"an aggressive creature eases into the hunting stance");
		world.getServer().runOnServer(MistinnCreatureTest::clearTestMobs);
	}

	/** Each family frozen at rest, hunting, 70% into its windup, and two ticks after the release. */
	private static void creatureSheet(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		// a roof keeps the undead out of the sun: burning smoke would cover the shots
		world.getServer().runOnServer(server -> {
			for (BlockPos roof : BlockPos.betweenClosed(stage.offset(-8, 12, -8), stage.offset(8, 12, 8))) {
				server.overworld().setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
			}
		});
		for (EntityType<? extends Mob> type : List.<EntityType<? extends Mob>>of(EntityTypes.ZOMBIE, EntityTypes.WITHER_SKELETON,
			EntityTypes.VINDICATOR, EntityTypes.PIGLIN_BRUTE, EntityTypes.ENDERMAN, EntityTypes.WARDEN, EntityTypes.SPIDER,
			EntityTypes.WOLF, EntityTypes.RAVAGER, EntityTypes.HOGLIN, EntityTypes.SILVERFISH, EntityTypes.CREAKING)) {
			String name = EntityType.getKey(type).getPath();
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				Mob mob = spawn(player, stage, type, 0.0);
				mob.setNoAi(true);
				double side = 0.7 + Math.max(mob.getBbWidth(), mob.getBbHeight()) * 0.9;
				mob.snapTo(stage.getX() + 0.5 + side, stage.getY(), stage.getZ() + 0.5 + side * 0.6, 150.0F, 0.0F);
				mob.setYHeadRot(150.0F);
				mob.setYBodyRot(150.0F);
				player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
					mob.position().add(0, mob.getBbHeight() * 0.55, 0));
			});
			context.waitTicks(6);
			context.takeScreenshot("creature-" + name + "-rest");
			world.getServer().runOnServer(server -> testMob(server).setAggressive(true));
			context.waitTicks(12);
			freezeAnd(context, world, () -> context.takeScreenshot("creature-" + name + "-hunt"));
			EnemyRig rig = EnemyRig.of(type);
			if (rig.windupTicks() > 0) {
				int length = rig.windupTicks();
				setWindup(world, Math.round(length * 0.7F), length, false);
				freezeAnd(context, world, () -> context.takeScreenshot("creature-" + name + "-windup"));
				setWindup(world, length + 2, length, true);
				freezeAnd(context, world, () -> context.takeScreenshot("creature-" + name + "-release"));
			}
		}
		world.getServer().runOnServer(MistinnCreatureTest::clearTestMobs);
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	private static void setWindup(final TestSingleplayerContext world, final int elapsed, final int length, final boolean released) {
		world.getServer().runOnServer(server -> {
			Mob mob = testMob(server);
			long now = server.overworld().getGameTime();
			mob.setAttached(WindupService.WINDUP, new WindupService.Windup(now - elapsed, length, player(server).getId(), released));
		});
	}

	private static void freezeAnd(final ClientGameTestContext context, final TestSingleplayerContext world, final Runnable shot) {
		world.getServer().runCommand("tick freeze");
		context.waitTicks(3);
		shot.run();
		world.getServer().runCommand("tick unfreeze");
		context.waitTicks(2);
	}

	private static void rigsCarrySpecWindups() {
		check(EnemyRig.of(EntityTypes.ZOMBIE).windupTicks() == 7, "a zombie winds up for 7 ticks");
		check(EnemyRig.of(EntityTypes.HUSK).windupTicks() == 8, "a husk winds up for 8 ticks");
		check(EnemyRig.of(EntityTypes.RAVAGER).windupTicks() == 10, "a ravager winds up for 10 ticks");
		check(EnemyRig.of(EntityTypes.SILVERFISH).windupTicks() == 3, "a silverfish winds up for 3 ticks");
		check(Math.abs(EnemyRig.of(EntityTypes.WARDEN).lunge() - 0.60F) < 1.0E-4F, "a warden lunges 0.6 blocks");
		for (EntityType<?> type : new EntityType<?>[] {EntityTypes.VEX, EntityTypes.PHANTOM, EntityTypes.BEE,
			EntityTypes.SLIME, EntityTypes.CREEPER, EntityTypes.GHAST}) {
			check(EnemyRig.of(type).windupTicks() == 0, "no windup for " + EntityType.getKey(type));
		}
	}

	// ── Windups ───────────────────────────────────────────────

	private static void windupDelaysTheBlow(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> spawn(player(server), stage, EntityTypes.HUSK, 2.5));
		WindupService.Windup windup = awaitWindup(context, world);
		float before = total(world);
		while (elapsed(world, windup) < windup.length()) {
			check(total(world) == before, "the hunter is untouched while the husk winds up, tick " + elapsed(world, windup));
			context.waitTick();
		}
		check(awaitDrop(context, world, before), "the wound-up blow lands when it is released");
	}

	private static void rollDodgesTheRelease(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> spawn(player(server), stage, EntityTypes.HUSK, 2.5));
		WindupService.Windup windup = awaitWindup(context, world);
		float before = total(world);
		while (elapsed(world, windup) < windup.length() - 3) {
			context.waitTick();
		}
		context.getInput().pressKey(options -> Arrays.stream(options.keyMappings)
			.filter(k -> k.getName().equals("key.mistinn.roll")).findFirst().orElseThrow());
		check(!awaitDrop(context, world, before), "a roll timed into the windup slips the blow");
	}

	private static void backingOffIsChased(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> spawn(player(server), stage, EntityTypes.HUSK, 2.5));
		WindupService.Windup windup = awaitWindup(context, world);
		float before = total(world);
		while (elapsed(world, windup) < windup.length()) {
			world.getServer().runOnServer(server -> backAway(player(server), 0.18));
			context.waitTick();
		}
		check(awaitDrop(context, world, before), "stepping straight back does not outrun the lunge");
	}

	private static void sprintingAwayEscapesRavager(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> spawn(player(server), stage, EntityTypes.RAVAGER, 3.5));
		WindupService.Windup windup = awaitWindup(context, world);
		float before = total(world);
		while (elapsed(world, windup) < windup.length()) {
			world.getServer().runOnServer(server -> backAway(player(server), 0.30));
			context.waitTick();
		}
		check(!awaitDrop(context, world, before), "sprinting away from a slow ravager windup escapes the blow");
	}

	private static void windupCancelsWhenTargetDies(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos stage) {
		world.getServer().runOnServer(server -> spawn(player(server), stage, EntityTypes.HUSK, 2.5));
		awaitWindup(context, world);
		world.getServer().runOnServer(server -> player(server).kill(player(server).level()));
		context.waitTicks(2);
		boolean cleared = world.getServer().computeOnServer(server -> {
			Mob mob = testMob(server);
			return mob == null || !mob.hasAttached(WindupService.WINDUP);
		});
		check(cleared, "a windup ends when its target dies");
		context.waitTicks(60);
		context.runOnClient(mc -> mc.player.respawn());
		context.waitFor(mc -> mc.player != null && mc.player.isAlive(), 200);
		world.getServer().runOnServer(MistinnCreatureTest::clearTestMobs);
	}

	// ── Helpers ───────────────────────────────────────────────

	static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	static Mob spawn(final ServerPlayer player, final BlockPos stage, final EntityType<? extends Mob> type, final double distance) {
		clearTestMobs(player.level().getServer());
		player.teleportTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5);
		player.setDeltaMovement(Vec3.ZERO);
		BodyService.set(player, Body.FULL);
		WoundService.clearAll(player);
		player.setHealth(player.getMaxHealth());
		player.setInvulnerableTime(0);
		Mob mob = type.create(player.level(), EntitySpawnReason.COMMAND);
		mob.snapTo(stage.getX() + 0.5, stage.getY(), stage.getZ() + 0.5 + distance, 180.0F, 0.0F);
		mob.addTag(TAG);
		player.level().addFreshEntity(mob);
		mob.setTarget(player);
		return mob;
	}

	static void clearTestMobs(final MinecraftServer server) {
		server.overworld().getAllEntities().forEach(entity -> {
			if (entity.entityTags().contains(TAG)) {
				entity.discard();
			}
		});
	}

	static Mob testMob(final MinecraftServer server) {
		for (Entity entity : server.overworld().getAllEntities()) {
			if (entity instanceof Mob mob && mob.isAlive() && entity.entityTags().contains(TAG)) {
				return mob;
			}
		}
		return null;
	}

	private static void backAway(final ServerPlayer player, final double step) {
		Mob mob = testMob(player.level().getServer());
		Vec3 away = player.position().subtract(mob.position()).multiply(1, 0, 1).normalize().scale(step);
		player.teleportTo(player.getX() + away.x, player.getY(), player.getZ() + away.z);
	}

	private static WindupService.Windup awaitWindup(final ClientGameTestContext context, final TestSingleplayerContext world) {
		for (int tick = 0; tick < 200; tick++) {
			WindupService.Windup windup = world.getServer().computeOnServer(server -> {
				Mob mob = testMob(server);
				return mob == null ? null : mob.getAttached(WindupService.WINDUP);
			});
			if (windup != null && !windup.released()) {
				return windup;
			}
			context.waitTick();
		}
		throw new AssertionError("the creature never wound up an attack");
	}

	private static long elapsed(final TestSingleplayerContext world, final WindupService.Windup windup) {
		return world.getServer().computeOnServer(server -> server.overworld().getGameTime() - windup.start());
	}

	private static float total(final TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> BodyService.get(player(server)).total());
	}

	/** Waits a few ticks past the release; true when the hunter lost body health. */
	private static boolean awaitDrop(final ClientGameTestContext context, final TestSingleplayerContext world, final float before) {
		for (int tick = 0; tick < 5; tick++) {
			context.waitTick();
			if (total(world) < before) {
				return true;
			}
		}
		return false;
	}

	static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
