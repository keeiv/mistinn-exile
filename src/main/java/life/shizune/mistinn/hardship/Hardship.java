package life.shizune.mistinn.hardship;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.training.TrainingGround;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * How hard a world is, chosen with its mode: 輕鬆, 普通 or 鏽錨站. Normal is the game as it was;
 * every number here is 1 for it. Rust Anchor adds its own rules on top (station food only,
 * weakened weapons, lost limbs, a heavier death), each kept by the system it belongs to.
 */
public enum Hardship {
	EASY("easy", 0.7F, 0.5F, 0.75F, 0.5F, 0.8F),
	NORMAL("normal", 1.0F, 1.0F, 1.0F, 1.0F, 1.0F),
	RUST_ANCHOR("rust_anchor", 1.25F, 1.5F, 1.5F, 2.0F, 1.25F);

	public final String id;
	/** Multiplies what a blow takes from a hunter's body. */
	public final float damageTaken;
	/** Multiplies the chance of a blow opening a wound or breaking a bone. */
	public final float woundChance;
	/** Multiplies what a bleeding part loses each second. */
	public final float bleeding;
	/** Multiplies the time between two points of natural recovery: above 1 is slower. */
	public final float recovery;
	/** Multiplies the hunger work and wounds cost. */
	public final float hunger;

	Hardship(final String id, final float damageTaken, final float woundChance, final float bleeding, final float recovery, final float hunger) {
		this.id = id;
		this.damageTaken = damageTaken;
		this.woundChance = woundChance;
		this.bleeding = bleeding;
		this.recovery = recovery;
		this.hunger = hunger;
	}

	public static final Codec<Hardship> CODEC = Codec.STRING.xmap(Hardship::byId, hardship -> hardship.id);
	public static final StreamCodec<ByteBuf, Hardship> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(Hardship::byId, hardship -> hardship.id);

	/** Kept on the overworld. A world from before the choice existed has none: it is normal. */
	public static final AttachmentType<Hardship> WORLD = AttachmentRegistry.<Hardship>builder()
		.persistent(CODEC)
		.buildAndRegister(Mistinn.id("hardship"));

	/** The world's hardship, copied onto each hunter for their client: tooltips and the hunger bar. */
	public static final AttachmentType<Hardship> SEEN = AttachmentRegistry.<Hardship>builder()
		.syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("hardship_seen"));

	/**
	 * The choice made on the client for the world being created now. The integrated server of
	 * that world reads it once when it first starts; a dedicated server never has one.
	 */
	private static volatile @Nullable Hardship pending;

	public static Hardship byId(final String id) {
		return switch (id) {
			case "easy" -> EASY;
			case "rust_anchor" -> RUST_ANCHOR;
			default -> NORMAL;
		};
	}

	public static void choose(final @Nullable Hardship hardship) {
		pending = hardship;
	}

	public static Hardship of(final MinecraftServer server) {
		return server.overworld().getAttachedOrElse(WORLD, NORMAL);
	}

	/** The hardship of the world this player is in, on either side. */
	public static Hardship of(final Player player) {
		if (player.level().isClientSide()) {
			return player.getAttachedOrElse(SEEN, NORMAL);
		}
		MinecraftServer server = player.level().getServer();
		return server == null ? NORMAL : of(server);
	}

	public static boolean rustAnchor(final Player player) {
		return of(player) == RUST_ANCHOR;
	}

	/**
	 * Rust Anchor's harshest rules (lost limbs, the heavier death) wait until the hunter has left
	 * the station: the course has a fall in it, and the station is where the rules are taught.
	 */
	public static boolean rustAnchorAfield(final Player player) {
		return rustAnchor(player) && !player.level().dimension().equals(TrainingGround.KEY);
	}

	/**
	 * Called when a world first starts. A new world takes the choice made for it; a world that
	 * already ran without one (made before the choice existed) is normal.
	 */
	public static void settle(final MinecraftServer server, final boolean fresh) {
		ServerLevel overworld = server.overworld();
		if (!overworld.hasAttached(WORLD)) {
			Hardship chosen = fresh && pending != null ? pending : NORMAL;
			overworld.setAttached(WORLD, chosen);
			Mistinn.LOGGER.info("World hardship {}", chosen.id);
			if (chosen == EASY) {
				server.setDifficulty(Difficulty.EASY, true);
			} else if (chosen == RUST_ANCHOR) {
				server.setDifficulty(Difficulty.HARD, true);
			}
		}
		pending = null;
	}

	/** Tells a hunter's client how hard their world is. */
	public static void show(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server != null) {
			player.setAttached(SEEN, of(server));
		}
	}

	public static void init() {
		ServerPlayerEvents.JOIN.register(Hardship::show);
	}
}
