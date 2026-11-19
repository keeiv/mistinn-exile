package life.shizune.mistinn.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's protected damage steps, reused by the body-part damage that replaces them. */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
	@Invoker("getDamageAfterMagicAbsorb")
	float mistinn$getDamageAfterMagicAbsorb(DamageSource source, float damage);

	@Invoker("doHurtEquipment")
	void mistinn$doHurtEquipment(DamageSource source, float damage, EquipmentSlot... slots);
}
