/*
 * Copyright (c) 2018, Daniel Teo <https://github.com/takuyakanbr>
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.Getter;
import net.runelite.client.externalplugins.PluginHubStatus;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.SwingUtil;

class PluginListItem extends JPanel implements SearchablePlugin
{
	private static final ImageIcon ON_STAR;
	private static final ImageIcon OFF_STAR;
	private static final ImageIcon UPDATE_ICON = statusIcon(ColorScheme.PROGRESS_COMPLETE_COLOR, true);
	private static final ImageIcon PROBLEM_ICON = statusIcon(ColorScheme.PROGRESS_ERROR_COLOR, false);

	private final PluginListPanel pluginListPanel;

	@Getter
	private final PluginConfigurationDescriptor pluginConfig;

	@Getter
	private final List<String> keywords = new ArrayList<>();

	@Nullable
	private final PluginHubStatus status;

	private final JToggleButton pinButton;
	private final PluginToggleButton onOffToggle;

	static
	{
		BufferedImage onStar = ImageUtil.loadImageResource(ConfigPanel.class, "star_on.png");
		ON_STAR = new ImageIcon(onStar);

		BufferedImage offStar = ImageUtil.luminanceScale(
			ImageUtil.grayscaleImage(onStar),
			0.77f
		);
		OFF_STAR = new ImageIcon(offStar);
	}

	PluginListItem(PluginListPanel pluginListPanel, PluginConfigurationDescriptor pluginConfig)
	{
		this(pluginListPanel, pluginConfig, null);
	}

	PluginListItem(PluginListPanel pluginListPanel, PluginConfigurationDescriptor pluginConfig, @Nullable PluginHubStatus status)
	{
		this.pluginListPanel = pluginListPanel;
		this.pluginConfig = pluginConfig;
		this.status = status;
		final boolean placeholder = pluginConfig.getPlugin() == null && status != null;

		Collections.addAll(keywords, pluginConfig.getName().toLowerCase().split(" "));
		Collections.addAll(keywords, pluginConfig.getDescription().toLowerCase().split(" "));
		Collections.addAll(keywords, pluginConfig.getTags());
		String internalName = status != null ? status.getInternalName() : pluginConfig.getInternalPluginHubName();
		if (status != null)
		{
			keywords.add(status.isProblem() ? "broken" : "update");
		}
		if (internalName != null)
		{
			keywords.add("pluginhub");
			keywords.add(internalName);
		}
		else
		{
			keywords.add("plugin"); // we don't want searching plugin to only show hub plugins
		}

		setLayout(new BorderLayout(3, 0));
		setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 20));

		JLabel nameLabel = new JLabel(placeholder ? "<html><s>" + pluginConfig.getName() + "</s></html>" : pluginConfig.getName());
		nameLabel.setForeground(placeholder ? ColorScheme.LIGHT_GRAY_COLOR.darker() : Color.WHITE);

		if (status != null)
		{
			nameLabel.setToolTipText(statusTooltip(status));
		}
		else if (!pluginConfig.getDescription().isEmpty())
		{
			nameLabel.setToolTipText("<html>" + pluginConfig.getName() + ":<br>" + pluginConfig.getDescription() + "</html>");
		}

		pinButton = new JToggleButton(OFF_STAR);
		pinButton.setSelectedIcon(ON_STAR);
		SwingUtil.removeButtonDecorations(pinButton);
		SwingUtil.addModalTooltip(pinButton, "Unpin plugin", "Pin plugin");
		pinButton.setPreferredSize(new Dimension(21, 0));

		final JPanel startPanel = new JPanel(new BorderLayout(2, 0));
		startPanel.add(pinButton, BorderLayout.LINE_START);
		if (status != null)
		{
			JLabel statusLabel = new JLabel(status.isProblem() ? PROBLEM_ICON : UPDATE_ICON);
			statusLabel.setToolTipText(statusTooltip(status));
			startPanel.add(statusLabel, BorderLayout.LINE_END);
		}
		add(startPanel, BorderLayout.LINE_START);

		pinButton.addActionListener(e ->
		{
			pluginListPanel.savePinnedPlugins();
			pluginListPanel.refresh();
		});

		final JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(1, 2));
		add(buttonPanel, BorderLayout.LINE_END);

		JMenuItem configMenuItem = null;
		if (pluginConfig.getConfigDescriptor() != null)
		{
			JButton configButton = new JButton(ConfigPanel.CONFIG_ICON);
			SwingUtil.removeButtonDecorations(configButton);
			configButton.setPreferredSize(new Dimension(25, 0));
			configButton.setVisible(false);
			buttonPanel.add(configButton);

			configButton.addActionListener(e ->
			{
				configButton.setIcon(ConfigPanel.CONFIG_ICON);
				openGroupConfigPanel();
			});

			configButton.setVisible(true);
			configButton.setToolTipText("Edit plugin configuration");

			configMenuItem = new JMenuItem("Configure");
			configMenuItem.addActionListener(e -> openGroupConfigPanel());
		}

		JMenuItem uninstallItem = null;
		if (internalName != null)
		{
			uninstallItem = new JMenuItem("Uninstall");
			uninstallItem.addActionListener(ev -> pluginListPanel.getExternalPluginManager().remove(internalName));
		}

		JMenuItem updateItem = null;
		if (status != null && status.getState() == PluginHubStatus.State.UPDATE_AVAILABLE)
		{
			updateItem = new JMenuItem("Update");
			updateItem.addActionListener(ev -> pluginListPanel.getExternalPluginManager().update());
		}

		JMenuItem supportItem;
		if (placeholder)
		{
			supportItem = new JMenuItem("Plugin Hub page");
			supportItem.addActionListener(ev -> LinkBrowser.browse("https://runelite.net/plugin-hub/show/" + internalName));
		}
		else
		{
			supportItem = pluginConfig.createSupportMenuItem();
		}

		addLabelPopupMenu(nameLabel, updateItem, configMenuItem, supportItem, uninstallItem);
		add(nameLabel, BorderLayout.CENTER);

		onOffToggle = new PluginToggleButton();
		onOffToggle.setConflicts(pluginConfig.getConflicts());
		buttonPanel.add(onOffToggle);
		if (pluginConfig.getPlugin() != null)
		{
			onOffToggle.addActionListener(i ->
			{
				if (onOffToggle.isSelected())
				{
					pluginListPanel.startPlugin(pluginConfig.getPlugin());
				}
				else
				{
					pluginListPanel.stopPlugin(pluginConfig.getPlugin());
				}
			});
		}
		else
		{
			onOffToggle.setVisible(false);
		}
	}

	@Override
	public String getSearchableName()
	{
		return pluginConfig.getName();
	}

	@Override
	public boolean isPinned()
	{
		return pinButton.isSelected();
	}

	@Override
	public boolean needsAttention()
	{
		return status != null;
	}

	void setPinned(boolean pinned)
	{
		pinButton.setSelected(pinned);
	}

	void setPluginEnabled(boolean enabled)
	{
		onOffToggle.setSelected(enabled);
	}

	private void openGroupConfigPanel()
	{
		pluginListPanel.openConfigurationPanel(pluginConfig);
	}

	private static String statusTooltip(PluginHubStatus status)
	{
		String title;
		String action;
		switch (status.getState())
		{
			case UPDATE_AVAILABLE:
				title = "Update available";
				action = "Click the name and choose Update.";
				break;
			case UNAVAILABLE:
				title = status.getDisplayName() + " is unavailable";
				action = "Click the name to uninstall it or view it on the Plugin Hub.";
				break;
			default:
				title = status.getDisplayName() + " failed to load";
				action = "Click the name to uninstall it or view it on the Plugin Hub.";
				break;
		}

		StringBuilder sb = new StringBuilder("<html><b>").append(title).append("</b>");
		if (status.getReason() != null)
		{
			sb.append("<br>").append(status.getReason());
		}
		return sb.append("<br>").append(action).append("</html>").toString();
	}

	private static ImageIcon statusIcon(Color color, boolean upArrow)
	{
		BufferedImage img = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(color);
		g.fillOval(0, 0, 12, 12);
		g.setColor(Color.WHITE);
		if (upArrow)
		{
			g.fillPolygon(new Polygon(new int[]{6, 10, 2}, new int[]{2, 7, 7}, 3));
			g.fillRect(5, 6, 2, 4);
		}
		else
		{
			g.fillRect(5, 2, 2, 5);
			g.fillRect(5, 8, 2, 2);
		}
		g.dispose();
		return new ImageIcon(img);
	}

	/**
	 * Adds a mouseover effect to change the text of the passed label to {@link ColorScheme#BRAND_ORANGE} color, and
	 * adds the passed menu items to a popup menu shown when the label is clicked.
	 *
	 * @param label     The label to attach the mouseover and click effects to
	 * @param menuItems The menu items to be shown when the label is clicked
	 */
	static void addLabelPopupMenu(JLabel label, JMenuItem... menuItems)
	{
		final JPopupMenu menu = new JPopupMenu();
		final Color labelForeground = label.getForeground();
		menu.setBorder(new EmptyBorder(5, 5, 5, 5));

		for (final JMenuItem menuItem : menuItems)
		{
			if (menuItem == null)
			{
				continue;
			}

			// Some machines register mouseEntered through a popup menu, and do not register mouseExited when a popup
			// menu item is clicked, so reset the label's color when we click one of these options.
			menuItem.addActionListener(e -> label.setForeground(labelForeground));
			menu.add(menuItem);
		}

		label.addMouseListener(new MouseAdapter()
		{
			private Color lastForeground;

			@Override
			public void mouseClicked(MouseEvent mouseEvent)
			{
				Component source = (Component) mouseEvent.getSource();
				Point location = MouseInfo.getPointerInfo().getLocation();
				SwingUtilities.convertPointFromScreen(location, source);
				menu.show(source, location.x, location.y);
			}

			@Override
			public void mouseEntered(MouseEvent mouseEvent)
			{
				lastForeground = label.getForeground();
				label.setForeground(ColorScheme.BRAND_ORANGE);
			}

			@Override
			public void mouseExited(MouseEvent mouseEvent)
			{
				label.setForeground(lastForeground);
			}
		});
	}
}
