package life.shizune.mistinn.client.grid;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * 格子圖: art for backpacks and chest rigs whose footprint is not square (a 2x3 rig, a 3x4 pack),
 * drawn over every cell they take so their shape shows in the grid. A sprite lives at
 * {@code textures/gui/grid/<item>.png}, eight texels per cell of the upright footprint, so it
 * shows at the same texel size as an icon on a two-cell square. Every other item keeps its square
 * icon: stretching a chestplate or a hammer to its footprint distorts it.
 */
public final class GridSprites {
	/** Pixels of sprite texture per grid cell. */
	public static final int TEXELS_PER_CELL = 8;
	private static final Map<Item, Optional<Identifier>> FOUND = new IdentityHashMap<>();

	private GridSprites() {
	}

	/** The pack's or rig's grid sprite, or null for any other item or one without a sprite. Looked up once per item. */
	public static @Nullable Identifier of(final Item item) {
		if (!MistinnItems.BACKPACKS.contains(item) && !MistinnItems.RIGS.contains(item)) {
			return null;
		}
		return FOUND.computeIfAbsent(item, i -> {
			Identifier key = BuiltInRegistries.ITEM.getKey(i);
			Identifier texture = key.withPath(path -> "textures/gui/grid/" + path + ".png");
			return Minecraft.getInstance().getResourceManager().getResource(texture).map(resource -> texture);
		}).orElse(null);
	}
}
