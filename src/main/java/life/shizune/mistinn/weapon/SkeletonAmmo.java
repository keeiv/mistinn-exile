package life.shizune.mistinn.weapon;

import com.mojang.serialization.Codec;
import life.shizune.mistinn.Mistinn;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Some skeletons carry blast, frost or shock arrows. They pay for them: such a bow is slower to draw again, its shots go
 * wider, and loosing one rocks the skeleton back a moment, open. While it draws, its arrowhead shows in a wisp of colour
 * (red-brown, ice blue, yellow), so a hunter can see what is coming. Brought down, it may leave some of them.
 */
public final class SkeletonAmmo {
	/** One skeleton in this many carries such arrows. */
	public static final int ODDS = 7;
	/** Its draw is this much slower, and its shots this much wider. */
	public static final float SLOWER = 1.6F;
	public static final float WIDER = 6.0F;
	/** How long loosing one rocks it back, open. */
	public static final int RECOIL = 30;
	/** What it carries: the kind's name, or "" for plain arrows (so the roll is made once). */
	public static final AttachmentType<String> AMMO = AttachmentRegistry.<String>builder()
		.persistent(Codec.STRING)
		.buildAndRegister(Mistinn.id("skeleton_ammo"));

	private SkeletonAmmo() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractSkeleton skeleton && !skeleton.hasAttached(AMMO)) {
				ElementArrow.Kind[] kinds = ElementArrow.Kind.values();
				skeleton.setAttached(AMMO, skeleton.getRandom().nextInt(ODDS) == 0 ? kinds[skeleton.getRandom().nextInt(kinds.length)].name() : "");
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof AbstractSkeleton skeleton && entity.level() instanceof ServerLevel level) {
				ElementArrow.Kind kind = of(skeleton);
				int count = kind == null ? 0 : skeleton.getRandom().nextInt(3);
				if (count > 0) {
					skeleton.spawnAtLocation(level, new ItemStack(kind.item(), count));
				}
			}
		});
	}

	/** The arrows this skeleton carries, or null for plain ones. */
	public static ElementArrow.@Nullable Kind of(final AbstractSkeleton skeleton) {
		String name = skeleton.getAttachedOrElse(AMMO, "");
		for (ElementArrow.Kind kind : ElementArrow.Kind.values()) {
			if (kind.name().equals(name)) {
				return kind;
			}
		}
		return null;
	}

	/** Test hook and spawning: this skeleton carries these arrows (null: plain ones). */
	public static void give(final AbstractSkeleton skeleton, final ElementArrow.@Nullable Kind kind) {
		skeleton.setAttached(AMMO, kind == null ? "" : kind.name());
	}

	/** It looses one: rocked back a moment, open. */
	public static void loosed(final AbstractSkeleton skeleton) {
		skeleton.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, RECOIL, 4));
		skeleton.getNavigation().stop();
	}

	/** While it draws one, its head shows in a wisp of colour. */
	public static void drawing(final AbstractSkeleton skeleton) {
		ElementArrow.Kind kind = of(skeleton);
		if (kind == null || !skeleton.isUsingItem() || skeleton.tickCount % 3 != 0 || !(skeleton.level() instanceof ServerLevel level)) {
			return;
		}
		int colour = switch (kind) {
			case BLAST -> 0xB0472A;
			case FROST -> 0xA8E4FF;
			case SHOCK -> 0xFFE45A;
		};
		level.sendParticles(new DustParticleOptions(colour, 1.0F), skeleton.getX(), skeleton.getEyeY() - 0.2, skeleton.getZ(), 2, 0.25, 0.15, 0.25, 0.0);
	}
}
