package life.shizune.mistinn.test;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Set;
import java.util.UUID;
import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.enemy.ExecutionService;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.skill.SlideService;
import life.shizune.mistinn.stamina.RollService;
import life.shizune.mistinn.team.DownService;
import life.shizune.mistinn.weapon.HeavyService;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** 倒地: with a teammate standing, the killing blow puts a hunter down; a rescue in place or dragging brings them back in ten seconds. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnDownTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty easy");
			world.getServer().runCommand("time set noon");
			context.waitTicks(10);
			BlockPos arena = world.getServer().computeOnServer(MistinnDownTest::arena);
			context.waitTicks(4);
			fWithNoOneDownStillSwapsTheHands(context, world);
			world.getServer().runOnServer(MistinnDownTest::whatKillsOutright);
			DownService.standing = player -> 1;
			int husk = world.getServer().computeOnServer(server -> husk(server, arena));
			world.getServer().runOnServer(server -> theKillingBlowPutsTheHunterDown(server, husk));
			context.waitTicks(4);
			world.getServer().runOnServer(MistinnDownTest::downedCannotFight);
			context.waitTicks(25);
			lyingOnTheSide(context);
			world.getServer().runOnServer(server -> hitsOnTheGroundCostTime(server, husk));
			world.getServer().runOnServer(MistinnDownTest::revivedWithAQuarter);
			world.getServer().runOnServer(MistinnDownTest::rescueInPlaceTakesTenSeconds);
			world.getServer().runOnServer(MistinnDownTest::walkingAwayBreaksTheRescueOff);
			world.getServer().runOnServer(MistinnDownTest::lettingGoOfFStopsOnlyARescueInPlace);
			world.getServer().runOnServer(MistinnDownTest::draggingPullsThemAlong);
			world.getServer().runOnServer(MistinnDownTest::outOfTimeTheyBleedOut);
			world.getServer().runOnServer(MistinnDownTest::withEveryoneDownTheyDie);
			DownService.standing = player -> 0;
			world.getServer().runOnServer(server -> aloneTheHunterDiesAsBefore(server, husk));
			context.waitTicks(20);
			context.runOnClient(mc -> mc.player.respawn());
			context.waitTicks(40);
		} finally {
			DownService.standing = DownService.STANDING;
		}
	}

	/** The control for F: with no one down, it is still the hand swap. */
	private static void fWithNoOneDownStillSwapsTheHands(final ClientGameTestContext context, final TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getInventory().setSelectedSlot(0);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
		});
		context.waitTicks(4);
		context.getInput().pressKey(InputConstants.KEY_F);
		context.waitTicks(6);
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			check(player.getMainHandItem().is(Items.TORCH) && player.getOffhandItem().is(Items.STICK), "F swaps the hands when no teammate is down");
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		});
	}

	private static void whatKillsOutright(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		check(DownService.killsOutright(level.damageSources().fellOutOfWorld()), "the void kills outright");
		check(DownService.killsOutright(level.damageSources().genericKill()), "so does /kill");
		DamageSource finisher = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ExecutionService.DAMAGE));
		check(DownService.killsOutright(finisher), "and a finisher's blow");
		check(!DownService.killsOutright(level.damageSources().generic()) && !DownService.killsOutright(null), "a blow or a drain does not");
	}

	private static void theKillingBlowPutsTheHunterDown(final MinecraftServer server, final int huskId) {
		ServerPlayer player = player(server);
		Husk husk = (Husk) server.overworld().getEntity(huskId);
		check(husk.canAttack(player), "a standing hunter is prey");
		player.hurtServer(server.overworld(), server.overworld().damageSources().mobAttack(husk), 1000.0F);
		check(player.isAlive() && !player.isDeadOrDying(), "with a teammate standing, the blow does not kill");
		check(DownService.isDown(player), "it puts the hunter down");
		check(!BodyService.get(player).lethal(), "one part short of dying: " + BodyService.get(player));
		check(DownService.get(player).left() == DownService.BLEED_TICKS, "with a minute to hold out");
		check(!husk.canAttack(player), "and the creatures leave a downed hunter be");
	}

	private static void downedCannotFight(final MinecraftServer server) {
		ServerPlayer player = player(server);
		check(player.getPose() == Pose.SWIMMING, "the downed hunter lies low: " + player.getPose());
		check(HeavyService.refusal(player, new ItemStack(MistinnItems.WEAPONS.get(WeaponStyle.SLASH).get(WeaponMaterial.IRON))) == HeavyService.Refusal.BUSY,
			"no heavy blow from the ground");
		RollService.request(player, 0.0F);
		check(!RollService.dodging(player), "no roll");
		check(!SlideService.request(player, 0.0F), "no slide");
		check(player.getAttributeValue(Attributes.MOVEMENT_SPEED) <= 1.0E-6, "and no walking off");
	}

	/** How a downed hunter looks to the others: on their side, not the legless crawl. */
	private static void lyingOnTheSide(final ClientGameTestContext context) {
		context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(50);
		context.takeScreenshot("mistinn-downed");
		context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
	}

	private static void hitsOnTheGroundCostTime(final MinecraftServer server, final int huskId) {
		ServerPlayer player = player(server);
		Husk husk = (Husk) server.overworld().getEntity(huskId);
		int before = DownService.get(player).left();
		player.damageCooldownTime = 0;
		player.hurtServer(server.overworld(), server.overworld().damageSources().mobAttack(husk), 1000.0F);
		check(player.isAlive() && DownService.isDown(player), "a hit on the ground does not kill");
		check(before - DownService.get(player).left() == DownService.HIT_PENALTY, "it costs ten seconds: " + before + " then " + DownService.get(player).left());
		int after = DownService.get(player).left();
		player.damageCooldownTime = 0;
		player.hurtServer(server.overworld(), server.overworld().damageSources().mobAttack(husk), 5.0F);
		check(DownService.get(player).left() == after, "a second hit inside the same second costs nothing more");
	}

	private static void revivedWithAQuarter(final MinecraftServer server) {
		ServerPlayer player = player(server);
		DownService.revive(player, null);
		check(!DownService.isDown(player), "back up");
		float quarter = BodyService.max(player) * DownService.REVIVED_SHARE;
		for (BodyPart part : BodyPart.values()) {
			check(BodyService.get(player).get(part) >= quarter - 1.0E-3F, part + " back to a quarter at least: " + BodyService.get(player).get(part));
		}
		check(player.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.05, "and can walk again");
	}

	private static void rescueInPlaceTakesTenSeconds(final MinecraftServer server) {
		ServerPlayer player = player(server);
		Mate mate = down(server, player, 1.5, 0.0);
		check(DownService.startRescue(player, mate, false), "a teammate beside them starts the rescue");
		for (int i = 0; i < DownService.RESCUE_TICKS - 1; i++) {
			DownService.tick(mate);
		}
		check(DownService.isDown(mate) && DownService.get(mate).progress() == DownService.RESCUE_TICKS - 1, "not a tick early");
		DownService.tick(mate);
		check(!DownService.isDown(mate), "ten seconds bring them back");
	}

	private static void walkingAwayBreaksTheRescueOff(final MinecraftServer server) {
		ServerPlayer player = player(server);
		Mate mate = down(server, player, 1.5, 0.0);
		DownService.startRescue(player, mate, false);
		for (int i = 0; i < 50; i++) {
			DownService.tick(mate);
		}
		mate.setPos(player.getX() + 6.0, player.getY(), player.getZ());
		DownService.tick(mate);
		check(DownService.get(mate).rescuer() == -1 && DownService.get(mate).progress() == 0, "out of reach, the rescue breaks off and starts over");
		mate.setPos(player.getX() + 9.0, player.getY(), player.getZ());
		check(!DownService.startRescue(player, mate, false), "and cannot start from afar");
		DownService.revive(mate, null);
	}

	private static void lettingGoOfFStopsOnlyARescueInPlace(final MinecraftServer server) {
		ServerPlayer player = player(server);
		Mate mate = down(server, player, 1.5, 0.0);
		// a tap: held and let go before anything was seen of it
		DownService.handle(player, mate, DownService.RescuePayload.HOLD);
		check(DownService.get(mate).rescuer() == player.getId(), "F held starts the rescue");
		DownService.handle(player, mate, DownService.RescuePayload.RELEASE);
		check(DownService.get(mate).rescuer() == -1, "letting go of F ends a rescue in place");
		DownService.handle(player, mate, DownService.RescuePayload.DRAG);
		check(DownService.get(mate).dragging(), "H starts dragging");
		DownService.handle(player, mate, DownService.RescuePayload.RELEASE);
		check(DownService.get(mate).rescuer() == player.getId() && DownService.get(mate).dragging(), "but not a drag: " + DownService.get(mate));
		DownService.letGo(player, mate);
		check(DownService.get(mate).rescuer() == -1, "a rescuer who swings lets go");
		DownService.revive(mate, null);
	}

	private static void draggingPullsThemAlong(final MinecraftServer server) {
		ServerPlayer player = player(server);
		player.setYRot(0.0F);
		Mate mate = down(server, player, 2.0, 0.0);
		check(DownService.startRescue(player, mate, true), "H starts dragging");
		check(player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(life.shizune.mistinn.Mistinn.id("dragging")), "the rescuer walks slower with the weight");
		DownService.tick(mate);
		check(mate.getDeltaMovement().x < 0.0 && mate.getDeltaMovement().z < 0.0, "the dragged hunter is pulled to a point behind the rescuer: " + mate.getDeltaMovement());
		for (int i = 0; i < DownService.RESCUE_TICKS; i++) {
			mate.setPos(player.getX(), player.getY(), player.getZ() - 1.0);
			DownService.tick(mate);
		}
		check(!DownService.isDown(mate), "dragging brings them back in ten seconds too");
		check(!player.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(life.shizune.mistinn.Mistinn.id("dragging")), "and the rescuer walks freely again");
	}

	private static void outOfTimeTheyBleedOut(final MinecraftServer server) {
		ServerPlayer player = player(server);
		Mate mate = down(server, player, 3.0, 3.0);
		DownService.Down down = DownService.get(mate);
		mate.setAttached(DownService.DOWN, new DownService.Down(1, down.since(), down.lastHit(), -1, 0, false));
		DownService.tick(mate);
		DownService.tick(mate);
		check(!DownService.isDown(mate) && mate.isDeadOrDying(), "with no one to help, the minute runs out and they die");
	}

	private static void withEveryoneDownTheyDie(final MinecraftServer server) {
		ServerPlayer player = player(server);
		Mate mate = down(server, player, -3.0, 3.0);
		DownService.standing = p -> 0;
		DownService.tick(mate);
		check(mate.isDeadOrDying(), "with no one left standing, the downed die");
		DownService.standing = p -> 1;
	}

	private static void aloneTheHunterDiesAsBefore(final MinecraftServer server, final int huskId) {
		ServerPlayer player = player(server);
		Husk husk = (Husk) server.overworld().getEntity(huskId);
		player.damageCooldownTime = 0;
		player.hurtServer(server.overworld(), server.overworld().damageSources().mobAttack(husk), 1000.0F);
		check(player.isDeadOrDying() && !DownService.isDown(player), "alone, the killing blow kills as it always did");
	}

	// ── Helpers ───────────────────────────────────────────────────

	/** A teammate put down beside the player, {@code dx}, {@code dz} away. */
	private static Mate down(final MinecraftServer server, final ServerPlayer player, final double dx, final double dz) {
		Mate mate = new Mate(server.overworld());
		mate.setPos(player.getX() + dx, player.getY(), player.getZ() + dz);
		Body body = BodyService.get(mate);
		BodyService.set(mate, body.with(BodyPart.HEAD, 0.0F).with(BodyPart.TORSO, 0.0F).with(BodyPart.LEFT_ARM, 0.0F));
		check(DownService.isDown(mate), "the teammate goes down");
		return mate;
	}

	private static int husk(final MinecraftServer server, final BlockPos arena) {
		Husk mob = EntityTypes.HUSK.create(server.overworld(), EntitySpawnReason.COMMAND);
		mob.setNoAi(true);
		mob.snapTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 6.5, 180.0F, 0.0F);
		server.overworld().addFreshEntity(mob);
		return mob.getId();
	}

	private static BlockPos arena(final MinecraftServer server) {
		ServerPlayer player = player(server);
		BlockPos pos = player.blockPosition().above(30).immutable();
		for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-10, -1, -10), pos.offset(10, -1, 10))) {
			player.level().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		}
		for (BlockPos air : BlockPos.betweenClosed(pos.offset(-10, 0, -10), pos.offset(10, 4, 10))) {
			player.level().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
		}
		player.teleportTo(server.overworld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		return pos;
	}

	/** A teammate the test can put down: Fabric's fake player is never hurt at all, this one can be. */
	private static final class Mate extends FakePlayer {
		Mate(final ServerLevel level) {
			super(level, new GameProfile(UUID.randomUUID(), "mate"));
		}

		@Override
		public boolean isInvulnerableTo(final ServerLevel level, final DamageSource source) {
			return false;
		}
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
