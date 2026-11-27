package life.shizune.mistinn.client.path;

import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.client.ledger.LedgerClient;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.path.PathService;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;

/**
 * 3412's own voice, three times in a world: the first while standing on the zone's ground, the first dusk under the
 * open sky, the first time badly hurt. What they think there is how they carry themself, which is the world's
 * hardship: easy, sure of themself; ordinary, sure of their training; Rust Anchor's, saying the training over to
 * themself, and not believing it. Nothing says so. Each once in a world, kept in the ledger.
 */
public final class InnerVoice {
	/** Ticks on the ground before the first thought. */
	public static final int SETTLED = 100;
	/** The share of the body's strength below which they are badly hurt. */
	public static final float HURT = 0.4F;
	private static int grounded;
	/** What has come to them since joining, by key: for tests. */
	public static final java.util.List<String> SAID = new java.util.ArrayList<>();

	private InnerVoice() {
	}

	public static void tick(final Minecraft mc) {
		String world = LedgerClient.world();
		if (mc.player == null || mc.level == null || world == null) {
			grounded = 0;
			SAID.clear();
			return;
		}
		if (Boolean.getBoolean("mistinn.photo")) {
			// the website's pictures are of the world, not of what No. 3412 is thinking
			return;
		}
		String mode = LedgerClient.ledger().mode(world);
		// the tillers on their way think their own thoughts; a training ground is not the zone
		if (!"story".equals(mode) && !"free".equals(mode) || mc.player.hasAttached(PathService.VIEW) || !mc.player.isAlive() || mc.player.isSpectator()) {
			return;
		}
		String hardship = Hardship.of(mc.player).id;
		grounded = mc.player.onGround() ? grounded + 1 : 0;
		if (grounded == SETTLED) {
			say(world, "landing", hardship);
		}
		long time = Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
		if (time >= 12800L && time < 13600L && mc.level.canSeeSky(mc.player.blockPosition())) {
			say(world, "night", hardship);
		}
		if (BodyService.get(mc.player).total() < 6.0F * BodyService.max(mc.player) * HURT) {
			say(world, "hurt", hardship);
		}
	}

	/** The thought for this moment, once in this world. */
	private static void say(final String world, final String moment, final String hardship) {
		if (!LedgerClient.ledger().firstIn(world, "voice." + moment)) {
			return;
		}
		Language language = Language.getInstance();
		for (int i = 0; i < 3; i++) {
			String key = "voice.mistinn." + moment + "." + hardship + "." + i;
			if (language.has(key)) {
				Thoughts.come(key);
				SAID.add(key);
			}
		}
	}
}
