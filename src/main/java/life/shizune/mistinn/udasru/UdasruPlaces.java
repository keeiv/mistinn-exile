package life.shizune.mistinn.udasru;

import net.minecraft.core.BlockPos;

/**
 * Where things stand in Udasru, as the map was drawn (the map script names the same places). Heights are left to the
 * ground where a place sits on it; where a place was given flat ground of its own, its height is the drawn one.
 */
public final class UdasruPlaces {
	/** The world's border: a square this wide round the middle of the land. */
	public static final int BORDER = 4000;
	/** South of this the fog lies: the forbidden zone, as the station called all the land. */
	public static final int FOG_WALL_Z = 1340;
	/** Past this a hunter walking north has come out of the fog. */
	public static final int OUT_OF_THE_FOG_Z = 1290;

	/** 霧宿, in the fog on the southern coast. */
	public static final BlockPos MIST_INN = new BlockPos(-200, 0, 1520);
	/** 鐘都: the middle of the capital, and the height its ground was levelled to. */
	public static final BlockPos CAPITAL = new BlockPos(0, 78, 0);
	public static final int CAPITAL_RADIUS = 118;
	/** 錨冠: the anchor's head out of the southern sea, and where its chain comes ashore. */
	public static final BlockPos ANCHOR_CROWN = new BlockPos(300, 0, 1890);
	public static final BlockPos CHAIN_SHORE = new BlockPos(300, 0, 1770);

	/** Towns, by the flat ground drawn for each. */
	public static final BlockPos SALT_TOWN = new BlockPos(-1250, 0, 80);
	public static final BlockPos BELL_PORT = new BlockPos(-1380, 0, 1300);
	public static final BlockPos WOODS_VILLAGE = new BlockPos(-1080, 0, -980);
	public static final BlockPos FROST_GATE = new BlockPos(0, 0, -1320);
	public static final BlockPos FORGE_TOWN = new BlockPos(1150, 0, -1050);

	/** The eight bell towers: ringing one shows its country on the map and opens it to travel. */
	public enum Tower {
		ANCHOR_HILLS("anchor_hills", 100, 1150),
		BELL_PLAINS("bell_plains", -520, 520),
		SALT_FLATS("salt_flats", -1300, -320),
		SALT_MARSH("salt_marsh", -1540, 880),
		OLD_WOODS("old_woods", -880, -1480),
		FROST_RIDGE("frost_ridge", -260, -1640),
		RUST_WASTES("rust_wastes", 880, -720),
		CRYSTAL_HIGHLAND("crystal_highland", 1080, 620);

		/** How far round a tower its ringing shows the land. */
		public static final int SHOWS = 620;

		public final String id;
		public final int x;
		public final int z;

		Tower(final String id, final int x, final int z) {
			this.id = id;
			this.x = x;
			this.z = z;
		}

		public int bit() {
			return 1 << this.ordinal();
		}
	}

	/** The four great bells, each kept by a great beast the rust has taken. */
	public enum GreatBell {
		SALT("salt", -1580, -150),
		WOODS("woods", -1460, -1130),
		RUST("rust", 1560, -1120),
		CRYSTAL("crystal", 1620, 460);

		public final String id;
		public final int x;
		public final int z;

		GreatBell(final String id, final int x, final int z) {
			this.id = id;
			this.x = x;
			this.z = z;
		}

		public int bit() {
			return 1 << this.ordinal();
		}
	}

	private UdasruPlaces() {
	}
}
