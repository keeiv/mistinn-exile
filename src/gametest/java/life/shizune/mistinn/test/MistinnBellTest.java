package life.shizune.mistinn.test;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import life.shizune.mistinn.bell.BellService;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.body.WoundService;
import life.shizune.mistinn.body.Wounds;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Golden bells for each hunter apart, and the ring that restores them. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnBellTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			context.waitTicks(10);
			world.getServer().runOnServer(MistinnBellTest::theRingRestores);
			world.getServer().runOnServer(MistinnBellTest::eachHunterHasTheirOwnCooldown);
			world.getServer().runOnServer(MistinnBellTest::aOneTimeBellTakesEachHunterOnce);
		}
	}

	private static void theRingRestores(final MinecraftServer server) {
		ServerPlayer player = player(server);
		BlockPos pos = player.blockPosition().above(20);
		server.overworld().setBlockAndUpdate(pos, MistinnBlocks.GOLDEN_BELL.defaultBlockState());
		BodyService.set(player, BodyService.get(player).with(BodyPart.TORSO, 10.0F).with(BodyPart.LEFT_ARM, 0.0F));
		WoundService.set(player, Wounds.NONE.with(BodyPart.RIGHT_LEG, Wounds.Wound.NONE.fractured(true).bleeding(true)));
		player.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600));
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 600));
		player.getFoodData().setFoodLevel(5);
		player.igniteForSeconds(5.0F);
		check(BellService.rest(player, pos), "a ready bell rings");
		for (BodyPart part : BodyPart.values()) {
			check(BodyService.get(player).get(part) >= BodyService.max(player) - 1.0E-3F, part + " is whole again");
		}
		check(!WoundService.get(player).any(), "the wounds are closed");
		check(!player.hasEffect(MobEffects.POISON) && !player.hasEffect(MobEffects.WEAKNESS), "harmful effects are gone");
		check(player.hasEffect(MobEffects.SPEED), "but a good one stays");
		check(player.getFoodData().getFoodLevel() == 20 && !player.isOnFire(), "fed, and the fire out");
		BodyService.set(player, BodyService.get(player).with(BodyPart.TORSO, 10.0F));
		check(!BellService.rest(player, pos), "the same bell, cooling for this hunter, does not ring");
		check(BodyService.get(player).get(BodyPart.TORSO) == 10.0F, "and restores nothing");
		BodyService.restoreAll(player);
	}

	private static void eachHunterHasTheirOwnCooldown(final MinecraftServer server) {
		ServerPlayer player = player(server);
		BlockPos pos = player.blockPosition().above(22);
		ServerLevel level = server.overworld();
		level.setBlockAndUpdate(pos, MistinnBlocks.GOLDEN_BELL.defaultBlockState());
		check(BellService.rest(player, pos), "the first hunter rings it");
		Mate mate = new Mate(level, "mate");
		check(BellService.rest(mate, pos), "a teammate rings the same bell at once: the cooldown is the first hunter's own");
		check(GlobalPos.of(level.dimension(), pos).equals(mate.getAttached(MistinnAttachments.BELL)), "and is bound to it");
		check(!BellService.rest(mate, pos) && !BellService.rest(player, pos), "after that each waits out their own hour");
	}

	private static void aOneTimeBellTakesEachHunterOnce(final MinecraftServer server) {
		ServerPlayer player = player(server);
		ServerLevel level = server.overworld();
		BlockPos pos = player.blockPosition().above(24);
		level.setBlockAndUpdate(pos, MistinnBlocks.ORE_BELL.defaultBlockState());
		Mate first = new Mate(level, "first");
		Mate second = new Mate(level, "second");
		check(BellService.rest(first, pos) && BellService.rest(second, pos), "two hunters each claim the same one-time bell");
		check(!BellService.rest(first, pos), "but each only once");
		life.shizune.mistinn.test.MistinnBellTest.respawn(first);
		check(level.getBlockState(pos).is(MistinnBlocks.ORE_BELL), "bringing one back, it holds together for the other");
		check(first.getAttached(MistinnAttachments.BELL) == null, "and lets the first go");
		check(!BellService.rest(first, pos), "a hunter it brought back cannot claim it again");
		respawn(second);
		check(!level.getBlockState(pos).is(MistinnBlocks.ORE_BELL), "it cracks after bringing back the last one it held");
	}

	/** What a respawn does with the hunter's bell, without dying. */
	private static void respawn(final ServerPlayer hunter) {
		try {
			java.lang.reflect.Method back = BellService.class.getDeclaredMethod("onRespawn", ServerPlayer.class);
			back.setAccessible(true);
			back.invoke(null, hunter);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	/** A second hunter: Fabric's fake player, with a name of its own. */
	private static final class Mate extends FakePlayer {
		Mate(final ServerLevel level, final String name) {
			super(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
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
