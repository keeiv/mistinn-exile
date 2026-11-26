package life.shizune.mistinn.terra;

import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnBlocks;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The crystal tillers' two tools, sold for crystal at their settlements.
 * <p>
 * 晶屑磨石: worked along a blade a moment, it leaves an edge of crystal grit on whatever the hunter
 * fights with for a while: the lifeless of Terra no longer turn a spear's thrust, and every blow
 * bites a little deeper into their facets.
 * <p>
 * 共鳴音叉: struck, it rings, and every lifeless thing of the lattice near answers it: the lizards
 * lying still as stone, the drakes, the elder, all glow through stone and leaf a few seconds.
 */
public final class TillerTools {
	/** How long an edge of crystal grit lasts, and how much deeper it bites. */
	public static final int EDGE = 3600;
	public static final float BITE = 1.15F;
	/** How long the whetstone is worked along the blade. */
	public static final int HONING = 32;
	/** How far the fork is heard, how long they glow, and how long before it can be struck again. */
	public static final double HEARD = 40.0;
	public static final int GLOW = 200;
	public static final int RINGING = 200;

	/** On a hunter whose blade has an edge of crystal grit: the game tick it wears off. */
	public static final AttachmentType<Long> EDGED = AttachmentRegistry.<Long>builder()
		.persistent(com.mojang.serialization.Codec.LONG)
		.buildAndRegister(Mistinn.id("crystal_edge"));

	private TillerTools() {
	}

	/** Whether this hunter's blade has its edge of grit now. */
	public static boolean edged(final LivingEntity hunter) {
		return hunter.level() != null && hunter.getAttachedOrElse(EDGED, 0L) > hunter.level().getGameTime();
	}

	/**
	 * How a lifeless thing's facets take a blow, given how they would take it bare: a blade with an
	 * edge of grit is not turned (a thrust or a fist bites full), and bites a little deeper.
	 */
	public static float edge(final DamageSource source, final float bare) {
		if (!(source.getEntity() instanceof LivingEntity hunter) || source.getDirectEntity() != hunter || !edged(hunter)) {
			return bare;
		}
		return Math.max(bare, 1.0F) * BITE;
	}

	/** The whetstone has been worked along the blade: an edge of grit, for a while. */
	public static void hone(final ServerPlayer hunter) {
		ServerLevel level = hunter.level();
		hunter.setAttached(EDGED, level.getGameTime() + EDGE);
		level.playSound(null, hunter.getX(), hunter.getY(), hunter.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
		level.playSound(null, hunter.getX(), hunter.getY(), hunter.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, MistinnBlocks.LATTICE_CRYSTAL.defaultBlockState()),
			hunter.getX(), hunter.getY() + 1.1, hunter.getZ(), 12, 0.25, 0.2, 0.25, 0.05);
		hunter.sendSystemMessage(Component.translatable("message.mistinn.whetstone.honed", EDGE / 1200).withStyle(ChatFormatting.AQUA), true);
	}

	/** The fork is struck: every lifeless thing of the lattice near answers, and glows a while. Returns how many answered. */
	public static int ring(final ServerLevel level, final Player hunter) {
		level.playSound(null, hunter.getX(), hunter.getY(), hunter.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.2F, 1.8F);
		level.playSound(null, hunter.getX(), hunter.getY(), hunter.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 1.2F);
		List<Mob> answered = level.getEntitiesOfClass(Mob.class, new AABB(hunter.blockPosition()).inflate(HEARD),
			mob -> mob.isAlive() && (mob instanceof CrystalDrake || mob instanceof CrystalLizard));
		for (Mob mob : answered) {
			mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW, 0, false, false));
			level.sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
		}
		if (hunter instanceof ServerPlayer player) {
			player.sendSystemMessage(answered.isEmpty() ? Component.translatable("message.mistinn.fork.silent").withStyle(ChatFormatting.GRAY)
				: Component.translatable("message.mistinn.fork.answered", answered.size()).withStyle(ChatFormatting.AQUA), true);
		}
		return answered.size();
	}

	/** 晶屑磨石. */
	public static final class Whetstone extends Item {
		public Whetstone(final Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public int getUseDuration(final ItemStack stack, final LivingEntity user) {
			return HONING;
		}

		@Override
		public ItemUseAnimation getUseAnimation(final ItemStack stack) {
			return ItemUseAnimation.BRUSH;
		}

		@Override
		public ItemStack finishUsingItem(final ItemStack stack, final Level level, final LivingEntity user) {
			if (user instanceof ServerPlayer player) {
				hone(player);
				stack.consume(1, player);
			}
			return stack;
		}
	}

	/** 共鳴音叉. */
	public static final class Fork extends Item {
		public Fork(final Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (level instanceof ServerLevel server) {
				ring(server, player);
				stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND
					: net.minecraft.world.entity.EquipmentSlot.OFFHAND);
			}
			player.getCooldowns().addCooldown(stack, RINGING);
			return InteractionResult.SUCCESS;
		}
	}
}
