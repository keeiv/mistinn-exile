package life.shizune.mistinn.client.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import life.shizune.mistinn.Mistinn;
import net.minecraft.util.Mth;

/**
 * 動作片段: one animation as keyframes, read from {@code assets/mistinn/anim/<name>.json}.
 * A clip has shared key times (0 to 1) and, for each segment between two keys, the easing that
 * carries the values across it; every track lists one value per key. Tracks live in groups
 * ({@code "third_person"}, {@code "root"}, ...) and are read as {@code group.track}; a track a
 * clip does not have reads as 0. Other numbers and flags sit in the clip's top level.
 *
 * <p>Angles in clips are degrees; the code that applies a clip converts them.
 */
public final class Clip {
	/** 緩動: how a segment moves from one key to the next. */
	public enum Ease {
		LINEAR,
		IN_QUAD,
		OUT_QUAD,
		IN_OUT_QUAD,
		IN_CUBIC,
		OUT_CUBIC,
		IN_OUT_CUBIC,
		IN_QUART,
		OUT_QUART,
		IN_OUT_SINE,
		OUT_BACK,
		/** Holds the first value until the next key: a cut. */
		STEP;

		public float apply(final float t) {
			return switch (this) {
				case LINEAR -> t;
				case IN_QUAD -> t * t;
				case OUT_QUAD -> 1.0F - (1.0F - t) * (1.0F - t);
				case IN_OUT_QUAD -> t < 0.5F ? 2.0F * t * t : 1.0F - (-2.0F * t + 2.0F) * (-2.0F * t + 2.0F) / 2.0F;
				case IN_CUBIC -> t * t * t;
				case OUT_CUBIC -> 1.0F - cube(1.0F - t);
				case IN_OUT_CUBIC -> t < 0.5F ? 4.0F * t * t * t : 1.0F - cube(-2.0F * t + 2.0F) / 2.0F;
				case IN_QUART -> t * t * t * t;
				case OUT_QUART -> 1.0F - cube(1.0F - t) * (1.0F - t);
				case IN_OUT_SINE -> -(Mth.cos(Mth.PI * t) - 1.0F) / 2.0F;
				case OUT_BACK -> 1.0F + 2.70158F * cube(t - 1.0F) + 1.70158F * (t - 1.0F) * (t - 1.0F);
				case STEP -> t < 1.0F ? 0.0F : 1.0F;
			};
		}

		private static float cube(final float v) {
			return v * v * v;
		}
	}

	/** The clip used when a file is missing or broken: everything at rest. */
	private static final Clip EMPTY = new Clip(new float[] {0.0F, 1.0F}, new Ease[] {Ease.LINEAR}, Map.of(), new JsonObject());
	private static final Map<String, Clip> CACHE = new ConcurrentHashMap<>();

	private final float[] keys;
	private final Ease[] eases;
	private final Map<String, float[]> tracks;
	private final JsonObject meta;

	private Clip(final float[] keys, final Ease[] eases, final Map<String, float[]> tracks, final JsonObject meta) {
		this.keys = keys;
		this.eases = eases;
		this.tracks = tracks;
		this.meta = meta;
	}

	/** The clip at {@code assets/mistinn/anim/<name>.json}, read once. */
	public static Clip get(final String name) {
		return CACHE.computeIfAbsent(name, Clip::load);
	}

	/** The clip of that name, or null when the mod ships none (for optional per-creature variants). */
	public static @org.jspecify.annotations.Nullable Clip find(final String name) {
		if (Clip.class.getResource("/assets/mistinn/anim/" + name + ".json") == null) {
			return null;
		}
		return get(name);
	}

	/**
	 * How much a clip that runs in ticks owns the pose at {@code tick}: it takes the pose over during its first
	 * {@code blend_in} ticks and, when {@code handBack}, returns it during its last {@code blend_out}.
	 */
	public float owns(final float tick, final boolean handBack) {
		float length = this.number("length", 1.0F);
		float in = smooth(tick / Math.max(0.001F, this.number("blend_in", 3.0F)));
		return handBack ? in * smooth((length - tick) / Math.max(0.001F, this.number("blend_out", 4.0F))) : in;
	}

