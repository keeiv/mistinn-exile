package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.achievement.Achievement;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.client.station.StationTalkScreen;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.salt.SaltService;
import life.shizune.mistinn.stamina.StaminaService;
import life.shizune.mistinn.station.StaffRole;
import life.shizune.mistinn.station.StationBarks;
import life.shizune.mistinn.station.StationBroadcast;
import life.shizune.mistinn.station.StationHand;
import life.shizune.mistinn.station.StationTalk;
import life.shizune.mistinn.weapon.HeavyService;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The staff of Rust Anchor: nothing hurts them, nothing is earned from them, and they talk when spoken to. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnStationTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		everyLineIsWritten(context);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty easy");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			context.waitTicks(10);
			BlockPos arena = world.getServer().computeOnServer(MistinnStationTest::arena);
			lineup(context, world, arena);
			strikesPassThrough(context, world, arena);
			monstersLeaveThemAlone(context, world, arena);
			theyTalk(context, world, arena);
			onlyCommandsRemoveThem(context, world, arena);
		}
	}

	private static void everyLineIsWritten(final ClientGameTestContext context) {
		context.runOnClient(mc -> {
			Language language = Language.getInstance();
			for (StaffRole role : StaffRole.values()) {
				check(language.has(role.key()) && language.has(role.key() + ".title"), role.id + " has a name and a post");
				check(!StationTalkScreen.lines(role, StationTalk.HELLO).isEmpty(), role.id + " has a greeting");
				for (String topic : StationTalk.topics(role)) {
					check(language.has(role.key() + ".ask." + topic), role.id + ": every question has its wording: " + topic);
					check(!StationTalkScreen.lines(role, topic).isEmpty(), role.id + ": and its answer: " + topic);
				}
				check(StationBarks.count(role) > 0, role.id + " has something to say in passing");
				for (int n = 0; n < StationBarks.count(role); n++) {
					check(language.has(StationBarks.key(role, n)), role.id + ": every passing line is written: " + n);
				}
			}
			check(!StationTalkScreen.lines(StaffRole.QUARTERMASTER, "kit").isEmpty(), "the quartermaster has the kit lesson");
			check(language.has("staff.mistinn.bye") && language.has("staff.mistinn.ask"), "the goodbye and the prompt are written");
			for (int n = 0; n < StationBroadcast.LINES; n++) {
				check(language.has(StationBroadcast.key(n)), "every announcement is written: " + n);
			}
			check(language.has(StationBroadcast.key(StationBroadcast.FAREWELL)) && language.has(StationBroadcast.key(StationBroadcast.YOUR_TURN)),
				"and the two calls of the drop");
			check(language.has(StaffRole.ROOMMATE.key() + ".farewell"), "3411 says goodbye");
		});
	}

	/** A flat floor high above the spawn, the hunter at its middle facing +z. */
	private static BlockPos arena(final MinecraftServer server) {
		BlockPos pos = player(server).blockPosition().above(40).immutable();
		for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-8, -1, -8), pos.offset(8, -1, 8))) {
			server.overworld().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		}
		for (BlockPos air : BlockPos.betweenClosed(pos.offset(-8, 0, -8), pos.offset(8, 4, 8))) {
			server.overworld().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
		}
		return pos;
	}

	private static void clear(final MinecraftServer server, final BlockPos arena) {
		for (Entity entity : server.overworld().getEntities((Entity) null, new AABB(arena).inflate(30.0), e -> !(e instanceof ServerPlayer))) {
			entity.discard();
		}
		HeavyService.forgetAll();
	}

	private static StationHand staff(final MinecraftServer server, final BlockPos arena, final double dx, final double dz) {
		StationHand hand = MistinnEntities.STATION_HAND.create(server.overworld(), EntitySpawnReason.COMMAND);
		hand.setRole(StaffRole.QUARTERMASTER);
		hand.snapTo(arena.getX() + 0.5 + dx, arena.getY(), arena.getZ() + 0.5 + dz, 180.0F, 0.0F);
		hand.setYHeadRot(180.0F);
		hand.setYBodyRot(180.0F);
		server.overworld().addFreshEntity(hand);
		return hand;
	}

	private static void stand(final ServerPlayer player, final MinecraftServer server, final BlockPos arena) {
		player.teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		BodyService.restoreAll(player);
		player.setAttached(MistinnAttachments.STAMINA, StaminaService.max(player));
	}

	/** Everyone on the staff in a row, for a look at the skins. */
	private static void lineup(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		world.getServer().runOnServer(server -> {
			clear(server, arena);
			ServerPlayer player = player(server);
			player.teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() - 1.5, Set.of(), 0.0F, 8.0F, true);
			StaffRole[] roles = StaffRole.values();
			for (int i = 0; i < roles.length; i++) {
				StationHand hand = staff(server, arena, (i % 8 - 3.5) * 1.1, i < 8 ? 4.0 : 6.5);
				hand.setRole(roles[i]);
				hand.setNoAi(true);
			}
		});
		context.waitTicks(20);
		context.takeScreenshot("staff-lineup");
	}

	private static void strikesPassThrough(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		int[] ids = world.getServer().computeOnServer(server -> {
			clear(server, arena);
			ServerPlayer player = player(server);
			stand(player, server, arena);
			player.getInventory().setSelectedSlot(0);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.IRON)));
			return new int[] {staff(server, arena, -1.2, 2.0).getId(), staff(server, arena, 0.0, 2.5).getId(), staff(server, arena, 1.2, 2.0).getId()};
		});
		context.waitTicks(4);
		int salt = world.getServer().computeOnServer(server -> SaltService.get(player(server)));
		world.getServer().runOnServer(server -> player(server).attack(server.overworld().getEntity(ids[1])));
		check(lost(world, ids[1]) == 0.0F, "a light swing passes through the staff");
		context.waitTicks(20);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setAttached(MistinnAttachments.STAMINA, StaminaService.max(player));
			int ticks = HeavyService.chargeTicks(player, WeaponStyle.of(player.getMainHandItem()));
			check(HeavyService.release(player, player.getMainHandItem(), ticks), "a full heavy blow is let go into all three");
		});
		context.waitTicks(16);
		for (int id : ids) {
			check(lost(world, id) == 0.0F, "a heavy blow that hits everything in its arc passes through the staff");
		}
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(!AchievementService.has(player, Achievement.HEAVY_THREE), "three staff in one blow earn no achievement");
			check(SaltService.get(player) == salt, "and no salt");
			Mob husk = EntityTypes.HUSK.create(server.overworld(), EntitySpawnReason.COMMAND);
			Entity hand = server.overworld().getEntity(ids[0]);
			check(!((StationHand) hand).hurtServer(server.overworld(), server.overworld().damageSources().mobAttack(husk), 40.0F),
				"nor does a creature's blow hurt them");
		});
	}

	private static void monstersLeaveThemAlone(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		world.getServer().runCommand("gamemode creative @a");
		int[] ids = world.getServer().computeOnServer(server -> {
			clear(server, arena);
			stand(player(server), server, arena);
			StationHand hand = staff(server, arena, 0.0, 4.0);
			Mob zombie = EntityTypes.ZOMBIE.create(server.overworld(), EntitySpawnReason.COMMAND);
			zombie.setPersistenceRequired();
			zombie.snapTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 6.5, 180.0F, 0.0F);
			server.overworld().addFreshEntity(zombie);
			return new int[] {hand.getId(), zombie.getId()};
		});
		context.waitTicks(80);
		world.getServer().runOnServer(server -> {
			Mob zombie = (Mob) server.overworld().getEntity(ids[1]);
			check(zombie.getTarget() == null, "a zombie two blocks away does not hunt the staff: " + zombie.getTarget());
		});
		world.getServer().runCommand("gamemode survival @a");
	}

	private static void theyTalk(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		int id = world.getServer().computeOnServer(server -> {
			clear(server, arena);
			stand(player(server), server, arena);
			return staff(server, arena, 0.0, 2.0).getId();
		});
		context.waitTicks(4);
		check(context.computeOnClient(mc -> mc.level.getEntity(id) instanceof StationHand hand && hand.role() == StaffRole.QUARTERMASTER),
			"the client knows whose skin to draw");
		check(context.computeOnClient(mc -> {
			var renderer = (life.shizune.mistinn.client.station.StationHandRenderer) (Object) mc.getEntityRenderDispatcher().getRenderer(mc.level.getEntity(id));
			var state = renderer.createRenderState();
			renderer.extractRenderState((StationHand) mc.level.getEntity(id), state, 0.0F);
			return state.skin.body().texturePath().equals(StaffRole.QUARTERMASTER.texture());
		}), "the player model is handed the quartermaster's own skin, not the default one");
		world.getServer().runOnServer(server -> server.overworld().getEntity(id).interact(player(server), InteractionHand.MAIN_HAND, Vec3.ZERO));
		context.waitFor(mc -> mc.gui.screen() instanceof StationTalkScreen);
		context.waitTicks(40);
		context.takeScreenshot("staff-talk");
		check(context.computeOnClient(mc -> mc.gui.screen() instanceof StationTalkScreen talk
			&& talk.role() == StaffRole.QUARTERMASTER && StationTalk.HELLO.equals(talk.topic())), "right-clicking opens a talk that starts with a greeting");
		for (int i = 0; i < 8 && context.computeOnClient(mc -> ((StationTalkScreen) mc.gui.screen()).topic() != null); i++) {
			context.runOnClient(mc -> ((StationTalkScreen) mc.gui.screen()).advance());
		}
		context.waitTicks(4);
		context.takeScreenshot("staff-asks");
		int asks = context.computeOnClient(mc -> mc.gui.screen().children().size());
		check(asks == StationTalk.topics(StaffRole.QUARTERMASTER).size() + 1, "then every question, and goodbye: " + asks);
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	private static void onlyCommandsRemoveThem(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena) {
		int id = world.getServer().computeOnServer(server -> {
			clear(server, arena);
			return staff(server, arena, 0.0, 3.0).getId();
		});
		context.waitTicks(2);
		world.getServer().runOnServer(server -> server.overworld().getEntity(id).kill(server.overworld()));
		context.waitTicks(2);
		check(world.getServer().computeOnServer(server -> server.overworld().getEntity(id) == null || !server.overworld().getEntity(id).isAlive()),
			"/kill still removes a member of the staff");
	}

	private static float lost(final TestSingleplayerContext world, final int id) {
		return world.getServer().computeOnServer(server -> {
			StationHand hand = (StationHand) server.overworld().getEntity(id);
			return hand.getMaxHealth() - hand.getHealth();
		});
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
