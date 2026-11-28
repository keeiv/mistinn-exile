package life.shizune.mistinn.client.epic;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.client.worldmap.ExploredMapScreen;
import life.shizune.mistinn.epic.EpicState;
import life.shizune.mistinn.epic.EpicStep;
import life.shizune.mistinn.epic.EpicTravel;
import life.shizune.mistinn.udasru.UdasruPlaces;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * Udasru on the hunter's map. Under what they have seen lies the land's own parchment map, shown only round the bell
 * towers they have rung (as a tower's bell is heard, its country is drawn); over it the towers (gold once rung, a click
 * carries the hunter to one) and the four great bells. Once the epic is written the parchment is the one with
 * 「禁區」 struck out.
 */
public final class EpicMap {
	private static final Identifier PARCHMENT = Mistinn.id("textures/gui/udasru/udasru_map.png");
	private static final Identifier PARCHMENT_AFTER = Mistinn.id("textures/gui/udasru/udasru_map_after.png");
	/** The parchment covers the drawn land: this many blocks across, from this corner. */
	private static final int SPAN = 4096;
	private static final int CORNER = -2048;
	/** The parchment is shown in cells this many blocks across. */
	private static final int CELL = 64;
	private static final int PICK = 7;

	private EpicMap() {
	}

	private static @Nullable EpicState state() {
		return EpicClient.state(Minecraft.getInstance());
	}

	/** The land's parchment, round every rung tower. */
	public static void base(final GuiGraphicsExtractor g, final ExploredMapScreen map) {
		EpicState state = state();
		if (state == null) {
			return;
		}
		Identifier texture = state.at() == EpicStep.ENDED ? PARCHMENT_AFTER : PARCHMENT;
		for (int cx = 0; cx < SPAN / CELL; cx++) {
			for (int cz = 0; cz < SPAN / CELL; cz++) {
				int x0 = CORNER + cx * CELL;
				int z0 = CORNER + cz * CELL;
				if (!shown(state, x0 + CELL / 2, z0 + CELL / 2)) {
					continue;
				}
				double[] from = map.toScreen(x0, z0);
				double[] to = map.toScreen(x0 + CELL, z0 + CELL);
				if (to[0] < 0 || to[1] < 0 || from[0] > map.width || from[1] > map.height) {
					continue;
				}
				float u0 = (float) cx * CELL / SPAN;
				float v0 = (float) cz * CELL / SPAN;
				g.blit(texture, (int) Math.floor(from[0]), (int) Math.floor(from[1]), (int) Math.ceil(to[0]), (int) Math.ceil(to[1]),
					u0, u0 + (float) CELL / SPAN, v0, v0 + (float) CELL / SPAN);
			}
		}
	}

	/** Whether the parchment shows at a place: near a tower that has rung. */
	private static boolean shown(final EpicState state, final int x, final int z) {
		for (UdasruPlaces.Tower tower : UdasruPlaces.Tower.values()) {
			if (state.rungTower(tower.bit()) && Math.hypot(x - tower.x, z - tower.z) < UdasruPlaces.Tower.SHOWS) {
				return true;
			}
		}
		return false;
	}

	/** The towers and the great bells. */
	public static void icons(final GuiGraphicsExtractor g, final ExploredMapScreen map) {
		EpicState state = state();
		if (state == null) {
			return;
		}
		for (UdasruPlaces.GreatBell bell : UdasruPlaces.GreatBell.values()) {
			if (!shown(state, bell.x, bell.z)) {
				continue;
			}
			double[] at = map.toScreen(bell.x + 0.5, bell.z + 0.5);
			boolean rung = (state.bells() & bell.bit()) != 0;
			drawBell(g, (int) at[0], (int) at[1], rung ? 0xFFE8C060 : (state.beasts() & bell.bit()) != 0 ? 0xFFB07A3C : 0xFFC0503A, 3);
		}
		for (UdasruPlaces.Tower tower : UdasruPlaces.Tower.values()) {
			double[] at = map.toScreen(tower.x + 0.5, tower.z + 0.5);
			boolean rung = state.rungTower(tower.bit());
			if (rung || shown(state, tower.x, tower.z) || life.shizune.mistinn.client.worldmap.ExploredMap.seen(tower.x, tower.z)) {
				drawBell(g, (int) at[0], (int) at[1], rung ? 0xFFF2D080 : 0xFF7A7468, 2);
			}
		}
	}

	private static void drawBell(final GuiGraphicsExtractor g, final int x, final int y, final int colour, final int size) {
		g.fill(x - size - 1, y - size, x + size + 2, y + size + 2, 0xFF1A140C);
		g.fill(x - size, y - size + 1, x + size + 1, y + size + 1, colour);
		g.fill(x - size - 1, y + size, x + size + 2, y + size + 1, colour);
		g.fill(x, y - size - 1, x + 1, y - size + 1, 0xFF1A140C);
	}

	/** The tower under the pointer, if any has been rung or seen. */
	private static UdasruPlaces.@Nullable Tower towerUnder(final ExploredMapScreen map, final double mx, final double my) {
		EpicState state = state();
		if (state == null) {
			return null;
		}
		for (UdasruPlaces.Tower tower : UdasruPlaces.Tower.values()) {
			double[] at = map.toScreen(tower.x + 0.5, tower.z + 0.5);
			if (Math.abs(mx - at[0]) <= PICK && Math.abs(my - at[1]) <= PICK && state.rungTower(tower.bit())) {
				return tower;
			}
		}
		return null;
	}

	/** A click on a rung tower: the bell carries the hunter there. True if the click was the map's. */
	public static boolean click(final ExploredMapScreen map, final double mx, final double my) {
		UdasruPlaces.Tower tower = towerUnder(map, mx, my);
		if (tower == null) {
			return false;
		}
		ClientPlayNetworking.send(new EpicTravel.TowerPayload(tower.ordinal()));
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BELL_BLOCK, 1.0F, 1.2F));
		map.onClose();
		return true;
	}

	/** What a tower under the pointer says, or null. */
	public static @Nullable List<Component> tooltip(final ExploredMapScreen map, final double mx, final double my) {
		UdasruPlaces.Tower tower = towerUnder(map, mx, my);
		if (tower == null) {
			return null;
		}
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("epic.mistinn.map.tower", Component.translatable("epic.mistinn.tower." + tower.id)));
		lines.add(Component.translatable("epic.mistinn.map.travel"));
		return lines;
	}
}
