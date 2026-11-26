package life.shizune.mistinn.test;

import java.util.List;
import java.util.Set;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.body.BodyService;
import life.shizune.mistinn.registry.MistinnAttachments;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.stamina.StaminaService;
import life.shizune.mistinn.weapon.HeavyService;
import life.shizune.mistinn.weapon.WeaponMaterial;
import life.shizune.mistinn.weapon.WeaponStyle;
import life.shizune.mistinn.weapon.Weapons;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** The hunter's weapons and armour take enchantments, at the table and from books, and a heavy blow feels them. */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnEnchantTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("difficulty easy");
			world.getServer().runCommand("time set noon");
			context.waitTicks(10);
			world.getServer().runOnServer(MistinnEnchantTest::everyWeaponTakesWeaponEnchantments);
			world.getServer().runOnServer(MistinnEnchantTest::everyArmourPieceTakesItsSlotsEnchantments);
			heavyBlowFeelsSharpness(context, world);
		}
	}

	private static void everyWeaponTakesWeaponEnchantments(final MinecraftServer server) {
		HolderLookup.RegistryLookup<Enchantment> enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<Item> weapons = BuiltInRegistries.ITEM.stream()
			.filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(Mistinn.MOD_ID) && Weapons.profile(item) != null)
			.toList();
		check(weapons.size() == 39, "every weapon of the station is counted: " + weapons.size());
		for (Item item : weapons) {
			ItemStack stack = new ItemStack(item);
			String name = BuiltInRegistries.ITEM.getKey(item).toString();
			for (ResourceKey<Enchantment> key : List.of(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS,
				Enchantments.FIRE_ASPECT, Enchantments.KNOCKBACK, Enchantments.LOOTING, Enchantments.UNBREAKING, Enchantments.MENDING)) {
				check(enchantments.getOrThrow(key).value().canEnchant(stack), name + " takes " + key.identifier() + " from a book");
			}
			check(enchantments.getOrThrow(Enchantments.SHARPNESS).value().isPrimaryItem(stack), name + " is offered sharpness at the table");
			check(!enchantments.getOrThrow(Enchantments.PROTECTION).value().canEnchant(stack), name + " is no armour");
			check(!table(enchantments, stack).isEmpty(), name + " is offered something at the table");
		}
	}

	private static void everyArmourPieceTakesItsSlotsEnchantments(final MinecraftServer server) {
		HolderLookup.RegistryLookup<Enchantment> enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		piece(enchantments, MistinnItems.HELMETS, Enchantments.RESPIRATION);
		piece(enchantments, MistinnItems.ARMORS, Enchantments.THORNS);
		piece(enchantments, MistinnItems.LEGGINGS, Enchantments.SWIFT_SNEAK);
		piece(enchantments, MistinnItems.BOOTS, Enchantments.FEATHER_FALLING);
		check(!enchantments.getOrThrow(Enchantments.FEATHER_FALLING).value().canEnchant(new ItemStack(MistinnItems.HELMETS.getFirst())),
			"a helmet takes no feather falling");
	}

	private static void piece(final HolderLookup.RegistryLookup<Enchantment> enchantments, final List<Item> items, final ResourceKey<Enchantment> own) {
		for (Item item : items) {
			ItemStack stack = new ItemStack(item);
			String name = BuiltInRegistries.ITEM.getKey(item).toString();
			for (ResourceKey<Enchantment> key : List.of(Enchantments.PROTECTION, Enchantments.BLAST_PROTECTION, Enchantments.UNBREAKING,
				Enchantments.MENDING, Enchantments.BINDING_CURSE, own)) {
				check(enchantments.getOrThrow(key).value().canEnchant(stack), name + " takes " + key.identifier());
			}
			check(!enchantments.getOrThrow(Enchantments.SHARPNESS).value().canEnchant(stack), name + " is no weapon");
			check(!table(enchantments, stack).isEmpty(), name + " is offered something at the table");
		}
	}

	/** What the enchanting table may put on the stack at its strongest. */
	private static List<?> table(final HolderLookup.RegistryLookup<Enchantment> enchantments, final ItemStack stack) {
		return EnchantmentHelper.getAvailableEnchantmentResults(30, stack,
			enchantments.getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE).stream().map(holder -> (Holder<Enchantment>) holder));
	}

	/** The same full-charge blow, once plain and once with sharpness V: the second takes more. */
	private static void heavyBlowFeelsSharpness(final ClientGameTestContext context, final TestSingleplayerContext world) {
		BlockPos arena = world.getServer().computeOnServer(MistinnEnchantTest::arena);
		Item greatsword = MistinnItems.WEAPONS.get(WeaponStyle.CLEAVE).get(WeaponMaterial.IRON);
		float plain = blowTaken(context, world, arena, new ItemStack(greatsword));
		ItemStack sharp = world.getServer().computeOnServer(server -> {
			ItemStack stack = new ItemStack(greatsword);
			stack.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 5);
			return stack;
		});
		float enchanted = blowTaken(context, world, arena, sharp);
		check(plain > 0.0F, "the plain blow lands: " + plain);
		// sharpness V adds 3 to the blow before anything else scales it
		check(enchanted >= plain + 2.5F, "sharpness V makes the heavy blow hit harder: " + plain + " then " + enchanted);
	}

	private static float blowTaken(final ClientGameTestContext context, final TestSingleplayerContext world, final BlockPos arena, final ItemStack weapon) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			for (var entity : server.overworld().getEntities((net.minecraft.world.entity.Entity) null, new AABB(arena).inflate(20.0), e -> !(e instanceof ServerPlayer))) {
				entity.discard();
			}
			HeavyService.forgetAll();
			player.teleportTo(server.overworld(), arena.getX() + 0.5, arena.getY(), arena.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
			player.getInventory().setSelectedSlot(0);
			player.setItemInHand(InteractionHand.MAIN_HAND, weapon.copy());
			BodyService.restoreAll(player);
			player.setAttached(MistinnAttachments.STAMINA, StaminaService.max(player));
		});
		context.waitTicks(4);
		int id = world.getServer().computeOnServer(server -> {
			Husk mob = EntityTypes.HUSK.create(server.overworld(), EntitySpawnReason.COMMAND);
			mob.setNoAi(true);
			mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0F);
			mob.setHealth(500.0F);
			mob.snapTo(arena.getX() + 0.5, arena.getY(), arena.getZ() + 2.5, 180.0F, 0.0F);
			server.overworld().addFreshEntity(mob);
			return mob.getId();
		});
		context.waitTicks(2);
		world.getServer().runOnServer(server -> check(HeavyService.release(player(server), player(server).getMainHandItem(), 40), "the blow is let go"));
		context.waitTicks(20);
		return world.getServer().computeOnServer(server -> 500.0F - ((LivingEntity) server.overworld().getEntity(id)).getHealth());
	}

	private static BlockPos arena(final MinecraftServer server) {
		ServerPlayer player = player(server);
		BlockPos pos = player.blockPosition().above(30).immutable();
		for (BlockPos floor : BlockPos.betweenClosed(pos.offset(-8, -1, -8), pos.offset(8, -1, 16))) {
			player.level().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		}
		for (BlockPos air : BlockPos.betweenClosed(pos.offset(-8, 0, -8), pos.offset(8, 4, 16))) {
			player.level().setBlockAndUpdate(air, Blocks.AIR.defaultBlockState());
		}
		return pos;
	}

	private static ServerPlayer player(final MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
