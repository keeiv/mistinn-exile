package life.shizune.mistinn.body;

import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * 出血與骨折: wounds a part can carry before it is destroyed.
 * <ul>
 * <li><b>Bleeding</b> comes from blows and shots. A bleeding part loses
 * {@link #BLEED_PER_SECOND} a second until it is destroyed. A tourniquet stops it, but a
 * tourniquet left on longer than {@link #TOURNIQUET_SAFE} starves the part, which then dies
 * at {@link #NECROSIS_PER_SECOND} a second. Surgery takes the tourniquet off.</li>
 * <li><b>A broken bone</b> (arms and legs only) comes from falls, explosions and crushing blows.
 * Until it is set, the limb works as if it were destroyed: a broken leg means no rolling, only
 * the dive; a broken arm weakens blows and work. A splint holds it for {@link #SPLINT_TICKS}.
 * Surgery sets the bone.</li>
 * </ul>
 * A destroyed part has nothing left to bleed or break: its wounds are gone with it.
 */
public final class WoundService {
	/** Part health a bleeding part loses each second: about two and a half minutes from whole. */
	public static final float BLEED_PER_SECOND = 0.5F;
	/** Ticks a tourniquet can stay on before the part starts to die: three minutes. */
	public static final int TOURNIQUET_SAFE = 3 * 60 * 20;
	/** Part health a part starved by a tourniquet loses each second. */
	public static final float NECROSIS_PER_SECOND = 0.4F;
	/** Ticks a splint holds a broken bone: four minutes. */
	public static final int SPLINT_TICKS = 4 * 60 * 20;

	/** A blow must take at least this much part health to open a wound (a zombie's takes 18). */
	private static final float BLEED_FROM = 10.0F;
	/** A fall or crush must take at least this much part health from a limb to break it. */
	private static final float BREAK_FROM = 16.0F;

	private WoundService() {
	}

	public static Wounds get(final Player player) {
		return player.getAttachedOrElse(MistinnAttachments.WOUNDS, Wounds.NONE);
	}

	/** Stores the wounds; the penalties follow, since a broken limb acts as a destroyed one. */
	public static void set(final ServerPlayer player, final Wounds wounds) {
		if (wounds.equals(get(player))) {
			return;
		}
		player.setAttached(MistinnAttachments.WOUNDS, wounds);
		BodyService.applyPenalties(player, BodyService.get(player));
	}

	/** Clear every wound. */
	public static void clearAll(final ServerPlayer player) {
		set(player, Wounds.NONE);
	}

	/** One part treated by surgery: bleeding, bone, tourniquet and splint all dealt with. */
	public static void treat(final ServerPlayer player, final BodyPart part) {
		set(player, get(player).with(part, Wounds.Wound.NONE));
	}

	/** Breaks the bone and/or opens a bleeding wound on one part, keeping whatever the part already had. */
	public static void inflict(final ServerPlayer player, final BodyPart part, final boolean fracture, final boolean bleed) {
		Wounds wounds = get(player);
		Wounds.Wound wound = wounds.get(part);
		if (fracture) {
			wound = wound.fractured(true);
		}
		if (bleed) {
			wound = wound.bleeding(true);
		}
		set(player, wounds.with(part, wound));
	}

	// ── What a limb can do ────────────────────────────────────────

	/** A broken bone not held by a splint, on a living limb: it works as if destroyed. */
	public static boolean broken(final Player player, final BodyPart part) {
		Wounds.Wound wound = get(player).get(part);
		return wound.fractured() && !wound.splinted(player.level().getGameTime()) && !BodyService.get(player).destroyed(part);
	}

	/** The part is losing health to its wound: bleeding untied, or starved under a tourniquet. */
	public static boolean drains(final Player player, final BodyPart part) {
		Wounds.Wound wound = get(player).get(part);
		return wound.bleeding() && !wound.tied() || wound.tied() && player.level().getGameTime() - wound.tourniquet() > TOURNIQUET_SAFE;
	}

	/** Either leg broken and not held: no roll, only the dive. */
	public static boolean legBroken(final Player player) {
		return broken(player, BodyPart.LEFT_LEG) || broken(player, BodyPart.RIGHT_LEG);
	}

	// ── New wounds ────────────────────────────────────────────────

	/**
	 * After a hit has landed: blows and shots may open a bleeding wound where they struck; falls,
	 * explosions and heavy crushing blows may break the bones of the limbs they reached.
	 * {@code amount} is the part health the hit took (before it was spread).
	 */
	public static void afterHit(final ServerPlayer player, final BodyService.Hit hit, final DamageSource source, final float amount) {
		Body body = BodyService.get(player);
		Wounds wounds = get(player);
		RandomSource random = player.getRandom();
		boolean blow = source.getDirectEntity() != null && !source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypeTags.IS_FALL);
		boolean crush = source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_EXPLOSION) || hit.legs();
		java.util.List<BodyPart> reached = new java.util.ArrayList<>();
		float each = amount;
		if (hit.whole()) {
			java.util.Collections.addAll(reached, BodyPart.values());
			each = amount / BodyPart.values().length;
		} else if (hit.legs()) {
			reached.add(BodyPart.LEFT_LEG);
			reached.add(BodyPart.RIGHT_LEG);
			each = amount / 2.0F;
		} else if (hit.part() != null) {
			reached.add(hit.part());
		}
		java.util.List<Component> news = new java.util.ArrayList<>();
		boolean broke = false;
		for (BodyPart part : reached) {
			if (body.destroyed(part)) {
				continue;
			}
			Wounds.Wound wound = wounds.get(part);
			// constitution makes both rarer
			if (blow && !wound.bleeding() && random.nextFloat() < life.shizune.mistinn.skill.SkillService.woundChance(player, Math.min(0.55F, (each - BLEED_FROM) / 40.0F))) {
				wound = wound.bleeding(true);
				news.add(Component.translatable("message.mistinn.wound.bleeding", partName(part)));
			}
			boolean limb = part.isArm() || part.isLeg();
			float breakChance = crush ? (each - BREAK_FROM) / 30.0F : (each - 30.0F) / 60.0F;
			if (limb && !wound.fractured() && random.nextFloat() < life.shizune.mistinn.skill.SkillService.woundChance(player, Math.min(0.7F, breakChance))) {
				wound = wound.fractured(true);
				news.add(Component.translatable("message.mistinn.wound.fracture", partName(part)));
				broke = true;
			}
			wounds = wounds.with(part, wound);
		}
		if (!news.isEmpty()) {
			set(player, wounds);
			player.sendSystemMessage(news.getLast(), true);
			if (broke) {
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.LIMB_DESTROYED, SoundSource.PLAYERS, 0.7F, 1.25F);
			}
		}
	}

	public static Component partName(final BodyPart part) {
		return Component.translatable("gui.mistinn.part." + part.id);
	}

	// ── Every second ──────────────────────────────────────────────

	public static void tick(final MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!BodyService.applies(player) || player.isDeadOrDying()) {
				continue;
			}
			Wounds wounds = get(player);
			if (!wounds.any()) {
				continue;
			}
			long now = player.level().getGameTime();
			Body body = BodyService.get(player);
			Body hurt = body;
			for (BodyPart part : BodyPart.values()) {
				Wounds.Wound wound = wounds.get(part);
				if (body.destroyed(part)) {
					// nothing left to bleed or break
					if (wound.any()) {
						wounds = wounds.with(part, Wounds.Wound.NONE);
					}
					continue;
				}
				if (wound.tied() && now - wound.tourniquet() > TOURNIQUET_SAFE) {
					hurt = hurt.with(part, hurt.get(part) - NECROSIS_PER_SECOND);
				} else if (wound.bleeding() && !wound.tied()) {
					hurt = hurt.with(part, hurt.get(part) - BLEED_PER_SECOND);
				}
				if (wound.splint() > 0L && !wound.splinted(now)) {
					// the splint has worked loose: the bone is felt again
					wounds = wounds.with(part, wound.splint(0L));
					player.sendSystemMessage(Component.translatable("message.mistinn.wound.splint_loose", partName(part)), true);
				}
			}
			set(player, wounds);
			if (!hurt.equals(body)) {
				BodyService.set(player, hurt);
			}
		}
	}
}
