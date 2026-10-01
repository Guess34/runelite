/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.config;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.SwingUtilities;
import net.runelite.api.MenuAction;
import net.runelite.client.config.ChatColorConfig;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.PluginHubStatusChanged;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.externalplugins.PluginHubStatus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "Configuration",
	hidden = true // prevent users from disabling
)
public class ConfigPlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Provider<PluginListPanel> pluginListPanelProvider;

	@Inject
	private Provider<TopLevelConfigPanel> topLevelConfigPanelProvider;

	@Inject
	private ConfigManager configManager;

	@Inject
	private RuneLiteConfig runeLiteConfig;

	@Inject
	private ChatColorConfig chatColorConfig;

	@Inject
	private ExternalPluginManager externalPluginManager;

	private TopLevelConfigPanel topLevelConfigPanel;

	private static final String[][] BADGE_GLYPHS = {
		{"###", "#.#", "#.#", "#.#", "###"},
		{".#.", "##.", ".#.", ".#.", "###"},
		{"###", "..#", "###", "#..", "###"},
		{"###", "..#", ".##", "..#", "###"},
		{"#.#", "#.#", "###", "..#", "..#"},
		{"###", "#..", "###", "..#", "###"},
		{"###", "#..", "###", "#.#", "###"},
		{"###", "..#", ".#.", ".#.", ".#."},
		{"###", "#.#", "###", "#.#", "###"},
		{"###", "#.#", "###", "..#", "###"},
		{".#.", ".#.", ".#.", "...", ".#."},
	};

	private NavigationButton navButton;
	private BufferedImage icon;

	@Override
	protected void startUp() throws Exception
	{
		PluginListPanel pluginListPanel = pluginListPanelProvider.get();
		pluginListPanel.addFakePlugin(new PluginConfigurationDescriptor(
				"RuneLite", "RuneLite client settings",
				new String[]{"client", "notification", "size", "position", "window", "chrome", "focus", "font", "overlay", "tooltip", "infobox"},
				runeLiteConfig, configManager.getConfigDescriptor(runeLiteConfig)
			),
			new PluginConfigurationDescriptor(
				"Chat Color", "Recolor chat text", new String[]{"colour", "messages"},
				chatColorConfig, configManager.getConfigDescriptor(chatColorConfig)
			));
		pluginListPanel.rebuildPluginList();

		topLevelConfigPanel = topLevelConfigPanelProvider.get();

		icon = ImageUtil.loadImageResource(getClass(), "config_icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Configuration")
			.icon(icon)
			.priority(0)
			.panel(topLevelConfigPanel)
			.build();

		clientToolbar.addNavigation(navButton);
		updateNavigationBadge();
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onPluginHubStatusChanged(PluginHubStatusChanged event)
	{
		updateNavigationBadge();
	}

	private void updateNavigationBadge()
	{
		List<PluginHubStatus> status = externalPluginManager.getPluginHubStatus();
		if (status.isEmpty())
		{
			clientToolbar.updateNavigation(navButton, icon, "Configuration");
			return;
		}

		long problems = status.stream().filter(PluginHubStatus::isProblem).count();
		String tooltip = "Configuration (" + status.size() + (status.size() == 1 ? " plugin needs" : " plugins need") + " attention)";
		Color color = problems > 0 ? ColorScheme.PROGRESS_ERROR_COLOR : ColorScheme.PROGRESS_COMPLETE_COLOR;
		clientToolbar.updateNavigation(navButton, badge(icon, status.size() > 9 ? 10 : status.size(), color), tooltip);
	}

	private static BufferedImage badge(BufferedImage base, int glyph, Color color)
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.drawImage(ImageUtil.resizeImage(base, 16, 16), 0, 0, null);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(color);
		g.fillOval(0, 0, 7, 7);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setColor(Color.WHITE);
		String[] rows = BADGE_GLYPHS[glyph];
		for (int y = 0; y < rows.length; y++)
		{
			for (int x = 0; x < rows[y].length(); x++)
			{
				if (rows[y].charAt(x) == '#')
				{
					g.fillRect(2 + x, 1 + y, 1, 1);
				}
			}
		}
		g.dispose();
		return img;
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked overlayMenuClicked)
	{
		OverlayMenuEntry overlayMenuEntry = overlayMenuClicked.getEntry();
		if (overlayMenuEntry.getMenuAction() == MenuAction.RUNELITE_OVERLAY_CONFIG)
		{
			Overlay overlay = overlayMenuClicked.getOverlay();
			Plugin plugin = overlay.getPlugin();
			if (plugin == null)
			{
				return;
			}

			// Expand config panel for plugin
			SwingUtilities.invokeLater(() ->
			{
				clientToolbar.openPanel(navButton);
				topLevelConfigPanel.openConfigurationPanel(plugin.getName());
			});
		}
	}
}
