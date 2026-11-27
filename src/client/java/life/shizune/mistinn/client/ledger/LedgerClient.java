package life.shizune.mistinn.client.ledger;

import java.nio.file.Path;
import life.shizune.mistinn.ledger.LedgerService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jspecify.annotations.Nullable;

/** The client's ledger: read once, kept up to date as the server tells what happens. */
public final class LedgerClient {
	private static @Nullable Ledger ledger;

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

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LedgerService.WorldPayload.TYPE, (payload, context) -> ledger().world(payload.world(), payload.mode()));
		ClientPlayNetworking.registerGlobalReceiver(LedgerService.EventPayload.TYPE,
			(payload, context) -> ledger().event(payload.world(), payload.event(), payload.once()));
	}
}
