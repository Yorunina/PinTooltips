package snownee.pintooltips;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.obscuria.tooltips.client.TooltipHelper;
import dev.obscuria.fragmentum.client.ClientGroupTooltip;
import dev.obscuria.tooltips.client.TooltipState;
import dev.obscuria.tooltips.client.component.StackBuffer;
import dev.obscuria.tooltips.client.tooltip.TooltipScroll;
import dev.obscuria.tooltips.client.tooltip.layout.ArmorPreviewLayout;
import dev.obscuria.tooltips.client.tooltip.layout.DefaultLayout;
import dev.obscuria.tooltips.client.tooltip.layout.ToolPreviewLayout;
import dev.obscuria.tooltips.client.tooltip.layout.TooltipLayout;
import dev.obscuria.tooltips.config.ClientConfig;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.TieredItem;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import snownee.pintooltips.duck.PTContainerScreen;
import snownee.pintooltips.duck.PTGuiGraphics;
import snownee.pintooltips.mixin.pin.GuiGraphicsAccess;
import snownee.pintooltips.style.TooltipStyle;
import snownee.pintooltips.util.DummyHoveredSlot;

public final class PinnedTooltip implements ClientTooltipPositioner {
	private final TooltipStyle style;
	private final Vector2d position;
	private final Vector2i size;
	private final List<ClientTooltipComponent> components;
	private final @Nullable DummyHoveredSlot hoveredSlot;
	private final Map<Rect2i, ClientTooltipComponent> linesPosition;
	long autoPinnedTimestamp;
	private boolean hovered;
	private ItemStack lastStack;
	private ItemStack actualStack;
	private TooltipLayout<?> layout;
	private TooltipState state;

	public PinnedTooltip(
			TooltipStyle style,
			Vector2d position,
			Vector2i size,
			List<ClientTooltipComponent> components,
			long autoPinnedTimestamp,
			@Nullable DummyHoveredSlot hoveredSlot) {
		this.style = style;
		this.position = position;
		this.size = size;
		this.components = components;
		this.autoPinnedTimestamp = autoPinnedTimestamp;
		this.hoveredSlot = hoveredSlot;
		this.linesPosition = new Reference2ObjectOpenHashMap<>();
		this.lastStack = ItemStack.EMPTY;
		this.actualStack = ItemStack.EMPTY;
		this.layout = new DefaultLayout();
		this.state = new EmptyState();
	}

	public PinnedTooltip(
			TooltipStyle style,
			Vector2d position,
			List<ClientTooltipComponent> components,
			int screenWidth,
			int screenHeight,
			Font font,
			ItemStack itemStack,
			long autoPinnedTimestamp
	) {
		this(
				style,
				position,
				new Vector2i(),
				components,
				autoPinnedTimestamp,
				itemStack.isEmpty() ? null : new DummyHoveredSlot(itemStack.copy()));
		updateSize(screenWidth, screenHeight, font);
	}

	public boolean isHovering(double mouseX, double mouseY) {
		return style.isHovering(this, mouseX, mouseY);
	}

	public void updateSize(int screenWidth, int screenHeight, Font font) {
		style.updateSize(this, screenWidth, screenHeight, font);
	}

	public void render(PinnedTooltipsService service, Screen screen, Font font, GuiGraphics context, int mouseX, int mouseY) {
		context.pose().pushPose();
		updateSize(screen.width, screen.height, font);
		var inContainer = hoveredSlot() != null && screen instanceof PTContainerScreen;
		if (inContainer) {
			((PTContainerScreen) screen).pin_tooltips$setDummyHoveredSlot(hoveredSlot());
		}

		PTGuiGraphics graphics = PTGuiGraphics.of(context);
		graphics.pin_tooltips$setRenderingPinned(true);
		if (hoveredSlot != null) {
			graphics.pin_tooltips$setRenderingItemStack(hoveredSlot.getItem());
		}
		originalRenderTooltips(context, font, components, mouseX, mouseY, this);
		graphics.pin_tooltips$setRenderingPinned(false);

		if (service.hovered == this && !service.dragging) {
			var style = getStyleAt(mouseX, mouseY, font);
			if (style != null) {
				graphics.pin_tooltips$setRenderingPinnedEvent(true);
				context.pose().translate(0, 0, 1);
				context.renderComponentHoverEffect(font, style, mouseX, mouseY);
				graphics.pin_tooltips$setRenderingPinnedEvent(false);
			}
		}

		if (inContainer) {
			((PTContainerScreen) screen).pin_tooltips$dropDummyHoveredSlot();
		}
		context.pose().popPose();
	}

