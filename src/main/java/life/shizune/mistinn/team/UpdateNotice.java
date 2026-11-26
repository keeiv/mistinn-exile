package life.shizune.mistinn.team;

import com.mojang.serialization.Codec;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnAttachments;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * What changed for a hunter who played before: when an update changes how an old world plays, a
 * hunter who already knew it reads a few lines about it in the chat the next time they arrive,
 * once. A hunter new to the world has nothing to unlearn and reads none of them.
 */
public final class UpdateNotice {
	/** One update's notice: its id, and how many lines it has ({@code notice.mistinn.<id>.1} and on). */
	public record Notice(String id, int lines) {
	}

	/** Every notice, oldest first. */
	public static final List<Notice> NOTICES = List.of(new Notice("coop", 4), new Notice("downed", 3), new Notice("beacons", 2), new Notice("quick", 2), new Notice("bags", 2));

	/** The notices this hunter has read. */
	public static final AttachmentType<List<String>> SEEN = AttachmentRegistry.<List<String>>builder()
		.persistent(Codec.STRING.listOf())
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("notices_seen"));

	/** Before the guide is handed out on arrival, which is how a hunter new to the world is told apart. */
	private static final Identifier EARLY = Mistinn.id("notice_early");

	private UpdateNotice() {
	}

	public static void register() {
		ServerPlayerEvents.JOIN.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		ServerPlayerEvents.JOIN.register(EARLY, UpdateNotice::joined);
	}

	/** Shows the hunter what they have not read yet; a hunter who never had the guide is new and is spared all of it. */
	public static void joined(final ServerPlayer player) {
		List<String> seen = player.getAttached(SEEN);
		if (seen == null && !player.getAttachedOrElse(MistinnAttachments.GUIDE_GIVEN, false)) {
			player.setAttached(SEEN, NOTICES.stream().map(Notice::id).toList());
			return;
		}
		Set<String> read = new LinkedHashSet<>(seen == null ? List.of() : seen);
		boolean shown = false;
		for (Notice notice : NOTICES) {
			if (read.add(notice.id())) {
				show(player, notice);
				shown = true;
			}
		}
		if (shown || seen == null) {
			player.setAttached(SEEN, List.copyOf(read));
		}
	}

	private static void show(final ServerPlayer player, final Notice notice) {
		player.sendSystemMessage(Component.translatable("notice.mistinn." + notice.id() + ".title").withStyle(ChatFormatting.GOLD));
		for (int i = 1; i <= notice.lines(); i++) {
			player.sendSystemMessage(Component.literal("・").append(Component.translatable("notice.mistinn." + notice.id() + "." + i)).withStyle(ChatFormatting.GRAY));
		}
	}
}
