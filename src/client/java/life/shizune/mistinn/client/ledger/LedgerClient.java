package life.shizune.mistinn.client.ledger;

import java.nio.file.Path;
import life.shizune.mistinn.ledger.LedgerService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jspecify.annotations.Nullable;

/** The client's ledger: read once, kept up to date as the server tells what happens. */
public final class LedgerClient {
	private static @Nullable Ledger ledger;
	/** The world the hunter is in now, as the server named it, or null. */
	private static @Nullable String world;

	private LedgerClient() {
	}

	/** This game's ledger. */
	public static Ledger ledger() {
		if (ledger == null) {
			ledger = Ledger.load(Ledger.defaultFile());
		}
		return ledger;
	}

	/** Test hook: keep the ledger at this file from now on (read afresh). */
	public static void use(final Path file) {
		ledger = Ledger.load(file);
	}

	/** The world the hunter is in now, or null before the server has named it. */
	public static @Nullable String world() {
		return world;
	}

	public static void register() {
		// written out now and then, and when the hunter leaves the world or the game, not on every word from the server
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (ledger != null && mc.level != null && mc.level.getGameTime() % 100L == 0L) {
				ledger.flush();
			}
		});
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			world = null;
			if (ledger != null) {
				ledger.flush();
			}
		});
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			if (ledger != null) {
				ledger.flush();
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(LedgerService.WorldPayload.TYPE, (payload, context) -> {
			ledger().world(payload.world(), payload.mode());
			world = payload.world();
			// the server may know which way this hunter leans, for what the tillers say
			if (ClientPlayNetworking.canSend(LedgerService.TonePayload.TYPE)) {
				ClientPlayNetworking.send(new LedgerService.TonePayload(ledger().tone()));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(LedgerService.EventPayload.TYPE,
			(payload, context) -> ledger().event(payload.world(), payload.event(), payload.once()));
	}
}
