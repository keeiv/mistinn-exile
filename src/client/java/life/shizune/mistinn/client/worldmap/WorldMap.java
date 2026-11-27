package life.shizune.mistinn.client.worldmap;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What the world map shows and where, in pixels of its picture (640 by 1360): the layers of the
 * world from the End down to the Nether, and the places on it.
 * <p>
 * Rust Anchor Station stands on a sea that has nothing under it but water; a hunter dropped from
 * its crane sinks, goes weightless and falls out of the bottom of the water into the sky over the
 * misty coast. So the station and the coast are one above the other, the water between them.
 * <p>
 * On the coast the places stand as the game builds them in a story world (see StoryService): with
 * the drop station at (0, 0), +x along the coast toward the fog-bell station and +y inland, a
 * place at (bx, by) blocks is drawn at x = 120 + (bx + 80) * 0.95, y = 790 + by * 0.62.
 */
public final class WorldMap {
	public static final int W = 640;
	public static final int H = 1360;
	/** Where the view stands when the map opens: on the station, before it sinks to the coast. */
	public static final float OPEN_AT = 262.0F;
	/** Where the water ends and the sky over the coast begins. */
	public static final float UNDERSIDE = 600.0F;

	/** What a place on the map is. */
	public enum Kind {
		/** A place the story leads to. */
		STOP,
		/** A building the world scatters, or a place of the vanilla dimensions. */
		LANDMARK
	}

	/** A layer of the world, top to bottom. */
	public record Band(String id, int y0, int y1) {
		public String key() {
			return "worldmap.mistinn.band." + this.id;
		}

		public int middle() {
			return (this.y0 + this.y1) / 2;
		}
	}

	/**
	 * A place: its marker stands at (x, y), just above what is drawn, which lies in (x0, y0) to (x1, y1); it
	 * stands on the ground at (gx, gy), the point the formula above gives for where the game builds it.
	 * A stop belongs to a chapter (-1 for the training at Rust Anchor) and opens on the story cards
	 * listed; it stays in the fog until {@code revealedBy} has been shown on this computer (null: never
	 * hidden). A part of a stop (the salt road's later cairns) opens the stop it is part of ({@code of})
	 * and carries no name of its own.
	 */
	public record Place(String id, String of, Kind kind, int x, int y, int gx, int gy, int x0, int y0, int x1, int y1, int chapter, List<String> cards,
		@Nullable String revealedBy, boolean label) {
		public String key() {
			return "worldmap.mistinn.place." + this.id;
		}

		public String about(final int line) {
			return "worldmap.mistinn.about." + this.id + "." + line;
		}
	}

	/** A point on the way the story walks, belonging to a place. */
	public record Point(String place, int x, int y) {
	}

	public static final List<Band> BANDS = List.of(
		new Band("the_end", 0, 330),
		new Band("rust_anchor", 330, 650),
		new Band("overworld", 650, 1045),
		new Band("nether", 1045, H));

	private static Place stop(final String id, final int[] mark, final int[] ground, final int[] box, final int chapter, final List<String> cards,
		final @Nullable String revealedBy) {
		return new Place(id, id, Kind.STOP, mark[0], mark[1], ground[0], ground[1], box[0], box[1], box[2], box[3], chapter, cards, revealedBy, true);
	}

	private static Place part(final String id, final String of, final int[] mark, final int[] ground, final int[] box, final int chapter,
		final String revealedBy) {
		return new Place(id, of, Kind.STOP, mark[0], mark[1], ground[0], ground[1], box[0], box[1], box[2], box[3], chapter, List.of(), revealedBy, false);
	}

	private static Place landmark(final String id, final int[] mark, final int[] ground, final int[] box) {
		return landmark(id, mark, ground, box, List.of());
	}

	/** A landmark whose strip opens on story cards (those seen on this computer), as a stop's does. */
	private static Place landmark(final String id, final int[] mark, final int[] ground, final int[] box, final List<String> cards) {
		return new Place(id, id, Kind.LANDMARK, mark[0], mark[1], ground[0], ground[1], box[0], box[1], box[2], box[3], -2, cards, null, true);
	}

	private static int[] at(final int x, final int y) {
		return new int[] {x, y};
	}

	private static int[] box(final int x0, final int y0, final int x1, final int y1) {
		return new int[] {x0, y0, x1, y1};
	}

