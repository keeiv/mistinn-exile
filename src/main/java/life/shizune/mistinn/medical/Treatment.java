package life.shizune.mistinn.medical;

/**
 * 處置: the kinds of timed use, each with its own animation ({@code anim/use/<id>.json}) and the
 * share of walking speed it takes away while it runs.
 */
public enum Treatment {
	/** 修護甲: working on the worn body armour, hands at the chest. */
	ARMOR_REPAIR("armor_repair", 0.4),
	/** 修頭盔: working on the worn helmet, hands at the head. */
	HELMET_REPAIR("helmet_repair", 0.4),
	/** 注射: the injector driven into the thigh. */
	INJECT("inject", 0.3),
	/** 手術: stitching and binding a destroyed part. */
	SURGERY("surgery", 0.6),
	/** 服藥: pills to the mouth. */
	PILLS("pills", 0.2);

	public final String id;
	/** Walking speed lost while it runs: 0.4 is 40% slower. */
	public final double slow;

	Treatment(final String id, final double slow) {
		this.id = id;
		this.slow = slow;
	}

	public static Treatment byId(final int ordinal) {
		Treatment[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : PILLS;
	}
}