	private static Clip load(final String name) {
		String path = "/assets/mistinn/anim/" + name + ".json";
		try (InputStream in = Clip.class.getResourceAsStream(path)) {
			if (in == null) {
				Mistinn.LOGGER.error("Missing animation clip {}", path);
				return EMPTY;
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				return parse(JsonParser.parseReader(reader).getAsJsonObject(), path);
			}
		} catch (IOException | RuntimeException e) {
			Mistinn.LOGGER.error("Broken animation clip {}", path, e);
			return EMPTY;
		}
	}

	private static Clip parse(final JsonObject json, final String path) {
		float[] keys = floats(json.getAsJsonArray("keys"));
		JsonArray easeJson = json.getAsJsonArray("eases");
		if (keys.length < 2 || easeJson.size() != keys.length - 1) {
			throw new IllegalArgumentException(path + ": needs at least two keys and one ease per segment");
		}
		Ease[] eases = new Ease[easeJson.size()];
		for (int i = 0; i < eases.length; i++) {
			eases[i] = Ease.valueOf(easeJson.get(i).getAsString().toUpperCase(Locale.ROOT));
		}
		Map<String, float[]> tracks = new HashMap<>();
		JsonObject meta = new JsonObject();
		for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
			String key = entry.getKey();
			if (key.equals("keys") || key.equals("eases") || key.startsWith("_")) {
				continue;
			}
			if (entry.getValue().isJsonObject()) {
				for (Map.Entry<String, JsonElement> track : entry.getValue().getAsJsonObject().entrySet()) {
					float[] values = floats(track.getValue().getAsJsonArray());
					if (values.length != keys.length) {
						throw new IllegalArgumentException(path + ": track " + key + "." + track.getKey() + " needs one value per key");
					}
					tracks.put(key + "." + track.getKey(), values);
				}
			} else {
				meta.add(key, entry.getValue());
			}
		}
		return new Clip(keys, eases, tracks, meta);
	}

	private static float[] floats(final JsonArray array) {
		float[] out = new float[array.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = array.get(i).getAsFloat();
		}
		return out;
	}

	/** One track at progress {@code p} (clamped to 0..1); 0 for a track the clip lacks. */
	public float at(final String track, final float p) {
		float[] values = this.tracks.get(track);
		if (values == null) {
			return 0.0F;
		}
		float c = Mth.clamp(p, 0.0F, 1.0F);
		for (int i = 0; i < this.eases.length; i++) {
			if (c <= this.keys[i + 1]) {
				float span = this.keys[i + 1] - this.keys[i];
				float t = span <= 0.0F ? 1.0F : (c - this.keys[i]) / span;
				return Mth.lerp(this.eases[i].apply(t), values[i], values[i + 1]);
			}
		}
		return values[values.length - 1];
	}

	/** The tracks of one group, by their names inside the group (without the {@code group.} prefix). */
	public java.util.Set<String> tracks(final String group) {
		String prefix = group + ".";
		java.util.Set<String> names = new java.util.TreeSet<>();
		for (String track : this.tracks.keySet()) {
			if (track.startsWith(prefix)) {
				names.add(track.substring(prefix.length()));
			}
		}
		return names;
	}

	/** {@link #at} in radians, for clips that keep angles in degrees. */
	public float rad(final String track, final float p) {
		return this.at(track, p) * Mth.DEG_TO_RAD;
	}

	/** The time of key {@code i}, as progress. */
	public float key(final int i) {
		return this.keys[i];
	}

	public int keyCount() {
		return this.keys.length;
	}

	public boolean has(final String track) {
		return this.tracks.containsKey(track);
	}

	/**
	 * How much the clip owns the pose at {@code p}: it eases in over the first segment and out
	 * over the last, so it takes over from (and hands back to) whatever pose came before.
	 */
	public float weight(final float p) {
		float in = smooth(p / this.keys[1]);
		float out = smooth((1.0F - p) / (1.0F - this.keys[this.keys.length - 2]));
		return in * out;
	}

	/** {@link #weight} without the hand-back: for clips that end in a held pose. */
	public float weightIn(final float p) {
		return smooth(p / this.keys[1]);
	}

	public float number(final String name, final float fallback) {
		JsonElement value = this.meta.get(name);
		return value == null ? fallback : value.getAsFloat();
	}

	public boolean flag(final String name) {
		JsonElement value = this.meta.get(name);
		return value != null && value.getAsBoolean();
	}

	private static float smooth(final float t) {
		float c = Mth.clamp(t, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}
}
