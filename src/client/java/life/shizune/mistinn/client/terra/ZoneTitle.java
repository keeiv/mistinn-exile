package life.shizune.mistinn.client.terra;

import java.util.Map;
import life.shizune.mistinn.rift.RiftShaper;
import life.shizune.mistinn.terra.TerraShaper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * The name of the land, shown as the hunter walks into it: the Terra highland's three rings, a sky
 * rift and the scar one leaves. Only on the way in, and not again for the same place until the
 * hunter has been out of it a while.
 */
public final class ZoneTitle {
	/** What each place is called: the title, and the subtitle under it (or null). */
	private record Name(String title, @Nullable String subtitle) {
	}

	private static final Map<ResourceKey<Biome>, Name> NAMES = Map.of(
		TerraShaper.VEINS, new Name("zone.mistinn.terra", "biome.mistinn.terra_veins"),
		TerraShaper.TERRACES, new Name("zone.mistinn.terra", "biome.mistinn.terra_terraces"),
		TerraShaper.CROWN, new Name("zone.mistinn.terra", "biome.mistinn.terra_crown"),
		RiftShaper.OPEN, new Name("biome.mistinn.rift_open", null),
		RiftShaper.SCAR, new Name("biome.mistinn.rift_scar", null));
	/** How long outside a place before walking back in shows its name again. */
	private static final int AWAY = 200;

	private static @Nullable ResourceKey<Biome> shown;
	private static int away;

	private ZoneTitle() {
	}

	public static void tick(final Minecraft mc) {
		if (mc.player == null || mc.level == null || mc.player.tickCount % 10 != 0) {
			return;
		}
		Holder<Biome> biome = mc.level.getBiome(mc.player.blockPosition());
		ResourceKey<Biome> here = biome.unwrapKey().filter(NAMES::containsKey).orElse(null);
		if (here == null) {
			away += 10;
			if (away >= AWAY) {
				shown = null;
			}
			return;
		}
		away = 0;
		if (here.equals(shown)) {
			return;
		}
		shown = here;
		Name name = NAMES.get(here);
		mc.gui.hud.setTimes(15, 60, 25);
		mc.gui.hud.setSubtitle(name.subtitle() == null ? Component.empty() : Component.translatable(name.subtitle()));
		mc.gui.hud.setTitle(Component.translatable(name.title()));
	}

	/** The place whose name was last shown, for tests. */
	public static @Nullable ResourceKey<Biome> shown() {
		return shown;
	}
}
