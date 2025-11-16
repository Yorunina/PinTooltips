package snownee.pintooltips;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.minecraftforge.api.distmarker.Dist;

import org.joml.Vector2ic;
import org.slf4j.Logger;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;

import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import snownee.pintooltips.util.DefaultDescriptions;

@Mod(PinTooltips.ID)
public class PinTooltips {
	public static final String ID = "pin_tooltips";
	public static final Logger LOGGER = LogUtils.getLogger();
	public static final Component CLICK_TO_COPY = Component.translatable("chat.copy.click").withStyle(ChatFormatting.GRAY);
	public static final HoverEvent CLICK_TO_COPY_EVENT = new HoverEvent(HoverEvent.Action.SHOW_TEXT, CLICK_TO_COPY);
	private static int keyPressedFrames = -1;
	private static long lastRenderTooltipTime;
	private static int lastMouseX;
	private static int lastMouseY;
	public static long lastMouseMovedTime;
	private static boolean hasTooltipInThisFrame;

	public static final KeyMapping GRAB_KEY = new KeyMapping(
			"key.pin_tooltips.pin",
			InputConstants.Type.KEYSYM,
			InputConstants.KEY_LALT,
			"key.categories.misc"
	);

	public static File configDirectory = FMLPaths.CONFIGDIR.get().toFile();
	private static boolean validateTranslations = !FMLLoader.isProduction();

	public PinTooltips() {
	    MinecraftForge.EVENT_BUS.register(this);
	    PinTooltipsConfig.save();
	}

