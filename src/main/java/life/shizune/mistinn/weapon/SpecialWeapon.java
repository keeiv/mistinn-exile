package life.shizune.mistinn.weapon;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.achievement.AchievementService;
import life.shizune.mistinn.skill.SkillService;
import life.shizune.mistinn.skill.Stat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * 特殊武器: named weapons that only answer a hunter who has earned them. Short of a requirement,
 * the weapon hits at half strength, swings slowly and gives no heavy blow; each has a heavy blow of
 * its own (see {@link HeavyService}).
 */
public enum SpecialWeapon {
	/** 熄燈鉤鐮: torn from the Snuffer. Drags the farthest foe it reaps to the hunter's feet. */
	SNUFFER_HOOK("snuffer_hook", List.of(Requirement.achievement(AchievementService.SNUFFER), Requirement.stat(Stat.STRENGTH, 3))),
	/** 霧燈杖: its heavy blow bursts the lantern, burning everything near; intellect, not strength, drives it. */
	LANTERN_STAFF("lantern_staff", List.of(Requirement.stat(Stat.INTELLECT, 4))),
	/** 潮鐘巨鎚: its heavy blow tolls the bell, striking all around. */
	TIDE_MAUL("tide_maul", List.of(Requirement.stat(Stat.STRENGTH, 5), Requirement.stat(Stat.CONSTITUTION, 3))),
	/** 鐘舌: the Sunken Bell's own clapper. Its full heavy blow rings out all round and breaks off every windup it reaches. */
	BELL_CLAPPER("bell_clapper", List.of(Requirement.achievement(AchievementService.SUNKEN_BELL), Requirement.stat(Stat.STRENGTH, 4)));

	/** What the weapon asks of its bearer: a stat at or above a value, or an achievement. */
	public record Requirement(@Nullable Stat stat, int value, @Nullable String achievement) {
		public static Requirement stat(final Stat stat, final int value) {
			return new Requirement(stat, value, null);
		}

		public static Requirement achievement(final String id) {
			return new Requirement(null, 0, id);
		}

		public boolean met(final Player player) {
			if (this.stat != null) {
				return SkillService.stat(player, this.stat) >= this.value;
			}
			return this.achievement == null || AchievementService.has(player, this.achievement);
		}

		/** "力量 6" or the achievement's name. */
		public Component describe() {
			if (this.stat != null) {
				return Component.translatable("weapon.mistinn.requires.stat", Component.translatable(this.stat.key()), this.value);
			}
			return Component.translatable("achievement.mistinn." + this.achievement);
		}
	}

	/** Strength and swing taken from a hunter who does not meet the weapon's requirements. */
	public static final double UNFIT_DAMAGE = -0.5;
	public static final double UNFIT_SPEED = -0.3;
	private static final Identifier UNFIT_DAMAGE_ID = Mistinn.id("unfit_damage");
	private static final Identifier UNFIT_SPEED_ID = Mistinn.id("unfit_speed");

	private static final Map<Item, SpecialWeapon> BY_ITEM = new IdentityHashMap<>();
	private static final Map<SpecialWeapon, Item> ITEMS = new EnumMap<>(SpecialWeapon.class);

	public final String id;
	public final List<Requirement> requirements;

	SpecialWeapon(final String id, final List<Requirement> requirements) {
		this.id = id;
		this.requirements = requirements;
	}

	/** Called once as the weapon's item is registered. */
	public void bind(final Item item) {
		BY_ITEM.put(item, this);
		ITEMS.put(this, item);
	}

	public Item item() {
		return ITEMS.get(this);
	}

	public static @Nullable SpecialWeapon of(final ItemStack stack) {
		return stack.isEmpty() ? null : BY_ITEM.get(stack.getItem());
	}

	public boolean fits(final Player player) {
		for (Requirement requirement : this.requirements) {
			if (!requirement.met(player)) {
				return false;
			}
		}
		return true;
	}

	/** The first requirement this hunter falls short of, or null. */
	public @Nullable Requirement lacking(final Player player) {
		for (Requirement requirement : this.requirements) {
			if (!requirement.met(player)) {
				return requirement;
			}
		}
		return null;
	}

	/** True when the hunter's main hand holds a special weapon they have not earned. */
	public static boolean unfit(final Player player) {
		SpecialWeapon special = of(player.getMainHandItem());
		return special != null && !special.fits(player);
	}

	/** Each tick: a special weapon held by an unfit hunter loses half its strength and a third of its pace. */
	public static void tick(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean unfit = unfit(player);
			penalize(player.getAttribute(Attributes.ATTACK_DAMAGE), UNFIT_DAMAGE_ID, UNFIT_DAMAGE, unfit);
			penalize(player.getAttribute(Attributes.ATTACK_SPEED), UNFIT_SPEED_ID, UNFIT_SPEED, unfit);
		}
	}

	private static void penalize(final @Nullable AttributeInstance attribute, final Identifier id, final double amount, final boolean on) {
		if (attribute == null) {
			return;
		}
		boolean has = attribute.getModifier(id) != null;
		if (on && !has) {
			attribute.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!on && has) {
			attribute.removeModifier(id);
		}
	}
}
