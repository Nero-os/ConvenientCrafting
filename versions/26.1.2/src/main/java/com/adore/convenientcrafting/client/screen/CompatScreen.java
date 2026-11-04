package com.adore.convenientcrafting.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Bridges the shared screen handlers to Minecraft's structured input events. */
public abstract class CompatScreen extends Screen {
    private MouseButtonEvent mouseEvent;
    private KeyEvent keyEvent;
    private CharacterEvent characterEvent;
    private boolean doubleClick;

    protected CompatScreen(Component title) { super(title); }

    protected void renderBackground(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
        super.extractBackground(graphics, x, y, partialTick);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.mouseEvent = event;
        this.doubleClick = doubleClick;
        return mouseClicked(event.x(), event.y(), event.button());
    }
    public boolean mouseClicked(double x, double y, int button) {
        return super.mouseClicked(mouseEvent, doubleClick);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        this.mouseEvent = event;
        return mouseReleased(event.x(), event.y(), event.button());
    }
    public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(mouseEvent);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        this.mouseEvent = event;
        return mouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(mouseEvent, dx, dy);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        this.keyEvent = event;
        return keyPressed(event.key(), event.scancode(), event.modifiers());
    }
    public boolean keyPressed(int key, int scancode, int modifiers) {
        return super.keyPressed(keyEvent);
    }
    @Override public boolean charTyped(CharacterEvent event) {
        this.characterEvent = event;
        boolean handled = false;
        for (char character : event.codepointAsString().toCharArray()) handled |= charTyped(character, 0);
        return handled;
    }
    public boolean charTyped(char character, int modifiers) {
        return super.charTyped(characterEvent);
    }
    private static boolean down(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }
    public static boolean hasAltDown() {
        return down(GLFW.GLFW_KEY_LEFT_ALT) || down(GLFW.GLFW_KEY_RIGHT_ALT);
    }
    public static boolean hasShiftDown() {
        return down(GLFW.GLFW_KEY_LEFT_SHIFT) || down(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }
    public static boolean hasControlDown() {
        return net.minecraft.client.input.InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER == GLFW.GLFW_MOD_SUPER
                ? down(GLFW.GLFW_KEY_LEFT_SUPER) || down(GLFW.GLFW_KEY_RIGHT_SUPER)
                : down(GLFW.GLFW_KEY_LEFT_CONTROL) || down(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
    private static boolean shortcut(int key, int expected) {
        return key == expected && hasControlDown() && !hasAltDown() && !hasShiftDown();
    }
    public static boolean isSelectAll(int key) { return shortcut(key, GLFW.GLFW_KEY_A); }
    public static boolean isCopy(int key) { return shortcut(key, GLFW.GLFW_KEY_C); }
    public static boolean isCut(int key) { return shortcut(key, GLFW.GLFW_KEY_X); }
    public static boolean isPaste(int key) { return shortcut(key, GLFW.GLFW_KEY_V); }
}
