package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.bestiary.Bestiary;
import life.shizune.mistinn.bestiary.BestiaryService;
import life.shizune.mistinn.client.guide.GuideBestiaryPage;
import life.shizune.mistinn.client.guide.HunterGuideScreen;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;

/**
 * 獵物圖鑑: what a hunter knows of what they hunt. A creature is met when it strikes them, when
 * they strike it, or when it winds up a blow at them: its name, its picture and what it is. One
 * brought down tells its weakness; five, its tell. A keeper or a great foe brought down once
 * tells everything. Deeds already done in an old world fill it in. In the guide the bestiary is a
 * bookmark of its own, every creature drawn and every word fitting its page in both languages.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnBestiaryTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		context.runOnClient(mc -> everyWordFits(mc));
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamerule spawn_mobs false");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("difficulty normal");
			context.waitTicks(10);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.setGameMode(GameType.SURVIVAL);
				Bestiary.Record record = BestiaryService.get(player);
				check(Bestiary.ENTRIES.stream().noneMatch(entry -> record.seen(entry.id())), "a new hunter knows nothing of what they hunt");
				check(Bestiary.ENTRIES.size() == 21, "twenty-one creatures in the bestiary: " + Bestiary.ENTRIES.size());
			});
			hunted(context, world);
			theGuide(context, world);
			naturalist(world);
		}
	}

	/** 博物: the last creature the bestiary lacks, met, fills it. */
	private static void naturalist(final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Bestiary.Entry skeleton = Bestiary.of(EntityTypes.SKELETON);
			Bestiary.Record allBut = Bestiary.Record.EMPTY;
			for (Bestiary.Entry entry : Bestiary.ENTRIES) {
				if (entry != skeleton) {
					allBut = allBut.with(entry.id(), 0);
				}
			}
			player.setAttached(life.shizune.mistinn.registry.MistinnAttachments.BESTIARY, allBut);
			AchievementService.clear(player);
			check(!AchievementService.has(player, Achievement.BESTIARY_FULL), "one creature short is not yet every one");
			BestiaryService.met(player, spawn(server.overworld(), player, EntityTypes.SKELETON));
			check(AchievementService.has(player, Achievement.BESTIARY_FULL), "the last one met fills the bestiary: naturalist");
		});
	}

	private static void hunted(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			ServerLevel level = server.overworld();
			Bestiary.Entry zombie = Bestiary.of(EntityTypes.ZOMBIE);
			Mob first = spawn(level, player, EntityTypes.ZOMBIE);
			first.hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
			check(BestiaryService.get(player).kills(zombie.id()) == 1, "a zombie brought down is counted: " + BestiaryService.get(player).kills(zombie.id()));
			check(Bestiary.knows(BestiaryService.get(player), zombie, Bestiary.Detail.WEAK), "one tells its weakness");
			check(!Bestiary.knows(BestiaryService.get(player), zombie, Bestiary.Detail.TELL), "but not yet its tell");

			int known = BestiaryService.get(player).size();
			spawn(level, player, EntityTypes.COW).hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
			check(BestiaryService.get(player).size() == known, "a cow is no prey of the bestiary");

			Bestiary.Entry thrall = Bestiary.of(MistinnEntities.LAMP_THRALL);
			Mob lamp = spawn(level, player, MistinnEntities.LAMP_THRALL);
			player.hurtServer(level, lamp.damageSources().mobAttack(lamp), 1.0F);
			check(BestiaryService.get(player).seen(thrall.id()) && BestiaryService.get(player).kills(thrall.id()) == 0, "struck by a lamp thrall, the hunter has met one");
			check(Bestiary.knows(BestiaryService.get(player), thrall, Bestiary.Detail.NAME)
				&& !Bestiary.knows(BestiaryService.get(player), thrall, Bestiary.Detail.WEAK), "its name and what it is, nothing more");

			Bestiary.Entry spider = Bestiary.of(EntityTypes.SPIDER);
			Mob crawler = spawn(level, player, EntityTypes.SPIDER);
			player.setInvulnerableTime(0);
			crawler.doHurtTarget(level, player);
			check(BestiaryService.get(player).seen(spider.id()), "a spider winding up a blow at the hunter is met too");

			Bestiary.Entry snuffer = Bestiary.of(MistinnEntities.SNUFFER);
			check(snuffer.great(), "a keeper is a great foe");
			BestiaryService.killed(player, spawn(level, player, MistinnEntities.SNUFFER));
			check(Bestiary.knows(BestiaryService.get(player), snuffer, Bestiary.Detail.TELL), "a keeper brought down once tells everything");

			for (int i = 1; i < Bestiary.TELL_KILLS; i++) {
				spawn(level, player, EntityTypes.ZOMBIE).hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
			}
			check(Bestiary.knows(BestiaryService.get(player), zombie, Bestiary.Detail.TELL), "five brought down tell its tell: " + BestiaryService.get(player).kills(zombie.id()));

			// an old world: what the hunter has already done fills the bestiary in
			player.setAttached(MistinnAttachments.BESTIARY, Bestiary.Record.EMPTY);
			AchievementService.grant(player, Achievement.RUST_WHEEL);
			BestiaryService.joined(player);
			check(BestiaryService.get(player).kills(Bestiary.of(MistinnEntities.RUST_WHEEL).id()) >= 1, "the rust wheel brought down long ago is in it");
			// and back to what was hunted here, for the guide
			for (int i = 0; i < Bestiary.TELL_KILLS; i++) {
				BestiaryService.killed(player, spawn(level, player, EntityTypes.ZOMBIE));
			}
			BestiaryService.met(player, spawn(level, player, MistinnEntities.LAMP_THRALL));
		});
		context.waitTicks(5);
		check(context.computeOnClient(mc -> mc.player.getAttachedOrElse(MistinnAttachments.BESTIARY, Bestiary.Record.EMPTY)
			.kills(Bestiary.of(EntityTypes.ZOMBIE).id())) == Bestiary.TELL_KILLS, "the hunter's own client hears of it");
	}

	private static void theGuide(final ClientGameTestContext context, final TestSingleplayerContext world) {
		HunterGuideScreen screen = context.computeOnClient(mc -> {
			// the deeds earned above would cover the picture
			mc.gui.toastManager().clear();
			HunterGuideScreen opened = new HunterGuideScreen();
			mc.gui.setScreen(opened);
			return opened;
		});
		context.waitTicks(3);
		click(context, context.computeOnClient(mc -> screen.tabCentre(HunterGuideScreen.Tab.BESTIARY)));
		check(context.computeOnClient(mc -> screen.tab()) == HunterGuideScreen.Tab.BESTIARY, "the bestiary has a bookmark of its own");
		context.takeScreenshot("mistinn-bestiary-first");
		int zombie = Bestiary.ENTRIES.indexOf(Bestiary.of(EntityTypes.ZOMBIE));
		if (zombie >= GuideBestiaryPage.PER_PAGE) {
			click(context, context.computeOnClient(mc -> screen.nextPage()));
			check(context.computeOnClient(mc -> screen.bestiaryPage()) == zombie / GuideBestiaryPage.PER_PAGE, "the arrow turns the list");
		}
		click(context, context.computeOnClient(mc -> screen.bestiaryRow(zombie)));
		check(context.computeOnClient(mc -> screen.bestiarySelected()) == zombie, "a creature's line opens it");
		context.takeScreenshot("mistinn-bestiary-zombie");
		// every creature drawn once, with all of it known
		world.getServer().runOnServer(server -> {
			Bestiary.Record all = Bestiary.Record.EMPTY;
			for (Bestiary.Entry entry : Bestiary.ENTRIES) {
				all = all.with(entry.id(), Bestiary.TELL_KILLS);
			}
			player(server).setAttached(MistinnAttachments.BESTIARY, all);
		});
		context.waitTicks(5);
		for (int i = 0; i < Bestiary.ENTRIES.size(); i++) {
			final int index = i;
			context.runOnClient(mc -> {
				mc.gui.toastManager().clear();
				screen.selectBestiary(index);
			});
			context.waitTicks(2);
			String id = Bestiary.ENTRIES.get(i).id();
			if (Set.of("mistinn:snuffer", "mistinn:crystal_elder", "mistinn:pirate", "minecraft:enderman").contains(id)) {
				context.takeScreenshot("mistinn-bestiary-" + id.substring(id.indexOf(':') + 1));
			}
		}
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	/** Every bookmark's label fits its bookmark, and every creature's name and words their page, in both languages. */
	private static void everyWordFits(final net.minecraft.client.Minecraft mc) {
		StringBuilder over = new StringBuilder();
		for (String code : List.of("zh_tw", "en_us")) {
			ClientLanguage language = ClientLanguage.loadFrom(mc.getResourceManager(), List.of(code), false);
			for (HunterGuideScreen.Tab tab : HunterGuideScreen.Tab.values()) {
				int width = mc.font.width(language.getOrDefault(tab.key()));
				check(width <= HunterGuideScreen.TAB_W - 4, code + " bookmark " + tab + " fits: " + width + " wide");
			}
			for (Bestiary.Entry entry : Bestiary.ENTRIES) {
				String name = language.getOrDefault(entry.type().getDescriptionId());
				check(mc.font.width(name) <= GuideBestiaryPage.NAME_WIDTH, code + " " + entry.id() + "'s name fits its line");
				int rows = 0;
				rows += rows(mc, language.getOrDefault(GuideBestiaryPage.key(entry, "desc")));
				rows += rows(mc, language.getOrDefault("gui.mistinn.bestiary.weak").replace("%s", language.getOrDefault(GuideBestiaryPage.key(entry, "weak"))));
				rows += rows(mc, language.getOrDefault("gui.mistinn.bestiary.tell").replace("%s", language.getOrDefault(GuideBestiaryPage.key(entry, "tell"))));
				if (rows > GuideBestiaryPage.TEXT_ROWS) {
					over.append(' ').append(code).append(' ').append(entry.key()).append('=').append(rows);
				}
				check(!language.getOrDefault(GuideBestiaryPage.key(entry, "tell")).equals(GuideBestiaryPage.key(entry, "tell")), code + " has " + entry.id() + "'s words");
			}
		}
		check(over.isEmpty(), "every creature's words fit under its picture (" + GuideBestiaryPage.TEXT_ROWS + " rows):" + over);
	}

	private static int rows(final net.minecraft.client.Minecraft mc, final String text) {
		return mc.font.split(Component.literal(text), HunterGuideScreen.PAGE_WIDTH).size();
	}

	private static Mob spawn(final ServerLevel level, final ServerPlayer player, final EntityType<? extends Mob> type) {
		Mob mob = type.create(level, EntitySpawnReason.COMMAND);
		mob.setNoAi(true);
		BlockPos at = player.blockPosition().offset(0, 0, 2);
		mob.snapTo(at.getX() + 0.5, player.getY(), at.getZ() + 0.5, 180.0F, 0.0F);
		level.addFreshEntity(mob);
		return mob;
	}

	private static void click(final ClientGameTestContext context, final double[] gui) {
		double scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());
		context.getInput().setCursorPos(gui[0] * scale, gui[1] * scale);
		context.waitTicks(2);
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError("failed: " + message);
		}
		System.out.println("[mistinn bestiary] ok: " + message);
	}
}
