package life.shizune.mistinn.client.path;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.path.PathService;
import life.shizune.mistinn.terra.Tiller;
import net.minecraft.client.Minecraft;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/**
 * On the path of longing everyone is one of the crystal tillers, and wears a tiller's clothes:
 * the field hands' two looks, turn about. Each time the way passes to the next of the settlement
 * the look turns too, so the one walking it is plainly someone else.
 */
public final class PathSkins {
	/** The looks a walker may have, in turn. */
	public static final Tiller.Role[] LOOKS = {Tiller.Role.TILLER_1, Tiller.Role.TILLER_2};
	private static final Map<Tiller.Role, PlayerSkin> SKINS = new EnumMap<>(Tiller.Role.class);
	/** The twelve, stitched into one: the thirteenth's skin. */
	private static final Identifier STITCHED = Mistinn.id("textures/entity/terra/stitched.png");
	private static @Nullable PlayerSkin stitched;
	/** While set, a hunter's own skin is asked for, as they chose it (the one who comes for the thirteenth wears it). */
	public static boolean bypass;

	private PathSkins() {
	}

	/** The look of a hunter on this world's way, or nothing when the world is not walking it. */
	public static @Nullable PlayerSkin skin(final UUID id) {
		if (bypass) {
			return null;
		}
		Minecraft mc = Minecraft.getInstance();
		net.minecraft.world.entity.player.Player who = mc.level == null ? null : mc.level.getPlayerByUUID(id);
		if (who != null && life.shizune.mistinn.path.Thirteenth.stitched(who)) {
			if (stitched == null) {
				stitched = PlayerSkin.insecure(new ClientAsset.ResourceTexture(STITCHED, STITCHED), null, null, PlayerModelType.WIDE);
			}
			return stitched;
		}
		PathService.View view = mc.player == null ? null : mc.player.getAttached(PathService.VIEW);
		if (view == null) {
			return null;
		}
		return SKINS.computeIfAbsent(look(view.walker(), id), PathSkins::of);
	}

	/** Whose clothes a hunter wears: turned with each new walker, and apart for each hunter. */
	public static Tiller.Role look(final int walker, final UUID id) {
		return LOOKS[Math.floorMod(walker + (int) (id.getLeastSignificantBits() & 1L), LOOKS.length)];
	}

	private static PlayerSkin of(final Tiller.Role role) {
		Identifier texture = Mistinn.id("textures/entity/terra/" + role.id + ".png");
		return PlayerSkin.insecure(new ClientAsset.ResourceTexture(texture, texture), null, null, role.slim ? PlayerModelType.SLIM : PlayerModelType.WIDE);
	}
}
