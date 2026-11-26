package life.shizune.mistinn.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import life.shizune.mistinn.Mistinn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * The terrain under the minimap: one pixel per {@code scale} blocks around the player, coloured
 * like a vanilla map (block colour, lighter when the ground rises to the north, darker when it
 * falls). Redrawn a few times a second on the client from the chunks it already has.
 */
public final class MinimapTexture {
	public static final int SIZE = 64;
	public static final Identifier ID = Mistinn.id("minimap");
	private static final int UNKNOWN = 0xFF15181C;

	private static DynamicTexture texture;
	private static int centreX;
	private static int centreZ;
	private static int drawnScale;

	private MinimapTexture() {
	}

	public static int centreX() {
		return centreX;
	}

	public static int centreZ() {
		return centreZ;
	}

	public static int scale() {
		return drawnScale;
	}

	public static boolean ready() {
		return texture != null;
	}

	public static void refresh(final Minecraft minecraft, final int scale) {
		ClientLevel level = minecraft.level;
		Player player = minecraft.player;
		if (level == null || player == null) {
			return;
		}
		if (texture == null) {
			texture = new DynamicTexture(() -> "mistinn minimap", SIZE, SIZE, true);
			minecraft.getTextureManager().register(ID, texture);
		}
		NativeImage pixels = texture.getPixels();
		if (pixels == null) {
			return;
		}
		centreX = player.getBlockX();
		centreZ = player.getBlockZ();
		drawnScale = scale;
		boolean ceiling = level.dimensionType().hasCeiling();
		int playerY = player.getBlockY();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int px = 0; px < SIZE; px++) {
			int wx = centreX + (px - SIZE / 2) * scale;
			int northY = Integer.MIN_VALUE;
			for (int pz = -1; pz < SIZE; pz++) {
				int wz = centreZ + (pz - SIZE / 2) * scale;
				if (!level.getChunkSource().hasChunk(wx >> 4, wz >> 4)) {
					if (pz >= 0) {
						pixels.setPixel(px, pz, UNKNOWN);
					}
					northY = Integer.MIN_VALUE;
					continue;
				}
				int y = ceiling ? caveSurface(level, pos, wx, wz, playerY) : level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
				if (pz >= 0) {
					pixels.setPixel(px, pz, colour(level, pos.set(wx, y, wz), y, northY));
				}
				northY = y;
			}
		}
		texture.upload();
	}

	/** Under a ceiling (the Nether) the map shows the first floor below the player's head. */
	private static int caveSurface(final ClientLevel level, final BlockPos.MutableBlockPos pos, final int x, final int z, final int playerY) {
		for (int y = playerY + 2; y > playerY - 24; y--) {
			if (!level.getBlockState(pos.set(x, y, z)).isAir() && level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
				return y;
			}
		}
		return playerY - 1;
	}

	public static int colour(final ClientLevel level, final BlockPos pos, final int y, final int northY) {
		BlockState state = level.getBlockState(pos);
		MapColor mapColor = state.getMapColor(level, pos);
		if (mapColor == MapColor.NONE) {
			BlockPos below = pos.below();
			mapColor = level.getBlockState(below).getMapColor(level, below);
		}
		if (mapColor == MapColor.NONE) {
			return UNKNOWN;
		}
		MapColor.Brightness brightness = MapColor.Brightness.NORMAL;
		if (northY != Integer.MIN_VALUE) {
			if (y > northY) {
				brightness = MapColor.Brightness.HIGH;
			} else if (y < northY) {
				brightness = MapColor.Brightness.LOW;
			}
		}
		return 0xFF000000 | mapColor.calculateARGBColor(brightness);
	}
}