	public boolean originalRenderTooltips(
			GuiGraphics graphics,
			Font font,
			List<ClientTooltipComponent> components,
			int mouseX,
			int mouseY,
			ClientTooltipPositioner positioner) {
		if (!(Boolean) ClientConfig.ENABLED.get()) {
			return false;
		} else if (components.isEmpty()) {
			return false;
		} else if (!perform(components)) {
			((GuiGraphicsAccess) graphics).callRenderTooltipInternal(
					font,
					components,
					(int) position().x(),
					(int) position().y(),
					this);
			return false;
		} else {
			List<ClientTooltipComponent> var14 = new ArrayList(components);
			List<ClientTooltipComponent> var15 = this.layout.rawProcessPreWrap(state, var14, font);
			var15 = TooltipHelper.wrapLines(graphics, var15, font);
			var15 = this.layout.rawProcessPostWrap(this.state, var15, font);
			Integer margin = ClientConfig.CONTENT_MARGIN.get();
			int width = margin * 2 + TooltipHelper.widthOf(var15, font);
			int height = margin * 2 + TooltipHelper.heightOf(var15) - 2;
			Vector2ic pos = positioner.positionTooltip(graphics.guiWidth(), graphics.guiHeight(), mouseX, mouseY, width, height);
			TooltipScroll.update(this.state, 6 + height, graphics.guiHeight());
			graphics.pose().pushPose();
			graphics.pose().translate(0.0F, TooltipScroll.getScroll(), 400.0F);
			graphics.flush();
			this.state.renderPanel(graphics, pos, width, height);
			this.state.renderEffects(graphics, pos, width, height);
			graphics.flush();
			graphics.pose().pushPose();
			graphics.pose().translate(0.0F, 0.0F, 2.0F);
			this.state.renderFrame(graphics, pos, width, height);
			graphics.pose().popPose();
			graphics.flush();
			int componentX = margin + pos.x();
			int componentY = margin + pos.y();

			for (ClientTooltipComponent component : var15) {
				component.renderText(font, componentX, componentY, graphics.pose().last().pose(), graphics.bufferSource());
				component.renderImage(font, componentX, componentY, graphics);
				componentY += component.getHeight();
			}

			graphics.pose().popPose();
			this.lastStack = this.actualStack;
			this.actualStack = ItemStack.EMPTY;
			this.state.update();
			return true;
		}
	}

	private boolean perform(List<ClientTooltipComponent> components) {
		StackBuffer buffer = ClientGroupTooltip.findFirst(components, StackBuffer.class);
		if (buffer == null) {
			return false;
		} else {
			this.actualStack = buffer.stack();
			if (ItemStack.isSameItemSameTags(lastStack, actualStack)) {
				return true;
			} else {
				this.layout = shouldShowArmorPreview(actualStack) ?
						ArmorPreviewLayout.INSTANCE :
						(shouldShowToolPreview(actualStack) ? ToolPreviewLayout.INSTANCE : DefaultLayout.INSTANCE);
				this.state = this.layout.extractState(actualStack);
				return true;
			}
		}
	}

	private static boolean shouldShowArmorPreview(ItemStack stack) {
		if (!(Boolean) ClientConfig.ARMOR_PREVIEW_ENABLED.get()) {
			return false;
		} else if (ClientConfig.isInArmorPreviewBlacklist(stack.getItem())) {
			return false;
		} else {
			return stack.getItem() instanceof ArmorItem || ClientConfig.isInArmorPreviewWhitelist(stack.getItem());
		}
	}

	private static boolean shouldShowToolPreview(ItemStack stack) {
		if (!(Boolean) ClientConfig.TOOL_PREVIEW_ENABLED.get()) {
			return false;
		} else if (ClientConfig.isInToolPreviewBlacklist(stack.getItem())) {
			return false;
		} else {
			return stack.getItem() instanceof TieredItem || ClientConfig.isInToolPreviewWhitelist(stack.getItem());
		}
	}

	public void setPosition(int screenWidth, int screenHeight, double x, double y) {
		position.set(x, y);
	}

	public Vector2d position() {return position;}

	public Vector2ic size() {return size;}

	public List<ClientTooltipComponent> components() {return components;}

	public @Nullable DummyHoveredSlot hoveredSlot() {return hoveredSlot;}

	public @Nullable Style getStyleAt(double mouseX, double mouseY, Font font) {
		return style.getStyleAt(this, mouseX, mouseY, font);
	}

	@Override
	public @NotNull Vector2ic positionTooltip(
			int screenWidth,
			int screenHeight,
			int mouseX,
			int mouseY,
			int tooltipWidth,
			int tooltipHeight) {
		return new Vector2i((int) position.x, (int) position.y);
	}

	public void hovered() {
		hovered = true;
	}

	public boolean isHovered() {
		return hovered;
	}

	public Map<Rect2i, ClientTooltipComponent> linesPosition() {
		return linesPosition;
	}

	public TooltipStyle style() {
		return style;
	}

	public void setSize(int width, int height) {
		size.set(width, height);
	}

	private static final class EmptyState extends TooltipState {
		private EmptyState() {
			super(ItemStack.EMPTY);
		}
	}
}
