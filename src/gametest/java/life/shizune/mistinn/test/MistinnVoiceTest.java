package life.shizune.mistinn.test;

import life.shizune.mistinn.client.voice.VoiceClient;
import life.shizune.mistinn.client.voice.VoiceScreen;
import life.shizune.mistinn.voice.Voice;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;

/**
 * 獵人的聲音: a hunter with no voice is asked for one when they stand in a world, and not again once they have chosen.
 * The choice reaches the server and comes back to the client, their cries are in that voice (a worse one when badly
 * wounded), and every sound of both voices is there to be played. Under the test harness the question waits to be
 * called for, so no other test is left standing behind it.
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
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
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
				VoiceClient.askUnderHarness = true;
			});
			try {
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
				check(world.getServer().computeOnServer(server -> {
					ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
					player.setHealth(player.getMaxHealth() * 0.2F);
					boolean bad = Voice.hurt(player) == Voice.FEMALE.hurtBad;
					player.setHealth(player.getMaxHealth());
					return bad;
				}), "and badly wounded, the cry is a worse one");
				context.waitTicks(60);
				context.runOnClient(mc -> check(!(mc.gui.screen() instanceof VoiceScreen), "once chosen they are not asked again: " + mc.gui.screen()));
			} finally {
				context.runOnClient(mc -> VoiceClient.askUnderHarness = false);
			}
		}
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
