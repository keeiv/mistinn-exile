package life.shizune.mistinn.grid;

import java.util.ArrayList;
import java.util.List;

/** A storage made of one or more rectangular compartments (sections), each {@code w x h} cells. */
public record GridShape(List<Section> sections) {
	/** One compartment, in cells. */
	public record Section(int width, int height) {
	}

	/** A shape from pairs of sizes: {@code of(w0, h0, w1, h1, ...)}. */
	public static GridShape of(final int... sizes) {
		List<Section> sections = new ArrayList<>();
		for (int i = 0; i + 1 < sizes.length; i += 2) {
			sections.add(new Section(sizes[i], sizes[i + 1]));
		}
		return new GridShape(List.copyOf(sections));
	}

	/** Several separate compartments of the same size, such as four one-cell pockets. */
	public static GridShape repeat(final int count, final int width, final int height) {
		List<Section> sections = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			sections.add(new Section(width, height));
		}
		return new GridShape(List.copyOf(sections));
	}

	public int cells() {
		return this.sections.stream().mapToInt(s -> s.width() * s.height()).sum();
	}

	public static final GridShape EMPTY = new GridShape(List.of());
}
