package life.shizune.mistinn.world;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignText;

/**
 * The number plate a drop pod carries by its hatch: the station's name, the word for a pod, and
 * the number of the hunter it carries. The station's own plates are written in its gold; others
 * only look like them.
 */
public final class PodPlate {
	/** The station's own name, as Rust Anchor paints it. */
	public static final String STATION_KEY = "sign.mistinn.pod.station";
	/** The name as someone else painted it, from memory. */
	public static final String COPY_KEY = "sign.mistinn.pod.station_copy";
	public static final String KIND_KEY = "sign.mistinn.pod.kind";
	public static final String NUMBER_KEY = "sign.mistinn.pod.number";
	/** The hunter, and the one dropped before them. */
	public static final String HUNTER = "3412";
	public static final String ROOMMATE = "3411";

	private PodPlate() {
	}

	/** A plate's front: the station line, the pod line and the number, the last line left blank. */
	public static SignText text(final String stationKey, final String number, final DyeColor color, final boolean glowing) {
		List<Component> lines = List.of(
			Component.translatable(stationKey),
			Component.translatable(KIND_KEY),
			Component.translatable(NUMBER_KEY, number),
			Component.empty());
		return new SignText(lines, lines, color, glowing);
	}
}
