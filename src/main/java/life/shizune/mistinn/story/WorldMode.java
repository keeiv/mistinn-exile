package life.shizune.mistinn.story;

import com.mojang.serialization.Codec;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * How a world is played: the story (Rust Anchor's orders, chapter by chapter) or free
 * exploration. Chosen before the world is created and kept with the world.
 */
public enum WorldMode {
	STORY("story"),
	FREE("free");

	public final String id;

	WorldMode(final String id) {
		this.id = id;
	}

	public static final Codec<WorldMode> CODEC = Codec.STRING.xmap(WorldMode::byId, mode -> mode.id);

	/** Kept on the overworld. A world from before the choice existed has none: it is free. */
	public static final AttachmentType<WorldMode> ATTACHMENT = AttachmentRegistry.<WorldMode>builder()
		.persistent(CODEC)
		.buildAndRegister(Mistinn.id("world_mode"));

	/**
	 * The choice made on the client for the world being created now. The integrated server of
	 * that world reads it once when it first starts; a dedicated server never has one.
	 */
	private static volatile @Nullable WorldMode pending;

	public static WorldMode byId(final String id) {
		return STORY.id.equals(id) ? STORY : FREE;
	}

	public static void choose(final WorldMode mode) {
		pending = mode;
	}

	public static WorldMode of(final MinecraftServer server) {
		return server.overworld().getAttachedOrElse(ATTACHMENT, FREE);
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel overworld = server.overworld();
			if (!overworld.hasAttached(ATTACHMENT)) {
				WorldMode chosen = pending;
				overworld.setAttached(ATTACHMENT, chosen == null ? FREE : chosen);
				Mistinn.LOGGER.info("New world, mode {}", overworld.getAttachedOrElse(ATTACHMENT, FREE).id);
			}
			pending = null;
		});
	}
}
