package life.shizune.mistinn.client.worldmap;

import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.registry.MistinnItems;
import life.shizune.mistinn.supply.SupplyService;
import life.shizune.mistinn.terra.HomesteadTravel;
import life.shizune.mistinn.terra.Settlements;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The hunter's map: the land they have seen, drawn from what this computer remembers, with the
 * homesteads, the Terra highlands they have come upon, the sky rifts and their scars, a standing
 * bounty and a supply drop marked on it. The wheel zooms about the pointer and a drag moves it.
 * A homestead under the pointer says how far it is and whether the way is open; a click sets off.
 */
public class ExploredMapScreen extends Screen {
	/** The zooms, in pixels a block. */
	private static final float[] ZOOMS = {0.125F, 0.25F, 0.5F, 1.0F, 2.0F, 4.0F};
	private static final int BACKGROUND = 0xFF101317;
	private static final int HOME = 0xFF5CE1E6;
	private static final int HOME_DARK = 0xFF0C2C30;
	private static final int TERRA = 0x9963C7C9;
	private static final int RIFT = 0xFFE0402A;
	private static final int SCAR = 0xAA8A2A20;
	/** How near the pointer has to be to a homestead to pick it. */
	private static final int PICK = 7;

	private double centreX;
	private double centreZ;
	private int zoom = 3;

	public ExploredMapScreen() {
		super(Component.translatable("gui.mistinn.map.title"));
	}

