package life.shizune.mistinn.weapon;

import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An arrow with a head of its own: the blast arrow's charge goes off where it strikes (it breaks no block); the frost
 * arrow's ice freezes what it strikes, slow and shivering; the shock arrow's copper jolts what it strikes, and a jolted
 * hand lets go of its weapon ({@link Shock#disarm}).
 */
public class ElementArrow extends AbstractArrow {
	/** What its head is. */
	public enum Kind {
		BLAST("blast_arrow"), FROST("frost_arrow"), SHOCK("shock_arrow");

		public final String id;

		Kind(final String id) {
			this.id = id;
		}

		public EntityType<ElementArrow> type() {
			return switch (this) {
				case BLAST -> MistinnEntities.BLAST_ARROW;
				case FROST -> MistinnEntities.FROST_ARROW;
				case SHOCK -> MistinnEntities.SHOCK_ARROW;
			};
		}

		/** The kind an arrow's entity type stands for. */
		public static Kind of(final EntityType<?> type) {
			for (Kind kind : values()) {
				if (kind.type() == type) {
					return kind;
				}
			}
			return BLAST;
		}

		public Item item() {
			return switch (this) {
				case BLAST -> MistinnItems.BLAST_ARROW;
				case FROST -> MistinnItems.FROST_ARROW;
				case SHOCK -> MistinnItems.SHOCK_ARROW;
			};
		}
	}

	/** How wide the blast is (it breaks no block). */
	public static final float BLAST = 1.8F;
	/** How long the frost holds one, slowed, and how long it keeps them shivering. */
	public static final int FROST_SLOW = 80;
	public static final int FROST_FREEZE = 80;
	/** What the shock adds to the arrow's own hurt. */
	public static final float SHOCK = 2.0F;

	/** Who is frozen, until when (the world's freezing thaws one at once out of powder snow: this keeps them shivering). */
	private static final java.util.Map<LivingEntity, Long> FROZEN = new java.util.WeakHashMap<>();

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (FROZEN.isEmpty()) {
				return;
			}
			java.util.Iterator<java.util.Map.Entry<LivingEntity, Long>> it = FROZEN.entrySet().iterator();
			while (it.hasNext()) {
				java.util.Map.Entry<LivingEntity, Long> entry = it.next();
				LivingEntity living = entry.getKey();
				if (!living.isAlive() || living.level().getGameTime() > entry.getValue()) {
					it.remove();
				} else {
					living.setTicksFrozen(Math.max(living.getTicksFrozen(), living.getTicksRequiredToFreeze() + 20));
				}
			}
		});
	}

	/** Whether one is held frozen by a frost arrow now. */
	public static boolean frozen(final LivingEntity living) {
		Long until = FROZEN.get(living);
		return until != null && living.level().getGameTime() <= until;
	}

	public ElementArrow(final EntityType<? extends ElementArrow> type, final Level level, final Kind kind) {
		super(type, level);
	}

	public ElementArrow(final Kind kind, final LivingEntity shooter, final Level level, final ItemStack ammo, final @Nullable ItemStack weapon) {
		super(kind.type(), shooter, level, ammo, weapon);
	}

	/** Its head, as its entity type says (known even while it is still being made). */
	public Kind kind() {
		return Kind.of(this.getType());
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(this.kind().item());
	}

	@Override
	protected void onHitEntity(final EntityHitResult result) {
		super.onHitEntity(result);
		if (this.level() instanceof ServerLevel level) {
			this.struck(level, result.getEntity());
		}
	}

	/** What its head does to what it struck. */
	public void struck(final ServerLevel level, final Entity hit) {
		switch (this.kind()) {
			case BLAST -> this.blast(level, this.position());
			case FROST -> {
				if (hit instanceof LivingEntity living) {
					this.frost(level, living);
				}
			}
			case SHOCK -> {
				if (hit instanceof LivingEntity living) {
					this.shock(level, living);
				}
			}
		}
	}

	@Override
	protected void onHitBlock(final BlockHitResult result) {
		super.onHitBlock(result);
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 at = result.getLocation();
		switch (this.kind()) {
			case BLAST -> this.blast(level, at);
			case FROST -> level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 14, 0.25, 0.25, 0.25, 0.02);
			case SHOCK -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.1);
		}
	}

	/** The charge goes off: it hurts and throws whatever is near, and breaks no block. */
	private void blast(final ServerLevel level, final Vec3 at) {
		level.explode(this, at.x, at.y, at.z, BLAST, Level.ExplosionInteraction.NONE);
		this.discard();
	}

	private void frost(final ServerLevel level, final LivingEntity living) {
		living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FROST_SLOW, 3), this.getEffectSource());
		living.setTicksFrozen(Math.max(living.getTicksFrozen(), living.getTicksRequiredToFreeze() + 20));
		FROZEN.put(living, level.getGameTime() + FROST_FREEZE);
		level.sendParticles(ParticleTypes.SNOWFLAKE, living.getX(), living.getY(0.6), living.getZ(), 24, 0.35, 0.5, 0.35, 0.03);
		level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 0.8F, 1.6F);
	}

	private void shock(final ServerLevel level, final LivingEntity living) {
		living.setInvulnerableTime(0);
		living.hurtServer(level, level.damageSources().indirectMagic(this, this.getOwner()), SHOCK);
		Shock.jolt(level, living);
	}
}
