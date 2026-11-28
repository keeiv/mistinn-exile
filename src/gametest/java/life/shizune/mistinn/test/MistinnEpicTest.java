package life.shizune.mistinn.test;

import java.util.Set;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.epic.EpicService;
import life.shizune.mistinn.epic.EpicState;
import life.shizune.mistinn.epic.EpicStep;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.station.StaffRole;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.udasru.Udasru;
import life.shizune.mistinn.udasru.UdasruBuild;
import life.shizune.mistinn.udasru.UdasruPlaces;
import life.shizune.mistinn.udasru.UdasruSites;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 史詩成篇 from its first morning to its last page: a world of the epic is Udasru at the Hunter's hardship, with the Mist
 * Inn built and 3412 waking in it dressed in the best of everything; the epic moves out of the fog, up the first bell
 * tower, into the capital, through the Chronicler and the Regent, the four beasts and their bells (the rift over the
 * capital at the second), the capital's bell, the Helm on the anchor's crown, and the name.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MistinnEpicTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		if (TestFilter.skip(getClass())) {
			return;
		}
		WorldMode.choose(WorldMode.EPIC);
		Hardship.choose(Hardship.HUNTER);
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> s.setWorldType(
			new WorldCreationUiState.WorldTypeEntry(s.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(Udasru.PRESET))))
			.create()) {
			// the ground round the inn is made as the hunter arrives; give it a few seconds
			context.waitTicks(100);
			world.getServer().runOnServer(MistinnEpicTest::opening);
			context.runOnClient(mc -> {
				EpicState seen = mc.level.getAttached(EpicService.STATE);
				check(seen != null && seen.at() == EpicStep.WAKE, "the client knows the epic and where it stands: " + seen);
			});
			world.getServer().runOnServer(MistinnEpicTest::road);
		}
	}

	/** The world as it first opens, and 3412 in it. */
	private static void opening(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		check(WorldMode.of(server) == WorldMode.EPIC && Hardship.of(server) == Hardship.HUNTER, "the world is the epic's, at the Hunter's hardship");
		check(level.getChunkSource().getGenerator().getBiomeSource() instanceof life.shizune.mistinn.udasru.UdasruBiomes, "and it is Udasru");
		check(level.getBiome(UdasruPlaces.MIST_INN).getRegisteredName().startsWith("mistinn:udasru_"), "with its own biomes: "
			+ level.getBiome(UdasruPlaces.MIST_INN).getRegisteredName());
		check(Math.abs(level.getWorldBorder().getSize() - UdasruPlaces.BORDER) < 1.0, "the border holds the land: " + level.getWorldBorder().getSize());
		check(level.getGameRules().get(GameRules.KEEP_INVENTORY), "a fall costs nothing");
		BlockPos inn = level.getAttached(EpicService.INN);
		check(inn != null && !level.getBlockState(inn).isAir(), "the Mist Inn stands: " + inn);
		check(EpicService.state(server).at() == EpicStep.WAKE, "the epic begins at waking");
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		check(player.blockPosition().distSqr(inn) < 20 * 20, "3412 wakes in the inn: " + player.blockPosition() + " by " + inn);
		check(!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
			&& !player.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && !player.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "dressed head to foot");
		check(player.getItemBySlot(EquipmentSlot.HEAD).isEnchanted() && player.getItemBySlot(EquipmentSlot.CHEST).isEnchanted(), "every piece enchanted");
		int weapons = 0;
		for (int i = 0; i < 9; i++) {
			if (life.shizune.mistinn.weapon.WeaponStyle.of(player.getInventory().getItem(i)) != null && player.getInventory().getItem(i).isEnchanted()) {
				weapons++;
			}
		}
		check(weapons == 6, "the six branches in hand, enchanted: " + weapons);
		check(!life.shizune.mistinn.grid.GridService.get(player).mask().isEmpty() && !life.shizune.mistinn.grid.GridService.get(player).backpack().isEmpty(),
			"mask and pack worn");
		check(life.shizune.mistinn.skill.SkillService.get(player).unlocked().size() == life.shizune.mistinn.skill.SkillTree.NODES.size(), "every skill learned");
		check(life.shizune.mistinn.achievement.ApostleService.is(player), "and they are the anchor's apostle");
		long people = level.getEntitiesOfClass(life.shizune.mistinn.station.StationHand.class, new net.minecraft.world.phys.AABB(inn).inflate(20)).size();
		check(people >= 6, "the inn's people are home: " + people);
	}

	/** The whole road, step by step. */
	private static void road(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);

		// out of the inn, north through the fog
		EpicService.heard(player, StaffRole.INNKEEPER, "wake");
		check(EpicService.state(server).at() == EpicStep.FOG_WALL, "the innkeeper sends 3412 north");
		go(player, 100, UdasruPlaces.OUT_OF_THE_FOG_Z - 40);
		tick(server, 20);
		check(EpicService.state(server).at() == EpicStep.FIRST_TOWER, "out of the fog: " + EpicService.state(server).at());

		// the first bell tower: built once its ground is loaded, then rung
		UdasruPlaces.Tower first = UdasruPlaces.Tower.ANCHOR_HILLS;
		go(player, first.x, first.z + 12);
		build(server, "tower_" + first.id);
		BlockPos bell = towerBell(level, first);
		check(level.getBlockState(bell).is(Blocks.BELL), "the tower's bell hangs: " + bell);
		EpicService.ring(player, bell);
		check(EpicService.state(server).at() == EpicStep.CAPITAL && EpicService.state(server).rungTower(first.bit()), "the first tower rung: "
			+ EpicService.state(server));

		// the capital, the Chronicler and the Regent
		go(player, 0, 30);
		tick(server, 20);
		check(EpicService.state(server).at() == EpicStep.CHRONICLER, "in the capital");
		EpicService.heard(player, StaffRole.CHRONICLER, "saga_first");
		EpicService.heard(player, StaffRole.REGENT, "plea");
		check(EpicService.state(server).at() == EpicStep.BELLS, "the Regent's plea heard");

		// the four beasts and their bells; the rift over the capital at the second
		int rung = 0;
		for (UdasruPlaces.GreatBell great : UdasruPlaces.GreatBell.values()) {
			int[] shrine = UdasruSites.shrinePlace(great);
			go(player, shrine[0], shrine[1] + 10);
			build(server, "shrine_" + great.id);
			LivingEntity beast = beast(level, great);
			beast.snapTo(shrine[0] + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, shrine[0], shrine[1]) + 4.0, shrine[1] + 20.5);
			level.addFreshEntity(beast);
			BlockPos small = shrineBell(level, shrine);
			EpicService.ring(player, small);
			check((EpicService.state(server).bells() & great.bit()) == 0, "the " + great.id + " bell will not ring while its beast lives");
			beast.kill(level);
			tick(server, 2);
			check((EpicService.state(server).beasts() & great.bit()) != 0, "the " + great.id + " beast is down");
			EpicService.ring(player, small);
			rung++;
			check(EpicService.state(server).bellsRung() == rung, "the " + great.id + " great bell rings: " + EpicService.state(server));
			if (rung == 2) {
				tick(server, 20);
				check(EpicService.state(server).at() == EpicStep.DEFEND, "a rift tears beside the capital: " + EpicService.state(server).at());
				for (Domain rift : DomainService.of(server, Domain.Kind.RIFT)) {
					if (rift.open()) {
						life.shizune.mistinn.rift.RiftService.seal(level, rift);
					}
				}
				tick(server, 20);
				check(EpicService.state(server).at() == EpicStep.BELLS && EpicService.state(server).has(EpicState.RIFTED), "the rift held");
			}
		}
		check(EpicService.state(server).at() == EpicStep.GREAT_BELL, "all four rung: " + EpicService.state(server).at());

		// the capital's great bell
		go(player, 0, 30);
		build(server, "capital_tower");
		BlockPos great = capitalBell(level);
		check(level.getBlockState(great).is(Blocks.BELL), "the capital's bell hangs: " + great);
		EpicService.ring(player, great);
		check(EpicService.state(server).at() == EpicStep.CROWN && EpicService.chainBare(server), "the tide out, the chain bare");

		// the crown, and the Helm on it
		BlockPos crown = UdasruPlaces.ANCHOR_CROWN;
		go(player, crown.getX(), crown.getZ() - 30);
		build(server, "crown");
		build(server, "chain");
		int deck = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, crown.getX() + 3, crown.getZ() + 3);
		player.teleportTo(level, crown.getX() + 0.5, deck, crown.getZ() + 4.5, Set.of(), 0.0F, 0.0F, true);
		tick(server, 20);
		check(EpicService.state(server).at() == EpicStep.HELM, "the Helm rises on the crown: " + EpicService.state(server).at());
		var helm = level.getEntitiesOfClass(life.shizune.mistinn.path.RustApostle.class, player.getBoundingBox().inflate(40), a -> a.helm());
		check(helm.size() == 1, "one Helm, in the apostle's whole art: " + helm.size());
		check(helm.getFirst().getMaxHealth() >= 999.0F, "and stronger: " + helm.getFirst().getMaxHealth());
		helm.getFirst().kill(level);
		tick(server, 2);
		check(EpicService.state(server).at() == EpicStep.NAME, "the Helm down, the Chronicler asks for the name");

		// the name
		EpicService.name(player, "§c小石\u0007頭");
		check(EpicService.state(server).at() == EpicStep.ENDED && "小石頭".equals(EpicService.state(server).name()), "written under its name, cleaned: "
			+ EpicService.state(server).name());
	}

	private static LivingEntity beast(final ServerLevel level, final UdasruPlaces.GreatBell bell) {
		return switch (bell) {
			case SALT -> MistinnEntities.SUNKEN_BELL.create(level, EntitySpawnReason.COMMAND);
			case WOODS -> MistinnEntities.SNUFFER.create(level, EntitySpawnReason.COMMAND);
			case RUST -> MistinnEntities.RUST_WHEEL.create(level, EntitySpawnReason.COMMAND);
			case CRYSTAL -> MistinnEntities.CRYSTAL_ELDER.create(level, EntitySpawnReason.COMMAND);
		};
	}

	/** Takes the hunter somewhere and lets the ground there be made. */
	private static void go(final ServerPlayer player, final int x, final int z) {
		ServerLevel level = player.level();
		for (int cx = (x >> 4) - 3; cx <= (x >> 4) + 3; cx++) {
			for (int cz = (z >> 4) - 3; cz <= (z >> 4) + 3; cz++) {
				level.getChunk(cx, cz);
			}
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		player.teleportTo(level, x + 0.5, y, z + 0.5, Set.of(), 0.0F, 0.0F, true);
	}

	/** Lets the epic look at its hunters (it does every half second). */
	private static void tick(final MinecraftServer server, final int times) {
		EpicService.look(server);
	}

	/** Builds a site now, its ground made first. */
	private static void build(final MinecraftServer server, final String id) {
		UdasruSites.Site site = UdasruSites.sites().get(id);
		ServerLevel level = server.overworld();
		for (String before : site.after()) {
			build(server, before);
		}
		for (int cx = (site.minX() >> 4) - 1; cx <= (site.maxX() >> 4) + 1; cx++) {
			for (int cz = (site.minZ() >> 4) - 1; cz <= (site.maxZ() >> 4) + 1; cz++) {
				level.getChunk(cx, cz);
			}
		}
		for (int i = 0; i < 4 && !UdasruSites.built(server).contains(id); i++) {
			UdasruSites.tick(server);
		}
		check(UdasruSites.built(server).contains(id), "built " + id);
	}

	private static BlockPos towerBell(final ServerLevel level, final UdasruPlaces.Tower tower) {
		for (int y = level.getMaxY(); y > level.getMinY(); y--) {
			BlockPos at = new BlockPos(tower.x, y, tower.z);
			if (level.getBlockState(at).is(Blocks.BELL)) {
				return at;
			}
		}
		return new BlockPos(tower.x, 0, tower.z);
	}

	private static BlockPos shrineBell(final ServerLevel level, final int[] shrine) {
		for (BlockPos at : BlockPos.betweenClosed(shrine[0] - 6, level.getMinY() + 40, shrine[1] - 6, shrine[0] + 6, level.getMaxY() - 40, shrine[1] + 7)) {
			if (level.getBlockState(at).is(Blocks.BELL)) {
				return at.immutable();
			}
		}
		return new BlockPos(shrine[0], 0, shrine[1]);
	}

	private static BlockPos capitalBell(final ServerLevel level) {
		for (BlockPos at : BlockPos.betweenClosed(-7, UdasruSites.CAPITAL_Y + 30, -7, 7, UdasruSites.CAPITAL_Y + UdasruBuild.GREAT_TOWER + 20, 7)) {
			if (level.getBlockState(at).is(Blocks.BELL)) {
				return at.immutable();
			}
		}
		return BlockPos.ZERO;
	}

	private static void check(final boolean condition, final String what) {
		if (!condition) {
			throw new AssertionError("failed: " + what);
		}
		System.out.println("[mistinn epic] ok: " + what);
	}
}