	/** The story's stops, in the order its cards reach them; a chapter's last card shows at the stop where it ends. */
	public static final List<Place> STOPS = List.of(
		stop("rust_anchor_station", at(488, 376), at(470, 461), box(387, 345, 536, 470), -1, List.of(), null),
		stop("spawn_station", at(72, 845), at(72, 864), box(57, 848, 87, 866), 0, List.of("drop"), "drop"),
		stop("drop_station", at(196, 768), at(196, 790), box(179, 771, 214, 792), 0, List.of("unanswered"), "drop"),
		stop("lighthouse", at(120, 702), at(120, 762), box(109, 705, 131, 763), 1, List.of("lighthouse"), "lighthouse"),
		stop("salt_road", at(297, 768), at(297, 777), box(293, 771, 302, 779), 2, List.of("salt_road"), "salt_road"),
		stop("bell_station", at(533, 712), at(533, 747), box(518, 715, 549, 762), 3, List.of("bell_station", "answer"), "bell_station"),
		stop("pod", at(419, 803), at(419, 822), box(410, 806, 428, 824), 4, List.of(), "answer"),
		stop("salt_works", at(207, 900), at(207, 951), box(170, 903, 248, 953), 4, List.of("salt_works", "stopped"), "salt_works"),
		stop("mist_inn", at(305, 858), at(305, 892), box(285, 861, 325, 893), 5, List.of("the_call"), "the_call"));

	/** Every place on the map: the stops, the salt road's later cairns, and the landmarks. */
	public static final List<Place> PLACES;

	static {
		List<Place> places = new ArrayList<>(STOPS);
		places.add(part("cairn_2", "salt_road", at(382, 757), at(382, 766), box(378, 760, 387, 768), 2, "salt_road"));
		places.add(part("cairn_3", "salt_road", at(466, 746), at(466, 755), box(462, 749, 471, 757), 2, "salt_road"));
		places.add(landmark("anchor_wreck", at(600, 780), at(604, 796), box(588, 783, 613, 799)));
		places.add(landmark("watchpost", at(522, 895), at(522, 928), box(514, 898, 530, 930)));
		places.add(landmark("ruined_station", at(404, 961), at(404, 974), box(391, 964, 417, 977)));
		// far inland, 1200 to 3200 blocks from the coast: the crystal highland, and the tillers' way's cards once seen
		places.add(landmark("terra", at(590, 906), at(588, 995), box(536, 908, 639, 1000), List.of("path_fields", "path_record", "path_sea")));
		places.add(landmark("end_island", at(428, 140), at(315, 171), box(199, 36, 425, 198)));
		places.add(landmark("nether_portal", at(529, 1229), at(529, 1273), box(514, 1232, 545, 1274)));
		places.add(landmark("nether_fortress", at(128, 1132), at(275, 1214), box(115, 1135, 431, 1227)));
		PLACES = List.copyOf(places);
	}

	/**
	 * The way the story walks: from the crane's tip down through the water to where the hunter wakes,
	 * to the real drop station, out to the lighthouse and back, along the salt road to the fog-bell
	 * station, to the pod that answered it, inland to the salt works and on to the Mist Inn.
	 */
	public static final List<Point> ROUTE = List.of(
		new Point("rust_anchor_station", 389, 388),
		new Point("spawn_station", 72, 845),
		new Point("drop_station", 196, 768),
		new Point("lighthouse", 120, 702),
		new Point("drop_station", 196, 768),
		new Point("salt_road", 297, 768),
		new Point("cairn_2", 382, 757),
		new Point("cairn_3", 466, 746),
		new Point("bell_station", 533, 712),
		new Point("pod", 419, 803),
		new Point("salt_works", 207, 900),
		new Point("mist_inn", 305, 858));

	private WorldMap() {
	}

	public static Place place(final String id) {
		for (Place place : PLACES) {
			if (place.id().equals(id)) {
				return place;
			}
		}
		throw new IllegalArgumentException("No place on the world map: " + id);
	}

	public static Band band(final String id) {
		for (Band band : BANDS) {
			if (band.id().equals(id)) {
				return band;
			}
		}
		throw new IllegalArgumentException("No layer on the world map: " + id);
	}

	/** The layer a place stands in. */
	public static Band bandOf(final Place place) {
		for (Band band : BANDS) {
			if (place.y() >= band.y0() && place.y() < band.y1()) {
				return band;
			}
		}
		return BANDS.get(BANDS.size() - 1);
	}
}
