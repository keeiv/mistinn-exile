package life.shizune.mistinn.achievement;

import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.story.WorldMode;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;

/**
 * 鏽錨使徒, the hidden achievement. Its tab entry stays hidden until it is earned and its description
 * never says how: a hunter of a Rust Anchor world who has brought down all three keepers without once
 * dying in that world. The anchor's own takes a little less from every blow and carries a little more
 * of every stat than was bought, beyond the cap.
 */
public final class ApostleService {
	/** What a blow takes from an apostle, of what it would take from anyone else. */
	public static final float TAKEN = 0.92F;
	/** The points the anchor adds to each of an apostle's stats, beyond what was bought and beyond the cap. */
	public static final int BLESSING = 2;
	/** The anchor's rust, as it marks its apostle. */
	private static final DustParticleOptions RUST = new DustParticleOptions(0x9A4A1C, 1.6F);

	/** Whether this hunter is the anchor's: read from the advancement as they arrive, and told to their own client. */
	public static final AttachmentType<Boolean> APOSTLE = AttachmentRegistry.<Boolean>builder()
		.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly())
		.copyOnDeath()
		.buildAndRegister(Mistinn.id("apostle"));

	private ApostleService() {
	}

	public static boolean is(final Player player) {
		return player.getAttachedOrElse(APOSTLE, false);
	}

	/** What a blow takes from this hunter, of what it would otherwise. */
	public static float taken(final Player player) {
		return is(player) ? TAKEN : 1.0F;
	}

	/** The points the anchor adds to each of this hunter's stats. */
	public static int blessing(final Player player) {
		return is(player) ? BLESSING : 0;
	}

	/** Whether the hunter has walked the anchor's whole road. */
	public static boolean worthy(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		return server != null && Hardship.of(server) == Hardship.RUST_ANCHOR && WorldMode.of(server) != WorldMode.TRAINING
			&& AchievementService.has(player, Achievement.SNUFFER) && AchievementService.has(player, Achievement.SUNKEN_BELL)
			&& AchievementService.has(player, Achievement.RUST_WHEEL)
			&& player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS)) == 0;
	}

	/** As a keeper's achievement is earned and as a hunter arrives: the worthy are made the anchor's, and the mark kept in line. */
	public static void consider(final ServerPlayer player) {
		if (!AchievementService.has(player, Achievement.APOSTLE) && worthy(player)) {
			AchievementService.grant(player, Achievement.APOSTLE);
		}
		boolean apostle = AchievementService.has(player, Achievement.APOSTLE);
		if (is(player) != apostle) {
			if (apostle) {
				player.setAttached(APOSTLE, true);
			} else {
				player.removeAttached(APOSTLE);
			}
			SkillService.refresh(player);
		}
	}

	/** The moment it is earned: the anchor's toll, its rust about the hunter, and what it gives them. */
	static void anointed(final ServerPlayer player) {
		player.setAttached(APOSTLE, true);
		SkillService.refresh(player);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.5F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.4F, 0.6F);
		level.sendParticles(RUST, player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.9, 1.0, 0.9, 0.0);
		player.sendSystemMessage(Component.translatable("message.mistinn.apostle", Math.round((1.0F - TAKEN) * 100.0F), BLESSING));
	}
}
