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
				heard.clear();
				world.getServer().runOnServer(server -> {
					ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
					player.setHealth(player.getMaxHealth() * 0.3F);
				});
				// each client judges the wound by the health it last heard of
				context.waitTicks(10);
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
			}
		} finally {
			context.runOnClient(mc -> {
				VoiceClient.askUnderHarness = false;
				mc.getSoundManager().removeListener(listener);
			});
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
