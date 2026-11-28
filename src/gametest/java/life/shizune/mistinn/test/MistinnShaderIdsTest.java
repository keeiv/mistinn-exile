package life.shizune.mistinn.test;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import life.shizune.mistinn.client.compat.ShaderIds;
import life.shizune.mistinn.registry.MistinnBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The mod's blocks told to a shader pack as the vanilla blocks they are like: leaves as leaves (waterlogged or not, as
 * the pack tells them apart), grass as grass, wood as wood; in a Complementary pack the crystal and lit facilities to its
 * glow, the crystal vein ore to its ore; nothing the pack names itself changed; the boats as boats. Where Iris is there
 * with a Complementary pack, its loaded ids are looked at too.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnShaderIdsTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		context.runOnClient(client -> {
			// a pack laid out as Complementary's
			Object2IntMap<BlockState> ids = new Object2IntLinkedOpenHashMap<>();
			ids.defaultReturnValue(-1);
			for (BlockState state : Blocks.SPRUCE_LEAVES.getStateDefinition().getPossibleStates()) {
				ids.put(state, state.getValue(LeavesBlock.WATERLOGGED) ? 10011 : 10009);
			}
			ids.put(Blocks.SHORT_GRASS.defaultBlockState(), 10005);
			for (BlockState state : Blocks.AMETHYST_CLUSTER.getStateDefinition().getPossibleStates()) {
				ids.put(state, 10332);
			}
			ids.put(Blocks.SEA_LANTERN.defaultBlockState(), 10448);
			ids.put(Blocks.AMETHYST_BLOCK.defaultBlockState(), 10328);
			ids.put(Blocks.SPRUCE_PLANKS.defaultBlockState(), 10188);
			ids.put(Blocks.IRON_BLOCK.defaultBlockState(), 10400);
			// one of ours the pack names itself
			ids.put(MistinnBlocks.VEIN_STONE.defaultBlockState(), 4242);
			int told = ShaderIds.alias(ids);
			check(told > 50, "the pack is told of the mod's block states: " + told);
			BlockState dry = MistinnBlocks.MIST_PINE_LEAVES.defaultBlockState().setValue(LeavesBlock.WATERLOGGED, false);
			check(ids.getInt(dry) == 10009 && ids.getInt(dry.setValue(LeavesBlock.WATERLOGGED, true)) == 10011, "mist pine leaves sway as leaves, wet or dry");
			check(ids.getInt(MistinnBlocks.JADE_GRASS.defaultBlockState()) == 10005, "jade grass waves as grass");
			check(ids.getInt(MistinnBlocks.MIST_PINE_PLANKS.defaultBlockState()) == 10188, "mist pine planks are wood");
			check(ids.getInt(MistinnBlocks.TERRA_CRYSTAL_CLUSTER.defaultBlockState()) == ShaderIds.GLOW, "crystal clusters take Complementary's glow");
			check(ids.getInt(MistinnBlocks.LATTICE_CRYSTAL.defaultBlockState()) == ShaderIds.GLOW, "and the lattice's spires glow softly through");
			check(ids.getInt(MistinnBlocks.VEIN_CRYSTAL_ORE.defaultBlockState()) == ShaderIds.ORE, "the crystal vein ore takes its ore");
			BlockState hub = MistinnBlocks.HUB_CORE.defaultBlockState();
			BlockState lit = hub.setValue(life.shizune.mistinn.block.HubCoreBlock.LIT, true);
			BlockState dark = hub.setValue(life.shizune.mistinn.block.HubCoreBlock.LIT, false);
			check(ids.getInt(lit) == ShaderIds.GLOW && ids.getInt(dark) == 10400, "a lit hub glows, a sleeping one is metal");
			check(ids.getInt(MistinnBlocks.VEIN_STONE.defaultBlockState()) == 4242, "what the pack names itself is left as it is");
			// another pack: the crystal as amethyst
			Object2IntMap<BlockState> other = new Object2IntLinkedOpenHashMap<>();
			other.defaultReturnValue(-1);
			other.put(Blocks.AMETHYST_BLOCK.defaultBlockState(), 77);
			ShaderIds.alias(other);
			check(other.getInt(MistinnBlocks.LATTICE_CRYSTAL.defaultBlockState()) == 77 && !other.containsKey(MistinnBlocks.JADE_GRASS.defaultBlockState()),
				"another pack: the crystal as its amethyst, and nothing it does not have");
			// the boats as boats
			Object2IntMap<String> entities = new Object2IntOpenHashMap<>();
			entities.defaultReturnValue(-1);
			entities.put("minecraft:spruce_boat", 50076);
			entities.put("minecraft:spruce_chest_boat", 50076);
			check(ShaderIds.aliasEntities(entities, (ns, name) -> ns + ":" + name) == 2 && entities.getInt("mistinn:mist_pine_boat") == 50076,
				"the mist pine boats are boats");
			Object2IntMap<String> items = new Object2IntOpenHashMap<>();
			items.defaultReturnValue(-1);
			items.put("minecraft:sea_lantern", 44018);
			items.put("minecraft:redstone_torch", 44035);
			ShaderIds.aliasItems(items, (ns, name) -> ns + ":" + name);
			check(items.getInt("mistinn:facility_lamp") == 44018 && items.getInt("mistinn:rift_crystal_cluster") == 44035,
				"lights held in the hand in the nearest vanilla light's colour");
		});
		// with Iris and a Complementary pack loaded (only where both are in the run), the ids it holds
		if (FabricLoader.getInstance().isModLoaded("iris")) {
			context.waitTicks(40);
			context.runOnClient(client -> {
				try {
					Class<?> settings = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
					Object instance = settings.getField("INSTANCE").get(null);
					@SuppressWarnings("unchecked")
					Object2IntMap<BlockState> held = (Object2IntMap<BlockState>) settings.getMethod("getBlockStateIds").invoke(instance);
					if (held == null) {
						System.out.println("[mistinn shader] Iris has no pack loaded: skipped");
						return;
					}
					check(held.getInt(MistinnBlocks.TERRA_CRYSTAL_CLUSTER.defaultBlockState()) == ShaderIds.GLOW, "Iris holds the crystal clusters at the glow: "
						+ held.getInt(MistinnBlocks.TERRA_CRYSTAL_CLUSTER.defaultBlockState()));
					check(held.getInt(MistinnBlocks.MIST_PINE_LEAVES.defaultBlockState()) == held.getInt(Blocks.SPRUCE_LEAVES.defaultBlockState()),
						"and the mist pine leaves as spruce leaves: " + held.getInt(MistinnBlocks.MIST_PINE_LEAVES.defaultBlockState()));
					// the boats and held lights, in the pack's own entity and item lists
					Object pack = ((java.util.Optional<?>) Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPack").invoke(null)).orElseThrow();
					Object idMap = pack.getClass().getMethod("getIdMap").invoke(pack);
					java.lang.reflect.Constructor<?> name = Class.forName("net.irisshaders.iris.shaderpack.materialmap.NamespacedId").getConstructor(String.class, String.class);
					@SuppressWarnings("unchecked")
					it.unimi.dsi.fastutil.objects.Object2IntFunction<Object> entityIds = (it.unimi.dsi.fastutil.objects.Object2IntFunction<Object>) idMap.getClass()
						.getMethod("getEntityIdMap").invoke(idMap);
					@SuppressWarnings("unchecked")
					it.unimi.dsi.fastutil.objects.Object2IntFunction<Object> itemIds = (it.unimi.dsi.fastutil.objects.Object2IntFunction<Object>) idMap.getClass()
						.getMethod("getItemIdMap").invoke(idMap);
					int boat = entityIds.getInt(name.newInstance("mistinn", "mist_pine_boat"));
					check(boat == entityIds.getInt(name.newInstance("minecraft", "spruce_boat")) && boat != entityIds.defaultReturnValue(),
						"Iris holds the mist pine boat as a boat: " + boat);
					int lamp = itemIds.getInt(name.newInstance("mistinn", "facility_lamp"));
					check(lamp == itemIds.getInt(name.newInstance("minecraft", "sea_lantern")) && lamp != itemIds.defaultReturnValue(),
						"and the facility lamp in the hand as a sea lantern: " + lamp);
				} catch (ReflectiveOperationException e) {
					throw new AssertionError("failed: could not read Iris's ids", e);
				}
			});
		}
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn shader] ok: " + what);
	}
}
