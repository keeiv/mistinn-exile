package life.shizune.mistinn.client.epic;

import java.util.Set;
import life.shizune.mistinn.client.story.StoryCardScreen;
import life.shizune.mistinn.keeper.Keeper;
import life.shizune.mistinn.path.RustApostle;
import life.shizune.mistinn.registry.MistinnSounds;
import life.shizune.mistinn.terra.CrystalElder;
import life.shizune.mistinn.udasru.UdasruPlaces;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The epic's music. Under its cards, the land's swell or its ending; facing the Helm, the duel; with a great beast
 * close and roused, the hunt; within the capital's walls, the capital; and out in the land, the field, always.
 */
public final class EpicMusic {
	private static final Music FIELD = loop(MistinnSounds.MUSIC_EPIC_FIELD, false);
	private static final Music CAPITAL = loop(MistinnSounds.MUSIC_EPIC_CAPITAL, false);
	private static final Music HUNT = loop(MistinnSounds.MUSIC_EPIC_HUNT, true);
	private static final Music HELM = loop(MistinnSounds.MUSIC_EPIC_HELM, true);
	private static final Music REVEAL = loop(MistinnSounds.MUSIC_EPIC_REVEAL, true);
	private static final Music END = loop(MistinnSounds.MUSIC_EPIC_END, true);
	/** The cards the land's swell plays under, and the ones the ending's music does. */
	private static final Set<String> SWELL = Set.of("epic_wake", "epic_land", "epic_capital", "epic_bells", "epic_crown");
	private static final Set<String> ENDING = Set.of("epic_name", "epic_home");
	/** How near a roused great beast, or the Helm, brings their music. */
	private static final double HUNT_RANGE = 56.0;

	private EpicMusic() {
	}

	private static Music loop(final SoundEvent event, final boolean cutIn) {
		return new Music(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event), 0, 0, cutIn);
	}

	/** The music the epic wants now, or null outside it. */
	public static @Nullable Music current() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || !EpicClient.inEpic()) {
			return null;
		}
		if (mc.gui.screen() instanceof StoryCardScreen card) {
			if (ENDING.contains(card.cardId())) {
				return END;
			}
			if ("epic_helm".equals(card.cardId())) {
				return HELM;
			}
			if (SWELL.contains(card.cardId())) {
				return REVEAL;
			}
		}
		if (mc.gui.screen() instanceof EpicNameScreen) {
			return END;
		}
		if (!player.isAlive() || mc.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
			return null;
		}
		var near = player.getBoundingBox().inflate(HUNT_RANGE);
		for (RustApostle apostle : mc.level.getEntitiesOfClass(RustApostle.class, near)) {
			if (apostle.isAlive() && apostle.helm()) {
				return HELM;
			}
		}
		for (LivingEntity beast : mc.level.getEntitiesOfClass(LivingEntity.class, near, e -> e instanceof Keeper || e instanceof CrystalElder)) {
			if (beast.isAlive() && (beast.getHealth() < beast.getMaxHealth() || beast.distanceTo(player) < 28.0)) {
				return HUNT;
			}
		}
		if (Math.hypot(player.getX() - UdasruPlaces.CAPITAL.getX(), player.getZ() - UdasruPlaces.CAPITAL.getZ()) < UdasruPlaces.CAPITAL_RADIUS) {
			return CAPITAL;
		}
		return FIELD;
	}
}