	@Override
	protected void init() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && this.centreX == 0.0 && this.centreZ == 0.0) {
			this.centreX = player.getX();
			this.centreZ = player.getZ();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private float scale() {
		return ZOOMS[this.zoom];
	}

	/** Where a world column is drawn on the screen. */
	public double[] toScreen(final double x, final double z) {
		return new double[] {this.width / 2.0 + (x - this.centreX) * this.scale(), this.height / 2.0 + (z - this.centreZ) * this.scale()};
	}

	private double[] toWorld(final double sx, final double sy) {
		return new double[] {this.centreX + (sx - this.width / 2.0) / this.scale(), this.centreZ + (sy - this.height / 2.0) / this.scale()};
	}

	// ── Input ─────────────────────────────────────────────────────

	@Override
	public boolean mouseScrolled(final double mx, final double my, final double scrollX, final double scrollY) {
		int next = Mth.clamp(this.zoom + (scrollY > 0 ? 1 : scrollY < 0 ? -1 : 0), 0, ZOOMS.length - 1);
		if (next != this.zoom) {
			// zoom about the pointer: the column under it stays under it
			double[] before = this.toWorld(mx, my);
			this.zoom = next;
			double[] after = this.toWorld(mx, my);
			this.centreX += before[0] - after[0];
			this.centreZ += before[1] - after[1];
		}
		return true;
	}

	@Override
	public boolean mouseDragged(final MouseButtonEvent event, final double dx, final double dy) {
		this.centreX -= dx / this.scale();
		this.centreZ -= dy / this.scale();
		return true;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (life.shizune.mistinn.client.epic.EpicMap.click(this, event.x(), event.y())) {
			return true;
		}
		Settlements.Settlement home = this.homeUnder(event.x(), event.y());
		if (home != null && home.lit()) {
			ClientPlayNetworking.send(new HomesteadTravel.TravelPayload(home.domain()));
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F));
			this.onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** The homestead (or sleeping settlement the hunter has seen) under the pointer. */
	public Settlements.@Nullable Settlement homeUnder(final double mx, final double my) {
		for (Settlements.Settlement settlement : settlements()) {
			double[] at = this.toScreen(settlement.hub().getX() + 0.5, settlement.hub().getZ() + 0.5);
			if (Math.abs(mx - at[0]) <= PICK && Math.abs(my - at[1]) <= PICK) {
				return settlement;
			}
		}
		return null;
	}

	/** The settlements the map shows: every homestead, and the others once their ground has been seen. */
	private static List<Settlements.Settlement> settlements() {
		Minecraft mc = Minecraft.getInstance();
		List<Settlements.Settlement> view = mc.level == null ? null : mc.level.getAttached(Settlements.VIEW);
		List<Settlements.Settlement> shown = new ArrayList<>();
		if (view != null) {
			for (Settlements.Settlement settlement : view) {
				if (settlement.lit() || ExploredMap.seen(settlement.hub().getX(), settlement.hub().getZ())) {
					shown.add(settlement);
				}
			}
		}
		return shown;
	}

	// ── Drawing ───────────────────────────────────────────────────

	@Override
	public void extractRenderState(final GuiGraphicsExtractor g, final int mouseX, final int mouseY, final float a) {
		g.fill(0, 0, this.width, this.height, BACKGROUND);
		life.shizune.mistinn.client.epic.EpicMap.base(g, this);
		this.land(g);
		this.domains(g);
		this.marks(g);
		life.shizune.mistinn.client.epic.EpicMap.icons(g, this);
		for (Settlements.Settlement settlement : settlements()) {
			double[] at = this.toScreen(settlement.hub().getX() + 0.5, settlement.hub().getZ() + 0.5);
			drawHome(g, (int) at[0], (int) at[1], settlement.lit());
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			double[] at = this.toScreen(player.getX(), player.getZ());
			drawHunter(g, (int) at[0], (int) at[1], player.getYRot());
		}
		g.fill(0, 0, this.width, 18, 0xC0101317);
		g.text(this.font, this.title, 8, 5, 0xFFE8DCC4, false);
		Component help = Component.translatable("gui.mistinn.map.help");
		g.text(this.font, help, this.width - this.font.width(help) - 8, 5, 0xFF9AA4AA, false);
		Component status = this.status();
		if (status != null) {
			g.fill(0, this.height - 18, this.width, this.height, 0xC0101317);
			g.text(this.font, status, 8, this.height - 13, 0xFF9AA4AA, false);
		}
		List<Component> tower = life.shizune.mistinn.client.epic.EpicMap.tooltip(this, mouseX, mouseY);
		if (tower != null) {
			g.setTooltipForNextFrame(this.font, tower, java.util.Optional.empty(), mouseX, mouseY);
		}
		Settlements.Settlement under = this.homeUnder(mouseX, mouseY);
		if (under != null) {
			g.setTooltipForNextFrame(this.font, this.tooltip(under), java.util.Optional.empty(), mouseX, mouseY);
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	/** The land seen, region by region. */
	private void land(final GuiGraphicsExtractor g) {
		double[] topLeft = this.toWorld(0, 0);
		double[] bottomRight = this.toWorld(this.width, this.height);
		int r0x = Math.floorDiv((int) Math.floor(topLeft[0]), ExploredMap.REGION);
		int r0z = Math.floorDiv((int) Math.floor(topLeft[1]), ExploredMap.REGION);
		int r1x = Math.floorDiv((int) Math.floor(bottomRight[0]), ExploredMap.REGION);
		int r1z = Math.floorDiv((int) Math.floor(bottomRight[1]), ExploredMap.REGION);
		for (int rx = r0x; rx <= r1x; rx++) {
			for (int rz = r0z; rz <= r1z; rz++) {
				Identifier texture = ExploredMap.texture(rx, rz);
				if (texture == null) {
					continue;
				}
				double[] from = this.toScreen(rx * (double) ExploredMap.REGION, rz * (double) ExploredMap.REGION);
				double[] to = this.toScreen((rx + 1) * (double) ExploredMap.REGION, (rz + 1) * (double) ExploredMap.REGION);
				g.blit(texture, (int) Math.floor(from[0]), (int) Math.floor(from[1]), (int) Math.ceil(to[0]), (int) Math.ceil(to[1]), 0.0F, 1.0F, 0.0F, 1.0F);
			}
		}
	}

	/** The Terra highlands the hunter has come upon, the open rifts and their scars. */
	private void domains(final GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		List<Domain> view = mc.level == null ? null : mc.level.getAttached(DomainService.VIEW);
		if (view == null) {
			return;
		}
		for (Domain domain : view) {
			double[] at = this.toScreen(domain.centre().getX() + 0.5, domain.centre().getZ() + 0.5);
			double radius = domain.radius() * this.scale();
			if (domain.kind() == Domain.Kind.TERRA) {
				if (!seenAny(domain)) {
					continue;
				}
				ring(g, at[0], at[1], radius, TERRA);
				Component name = Component.translatable("zone.mistinn.terra");
				g.text(this.font, name, (int) at[0] - this.font.width(name) / 2, (int) at[1] - 4, 0xFFBFEFF0, true);
			} else if (domain.open()) {
				ring(g, at[0], at[1], radius, RIFT);
				drawRift(g, (int) at[0], (int) at[1]);
			} else {
				ring(g, at[0], at[1], radius, SCAR);
			}
		}
	}

	/** Whether any of a highland's ground has been seen: its middle, or a ring round it. */
	private static boolean seenAny(final Domain domain) {
		for (int i = 0; i <= 16; i++) {
			double a = i * Math.PI / 8.0;
			double r = i == 16 ? 0.0 : domain.radius() * 0.8;
			if (ExploredMap.seen((int) (domain.centre().getX() + Math.cos(a) * r), (int) (domain.centre().getZ() + Math.sin(a) * r))) {
				return true;
			}
		}
		return false;
	}

	/** A standing bounty and a supply barrel. */
	private void marks(final GuiGraphicsExtractor g) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		BlockPos bounty = player.getAttached(life.shizune.mistinn.bounty.BountyService.MARK);
		if (bounty != null) {
			double[] at = this.toScreen(bounty.getX() + 0.5, bounty.getZ() + 0.5);
			for (int i = -3; i <= 3; i++) {
				g.fill((int) at[0] + i, (int) at[1] + i, (int) at[0] + i + 1, (int) at[1] + i + 1, RIFT);
				g.fill((int) at[0] + i, (int) at[1] - i, (int) at[0] + i + 1, (int) at[1] - i + 1, RIFT);
			}
		}
		SupplyService.Drop drop = player.getAttached(SupplyService.DROP);
		if (drop != null) {
			double[] at = this.toScreen(drop.at().getX() + 0.5, drop.at().getZ() + 0.5);
			g.fill((int) at[0] - 3, (int) at[1] - 3, (int) at[0] + 4, (int) at[1] + 4, 0xFF1A1008);
			g.fill((int) at[0] - 2, (int) at[1] - 2, (int) at[0] + 3, (int) at[1] + 3, 0xFFB07A3C);
		}
	}

	private static void ring(final GuiGraphicsExtractor g, final double cx, final double cy, final double radius, final int colour) {
		int steps = Mth.clamp((int) (radius * 0.8), 24, 240);
		for (int i = 0; i < steps; i += 2) {
			double angle = i * Math.PI * 2.0 / steps;
			int x = (int) (cx + Math.cos(angle) * radius);
			int y = (int) (cy + Math.sin(angle) * radius);
			g.fill(x, y, x + 1, y + 1, colour);
		}
	}

	/** A homestead: a little house, glowing teal when its hub is awake, dark while it sleeps. */
	private static void drawHome(final GuiGraphicsExtractor g, final int x, final int y, final boolean lit) {
		int body = lit ? HOME : 0xFF6E7A7E;
		g.fill(x - 4, y - 1, x + 5, y + 5, 0xFF0A0D10);
		g.fill(x - 3, y, x + 4, y + 4, body);
		for (int i = 0; i <= 4; i++) {
			g.fill(x - 4 + i, y - 1 - i, x + 5 - i, y - i, 0xFF0A0D10);
			g.fill(x - 3 + i, y - 1 - i + 1, x + 4 - i, y - i + 1, body);
		}
		g.fill(x, y + 2, x + 1, y + 4, lit ? HOME_DARK : 0xFF2A2E30);
	}

	private static void drawRift(final GuiGraphicsExtractor g, final int x, final int y) {
		int[][] zig = {{-5, -1}, {-3, 1}, {-1, -2}, {1, 2}, {3, -1}, {5, 1}};
		for (int[] p : zig) {
			g.fill(x + p[0] - 1, y + p[1] - 1, x + p[0] + 2, y + p[1] + 2, 0xFF1A0806);
		}
		for (int[] p : zig) {
			g.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, RIFT);
		}
	}

	/** The hunter: a white arrow, pointing the way they face. */
	private static void drawHunter(final GuiGraphicsExtractor g, final int x, final int y, final float yaw) {
		double a = Math.toRadians(yaw);
		double fx = -Math.sin(a);
		double fz = Math.cos(a);
		for (int i = -1; i <= 4; i++) {
			int px = (int) Math.round(x + fx * i);
			int py = (int) Math.round(y + fz * i);
			g.fill(px - 1, py - 1, px + 2, py + 2, 0xFF0A0D10);
		}
		g.fill(x - 2, y - 2, x + 3, y + 3, 0xFF0A0D10);
		g.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
		for (int i = 1; i <= 4; i++) {
			int px = (int) Math.round(x + fx * i);
			int py = (int) Math.round(y + fz * i);
			g.fill(px, py, px + 1, py + 1, 0xFFFFFFFF);
		}
	}

	/** What the way says, at the foot of the map: when the next journey can be made, and whether there is a chip to make it. */
	private @Nullable Component status() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		long wait = player.getAttachedOrElse(HomesteadTravel.READY_AT, 0L) - player.level().getGameTime();
		if (wait > 0) {
			return Component.translatable("gui.mistinn.map.cooling", (wait + 19) / 20);
		}
		int chips = player.getInventory().countItem(MistinnItems.LATTICE_CHIP);
		return Component.translatable("gui.mistinn.map.ready", chips);
	}

	private List<Component> tooltip(final Settlements.Settlement settlement) {
		LocalPlayer player = Minecraft.getInstance().player;
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(settlement.lit() ? "gui.mistinn.map.homestead" : "gui.mistinn.map.settlement"));
		if (player != null) {
			int distance = (int) Math.round(Math.hypot(player.getX() - settlement.hub().getX(), player.getZ() - settlement.hub().getZ()));
			lines.add(Component.translatable("gui.mistinn.map.distance", distance));
		}
		lines.add(Component.translatable(settlement.lit() ? "gui.mistinn.map.go" : "gui.mistinn.map.asleep"));
		return lines;
	}
}
