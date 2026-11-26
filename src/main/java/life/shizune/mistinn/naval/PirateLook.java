package life.shizune.mistinn.naval;

import life.shizune.mistinn.Mistinn;
import net.minecraft.resources.Identifier;

/** Who a pirate looks like: a skin in the player model, its arms, and whose voice it has. */
public enum PirateLook {
	CAPTAIN_1("captain_1", false, false),
	CAPTAIN_2("captain_2", true, true),
	GUNNER("gunner", false, false),
	CUTLASS_1("cutlass_1", false, false),
	CUTLASS_2("cutlass_2", false, false),
	DECKHAND("deckhand", true, false),
	MUSKETEER("musketeer", true, false);

	public final String id;
	public final boolean slim;
	public final boolean female;

	PirateLook(final String id, final boolean slim, final boolean female) {
		this.id = id;
		this.slim = slim;
		this.female = female;
	}

	public Identifier texture() {
		return Mistinn.id("textures/entity/pirate/" + this.id + ".png");
	}

	public static PirateLook byIndex(final int index) {
		PirateLook[] values = values();
		return values[Math.floorMod(index, values.length)];
	}

	public static PirateLook byId(final String id) {
		for (PirateLook look : values()) {
			if (look.id.equals(id)) {
				return look;
			}
		}
		return CUTLASS_1;
	}
}