	// 添加鼠标拖动事件处理方法
	@SubscribeEvent
	public void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
	    PinTooltips.onDrag(event.getScreen(), event.getMouseButton(), event.getDragX(), event.getDragY());
	}

	public static int getMaxZOffset() {
		return 6000;
	}

	@Mod.EventBusSubscriber(modid = ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
	public static class ClientModEvents {
		@SubscribeEvent
		public static void onKeyRegister(RegisterKeyMappingsEvent event) {
			event.register(GRAB_KEY);
		}
	}


	@SubscribeEvent
	public void onScreenInit(ScreenEvent.Init.Post event) {
		Screen screen = event.getScreen();
		
		if (PinTooltipsConfig.get().screenBlacklist().contains(screen.getClass().getName())) {
			return;
		}

		if (validateTranslations && Minecraft.getInstance().level != null &&
				Minecraft.getInstance().level.registryAccess().registry(Registries.ENCHANTMENT).isPresent()) {
			validateTranslations = false;
			validateTranslations();
		}
		lastMouseMovedTime = 0;
	}

	@SubscribeEvent
	public void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
		Screen screen = event.getScreen();
		if (!shouldShowTooltips(screen)) return;
		
		if (GRAB_KEY.matches(event.getKeyCode(), event.getScanCode())) {
			GRAB_KEY.setDown(true);
			if (keyPressedFrames < 0) {
				keyPressedFrames = 0;
			}
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onKeyReleased(ScreenEvent.KeyReleased.Pre event) {
		Screen screen = event.getScreen();
		var service = PinnedTooltipsService.INSTANCE;
		
		if (!shouldShowTooltips(screen)) return;
		
		if (service.autoPinnedTooltip() != null && service.focused != service.autoPinnedTooltip()) {
			service.unpin(service.autoPinnedTooltip());
		}
		if (GRAB_KEY.matches(event.getKeyCode(), event.getScanCode())) {
			GRAB_KEY.setDown(false);
			keyPressedFrames = -1;
		}
		event.setCanceled(true);
	}

	@SubscribeEvent
	public void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
		Screen screen = event.getScreen();
		var service = PinnedTooltipsService.INSTANCE;
		
		if (!shouldShowTooltips(screen)) return;
		
		int button = event.getButton();
		if (button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_MIDDLE) {
			return;
		}
		if (button == InputConstants.MOUSE_BUTTON_MIDDLE && GRAB_KEY.isDown()) {
			service.clearTooltips();
			event.setCanceled(true);
			return;
		}
		var focused = service.hovered;
		if (focused != null) {
			if (button == InputConstants.MOUSE_BUTTON_LEFT) {
				service.focused = focused;
				service.placeOnTop(focused);
			} else {
				service.unpin(focused);
			}
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onMouseButtonReleased(ScreenEvent.MouseButtonReleased.Pre event) {
		Screen screen = event.getScreen();
		var service = PinnedTooltipsService.INSTANCE;
		
		if (!shouldShowTooltips(screen)) return;
		
		var focused = service.focused;
		var dragging = service.dragging;
		service.clearStates();
		if (service.autoPinnedTooltip() != null && focused != service.autoPinnedTooltip()) {
			service.unpin(service.autoPinnedTooltip());
		}
		if (focused != null) {
			if (event.getButton() == InputConstants.MOUSE_BUTTON_LEFT && !dragging) {
				Style style = focused.getStyleAt(event.getMouseX(), event.getMouseY(), Minecraft.getInstance().font);
				if (style != null) {
					screen.handleComponentClicked(style);
				}
			}
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onScreenRender(ScreenEvent.Render.Post event) {
		Screen screen = event.getScreen();
		var service = PinnedTooltipsService.INSTANCE;
		
		if (!shouldShowTooltips(screen)) return;
		
		var context = event.getGuiGraphics();
		int mouseX = event.getMouseX();
		int mouseY = event.getMouseY();
		
		if (hasTooltipInThisFrame) {
			hasTooltipInThisFrame = false;
			if (lastMouseX != mouseX || lastMouseY != mouseY) {
				lastMouseX = mouseX;
				lastMouseY = mouseY;
				lastMouseMovedTime = System.currentTimeMillis();
			}
		} else {
			lastMouseX = 0;
			lastMouseY = 0;
			lastMouseMovedTime = 0;
		}

		service.hovered = service.findHovered(mouseX, mouseY);
		var font = Minecraft.getInstance().font;
		var zOffset = 1;
		for (var tooltip : service.tooltips()) {
			context.pose().pushPose();
			context.pose().translate(0, 0, zOffset);
			tooltip.render(service, screen, font, context, mouseX, mouseY);
			context.pose().popPose();
			zOffset = Math.min(getMaxZOffset() - 1, zOffset + 400);
		}
		PinnedTooltip autoPinnedTooltip = service.autoPinnedTooltip();
		if (autoPinnedTooltip != null && autoPinnedTooltip.isHovered() && autoPinnedTooltip != service.hovered) {
			service.unpin(autoPinnedTooltip);
		}
		if (service.hovered != null) {
			service.hovered.hovered();
			Component hint;
			if (!GRAB_KEY.isUnbound() && System.currentTimeMillis() / 2000 % 2 == 0) {
				hint = Component.translatable("gui.pin_tooltips.clear_hint", GRAB_KEY.getTranslatedKeyMessage());
			} else {
				hint = Component.translatable("gui.pin_tooltips.unpin_hint");
			}
			context.drawCenteredString(font, hint, screen.width / 2, 4, 0xAAAAAA);
		}
	}

	@SubscribeEvent
	public void onScreenClosing(ScreenEvent.Closing event) {
		var service = PinnedTooltipsService.INSTANCE;
		
		PinnedTooltip tooltip = service.autoPinnedTooltip();
		if (tooltip != null) {
			service.unpin(tooltip);
		}
	}

	@SubscribeEvent
	public void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
		PinnedTooltipsService.INSTANCE.clearStates();
	}

	private static void validateTranslations() {
		LOGGER.info("Validating translations...");
		PinTooltipsConfig.setOverride(new PinTooltipsConfig(true, false, false, 1500, Set.of()));
		RegistryAccess registryAccess = Objects.requireNonNull(Minecraft.getInstance().level).registryAccess();
		List<ResourceLocation> missingEnchantments = Lists.newArrayList();
		for (Holder.Reference<Enchantment> holder : registryAccess.registryOrThrow(Registries.ENCHANTMENT).holders().toList()) {
			if (DefaultDescriptions.forEnchantmentRaw(holder) == null) {
				missingEnchantments.add(holder.key().location());
			}
		}
		if (!missingEnchantments.isEmpty()) {
			String msg = "Missing enchantment descriptions: %s".formatted(missingEnchantments);
			Minecraft.getInstance().getChatListener().handleSystemMessage(Component.literal(msg).withStyle(ChatFormatting.DARK_RED), false);
		}
		List<ResourceLocation> missingEffects = Lists.newArrayList();
		for (Holder.Reference<MobEffect> holder : registryAccess.registryOrThrow(Registries.MOB_EFFECT).holders().toList()) {
			if (DefaultDescriptions.forStatusEffectRaw(holder.value()) == null) {
				missingEffects.add(holder.key().location());
			}
		}
		if (!missingEffects.isEmpty()) {
			String msg = "Missing status effect descriptions: %s".formatted(missingEffects);
			Minecraft.getInstance().getChatListener().handleSystemMessage(Component.literal(msg).withStyle(ChatFormatting.DARK_RED), false);
		}
		PinTooltipsConfig.setOverride(null);
		LOGGER.info("Translations validated.");
	}

	public static void onDrag(Screen screen, int button, double deltaX, double deltaY) {
		var service = PinnedTooltipsService.INSTANCE;
		var focused = service.focused;
		if (button == InputConstants.MOUSE_BUTTON_LEFT && focused != null) {
			if (!service.dragging) {
				service.storedDragX += deltaX;
				service.storedDragY += deltaY;
				if (Math.abs(service.storedDragX) + Math.abs(service.storedDragY) > 5) {
					service.dragging = true;
					deltaX = service.storedDragX;
					deltaY = service.storedDragY;
				}
			}
			if (service.dragging) {
				var position = focused.position();
				focused.setPosition(screen.width, screen.height, position.x() + deltaX, position.y() + deltaY);
			}
		} else if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
			service.unpin(service.hovered);
		}
	}

	public static void onRenderTooltip(
			Font font,
			List<ClientTooltipComponent> components,
			Vector2ic position,
			ItemStack itemStack) {
		var service = PinnedTooltipsService.INSTANCE;
		if (service.focused != null) {
			return;
		}

		long time = System.currentTimeMillis();

		if (keyPressedFrames < 0) {
			int delay = PinTooltipsConfig.get().hoveringAutoPinDelay();
			if (delay >= 0) {
				hasTooltipInThisFrame = true;
				if (lastMouseMovedTime > 0 && time - lastMouseMovedTime >= delay) {
					service.pin(position, components, font, itemStack, time);
				}
			}
			return;
		}

		// there can be multiple renderTooltip calls in a single frame, so we need to skip some
		if (time - lastRenderTooltipTime < 10) {
			return;
		}
		lastRenderTooltipTime = time;

		// skip the first frame to skip the deferred tooltip
		if (keyPressedFrames++ != 1) {
			return;
		}

		service.pin(position, components, font, itemStack, -1);
	}

	public static boolean isGrabbing() {
		int delay = PinTooltipsConfig.get().hoveringAutoPinDelay();
		return GRAB_KEY.isDown() || delay >= 0 && lastMouseMovedTime > 0 && System.currentTimeMillis() - lastMouseMovedTime >= delay;
	}

	public static boolean shouldShowTooltips(Screen screen) {
		return Minecraft.getInstance().screen == screen;
	}
}