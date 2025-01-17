package pro.mikey.fabric.xray;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.ItemTags;
import net.minecraft.tag.TagKey;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryEntryList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;
import pro.mikey.fabric.xray.records.BasicColor;
import pro.mikey.fabric.xray.records.BlockEntry;
import pro.mikey.fabric.xray.records.BlockGroup;
import pro.mikey.fabric.xray.render.RenderOutlines;
import pro.mikey.fabric.xray.screens.forge.GuiOverlay;
import pro.mikey.fabric.xray.screens.forge.GuiSelectionScreen;
import pro.mikey.fabric.xray.storage.Stores;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class XRay implements ModInitializer {

    public static final String MOD_ID = "advanced-xray-fabric";
    public static final String PREFIX_GUI = String.format("%s:textures/gui/", MOD_ID);
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private final KeyBinding xrayButton = new KeyBinding("keybinding.enable_xray", GLFW.GLFW_KEY_X, "category.xray");

    private final KeyBinding guiButton = new KeyBinding("keybinding.open_gui", GLFW.GLFW_KEY_G, "category.xray");

    // private final KeyBinding wireButton = new KeyBinding("keybinding.enable_wire", GLFW.GLFW_KEY_V, "category.xray");

    @Override
    public void onInitialize() {
        LOGGER.info("XRay mod has been initialized");

        Stores.load();

        ClientTickEvents.END_CLIENT_TICK.register(this::clientTickEvent);
        ClientLifecycleEvents.CLIENT_STOPPING.register(this::gameClosing);
        HudRenderCallback.EVENT.register(GuiOverlay::RenderGameOverlayEvent);

        WorldRenderEvents.LAST.register(RenderOutlines::render);
        PlayerBlockBreakEvents.AFTER.register(ScanController::blockBroken);

        KeyBindingHelper.registerKeyBinding(this.xrayButton);
        KeyBindingHelper.registerKeyBinding(this.guiButton);
    }

    /**
     * Upon game closing, attempt to save our json stores. This means we can be a little lazy with how
     * we go about saving throughout the rest of the mod
     */
    private void gameClosing(MinecraftClient client) {
        Stores.SETTINGS.write();
        Stores.BLOCKS.write();
    }

    /**
     * Used to handle keybindings and fire off threaded scanning tasks
     */
    private void clientTickEvent(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) {
            return;
        }

        // Try and run the task :D
        ScanController.runTask(false);

        while (this.guiButton.wasPressed()) {
            mc.setScreen(new GuiSelectionScreen());
        }

        while (this.xrayButton.wasPressed()) {
            Stores.BLOCKS.updateCache();

            StateSettings stateSettings = Stores.SETTINGS.get();
            stateSettings.setActive(!stateSettings.isActive());

            ScanController.runTask(true);

            // mc.player.sendMessage(new TranslatableText("message.xray_" + (!stateSettings.isActive() ? "deactivate" : "active")).formatted(stateSettings.isActive() ? Formatting.GREEN : Formatting.RED), true);
        }
    }
}
