package ee.lastpagans.settlementprototype.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/** A real expandable, scrollable selection widget used by campaign screens. */
public final class DropdownWidget extends AbstractWidget {
    private final List<String> options;
    private final Consumer<String> listener;
    private int selectedIndex;
    private boolean expanded;
    private int scroll;
    private final int visibleRows;

    public DropdownWidget(int x, int y, int width, int height, List<String> options, int selectedIndex, Consumer<String> listener) {
        super(x, y, width, height, Component.empty());
        this.options = List.copyOf(options);
        this.selectedIndex = options.isEmpty() ? -1 : Math.max(0, Math.min(selectedIndex, options.size() - 1));
        this.listener = listener;
        this.visibleRows = 6;
        updateMessage();
    }

    public String value() { return selectedIndex < 0 ? "" : options.get(selectedIndex); }
    public boolean isExpanded() { return expanded; }
    public void close() { expanded = false; }

    private void updateMessage() { setMessage(Component.literal((value().isBlank() ? "Select…" : value()) + " ▼")); }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int border = active ? 0xFFB8A16D : 0xFF555555;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, border);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xFF2B2925);
        graphics.drawString(Minecraft.getInstance().font, getMessage(), getX() + 6, getY() + 6, active ? 0xFFFFFFFF : 0xFF888888, false);
        if (!expanded) return;
        int rows = Math.min(visibleRows, options.size() - scroll);
        int bottom = getY() + height + rows * height;
        graphics.fill(getX(), getY() + height, getX() + width, bottom, 0xFFB8A16D);
        for (int row = 0; row < rows; row++) {
            int index = scroll + row;
            int y = getY() + height + row * height;
            boolean hover = mouseX >= getX() && mouseX < getX() + width && mouseY >= y && mouseY < y + height;
            graphics.fill(getX() + 1, y + 1, getX() + width - 1, y + height - 1,
                    index == selectedIndex ? 0xFF564A34 : hover ? 0xFF45413A : 0xFF242424);
            graphics.drawString(Minecraft.getInstance().font, options.get(index), getX() + 6, y + 6, 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0) return false;
        if (mouseX >= getX() && mouseX < getX() + width && mouseY >= getY() && mouseY < getY() + height) {
            expanded = !expanded;
            return true;
        }
        if (expanded) {
            int row = (int) ((mouseY - (getY() + height)) / height);
            if (mouseX >= getX() && mouseX < getX() + width && row >= 0 && row < visibleRows) {
                int index = scroll + row;
                if (index < options.size()) {
                    selectedIndex = index;
                    expanded = false;
                    updateMessage();
                    listener.accept(value());
                    return true;
                }
            }
            expanded = false;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!expanded || options.size() <= visibleRows) return false;
        scroll = Math.max(0, Math.min(options.size() - visibleRows, scroll + (delta < 0 ? 1 : -1)));
        return true;
    }


    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && expanded) {
            expanded = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            if (!expanded) {
                expanded = true;
            } else if (selectedIndex >= 0) {
                expanded = false;
                updateMessage();
                listener.accept(value());
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP) {
            if (options.isEmpty()) return true;
            expanded = true;
            int direction = keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1;
            selectedIndex = Math.floorMod(selectedIndex + direction, options.size());
            if (selectedIndex < scroll) scroll = selectedIndex;
            if (selectedIndex >= scroll + visibleRows) scroll = selectedIndex - visibleRows + 1;
            updateMessage();
            listener.accept(value());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
