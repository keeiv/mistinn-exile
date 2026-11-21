package life.shizune.mistinn.boss;

/** What a hunter did once a tell began: which way they rolled, whether they backed off, hit back, or took it. */
public enum Response {
	ROLL_LEFT,
	ROLL_RIGHT,
	ROLL_BACK,
	BACK_OFF,
	COUNTER,
	STAY;

	public boolean roll() {
		return this == ROLL_LEFT || this == ROLL_RIGHT || this == ROLL_BACK;
	}
}
