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
	/** One update's notice: one line, {@code notice.mistinn.<id>}; the README tells the rest. */
	public record Notice(String id) {
	}

	/** Every notice, oldest first. */
	public static final List<Notice> NOTICES = List.of(new Notice("coop"), new Notice("downed"), new Notice("beacons"), new Notice("quick"),
		new Notice("bags"), new Notice("enchant"), new Notice("recipes"), new Notice("bells"), new Notice("controls"), new Notice("pirates"),
		new Notice("story_shared"), new Notice("pings"), new Notice("raids"));

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
		int shown = 0;
		for (Notice notice : NOTICES) {
			if (read.add(notice.id())) {
				show(player, notice);
				shown++;
			}
		}
		if (shown > 0) {
			// last, so it is the line still on screen however many came before it
			player.sendSystemMessage(Component.translatable("notice.mistinn.heading", shown).withStyle(ChatFormatting.GOLD));
		}
		if (shown > 0 || seen == null) {
			player.setAttached(SEEN, List.copyOf(read));
		}
	}

	private static void show(final ServerPlayer player, final Notice notice) {
		player.sendSystemMessage(Component.literal("・").append(Component.translatable("notice.mistinn." + notice.id())).withStyle(ChatFormatting.GRAY));
	}
}
