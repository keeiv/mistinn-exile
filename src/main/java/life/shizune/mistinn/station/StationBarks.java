package life.shizune.mistinn.station;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the staff say in passing: when a hunter comes near, a member calls out a line that floats
 * over their head for a few seconds, then goes quiet for a while. The line shows where their name
 * would, so it faces the camera and needs nothing drawn of its own.
 */
public final class StationBarks {
	/** How near the hunter must come. */
	public static final double REACH = 7.0;
	/** How long a line stays up, in ticks. */
	public static final int SHOWN = 80;
	private static final int QUIET = 400;
	private static final int QUIET_SPREAD = 300;
	private static final Map<StaffRole, Integer> COUNTS = new EnumMap<>(StaffRole.class);
	/** Per member: the tick their line comes down, the tick they may speak again, the line they are on. */
	private static final Map<Integer, Long> DOWN = new HashMap<>();
	private static final Map<Integer, Long> NEXT = new HashMap<>();
	private static final Map<Integer, Integer> LINE = new HashMap<>();

	static {
		for (StaffRole role : StaffRole.values()) {
			COUNTS.put(role, 2);
		}
		for (StaffRole role : new StaffRole[] {StaffRole.SMITH, StaffRole.SCOUT, StaffRole.DOCTOR, StaffRole.ARCHIVIST,
			StaffRole.BELL_KEEPER, StaffRole.RADIO_OPERATOR, StaffRole.CRANE_OPERATOR}) {
			COUNTS.put(role, 1);
		}
	}

	private StationBarks() {
	}

	/** How many passing lines this member has. */
	public static int count(final StaffRole role) {
		return COUNTS.get(role);
	}

	public static String key(final StaffRole role, final int line) {
		return role.key() + ".bark." + line;
	}

	/** Called every few ticks for the hunter: members near them speak up, lines that have been up long enough come down. */
	public static void tick(final ServerLevel level, final ServerPlayer hunter) {
		long now = level.getGameTime();
		for (StationHand hand : level.getEntitiesOfClass(StationHand.class, hunter.getBoundingBox().inflate(64.0))) {
			int id = hand.getId();
			Long down = DOWN.get(id);
			if (down != null && now >= down) {
				DOWN.remove(id);
				hand.setCustomName(Component.translatable(hand.role().key()));
				hand.setCustomNameVisible(false);
			}
			if (down == null && hand.distanceTo(hunter) <= REACH && now >= NEXT.getOrDefault(id, 0L)) {
				int line = LINE.merge(id, 1, Integer::sum) - 1;
				hand.setCustomName(Component.translatable(key(hand.role(), line % count(hand.role()))));
				hand.setCustomNameVisible(true);
				DOWN.put(id, now + SHOWN);
				NEXT.put(id, now + SHOWN + QUIET + level.getRandom().nextInt(QUIET_SPREAD));
			}
		}
	}

	/** Test and world-close hook. */
	public static void forgetAll() {
		DOWN.clear();
		NEXT.clear();
		LINE.clear();
	}
}
