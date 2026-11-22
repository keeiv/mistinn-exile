package life.shizune.mistinn.medical;

import life.shizune.mistinn.body.Body;
import life.shizune.mistinn.body.BodyPart;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.registry.MistinnSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 手術包: one operation on the part that needs it most. A destroyed part comes back to
 * {@link #REVIVE} health; otherwise a broken bone is set, a tourniquet taken off and the wound
 * under it closed, or a bleeding wound stitched. Destroyed parts first, then broken bones, then
 * tourniquets, then bleeding; within each, in {@link #ORDER}: head, torso, legs, then arms.
 * Three operations to a kit.
 */
public class SurgeryKitItem extends TreatmentItem {
	/** 8 s to use. */
	public static final int USE_TICKS = 160;
	/** Health the part comes back with: 30% of a whole part. */
	public static final float REVIVE = 24.0F;
	/** Operations in one kit (its durability). */
	public static final int USES = 3;
	/** Which destroyed part is operated on first. */
	public static final BodyPart[] ORDER = {BodyPart.HEAD, BodyPart.TORSO, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG, BodyPart.RIGHT_ARM, BodyPart.LEFT_ARM};

	public SurgeryKitItem(final Properties properties) {
		super(properties);
	}

	/** The part an operation on this body would restore, or null when none is destroyed. */
	public static @Nullable BodyPart next(final Body body) {
		return next(body, life.shizune.mistinn.body.Wounds.NONE);
	}

	/** The part an operation would treat, or null when nothing needs it. */
	public static @Nullable BodyPart next(final Body body, final life.shizune.mistinn.body.Wounds wounds) {
		for (BodyPart part : ORDER) {
			if (body.destroyed(part)) {
				return part;
			}
		}
		for (BodyPart part : ORDER) {
			if (wounds.get(part).fractured()) {
				return part;
			}
		}
		for (BodyPart part : ORDER) {
			if (wounds.get(part).tied()) {
				return part;
			}
		}
		for (BodyPart part : ORDER) {
			if (wounds.get(part).bleeding()) {
				return part;
			}
		}
		return null;
	}

	@Override
	public Treatment treatment(final ServerPlayer player, final ItemStack stack) {
		return Treatment.SURGERY;
	}

	@Override
	public @Nullable String refusal(final ServerPlayer player, final ItemStack stack) {
		return next(BodyService.get(player), life.shizune.mistinn.body.WoundService.get(player)) == null ? "message.mistinn.surgery.none" : null;
	}

	@Override
	public int ticks(final ServerPlayer player, final ItemStack stack) {
		return USE_TICKS;
	}

	@Override
	public void finish(final ServerPlayer player, final ItemStack stack, final InteractionHand hand) {
		Body body = BodyService.get(player);
		BodyPart part = next(body, life.shizune.mistinn.body.WoundService.get(player));
		if (part == null) {
			return;
		}
		life.shizune.mistinn.body.WoundService.treat(player, part);
		if (body.destroyed(part)) {
			BodyService.set(player, body.with(part, life.shizune.mistinn.skill.SkillService.treatmentAmount(player, REVIVE)));
			life.shizune.mistinn.achievement.AchievementService.grant(player, life.shizune.mistinn.achievement.Achievement.REVIVE_LIMB);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), MistinnSounds.SURGERY_FINISH, SoundSource.PLAYERS, 0.8F, 1.0F);
		player.sendSystemMessage(Component.translatable(body.destroyed(part) ? "message.mistinn.surgery.done" : "message.mistinn.surgery.treated", Component.translatable("gui.mistinn.part."
			+ part.id)), true);
		stack.hurtAndBreak(1, player, hand);
	}
}
