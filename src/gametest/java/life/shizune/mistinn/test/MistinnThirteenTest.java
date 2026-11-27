package life.shizune.mistinn.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.client.ledger.Ledger;
import life.shizune.mistinn.client.ledger.LedgerClient;
import life.shizune.mistinn.client.path.PathSkins;
import life.shizune.mistinn.client.path.RustApostleRenderer;
import life.shizune.mistinn.client.path.ThirteenthClient;
import life.shizune.mistinn.client.story.StoryCardScreen;
import life.shizune.mistinn.client.story.StoryClient;
import life.shizune.mistinn.ledger.LedgerService;
import life.shizune.mistinn.path.ApostleMove;
import life.shizune.mistinn.path.RustApostle;
import life.shizune.mistinn.path.Thirteenth;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.story.StoryScript;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * 十三人斬. A hunter whose other worlds sent the salt up and never burned it is due the thirteenth. Stitched, they
 * wear the twelve's patched skin in the best of everything and take less of every blow. Hunter No. 3412 comes in
 * the looker's own skin, switches among the station's branches, rolls and raises a guard against the thing's
 * swings, talks to themself about dinner, never goes down, and finishes it. Its death is its face and then dark,
 * with no death screen; then the hunter wakes, the deed is theirs, the ledger keeps it, and the end card follows.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnThirteenTest implements FabricClientGameTest {
	private static final int FIGHT_TICKS = 900;
	/** The most the thirteenth is left to stand, untended, before they are told to finish it. */
	private static final int STAND_TICKS = 2400;

	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		Path file = FabricLoader.getInstance().getGameDir().resolve("mistinn-thirteen-ledger.json");
		boolean[] reloaded = {false};
		context.runOnClient(mc -> {
			try {
				Files.deleteIfExists(file);
			} catch (java.io.IOException e) {
				throw new AssertionError(e);
			}
			LedgerClient.use(file);
			// in another world they sent the salt up, and never once burned it
			Ledger ledger = LedgerClient.ledger();
			ledger.world("00000000-0000-4000-8000-000000000001", "story");
			ledger.event("00000000-0000-4000-8000-000000000001", "ending.anchor", true);
			mc.options.languageCode = "zh_tw";
			mc.getLanguageManager().setSelected("zh_tw");
			mc.reloadResourcePacks().thenRun(() -> reloaded[0] = true);
		});
		context.waitFor(mc -> reloaded[0], 2400);
		check(context.computeOnClient(mc -> LedgerClient.ledger().cruelStory()), "the ledger knows they sent the salt up and never burned it");
		check((context.computeOnClient(mc -> LedgerClient.ledger().tone()) & Thirteenth.CRUEL_STORY) != 0, "and the server may hear it");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set 12800");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("gamerule spawn_mobs false");
			context.waitTicks(10);

			int hunterId = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				LedgerService.setTone(player, Thirteenth.CRUEL_STORY);
				check(Thirteenth.due(server), "the twelfth falling goes on as the thirteenth for them");
				// a quiet stretch of highland to fight on
				BlockPos floor = player.blockPosition().above(14);
				for (BlockPos stone : BlockPos.betweenClosed(floor.offset(-16, -1, -16), floor.offset(16, -1, 24))) {
					level.setBlockAndUpdate(stone, Blocks.MOSS_BLOCK.defaultBlockState());
				}
				for (BlockPos air : BlockPos.betweenClosed(floor.offset(-16, 0, -16), floor.offset(16, 6, 24))) {
					level.setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
				}
				player.teleportTo(level, floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
				RustApostle hunter = Thirteenth.beginNow(player);
				check(Thirteenth.stitched(player), "they walk as the thirteenth");
				check(player.gameMode() == GameType.SURVIVAL, "in survival");
				check(player.getItemBySlot(EquipmentSlot.HEAD).is(MistinnItems.HELMETS.getLast()), "in the best of everything");
				check(player.getInventory().getItem(0).is(MistinnItems.WEAPONS.get(WeaponStyle.CLEAVE).get(WeaponMaterial.NETHERITE)), "with the best of arms");
				check(Thirteenth.taken(player) == Thirteenth.TAKEN, "a blow takes less of them");
				return hunter.getId();
			});

			// the card of the stitching
			context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen card && card.cardId().equals(StoryScript.PATH_STITCHED.id()), 200);
			context.waitTicks(100);
			shot(context, "thirteen-0-stitched");
			context.runOnClient(mc -> mc.gui.setScreen(null));

			check(context.computeOnClient(mc -> {
				var skin = PathSkins.skin(mc.player.getUUID());
				return skin != null && skin.body().texturePath().getPath().endsWith("stitched.png");
			}), "they wear the twelve, stitched");
			check(context.computeOnClient(mc -> !RustApostleRenderer.own().body().texturePath().getPath().endsWith("stitched.png")),
				"the one who comes wears the looker's own skin");

			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			Set<ApostleMove> shotActs = new HashSet<>();
			boolean mutterShot = false;
			float least = 1.0F;
			for (int t = 0; t < FIGHT_TICKS; t += 4) {
				least = Math.min(least, step(context, world, hunterId, true));
				ApostleMove act = act(world, hunterId);
				if (act != null && act != ApostleMove.IAI && shotActs.add(act)) {
					context.waitTicks(Math.min(10, act.length / 3));
					shot(context, "thirteen-1-" + act.name().toLowerCase(java.util.Locale.ROOT));
				}
				if (!mutterShot && context.computeOnClient(mc -> {
					StoryScript.Line line = StoryClient.line();
					return line != null && line.key().contains("apostle_mutter") && StoryClient.lineSeconds(0.0F) > 1.5F;
				})) {
					mutterShot = true;
					shot(context, "thirteen-2-mutter");
				}
			}
			System.out.println("[mistinn thirteen] the least of the thirteenth's strength while kept standing: " + least);
			world.getServer().runOnServer(server -> {
				RustApostle hunter = (RustApostle) server.overworld().getEntity(hunterId);
				check(hunter.struckWith.size() >= 3, "they switch among the branches: " + hunter.struckWith);
				check(hunter.rolled + hunter.guarded >= 2, "they roll and guard against its swings: " + hunter.rolled + " rolls, " + hunter.guarded + " guards");
				check(hunter.muttered >= 2, "they talk to themself: " + hunter.muttered);
			});

			// they do not go down
			for (int i = 0; i < 40; i++) {
				boolean done = world.getServer().computeOnServer(server -> {
					RustApostle hunter = (RustApostle) server.overworld().getEntity(hunterId);
					if (hunter.move() != null && (hunter.move().immune(0) || hunter.move() == ApostleMove.GUARD)) {
						return false;
					}
					hunter.hurtServer(server.overworld(), hunter.damageSources().generic(), 100000.0F);
					return true;
				});
				if (done) {
					break;
				}
				context.waitTicks(1);
			}
			world.getServer().runOnServer(server -> {
				RustApostle hunter = (RustApostle) server.overworld().getEntity(hunterId);
				check(hunter.isAlive() && hunter.getHealth() >= hunter.getMaxHealth() * RustApostle.FLOOR - 0.01F,
					"no blow takes them below their floor: " + hunter.getHealth());
			});

			// left to it, whole again: how long the thirteenth stands, swinging all the while, until they finish it
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				life.shizune.mistinn.body.BodyService.restoreAll(player);
				player.setHealth(player.getMaxHealth());
			});
			int stood = 0;
			ApostleMove now = act(world, hunterId);
			while (stood < STAND_TICKS && now != ApostleMove.IAI) {
				step(context, world, hunterId, false);
				stood += 4;
				now = act(world, hunterId);
				if (!alive(world, hunterId)) {
					throw new AssertionError("failed: it does not fall before the last blow (fell after " + stood + " ticks)");
				}
			}
			System.out.println("[mistinn thirteen] the thirteenth stood " + stood + " ticks before they finished it");
			check(stood >= 400, "it is hard to put down: it stood " + stood + " ticks");
			if (now != ApostleMove.IAI) {
				world.getServer().runOnServer(server -> ((RustApostle) server.overworld().getEntity(hunterId)).finishNow());
				for (int i = 0; i < 20 && act(world, hunterId) != ApostleMove.IAI; i++) {
					context.waitTicks(1);
				}
			}
			check(act(world, hunterId) == ApostleMove.IAI, "enough is enough");
			for (int i = 0; i < 28; i += 4) {
				step(context, world, hunterId, false);
			}
			shot(context, "thirteen-3-finish");

			// its death: its face, then dark, no death screen
			context.waitFor(mc -> mc.gui.screen() instanceof ThirteenthClient.Death, 200);
			context.waitTicks(3);
			check(context.computeOnClient(mc -> ((ThirteenthClient.Death) mc.gui.screen()).showing()), "its face, too close");
			shot(context, "thirteen-4-face");
			// in the dark after it, the twelve, let go
			context.waitTicks(ThirteenthClient.FACE_TICKS + 30);
			check(context.computeOnClient(mc -> mc.gui.screen() instanceof ThirteenthClient.Death death && !death.showing()), "then dark");
			shot(context, "thirteen-5-dark");
			context.waitFor(mc -> mc.player != null && mc.player.isAlive() && !(mc.gui.screen() instanceof ThirteenthClient.Death), 400);

			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				check(!Thirteenth.stitched(player), "the thirteenth is gone");
				check(AchievementService.has(player, Achievement.THIRTEEN_SLAIN), "the deed is theirs");
			});
			for (int i = 0; i < 400 && world.getServer().computeOnServer(server -> !server.overworld().getEntitiesOfClass(RustApostle.class,
				server.getPlayerList().getPlayers().getFirst().getBoundingBox().inflate(160.0)).isEmpty()); i += 10) {
				context.waitTicks(10);
			}
			check(world.getServer().computeOnServer(server -> server.overworld().getEntitiesOfClass(RustApostle.class,
				server.getPlayerList().getPlayers().getFirst().getBoundingBox().inflate(160.0)).isEmpty()), "the hunter has gone home");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitFor(mc -> mc.gui.screen() instanceof StoryCardScreen card && card.cardId().equals(StoryScript.THIRTEEN_END.id()), 600);
			context.waitTicks(120);
			shot(context, "thirteen-6-home");
			check(context.computeOnClient(mc -> LedgerClient.ledger().count("path.thirteen")) >= 1, "the ledger keeps it");
			context.runOnClient(mc -> mc.gui.setScreen(null));
		}
	}

	/**
	 * Four ticks of the fight: the thing swings at the hunter (and strikes when near), and is turned toward them with the
	 * hunter a little off to the side, so the camera behind it sees them. When {@code tend}, it is made whole again
	 * each time. Returns how much of its strength is left.
	 */
	// where the thirteenth stands its ground while the hunter fights it
	private static net.minecraft.world.phys.@org.jspecify.annotations.Nullable Vec3 ground;

	private static float step(final ClientGameTestContext context, final TestSingleplayerContext world, final int hunterId, final boolean tend) {
		context.waitTicks(4);
		float left = world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			RustApostle hunter = (RustApostle) server.overworld().getEntity(hunterId);
			float strength = player.isAlive() ? Thirteenth.strength(player) : 0.0F;
			if (hunter != null && player.isAlive()) {
				player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), true);
				if (player.distanceTo(hunter) < 4.0F) {
					player.attack(hunter);
				}
				// it holds its ground: knocked back (its blows push the one they hit), it steps back to where it stood
				if (ground == null) {
					ground = player.position();
				} else if (player.position().distanceTo(ground) > 2.0) {
					player.teleportTo(server.overworld(), ground.x, ground.y, ground.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
				}
				if (tend) {
					life.shizune.mistinn.body.BodyService.restoreAll(player);
					player.setHealth(player.getMaxHealth());
				}
			}
			return strength;
		});
		context.runOnClient(mc -> {
			if (mc.player != null && mc.level.getEntity(hunterId) instanceof RustApostle hunter) {
				double dx = hunter.getX() - mc.player.getX();
				double dz = hunter.getZ() - mc.player.getZ();
				mc.player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)) - 32.0F);
				mc.player.setXRot(6.0F);
			}
		});
		return left;
	}

	private static @org.jspecify.annotations.Nullable ApostleMove act(final TestSingleplayerContext world, final int hunterId) {
		return world.getServer().computeOnServer(server -> server.overworld().getEntity(hunterId) instanceof RustApostle hunter ? hunter.move() : null);
	}

	private static boolean alive(final TestSingleplayerContext world, final int hunterId) {
		return world.getServer().computeOnServer(server -> server.overworld().getEntity(hunterId) instanceof RustApostle hunter && hunter.isAlive());
	}

	private static void shot(final ClientGameTestContext context, final String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(Path.of("photos", "thirteen")));
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn thirteen] ok: " + what);
	}
}
