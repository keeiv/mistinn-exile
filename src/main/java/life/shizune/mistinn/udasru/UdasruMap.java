package life.shizune.mistinn.udasru;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import life.shizune.mistinn.Mistinn;
import net.minecraft.util.Mth;

/**
 * The land of Udasru as it was drawn: a height and a region for every few blocks, read once from the mod's own file
 * ({@code /udasru/map.bin.gz}, written by the map script) and kept as it is. Between the drawn points the height is
 * smoothed; past the edge of the drawing is open sea.
 */
public final class UdasruMap {
	/** What lies beyond the drawing: the sea floor, and the sea's region. */
	public static final float SEA_FLOOR = 30.0F;
	public static final int SEA = 0;

	private static volatile UdasruMap loaded;

	/** Samples on a side, blocks between two samples, and the world coordinate of the first sample. */
	public final int size;
	public final int step;
	public final int origin;
	private final short[] height;
	private final byte[] region;
	/** How calm the ground is kept round towns and landmarks, 0 to 255 (none where the drawing has no such layer). */
	private final byte[] calm;

	private UdasruMap(final int size, final int step, final short[] height, final byte[] region, final byte[] calm) {
		this.size = size;
		this.step = step;
		this.origin = -size * step / 2;
		this.height = height;
		this.region = region;
		this.calm = calm;
	}

	public static UdasruMap get() {
		UdasruMap map = loaded;
		if (map == null) {
			synchronized (UdasruMap.class) {
				map = loaded;
				if (map == null) {
					map = read();
					loaded = map;
				}
			}
		}
		return map;
	}

	private static UdasruMap read() {
		try (InputStream raw = UdasruMap.class.getResourceAsStream("/udasru/map.bin.gz")) {
			if (raw == null) {
				throw new IllegalStateException("the map of Udasru is missing from the mod");
			}
			DataInputStream in = new DataInputStream(new GZIPInputStream(raw, 1 << 16));
			if (in.readInt() != 0x55445352) {
				throw new IllegalStateException("the map of Udasru is not one");
			}
			int size = in.readInt();
			int step = in.readInt();
			short[] height = new short[size * size];
			for (int i = 0; i < height.length; i++) {
				height[i] = in.readShort();
			}
			byte[] region = new byte[size * size];
			in.readFully(region);
			byte[] calm = new byte[size * size];
			try {
				in.readFully(calm);
			} catch (java.io.EOFException none) {
				calm = new byte[size * size];
			}
			Mistinn.LOGGER.info("Udasru drawn: {} samples a side, {} blocks apart", size, step);
			return new UdasruMap(size, step, height, region, calm);
		} catch (IOException error) {
			throw new IllegalStateException("could not read the map of Udasru", error);
		}
	}

	/** The width of the drawn land, in blocks. */
	public int width() {
		return this.size * this.step;
	}

	/** The drawn height at a sample, in blocks. */
	private float sample(final int sx, final int sz) {
		if (sx < 0 || sz < 0 || sx >= this.size || sz >= this.size) {
			return SEA_FLOOR;
		}
		return this.height[sz * this.size + sx] / 4.0F;
	}

	/** The land's height at a column, smoothed between the drawn points. */
	public float height(final double x, final double z) {
		double fx = (x - this.origin) / this.step;
		double fz = (z - this.origin) / this.step;
		int sx = Mth.floor(fx);
		int sz = Mth.floor(fz);
		double tx = smooth(fx - sx);
		double tz = smooth(fz - sz);
		double top = Mth.lerp(tx, this.sample(sx, sz), this.sample(sx + 1, sz));
		double bottom = Mth.lerp(tx, this.sample(sx, sz + 1), this.sample(sx + 1, sz + 1));
		return (float) Mth.lerp(tz, top, bottom);
	}

	private static double smooth(final double t) {
		return t * t * (3.0 - 2.0 * t);
	}

	/** How calm the ground is kept at a column, 0 to 1: towns and landmarks stand on calm ground. */
	public float calm(final double x, final double z) {
		double fx = (x - this.origin) / this.step;
		double fz = (z - this.origin) / this.step;
		int sx = Mth.floor(fx);
		int sz = Mth.floor(fz);
		double tx = fx - sx;
		double tz = fz - sz;
		double top = Mth.lerp(tx, this.calmAt(sx, sz), this.calmAt(sx + 1, sz));
		double bottom = Mth.lerp(tx, this.calmAt(sx, sz + 1), this.calmAt(sx + 1, sz + 1));
		return (float) Mth.lerp(tz, top, bottom);
	}

	private float calmAt(final int sx, final int sz) {
		if (sx < 0 || sz < 0 || sx >= this.size || sz >= this.size) {
			return 0.0F;
		}
		return (this.calm[sz * this.size + sx] & 0xFF) / 255.0F;
	}

	/** The region a column lies in: the drawn one nearest. */
	public int region(final double x, final double z) {
		int sx = Mth.floor((x - this.origin) / this.step + 0.5);
		int sz = Mth.floor((z - this.origin) / this.step + 0.5);
		if (sx < 0 || sz < 0 || sx >= this.size || sz >= this.size) {
			return SEA;
		}
		return this.region[sz * this.size + sx] & 0xFF;
	}
}
