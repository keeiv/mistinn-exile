package life.shizune.mistinn.medical;

import com.mojang.serialization.Codec;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.grid.GridService;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

/**
 * Below Rust Anchor the station sends every hunter off with a little medicine: two tourniquets, a
 * splint, painkillers and a potent injector, once per hunter and world (hunters of an old world get
 * it the next time they arrive). Rust Anchor gives nothing.
 */
public final class StarterKit {
	/** Whether this hunter has had the kit (or, at Rust Anchor, was never owed one). Kept through death. */
	public static final AttachmentType<Boolean> GIVEN = AttachmentRegistry.<Boolean>builder()
		.persistent(Codec.BOOL)
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("starter_kit"));

	private StarterKit() {
	}

	public static List<ItemStack> kit() {
		return List.of(new ItemStack(MistinnItems.TOURNIQUET, 2), new ItemStack(MistinnItems.SPLINT), new ItemStack(MistinnItems.PAINKILLERS),
			new ItemStack(MistinnItems.POTENT_INJECTOR));
	}

	public static void register() {
		ServerPlayerEvents.JOIN.register(StarterKit::joined);
	}

	/** On arrival: the kit, once, below Rust Anchor. Creative players are skipped, and owed it later. */
	public static void joined(final ServerPlayer player) {
		if (player.getAttachedOrElse(GIVEN, false) || !BodyService.applies(player)) {
			return;
		}
		player.setAttached(GIVEN, true);
		if (Hardship.of(player) == Hardship.RUST_ANCHOR) {
			return;
		}
		for (ItemStack stack : kit()) {
			ItemStack giving = stack.copy();
			GridService.insert(player, giving);
			if (!giving.isEmpty() && !player.getInventory().add(giving)) {
				player.drop(giving, false, Prediction.SERVER_ONLY);
			}
		}
		player.sendSystemMessage(Component.translatable("message.mistinn.starter_kit"));
	}
}
