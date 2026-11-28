package life.shizune.mistinn.weapon;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** A blast, frost or shock arrow, loosed from any bow or crossbow as an arrow is. */
public class ElementArrowItem extends ArrowItem {
	private final ElementArrow.Kind kind;

	public ElementArrowItem(final ElementArrow.Kind kind, final Properties properties) {
		super(properties);
		this.kind = kind;
	}

	@Override
	public AbstractArrow createArrow(final Level level, final ItemStack ammo, final LivingEntity shooter, final @Nullable ItemStack weapon) {
		return new ElementArrow(this.kind, shooter, level, ammo.copyWithCount(1), weapon);
	}

	@Override
	public Projectile asProjectile(final Level level, final Position at, final ItemStack stack, final Direction direction) {
		ElementArrow arrow = new ElementArrow(this.kind.type(), level, this.kind);
		arrow.setPos(at.x(), at.y(), at.z());
		arrow.pickup = AbstractArrow.Pickup.ALLOWED;
		return arrow;
	}
}
