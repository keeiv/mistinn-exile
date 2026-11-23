package life.shizune.mistinn.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import life.shizune.mistinn.client.voice.VoiceClient;
import life.shizune.mistinn.client.voice.VoiceScreen;
import life.shizune.mistinn.voice.Voice;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

/**
 * 獵人的聲音: a hunter with no voice is asked for one when they stand in a world, and not again once they have chosen:
 * not after falling and coming back, and not on returning to the world another day. The choice reaches the server and
 * comes back to the client, a blow is heard in that voice (a worse cry when badly wounded), and every sound of both
 * voices is there to be played. Under the test harness the question waits to be called for, so no other test is left
 * standing behind it.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnVoiceTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel") || Boolean.getBoolean("mistinn.bossreel")) {
			return;
		}
		List<String> heard = Collections.synchronizedList(new ArrayList<>());
		SoundEventListener listener = (sound, events, range) -> heard.add(sound.getIdentifier().getPath());
		try {
			TestWorldSave save;
			try (TestSingleplayerContext world = context.worldBuilder().create()) {
				save = world.getWorldSave();
				world.getConnection().waitForChunksRender();
				context.waitTicks(60);
				context.runOnClient(mc -> {
					check(!(mc.gui.screen() instanceof VoiceScreen), "under the harness nobody is asked unless a test calls for it");
					check(Voice.of(mc.player) == null && Voice.hurt(mc.player) == null, "a hunter who has not chosen keeps the plain sounds");
					for (Voice voice : Voice.values()) {
						for (var sound : new net.minecraft.sounds.SoundEvent[] {voice.hurt, voice.hurtBad, voice.effort, voice.land, voice.death, voice.breath, voice.pant}) {
							check(mc.getSoundManager().getSoundEvent(sound.location()) != null, "there is a sound for " + sound.location());
						}
					}
					mc.getSoundManager().addListener(listener);
					VoiceClient.askUnderHarness = true;
				});
				context.waitFor(mc -> mc.gui.screen() instanceof VoiceScreen, 100);
				context.runOnClient(mc -> check(!mc.gui.screen().shouldCloseOnEsc(), "the first time, the question cannot be waved away"));
				context.takeScreenshot("voice-00-ask");
				context.runOnClient(mc -> {
					Button girl = mc.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
						.filter(button -> button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals("voice.mistinn.female"))
						.findFirst().orElseThrow();
					girl.onPress(null);
				});
				context.waitFor(mc -> Voice.of(mc.player) == Voice.FEMALE, 100);
				check(world.getServer().computeOnServer(server -> Voice.of(server.getPlayerList().getPlayers().getFirst()) == Voice.FEMALE),
					"the choice is kept with the hunter on the server");
				context.runOnClient(mc -> check(Voice.hurt(mc.player) == Voice.FEMALE.hurt, "struck, they cry out in their own voice"));
				context.waitTicks(60);
				context.runOnClient(mc -> check(!(mc.gui.screen() instanceof VoiceScreen), "once chosen they are not asked again: " + mc.gui.screen()));

				// a real blow is heard in the hunter's voice, and a worse cry when little health is left
				heard.clear();
				world.getServer().runOnServer(server -> {
					ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
					player.setGameMode(GameType.SURVIVAL);
					player.hurtServer(server.overworld(), player.damageSources().generic(), 2.0F);
				});
				context.waitTicks(30);
				check(heard.contains("voice.female.hurt"), "a blow is heard in the hunter's own voice: " + heard);
				// every part worn down to a quarter: health follows the body, and each client judges the wound by the health it last heard of
				world.getServer().runOnServer(server -> {
					ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
					life.shizune.mistinn.body.Body body = life.shizune.mistinn.body.BodyService.get(player);
					for (life.shizune.mistinn.body.BodyPart part : life.shizune.mistinn.body.BodyPart.values()) {
						body = body.with(part, life.shizune.mistinn.body.BodyService.max(player) * 0.25F);
					}
					life.shizune.mistinn.body.BodyService.set(player, body);
				});
				context.waitFor(mc -> mc.player.getHealth() < mc.player.getMaxHealth() * Voice.BAD, 100);
				heard.clear();
				world.getServer().runOnServer(server -> {
					ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
					player.hurtServer(server.overworld(), player.damageSources().generic(), 1.0F);
				});
				context.waitTicks(30);
				check(heard.contains("voice.female.hurt_bad"), "badly wounded, the cry is a worse one: " + heard);

				// fallen and come back, the hunter still has their voice and is not asked for it
				world.getServer().runOnServer(server -> server.getPlayerList().respawn(server.getPlayerList().getPlayers().getFirst(), false, Entity.RemovalReason.KILLED));
				context.waitTicks(80);
				context.runOnClient(mc -> {
					check(Voice.of(mc.player) == Voice.FEMALE, "come back from a fall, the hunter keeps their voice");
					check(!(mc.gui.screen() instanceof VoiceScreen), "and is not asked for it again");
				});
			}
			// another day in the same world
			try (TestSingleplayerContext world = save.open()) {
				world.getConnection().waitForChunksRender();
				context.waitTicks(80);
				context.runOnClient(mc -> {
					check(Voice.of(mc.player) == Voice.FEMALE, "back in the world another day, the voice is still theirs");
					check(!(mc.gui.screen() instanceof VoiceScreen), "and nobody asks");
				});

				// the voice key opens the same screen to change it; it shows the voice in use and can be left with Esc
				context.runOnClient(VoiceClient::open);
				context.waitFor(mc -> mc.gui.screen() instanceof VoiceScreen, 40);
				context.runOnClient(mc -> {
					check(mc.gui.screen().shouldCloseOnEsc(), "opened with its key, the screen closes like any other");
					check(!button(mc, "voice.mistinn.female").active && button(mc, "voice.mistinn.male").active,
						"the voice in use is marked and cannot be chosen again");
				});
				context.takeScreenshot("voice-01-change");
				// no voice at all is a choice too: kept, never asked about again, and the plain sounds come back
				context.runOnClient(mc -> button(mc, "voice.mistinn.none").onPress(null));
				context.waitFor(mc -> Voice.choice(mc.player) == Voice.NONE, 100);
				context.runOnClient(mc -> check(Voice.chosen(mc.player) && Voice.of(mc.player) == null && Voice.hurt(mc.player) == null,
					"a hunter who chose no voice has answered, and has none"));
				check(world.getServer().computeOnServer(server -> Voice.choice(server.getPlayerList().getPlayers().getFirst()) == Voice.NONE),
					"the server keeps that answer too");
				heard.clear();
				world.getServer().runOnServer(server -> {
					ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
					player.setGameMode(GameType.SURVIVAL);
					player.hurtServer(server.overworld(), player.damageSources().generic(), 1.0F);
				});
				context.waitTicks(80);
				check(heard.stream().noneMatch(sound -> sound.startsWith("voice.")), "with no voice, a blow is heard as it always was: " + heard);
				context.runOnClient(mc -> check(!(mc.gui.screen() instanceof VoiceScreen), "and choosing none is not asked about again"));
			}
		} finally {
			context.runOnClient(mc -> {
				VoiceClient.askUnderHarness = false;
				mc.getSoundManager().removeListener(listener);
			});
		}
	}

	/** The voice screen's button whose label is (or wraps) this translation key. */
	private static Button button(final net.minecraft.client.Minecraft mc, final String key) {
		return mc.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
			.filter(button -> button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals(key))
			.findFirst().orElseThrow();
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
