package dev.analyzer.ui;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.util.List;
import java.util.Map;

/** A home-backed native page; its widgets still own Minecraft's real actions and data. */
public interface HomePage {
    HomeScreen home();
    String section();
    List<ButtonWidget> navigationButtons();
    Map<String, ClickableWidget> controls();
}
