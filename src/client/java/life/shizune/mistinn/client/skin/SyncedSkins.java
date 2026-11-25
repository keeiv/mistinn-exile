package life.shizune.mistinn.client.skin;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.skin.SkinSync;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/**
 * The skins other players sent ({@link SkinSync}), as textures the player list hands out in place
 * of the default ones, and this player's own skin from the launcher
 * ({@code -Dmistinn.skin=<png>}, {@code -Dmistinn.skin.slim=true}), sent on joining a world.
 */
public final class SyncedSkins {
	private static final Map<UUID, PlayerSkin> SKINS = new ConcurrentHashMap<>();
	private static byte @Nullable [] localPng;
	private static boolean localSlim;

	private SyncedSkins() {
	}

	/** Reads the launcher's skin and registers the client's side. */
	public static void register() {
		String path = System.getProperty("mistinn.skin");
		if (path != null && !path.isBlank()) {
			try {
				Path file = Path.of(path);
				if (Files.size(file) <= SkinSync.MAX_BYTES) {
					setLocal(Files.readAllBytes(file), Boolean.getBoolean("mistinn.skin.slim"));
				}
			} catch (IOException | RuntimeException unreadable) {
				Mistinn.LOGGER.warn("Could not read the skin {}", path, unreadable);
			}
		}
		ClientPlayNetworking.registerGlobalReceiver(SkinSync.Skin.TYPE, (payload, context) -> apply(context.client(), payload));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			byte[] png = localPng;
			if (png != null && ClientPlayNetworking.canSend(SkinSync.Upload.TYPE)) {
				ClientPlayNetworking.send(new SkinSync.Upload(localSlim, png));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> clear(client)));
	}

	/** Sets this player's own skin (null for none); it is sent the next time a world is joined. */
	public static void setLocal(final byte @Nullable [] png, final boolean slim) {
		if (png != null && !SkinSync.valid(png)) {
			Mistinn.LOGGER.warn("The skin is not a 64x64 PNG; playing with the default skin");
			localPng = null;
			return;
		}
		localPng = png == null ? null : png.clone();
		localSlim = slim;
	}

	/** The skin a player sent, or null to keep the usual one. */
	public static @Nullable PlayerSkin get(final UUID player) {
		return SKINS.get(player);
	}

	/** The texture a player's sent skin is kept under. */
	public static Identifier textureOf(final UUID player) {
		return Mistinn.id("skins/" + player);
	}

	private static void apply(final Minecraft mc, final SkinSync.Skin skin) {
		NativeImage image;
		try {
			image = NativeImage.read(skin.png());
		} catch (IOException | RuntimeException broken) {
			Mistinn.LOGGER.warn("A player's skin could not be read", broken);
			return;
		}
		if (image.getWidth() != 64 || image.getHeight() != 64) {
			image.close();
			return;
		}
		Identifier id = textureOf(skin.player());
		mc.getTextureManager().register(id, new DynamicTexture(() -> "Mistinn skin " + skin.player(), image));
		ClientAsset.ResourceTexture body = new ClientAsset.ResourceTexture(id, id);
		SKINS.put(skin.player(), PlayerSkin.insecure(body, null, null, skin.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE));
	}

	private static void clear(final Minecraft mc) {
		for (UUID player : SKINS.keySet()) {
			mc.getTextureManager().release(textureOf(player));
		}
		SKINS.clear();
	}
}
