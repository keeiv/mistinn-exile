package life.shizune.mistinn.grid;

/** The places a hunter keeps things, besides the hotbar. */
public enum Area {
	/** Four single-cell pockets. */
	POCKETS,
	/** The equipped backpack's grid; its contents live inside the backpack item. */
	BACKPACK,
	/** 安全箱: kept through death. */
	SAFE;

	public static Area byId(final int id) {
		Area[] values = values();
		return id >= 0 && id < values.length ? values[id] : POCKETS;
	}
}
