package life.shizune.mistinn.client.worldmap;

import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.map.MinimapTexture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * The land the hunter has seen, remembered on this computer for each world: the overworld drawn a
 * pixel a block, coloured as the minimap colours it, in regions of 512 by 512 kept as pictures
 * under the config folder. Chunks are drawn as they arrive and again now and then round the
 * hunter, so the map keeps up with what changes.
 */
public final class ExploredMap {
	public static final int REGION = 512;
	private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("mistinn-maps");
	/** How many waiting chunks are drawn each tick, and how often the regions are written out. */
	private static final int PER_TICK = 6;
	private static final int SAVE_EVERY = 600;

	/** One region: its picture, and the texture the map draws it with (made when first shown). */
	private static final class Region {
		final int rx;
		final int rz;
		final NativeImage pixels;
		@Nullable DynamicTexture texture;
		boolean dirty;
		boolean stale;

		Region(final int rx, final int rz, final NativeImage pixels) {
			this.rx = rx;
			this.rz = rz;
			this.pixels = pixels;
		}

		Identifier id() {
			return Mistinn.id("explored/" + key(this.rx) + "_" + key(this.rz));
		}

		private static String key(final int v) {
			return v < 0 ? "m" + -v : String.valueOf(v);
		}
	}

	private static final Map<Long, Region> REGIONS = new HashMap<>();
	private static final LongLinkedOpenHashSet WAITING = new LongLinkedOpenHashSet();
	private static @Nullable Path folder;
	private static @Nullable ClientLevel seen;
	private static int ticks;

	private ExploredMap() {
	}

	/** A chunk has arrived: it will be drawn. */
	public static void arrived(final ClientLevel level, final ChunkPos pos) {
		if (level.dimension() == Level.OVERWORLD) {
			WAITING.add(pos.pack());
		}
	}

	public static void tick(final Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != seen) {
			// a different world (or none): the last one's map is written out and put away
			forget(mc);
			seen = level;
			folder = level == null ? null : ROOT.resolve(worldName(mc));
		}
		if (level == null || mc.player == null || level.dimension() != Level.OVERWORLD) {
			return;
		}
		ticks++;
		if (ticks % 20 == 0) {
			ChunkPos here = ChunkPos.containing(mc.player.blockPosition());
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					WAITING.add(ChunkPos.pack(here.x() + dx, here.z() + dz));
				}
			}
		}
		for (int i = 0; i < PER_TICK && !WAITING.isEmpty(); i++) {
			long packed = WAITING.removeFirstLong();
			draw(level, ChunkPos.getX(packed), ChunkPos.getZ(packed));
		}
		if (ticks % SAVE_EVERY == 0) {
			save();
		}
	}

	private static String worldName(final Minecraft mc) {
		String name;
		if (mc.getSingleplayerServer() != null) {
			name = "local_" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).normalize().getFileName();
		} else if (mc.getCurrentServer() != null) {
			name = "server_" + mc.getCurrentServer().ip;
		} else {
			name = "world";
		}
		return name.replaceAll("[^A-Za-z0-9_.-]", "_");
	}

	/** Draws one chunk's columns into its region, shaded against the column to the north. */
	private static void draw(final ClientLevel level, final int cx, final int cz) {
		if (!level.getChunkSource().hasChunk(cx, cz)) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int x0 = cx << 4;
		int z0 = cz << 4;
		Region region = region(Math.floorDiv(x0, REGION), Math.floorDiv(z0, REGION), true);
		for (int x = x0; x < x0 + 16; x++) {
			int northY = level.getChunkSource().hasChunk(cx, (z0 - 1) >> 4) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z0 - 1) - 1 : Integer.MIN_VALUE;
			for (int z = z0; z < z0 + 16; z++) {
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				int colour = MinimapTexture.colour(level, pos.set(x, y, z), y, northY);
				region.pixels.setPixel(Math.floorMod(x, REGION), Math.floorMod(z, REGION), colour);
				northY = y;
			}
		}
		region.dirty = true;
		region.stale = true;
	}

	private static @Nullable Region region(final int rx, final int rz, final boolean make) {
		long key = ChunkPos.pack(rx, rz);
		Region region = REGIONS.get(key);
		if (region != null || !make) {
			return region;
		}
		NativeImage pixels = null;
		Path file = file(rx, rz);
		if (file != null && Files.exists(file)) {
			try (InputStream in = Files.newInputStream(file)) {
				pixels = NativeImage.read(in);
			} catch (IOException e) {
				Mistinn.LOGGER.warn("Could not read the explored map {}", file, e);
			}
		}
		if (pixels == null || pixels.getWidth() != REGION || pixels.getHeight() != REGION) {
			pixels = new NativeImage(REGION, REGION, true);
		}
		region = new Region(rx, rz, pixels);
		REGIONS.put(key, region);
		return region;
	}

	/** The texture for a region the map shows, or null if nothing of it has been seen (or remembered). */
	public static @Nullable Identifier texture(final int rx, final int rz) {
		Region region = region(rx, rz, folder != null && Files.exists(file(rx, rz)));
		if (region == null) {
			return null;
		}
		if (region.texture == null) {
			region.texture = new DynamicTexture(() -> "mistinn explored map", region.pixels);
			Minecraft.getInstance().getTextureManager().register(region.id(), region.texture);
			region.stale = false;
		} else if (region.stale) {
			region.texture.upload();
			region.stale = false;
		}
		return region.id();
	}

	/** Whether this column has been seen. */
	public static boolean seen(final int x, final int z) {
		Region region = region(Math.floorDiv(x, REGION), Math.floorDiv(z, REGION), folder != null && Files.exists(file(Math.floorDiv(x, REGION), Math.floorDiv(z, REGION))));
		return region != null && (region.pixels.getPixel(Math.floorMod(x, REGION), Math.floorMod(z, REGION)) >>> 24) != 0;
	}

	private static @Nullable Path file(final int rx, final int rz) {
		return folder == null ? null : folder.resolve("r." + rx + "." + rz + ".png");
	}

	/** Writes out every region drawn on since it was last written. */
	public static void save() {
		if (folder == null) {
			return;
		}
		try {
			Files.createDirectories(folder);
			for (Region region : REGIONS.values()) {
				if (region.dirty) {
					region.pixels.writeToFile(file(region.rx, region.rz));
					region.dirty = false;
				}
			}
		} catch (IOException e) {
			Mistinn.LOGGER.warn("Could not write the explored map", e);
		}
	}

	private static void forget(final Minecraft mc) {
		save();
		for (Region region : REGIONS.values()) {
			if (region.texture != null) {
				mc.getTextureManager().release(region.id());
			} else {
				region.pixels.close();
			}
		}
		REGIONS.clear();
		WAITING.clear();
	}
}
