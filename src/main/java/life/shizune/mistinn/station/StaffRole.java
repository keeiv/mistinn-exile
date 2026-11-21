package life.shizune.mistinn.station;

import io.netty.buffer.ByteBuf;
import life.shizune.mistinn.Mistinn;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Who a member of Rust Anchor's staff is: the skin they wear and the lines they speak. */
public enum StaffRole {
	ROOMMATE("roommate", "hunter_3411", false),
	QUARTERMASTER("quartermaster", "quartermaster", false),
	COOK("cook", "cook", false),
	SMITH("smith", "smith", false),
	INSTRUCTOR("instructor", "instructor", false),
	SCOUT("scout", "scout", true),
	DOCTOR("doctor", "doctor", true),
	ARCHIVIST("archivist", "archivist", true),
	BELL_KEEPER("bell_keeper", "bell_keeper", true),
	RADIO_OPERATOR("radio_operator", "radio_operator", true),
	CRANE_OPERATOR("crane_operator", "crane_operator", false),
	DOCK_WORKER_1("dock_worker_1", "dock_worker_1", false),
	DOCK_WORKER_2("dock_worker_2", "dock_worker_2", false),
	DOCK_WORKER_3("dock_worker_3", "dock_worker_3", false),
	DOCK_WORKER_4("dock_worker_4", "dock_worker_4", false);

	public static final StreamCodec<ByteBuf, StaffRole> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(StaffRole::byIndex, StaffRole::ordinal);

	public final String id;
	public final boolean slim;
	private final Identifier texture;

	StaffRole(final String id, final String skin, final boolean slim) {
		this.id = id;
		this.slim = slim;
		this.texture = Mistinn.id("textures/entity/staff/" + skin + ".png");
	}

	/** The 64 by 64 player skin this member wears. */
	public Identifier texture() {
		return this.texture;
	}

	/** The translation prefix of this member's name and lines. */
	public String key() {
		return "staff.mistinn." + this.id;
	}

	public static StaffRole byIndex(final int index) {
		StaffRole[] roles = values();
		return index >= 0 && index < roles.length ? roles[index] : DOCK_WORKER_1;
	}

	public static StaffRole byId(final String id) {
		for (StaffRole role : values()) {
			if (role.id.equals(id)) {
				return role;
			}
		}
		return DOCK_WORKER_1;
	}
}
