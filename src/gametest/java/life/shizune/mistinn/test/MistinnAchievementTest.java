package life.shizune.mistinn.test;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.achievement.Achievements;
import life.shizune.mistinn.bell.BellService;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.client.achievement.ClientAchievements;
import life.shizune.mistinn.client.death.DeathScene;
import life.shizune.mistinn.client.death.MissionEndScreen;
import life.shizune.mistinn.client.guide.HunterGuideScreen;
import life.shizune.mistinn.enemy.WindupService;
import life.shizune.mistinn.grid.Area;
import life.shizune.mistinn.grid.GridInventory;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.grid.Placed;
import life.shizune.mistinn.keeper.Snuffer;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.residue.ResidueService;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.skill.SkillTree;
import life.shizune.mistinn.skill.Skills;
import life.shizune.mistinn.skill.SlideService;
import life.shizune.mistinn.stamina.RollService;
import life.shizune.mistinn.stamina.StaminaService;
import life.shizune.mistinn.medical.SurgeryKitItem;
import life.shizune.mistinn.weapon.HeavyService;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** 成就: each earned the way it is earned in play, paid once, kept through death, shown on screen and in the guide. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnAchievementTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty easy");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			context.waitTicks(10);
			BlockPos arena = world.getServer().computeOnServer(MistinnAchievementTest::arena);
			world.getServer().runOnServer(server -> {
				AchievementService.clear(player(server));
				SaltService.set(player(server), 0);
			});
			oldRecordMovesOver(world);
			huntsAndWeapons(context, world, arena);
			toastSlidesIn(context);
			skillsAndStats(context, world, arena);
			heavyBlows(context, world, arena);
			rollAndSlide(context, world, arena);
			keeperSurgeryAndBell(context, world, arena);
			deathKeepsThemAndResidue(context, world);
			guidePage(context, world);
		}
	}

	/** A record kept the old way still reads, and is moved into the advancements once, without paying its salt again. */
	private static void oldRecordMovesOver(final TestSingleplayerContext world) {
		Achievements some = new Achievements(new LinkedHashSet<>(List.of("first_kill", "kill:blade", "snuffer")));
		check(Achievements.CODEC.parse(JsonOps.INSTANCE, Achievements.CODEC.encodeStart(JsonOps.INSTANCE, some).getOrThrow()).getOrThrow().equals(some), "an old record still reads");
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(AchievementService.has(player, AchievementService.ROOT.getPath().substring("hunt/".length())), "every hunter has the tab's root from the start");
			// the tabs: four in a world that is not the tillers' way; that one only for a hunter who has walked it
			for (net.minecraft.resources.Identifier root : AchievementService.ROOTS) {
				boolean want = !root.equals(AchievementService.PATH_TAB);
				check(AchievementService.has(player, root.getPath().substring("hunt/".length())) == want, root + (want ? " is a tab from the start" : " is not a tab here"));
			}
			player.setAttached(MistinnAttachments.ACHIEVEMENTS, some);
			AchievementService.joined(player);
			check(AchievementService.has(player, Achievement.FIRST_KILL) && AchievementService.has(player, AchievementService.SNUFFER) && AchievementService.has(player, "kill:blade"),
				"an old record is moved into the advancements");
			check(!AchievementService.has(player, "kill:scythe") && !AchievementService.has(player, Achievement.SIX_BRANCHES), "and nothing else with it");
			check(SaltService.get(player) == 0, "without paying its salt a second time: " + SaltService.get(player));
			check(player.getAttached(MistinnAttachments.ACHIEVEMENTS) == null, "and the old record is gone");
			AchievementService.clear(player);
			check(AchievementService.earned(player) == 0 && !AchievementService.has(player, "kill:blade"), "clearing forgets every one");
			check(AchievementService.has(player, AchievementService.SHORE_TAB.getPath().substring("hunt/".length())), "but the tabs stay");
			AchievementService.grant(player, Achievement.SEA_SEEN);
			AchievementService.joined(player);
			check(AchievementService.has(player, AchievementService.PATH_TAB.getPath().substring("hunt/".length())), "a hunter who has walked the way keeps its tab anywhere");
			AchievementService.clear(player);
		});
	}

	// ── The arena ─────────────────────────────────────────────────

	private static BlockPos arena(final MinecraftServer server) {
		ServerPlayer player = player(server);
		BlockPos pos = player.blockPosition().above(30).immutable();
		for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-8, -1, -8), pos.offset(8, -1, 16))) {
			player.level().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		}
		for (BlockPos air : BlockPos.betweenClosed(pos.offset(-8, 0, -8), pos.offset(8, 4, 16))) {
			player.level().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
		}
		return pos;
	}

	private static void stand(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena, final Item weapon) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			for (var entity : server.overworld().getEntities((net.minecraft.world.entity.Entity) null, new AABB(arena).inflate(20.0), e -> !(e instanceof ServerPlayer))) {
				entity.discard();
			}
			HeavyService.forgetAll();
			player.teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			player.getInventory().setSelectedSlot(0);
			player.setItemInHand(InteractionHand.MAIN_HAND, weapon == null ? ItemStack.EMPTY : new ItemStack(weapon));
			BodyService.restoreAll(player);
			player.setAttached(MistinnAttachments.STAMINA, StaminaService.max(player));
		});
		context.waitTicks(4);
	}

	private static int husk(final TestSingleplayerContext world, final BlockPos arena, final double dx, final double dz, final float health) {
		return world.getServer().computeOnServer(server -> {
			Husk mob = EntityTypes.HUSK.create(server.overworld(), EntitySpawnReason.COMMAND);
			mob.setNoAi(true);
			mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
			mob.setHealth(health);
			mob.snapTo(arena.getX() + 0.5 + dx, arena.getY(), arena.getZ() + 0.5 + dz, 180.0F, 0.0F);
			server.overworld().addFreshEntity(mob);
			return mob.getId();
		});
	}

	private static boolean has(final TestSingleplayerContext world, final Achievement achievement) {
		return world.getServer().computeOnServer(server -> AchievementService.has(player(server), achievement));
	}

	private static Item iron(final WeaponStyle style) {
		return MistinnItems.WEAPONS.get(style).get(WeaponMaterial.IRON);
	}

	// ── Earning them ──────────────────────────────────────────────

	/** A monster brought down: the first hunt; one with every branch: the armory. Each pays once. */
	private static void huntsAndWeapons(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		check(!has(world, Achievement.FIRST_KILL), "nothing earned yet");
		WeaponStyle[] styles = WeaponStyle.values();
		for (int i = 0; i < styles.length; i++) {
			stand(context, world, arena, iron(styles[i]));
			int id = husk(world, arena, 0.0, 2.0, 20.0F);
			int before = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
			world.getServer().runOnServer(server -> {
				LivingEntity mob = (LivingEntity) server.overworld().getEntity(id);
				mob.hurtServer(server.overworld(), player(server).damageSources().playerAttack(player(server)), 1000.0F);
			});
			context.waitTicks(2);
			int gained = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - before;
			if (i == 0) {
				check(has(world, Achievement.FIRST_KILL), "the first monster brought down is the first hunt");
				check(gained == 20 + Achievement.FIRST_KILL.reward, "it pays the husk's 20 salt and the achievement's 50: " + gained);
				check(!has(world, Achievement.SIX_BRANCHES), "one branch is not the armory");
			} else if (i < styles.length - 1) {
				check(gained == 20, "later kills pay only the husk's salt: " + gained);
			} else {
				check(has(world, Achievement.SIX_BRANCHES), "a monster down with each of the six branches is the armory");
				check(gained == 20 + Achievement.SIX_BRANCHES.reward, "which pays 600: " + gained);
			}
		}
	}

	/** The game's own toast shows at the top right when an achievement is earned, and the client knows it has it. */
	private static void toastSlidesIn(final ClientGameTestContext context) {
		context.waitFor(mc -> ClientAchievements.has(Achievement.SIX_BRANCHES.id), 100);
		check(context.computeOnClient(mc -> ClientAchievements.has("kill:scythe") && ClientAchievements.has(Achievement.FIRST_KILL.id)), "the client knows what was earned, mark by mark");
		check(context.computeOnClient(mc -> mc.gui.toastManager().getToast(net.minecraft.client.gui.components.toasts.AdvancementToast.class,
			net.minecraft.client.gui.components.toasts.Toast.NO_TOKEN)) != null, "the game's toast is up after an achievement");
		context.waitTicks(10);
		context.takeScreenshot(TestScreenshotOptions.of("toast").disableCounterPrefix().withDestinationDir(Path.of("achievements")));
		context.runOnClient(mc -> mc.gui.toastManager().clear());
	}

	private static void skillsAndStats(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, null);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			SkillService.set(player, Skills.EMPTY);
			GridService.set(player, GridInventory.EMPTY.with(Area.SAFE, List.of(new Placed(0, 0, 0, false, new ItemStack(MistinnItems.RUST_GOD_SALT)))));
			SaltService.set(player, 4999);
			check(!AchievementService.has(player, Achievement.RICH.id), "4999 salt is not yet a salt merchant");
			SaltService.add(player, 1);
			check(AchievementService.has(player, Achievement.RICH.id), "carrying 5000 is");
			SaltService.add(player, 20000);
			check(SkillService.unlock(player, SkillTree.ROOT) == SkillService.Result.UNLOCKED, "the slide is learned");
			check(AchievementService.has(player, Achievement.FIRST_SKILL.id), "the first skill");
			for (int i = 1; i <= 4; i++) {
				SkillService.unlock(player, "strength_" + i);
			}
			check(!AchievementService.has(player, Achievement.STAT_FIVE.id), "four strength is not mastery");
			SkillService.unlock(player, "strength_5");
			check(AchievementService.has(player, Achievement.STAT_FIVE.id), "five is");
			SkillService.unlock(player, "speed_1");
			SkillService.unlock(player, "constitution_1");
			check(!AchievementService.has(player, Achievement.ALL_STATS.id), "three stats is not all round");
			SkillService.unlock(player, "intellect_1");
			check(AchievementService.has(player, Achievement.ALL_STATS.id), "all four is");
		});
	}

	private static void heavyBlows(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, iron(WeaponStyle.SLASH));
		husk(world, arena, -1.0, 2.0, 200.0F);
		husk(world, arena, 1.0, 2.0, 200.0F);
		context.waitTicks(2);
		world.getServer().runOnServer(server -> HeavyService.release(player(server), player(server).getMainHandItem(), 40));
		context.waitTicks(10);
		check(!has(world, Achievement.HEAVY_THREE), "two at one blow is not three");
		int third = husk(world, arena, 0.0, 2.6, 200.0F);
		world.getServer().runOnServer(server -> {
			player(server).setAttached(MistinnAttachments.STAMINA, StaminaService.max(player(server)));
			LivingEntity mob = (LivingEntity) server.overworld().getEntity(third);
			mob.setAttached(WindupService.WINDUP, new WindupService.Windup(server.overworld().getGameTime(), 200, player(server).getId(), false));
			HeavyService.release(player(server), player(server).getMainHandItem(), 40);
		});
		context.waitTicks(10);
		check(has(world, Achievement.HEAVY_THREE), "three at one blow");
		check(has(world, Achievement.BREAK_WINDUP), "and the full charge broke off the third's attack");
		check(!has(world, Achievement.SPECIAL_WEAPON), "an iron blade is no special weapon");
		stand(context, world, arena, MistinnItems.LANTERN_STAFF);
		world.getServer().runOnServer(server -> SkillService.set(player(server), new Skills(Set.of(SkillTree.ROOT, "intellect_1", "intellect_2", "intellect_3", "intellect_4"))));
		husk(world, arena, 0.0, 3.0, 200.0F);
		context.waitTicks(2);
		world.getServer().runOnServer(server -> HeavyService.release(player(server), player(server).getMainHandItem(), 40));
		context.waitTicks(10);
		check(has(world, Achievement.SPECIAL_WEAPON), "the lantern's own burst, with intellect enough, is the weapon answering");
	}

	private static void rollAndSlide(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, null);
		int attacker = husk(world, arena, 0.0, 1.5, 200.0F);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			LivingEntity mob = (LivingEntity) server.overworld().getEntity(attacker);
			player.hurtServer(server.overworld(), server.overworld().damageSources().mobAttack(mob), 4.0F);
			check(!AchievementService.has(player, Achievement.PERFECT_ROLL.id), "a hit taken standing is no close shave");
			RollService.request(player, 90.0F);
			player.hurtServer(server.overworld(), server.overworld().damageSources().mobAttack(mob), 4.0F);
			check(AchievementService.has(player, Achievement.PERFECT_ROLL.id), "a blow rolled through is");
		});
		context.waitTicks(30);
		stand(context, world, arena, null);
		husk(world, arena, 0.0, 1.0, 200.0F);
		context.waitTicks(2);
		world.getServer().runOnServer(server -> check(SlideService.request(player(server), 0.0F), "the slide starts"));
		context.waitTicks(12);
		check(has(world, Achievement.SLIDE_KICK), "and sweeping a monster off its feet is swept away");
	}

	private static void keeperSurgeryAndBell(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		stand(context, world, arena, null);
		int before = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
		world.getServer().runOnServer(server -> {
			Snuffer keeper = MistinnEntities.SNUFFER.create(server.overworld(), EntitySpawnReason.COMMAND);
			keeper.setNoAi(true);
			keeper.snapTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 8.5, 180.0F, 0.0F);
			server.overworld().addFreshEntity(keeper);
			keeper.kill(server.overworld());
		});
		context.waitTicks(5);
		check(has(world, Achievement.SNUFFER), "the Snuffer falling near is remembered");
		int gained = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - before;
		check(gained == Achievement.SNUFFER.reward, "and pays 1000 (it was not the hunter's own blow): " + gained);
		int beforeBell = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
		world.getServer().runOnServer(server -> {
			life.shizune.mistinn.keeper.SunkenBell bell = MistinnEntities.SUNKEN_BELL.create(server.overworld(), EntitySpawnReason.COMMAND);
			bell.setNoAi(true);
			bell.snapTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 8.5, 180.0F, 0.0F);
			server.overworld().addFreshEntity(bell);
			bell.kill(server.overworld());
		});
		context.waitTicks(5);
		check(has(world, Achievement.SUNKEN_BELL), "so is the Sunken Bell falling near");
		int gainedBell = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - beforeBell;
		check(gainedBell == Achievement.SUNKEN_BELL.reward, "and pays 1200: " + gainedBell);
		int beforeWheel = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
		world.getServer().runOnServer(server -> {
			life.shizune.mistinn.keeper.RustWheel wheel = MistinnEntities.RUST_WHEEL.create(server.overworld(), EntitySpawnReason.COMMAND);
			wheel.setNoAi(true);
			wheel.snapTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 8.5, 180.0F, 0.0F);
			server.overworld().addFreshEntity(wheel);
			wheel.kill(server.overworld());
		});
		context.waitTicks(5);
		check(has(world, Achievement.RUST_WHEEL), "so is the Rust Wheel stopping near");
		int gainedWheel = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - beforeWheel;
		check(gainedWheel == Achievement.RUST_WHEEL.reward && gainedWheel == 1500, "and pays 1500: " + gainedWheel);
		// the story's endings are reached only in a story world (MistinnStoryTest plays both): here they are granted as the story grants them
		int beforeEndings = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
		world.getServer().runOnServer(server -> {
			AchievementService.grant(player(server), AchievementService.ENDING_ANCHOR);
			AchievementService.grant(player(server), AchievementService.ENDING_INN);
		});
		context.waitTicks(5);
		check(has(world, Achievement.ENDING_ANCHOR) && has(world, Achievement.ENDING_INN), "each ending of the story is remembered");
		int gainedEndings = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - beforeEndings;
		check(gainedEndings == Achievement.ENDING_ANCHOR.reward + Achievement.ENDING_INN.reward && gainedEndings == 4000, "and each pays 2000: " + gainedEndings);
		// a pirate ship is taken out at sea (MistinnNavalTest takes one): here it is granted as the fight grants it
		int beforeShip = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
		world.getServer().runOnServer(server -> AchievementService.grant(player(server), AchievementService.PIRATE_SHIP));
		context.waitTicks(5);
		check(has(world, Achievement.PIRATE_SHIP), "taking a pirate ship is remembered");
		int gainedShip = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - beforeShip;
		check(gainedShip == Achievement.PIRATE_SHIP.reward && gainedShip == 1200, "and pays 1200: " + gainedShip);
		// the rest of the sea's: the third ship, a landing fought off, a gunshot (MistinnNavalTest and MistinnRaidTest earn them)
		int beforeSea = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
		world.getServer().runOnServer(server -> {
			AchievementService.grant(player(server), AchievementService.FLEET_BREAKER);
			AchievementService.grant(player(server), AchievementService.SHORE_HELD);
			AchievementService.grant(player(server), AchievementService.SHARPSHOOTER);
		});
		context.waitTicks(5);
		check(has(world, Achievement.FLEET_BREAKER) && has(world, Achievement.SHORE_HELD) && has(world, Achievement.SHARPSHOOTER), "the sea's other deeds are remembered");
		int gainedSea = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - beforeSea;
		check(gainedSea == 2000 + 400 + 300, "and pay 2700 between them: " + gainedSea);
		// the elites' two (MistinnEliteTest earns them)
		int beforeElite = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
		world.getServer().runOnServer(server -> {
			AchievementService.grant(player(server), AchievementService.ELITE_SLAIN);
			AchievementService.grant(player(server), AchievementService.ELITE_TRIPLE);
		});
		context.waitTicks(5);
		check(has(world, Achievement.ELITE_SLAIN) && has(world, Achievement.ELITE_TRIPLE), "the elites' deeds are remembered");
		int gainedElite = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - beforeElite;
		check(gainedElite == 300 + 900, "and pay 1200 between them: " + gainedElite);
		// coming through a fog tide (MistinnTideTest earns it)
		int beforeTide = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
		world.getServer().runOnServer(server -> AchievementService.grant(player(server), AchievementService.TIDE));
		context.waitTicks(5);
		check(has(world, Achievement.TIDE), "the tide's deed is remembered");
		int gainedTide = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - beforeTide;
		check(gainedTide == 500, "and pays 500: " + gainedTide);
		// a bounty claimed (MistinnBountyTest earns it)
		int beforeBounty = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
		world.getServer().runOnServer(server -> AchievementService.grant(player(server), AchievementService.BOUNTY));
		context.waitTicks(5);
		check(has(world, Achievement.BOUNTY), "the bounty hunter is remembered");
		int gainedBounty = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - beforeBounty;
		check(gainedBounty == 500, "and pays 500: " + gainedBounty);
		// a sky rift sealed (MistinnRiftTest earns it)
		int beforeRift = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
		world.getServer().runOnServer(server -> AchievementService.grant(player(server), AchievementService.RIFT_SEALED));
		context.waitTicks(5);
		check(has(world, Achievement.RIFT_SEALED), "the sewn sky is remembered");
		int gainedRift = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - beforeRift;
		check(gainedRift == 600, "and pays 600: " + gainedRift);
		// a crystal drake shattered (MistinnCrystalDrakeTest earns it)
		int beforeDrake = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
		world.getServer().runOnServer(server -> AchievementService.grant(player(server), AchievementService.DRAKE_SHATTERED));
		context.waitTicks(5);
		check(has(world, Achievement.DRAKE_SHATTERED), "the shattered drake is remembered");
		int gainedDrake = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - beforeDrake;
		check(gainedDrake == 400, "and pays 400: " + gainedDrake);
		// a homestead woken (MistinnTillersTest earns it)
		int beforeHome = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
		world.getServer().runOnServer(server -> AchievementService.grant(player(server), AchievementService.HOMESTEAD));
		context.waitTicks(5);
		check(has(world, Achievement.HOMESTEAD), "the homestead is remembered");
		int gainedHome = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - beforeHome;
		check(gainedHome == 500, "and pays 500: " + gainedHome);
		// a crown elder brought down (MistinnCrystalElderTest earns it)
		int beforeElder = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
		world.getServer().runOnServer(server -> AchievementService.grant(player(server), AchievementService.ELDER_SLAIN));
		context.waitTicks(5);
		check(has(world, Achievement.ELDER_SLAIN), "the crown is remembered");
		int gainedElder = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - beforeElder;
		check(gainedElder == 1000, "and pays 1000: " + gainedElder);
		// the elder stunned against stone, a siege held, an alpha brought down (MistinnCrystalElderTest, MistinnSiegeTest and MistinnRiftHoundTest earn them)
		String[] terra = {AchievementService.ELDER_STUNNED, AchievementService.SIEGE_HELD, AchievementService.ALPHA_SLAIN,
			AchievementService.LIZARD_TAMED, AchievementService.RUIN_OPENED, AchievementService.RIFT_CALLED, AchievementService.SEA_SEEN,
			AchievementService.TWELVE_SLAIN, AchievementService.THIRTEEN_SLAIN, AchievementService.XIRANG, AchievementService.THE_LAND,
			// earned in play by their own tests: the parry, throwables, bestiary, down, supply, gear lines, rift camps, homestead and path tests
			Achievement.PARRY_PERFECT.id, Achievement.SALT_BOMB_THREE.id, Achievement.BESTIARY_FULL.id, Achievement.RESCUE.id, Achievement.SUPPLY_DROP.id,
			Achievement.FULL_SET.id, Achievement.CAMP_BARREL.id, Achievement.HOMESTEAD_GROWN.id, Achievement.TRAVEL.id, Achievement.INHERITED.id};
		int[] pays = {300, 600, 400, 200, 300, 300, 2000, 12, 13, 3000, 1500, 200, 200, 800, 300, 150, 300, 200, 800, 200, 300};
		for (int i = 0; i < terra.length; i++) {
			String id = terra[i];
			int beforeEach = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
			world.getServer().runOnServer(server -> AchievementService.grant(player(server), id));
			context.waitTicks(5);
			check(has(world, Achievement.byId(id)), id + " is remembered");
			int gainedEach = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - beforeEach;
			check(gainedEach == pays[i], "and pays " + pays[i] + ": " + gainedEach);
		}
		// the hidden one (MistinnApostleTest earns it the way the game does)
		int beforeApostle = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
		world.getServer().runOnServer(server -> AchievementService.grant(player(server), AchievementService.APOSTLE));
		context.waitTicks(5);
		check(has(world, Achievement.APOSTLE), "the hidden one is remembered once earned");
		int gainedApostle = world.getServer().computeOnServer(server -> SaltService.get(player(server))) - beforeApostle;
		check(gainedApostle == 5000, "and pays 5000: " + gainedApostle);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.removeAttached(MistinnAttachments.WOUNDS);
			BodyService.set(player, BodyService.get(player).with(BodyPart.LEFT_ARM, 0.0F));
			ItemStack kit = new ItemStack(MistinnItems.SURGERY_KIT);
			player.setItemInHand(InteractionHand.MAIN_HAND, kit);
			((SurgeryKitItem) kit.getItem()).finish(player, kit, InteractionHand.MAIN_HAND);
			check(!BodyService.get(player).destroyed(BodyPart.LEFT_ARM), "the kit brings the arm back");
			check(AchievementService.has(player, Achievement.REVIVE_LIMB.id), "steady hands");
			BlockPos bell = arena.offset(3, 0, 0);
			server.overworld().setBlockAndUpdate(bell, MistinnBlocks.GOLDEN_BELL.defaultBlockState());
			check(BellService.rest(player, bell), "resting at a bell");
			check(AchievementService.has(player, Achievement.BELL.id), "is the bell");
		});
	}

	/** Dying keeps every achievement; taking the residue back from the gravestone is one more. */
	private static void deathKeepsThemAndResidue(final ClientGameTestContext context, final TestSingleplayerContext world) {
		int before = world.getServer().computeOnServer(server -> AchievementService.earned(player(server)));
		world.getServer().runCommand("kill @a");
		context.waitForScreen(DeathScreen.class);
		context.waitFor(mc -> mc.gui.screen() instanceof MissionEndScreen && DeathScene.seconds() > DeathScene.SCREEN + 1.8F, 600);
		double[] at = context.computeOnClient(mc -> {
			double[] gui = ((MissionEndScreen) mc.gui.screen()).choiceCentre(0);
			double scale = mc.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTicks(2);
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitFor(mc -> mc.player != null && mc.player.isAlive() && !(mc.gui.screen() instanceof MissionEndScreen), 200);
		context.waitFor(mc -> mc.player.getAttached(MistinnAttachments.DEATH_MARK) != null, 200);
		context.waitTicks(ResidueService.STONE_DELAY + 20);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(AchievementService.earned(player) == before, "the respawned hunter keeps every achievement: " + AchievementService.earned(player) + " of " + before);
			BlockPos stone = player.getAttached(MistinnAttachments.DEATH_MARK).pos().pos();
			check(ResidueService.open(player, stone), "the gravestone opens");
			check(AchievementService.has(player, Achievement.RESIDUE.id), "and taking the residue back is gathered back");
		});
	}

	private static void guidePage(final ClientGameTestContext context, final TestSingleplayerContext world) {
		int server = world.getServer().computeOnServer(s -> AchievementService.earned(player(s)));
		context.waitTicks(5);
		int client = context.computeOnClient(mc -> AchievementService.earned(mc.player));
		check(client == server && client == Achievement.values().length, "the client knows every one: " + client + " / " + server);
		// half earned, for the picture of the tab
		world.getServer().runOnServer(s -> {
			ServerPlayer player = player(s);
			AchievementService.clear(player);
			for (Achievement each : Achievement.values()) {
				if (each.ordinal() % 2 == 0) {
					AchievementService.grant(player, each);
				}
			}
			AchievementService.grant(player, "kill:blade");
			AchievementService.grant(player, "kill:hammer");
		});
		context.waitTicks(10);
		context.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			mc.gui.setScreen(new HunterGuideScreen());
		});
		context.waitTicks(5);
		HunterGuideScreen screen = context.computeOnClient(mc -> (HunterGuideScreen) mc.gui.screen());
		click(context, context.computeOnClient(mc -> screen.tabCentre(HunterGuideScreen.Tab.ACHIEVEMENTS)));
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.advancements.AdvancementsScreen),
			"the fourth bookmark opens the game's advancements");
		context.waitTicks(5);
		context.takeScreenshot(TestScreenshotOptions.of("advancements-tab").disableCounterPrefix().withDestinationDir(Path.of("achievements")));
		context.runOnClient(mc -> mc.gui.screen().onClose());
		context.waitTicks(3);
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof HunterGuideScreen), "and closing them goes back to the guide");
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	private static void click(final ClientGameTestContext context, final double[] gui) {
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		context.getInput().setCursorPos(gui[0] * scale, gui[1] * scale);
		context.waitTicks(2);
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
