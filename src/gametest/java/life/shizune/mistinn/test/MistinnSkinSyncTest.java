package life.shizune.mistinn.test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import javax.imageio.ImageIO;
import life.shizune.mistinn.client.skin.SyncedSkins;
import life.shizune.mistinn.skin.SkinSync;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * The launcher's skin reaches the world: the client sends it on joining, the server checks it and
 * sends it back to everyone, and the player wears it with the right arms. The server refuses
 * anything that is not a small 64×64 PNG.
 */
public final class MistinnSkinSyncTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		if (Boolean.getBoolean("mistinn.photo") || Boolean.getBoolean("mistinn.animsheet")
			|| Boolean.getBoolean("mistinn.reel") || Boolean.getBoolean("mistinn.medreel")) {
			return;
		}
		byte[] skin = png(64, 64, 0xFF2E7D6B);
		check(SkinSync.valid(skin), "a 64x64 PNG is a skin");
		check(!SkinSync.valid(png(64, 32, 0xFF2E7D6B)), "the old 64x32 layout is refused");
		check(!SkinSync.valid(png(128, 128, 0xFF2E7D6B)), "a large image is refused");
		byte[] fake = Arrays.copyOf(skin, skin.length);
		fake[1] = 'X';
		check(!SkinSync.valid(fake), "something that is not a PNG is refused");
		check(!SkinSync.valid(new byte[SkinSync.MAX_BYTES + 1]), "an oversized file is refused");

		SyncedSkins.setLocal(skin, true);
		try (var world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			UUID me = context.computeOnClient(mc -> mc.player.getUUID());
			context.waitFor(mc -> SyncedSkins.get(me) != null, 100);
			check(context.computeOnClient(mc -> mc.player.getSkin().body().texturePath().equals(SyncedSkins.textureOf(me))),
				"the player wears the skin sent back by the server");
			check(context.computeOnClient(mc -> mc.player.getSkin().model() == PlayerModelType.SLIM), "the skin keeps its slim arms");
			int pixel = context.computeOnClient(mc -> ((DynamicTexture) mc.getTextureManager().getTexture(SyncedSkins.textureOf(me)))
				.getPixels().getPixel(20, 20));
			check((pixel & 0x00FFFFFF) == 0x2E7D6B, "the texture holds the sent pixels: " + Integer.toHexString(pixel));
			context.runOnClient(mc -> {
				mc.player.setYRot(180.0F);
				mc.player.setXRot(10.0F);
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});
			context.waitTicks(10);
			context.takeScreenshot("skin-sync-front");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		}
		check(context.computeOnClient(mc -> SyncedSkins.get(UUID.randomUUID()) == null), "unknown players keep their usual skin");
		SyncedSkins.setLocal(null, false);
	}

	/** A plain image of one colour, as PNG bytes. {@code argb} is 0xAARRGGBB. */
	private static byte[] png(final int width, final int height, final int argb) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, argb);
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", out);
		} catch (IOException error) {
			throw new AssertionError(error);
		}
		return out.toByteArray();
	}

	private static void check(final boolean result, final String message) {
		if (!result) {
			throw new AssertionError(message);
		}
	}
}
