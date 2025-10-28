package eclipse.plugin.aiassistant.view;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import eclipse.plugin.aiassistant.utility.Eclipse;

/**
 * Manages the tab button bar with previous, next, new tab, clone tab, refresh, import, export, and close all buttons.
 */
public class TabButtonBarArea {

	private final MainPresenter mainPresenter;

	private final ButtonConfig NEW_TAB_BUTTON = button("New Tab", "NewTab.png", this::onNewTab);
	private final ButtonConfig CLONE_TAB_BUTTON = button("Clone Current Tab", "CloneTab.png", this::onCloneTab);
	private final ButtonConfig PREVIOUS_BUTTON = button("Previous Tab", "ArrowLeft.png", this::onPrevious);
	private final ButtonConfig NEXT_BUTTON = button("Next Tab", "ArrowRight.png", this::onNext);
	private final ButtonConfig CLOSE_ALL_BUTTON = button("Close All Tabs", "Close.png", this::onCloseAll);
	private final ButtonConfig BUTTON_SEPARATOR = new ButtonConfig("", "", () -> {});

	private final List<ButtonConfig> BUTTON_CONFIGS = List.of(
			PREVIOUS_BUTTON,
			NEXT_BUTTON,
			BUTTON_SEPARATOR,
			NEW_TAB_BUTTON,
			BUTTON_SEPARATOR,
			CLONE_TAB_BUTTON,
			BUTTON_SEPARATOR,
			CLOSE_ALL_BUTTON);

	private final MenuItemConfig DISCUSS_MENU_ITEM = menuItem("Discuss", "Discuss the Code");
	private final MenuItemConfig EXPLAIN_MENU_ITEM = menuItem("Explain", "Explain the Code");
	private final MenuItemConfig CODE_REVIEW_MENU_ITEM = menuItem("Code Review", "Suggest Improvements to the Code");
	private final MenuItemConfig BEST_PRACTICES_MENU_ITEM = menuItem("Best Practices", "Check for Adherence to Best Practices");
	private final MenuItemConfig ROBUSTIFY_MENU_ITEM = menuItem("Robustify", "Suggest Ways to Improve Error Handling");
	private final MenuItemConfig OPTIMIZE_MENU_ITEM = menuItem("Optimize", "Suggest Ways to Optimize the Code");
	private final MenuItemConfig DEBUG_MENU_ITEM = menuItem("Debug", "Try to Find Bugs in the Code");
	private final MenuItemConfig REFACTOR_MENU_ITEM = menuItem("Refactor", "Refactor the Code");
	private final MenuItemConfig WRITE_COMMENTS_MENU_ITEM = menuItem("Write Comments", "Document the Code");
	private final MenuItemConfig MENU_ITEM_SEPARATOR = new MenuItemConfig("", "", "", () -> {});

	private final List<MenuItemConfig> MENU_ITEM_CONFIGS = List.of(
			DISCUSS_MENU_ITEM,
			EXPLAIN_MENU_ITEM,
			CODE_REVIEW_MENU_ITEM,
			BEST_PRACTICES_MENU_ITEM,
			ROBUSTIFY_MENU_ITEM,
			OPTIMIZE_MENU_ITEM,
			DEBUG_MENU_ITEM,
			MENU_ITEM_SEPARATOR,
			REFACTOR_MENU_ITEM,
			WRITE_COMMENTS_MENU_ITEM);

	private Composite buttonContainer;
	private ToolBar toolBar;
	private List<ToolItem> toolItems;
	private ToolItem newTabToolItem;
	private Menu newTabMenu;

	/**
	 * Constructs a new TabButtonBarArea instance with the given main presenter and
	 * parent composite.
	 *
	 * @param mainPresenter The main presenter for the application.
	 * @param parent        The parent composite for the button container.
	 */
	public TabButtonBarArea(MainPresenter mainPresenter, Composite parent) {
		this.mainPresenter = mainPresenter;
		buttonContainer = createButtonContainer(parent);
		createButtons();
	}

	/**
	 * Gets the button container composite.
	 *
	 * @return The button container composite.
	 */
	public Composite getButtonContainer() {
		return buttonContainer;
	}

	/**
	 * Sets the input enabled state for all buttons in the tab button bar area.
	 *
	 * @param enabled True to enable input, false to disable it.
	 */
	public void setInputEnabled(boolean enabled) {
		Eclipse.runOnUIThreadAsync(() -> {
			for (ToolItem toolItem : toolItems) {
				toolItem.setEnabled(enabled);
			}
			if (enabled) {
				updateButtonStates();
			}
			buttonContainer.setCursor(enabled ? null : Display.getCurrent().getSystemCursor(SWT.CURSOR_WAIT));
		});
	}

	/**
	 * Updates the state of all buttons based on current conditions.
	 */
	public void updateButtonStates() {
		Eclipse.runOnUIThreadAsync(() -> {
			for (ToolItem toolItem : toolItems) {
				if (toolItem.getToolTipText().equals(PREVIOUS_BUTTON.tooltip)) {
					toolItem.setEnabled(mainPresenter.getTabCount() > 1);
				} else if (toolItem.getToolTipText().equals(NEXT_BUTTON.tooltip)) {
					toolItem.setEnabled(mainPresenter.getTabCount() > 1);
				} else if (toolItem.getToolTipText().equals(CLONE_TAB_BUTTON.tooltip)) {
					toolItem.setEnabled(!mainPresenter.isConversationEmpty());
				} else if (toolItem.getToolTipText().equals(CLOSE_ALL_BUTTON.tooltip)) {
					toolItem.setEnabled(true);
				}
			}
		});
	}

	/**
	 * Creates a new Composite to contain buttons in the tab button bar area.
	 *
	 * @param parent The parent composite for the button container.
	 * @return The newly created button container composite.
	 */
	private Composite createButtonContainer(Composite parent) {
		Composite container = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		container.setLayout(layout);

		// Right-justify the button container
		GridData gridData = new GridData(SWT.END, SWT.CENTER, true, false);
		container.setLayoutData(gridData);

		// Create the single toolbar for all buttons
		toolBar = new ToolBar(container, SWT.FLAT | SWT.RIGHT | SWT.WRAP);
		toolBar.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));

		return container;
	}

	/**
	 * Creates and initializes buttons based on the provided button configurations.
	 */
	private void createButtons() {
		toolItems = new ArrayList<>();
		for (ButtonConfig config : BUTTON_CONFIGS) {
			if (config.tooltip.isEmpty()) {
				new ToolItem(toolBar, SWT.SEPARATOR);
			} else if (config == NEW_TAB_BUTTON) {
				createNewTabToolItem(config);
			} else {
				createToolItem(config);
			}
		}
	}

	/**
	 * Creates a toolbar item for the given configuration.
	 *
	 * @param config The button configuration.
	 */
	private void createToolItem(ButtonConfig config) {
		ToolItem toolItem = new ToolItem(toolBar, SWT.PUSH);
		toolItem.setToolTipText(config.tooltip);
		toolItem.setImage(Eclipse.loadIcon(config.iconFilename));

		toolItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				config.action.run();
			}
		});

		toolItems.add(toolItem);
	}

	/**
	 * Creates a toolbar item with a dropdown for the New Tab button.
	 *
	 * @param config The button configuration for the New Tab button.
	 */
	private void createNewTabToolItem(ButtonConfig config) {
		newTabToolItem = new ToolItem(toolBar, SWT.DROP_DOWN);
		newTabToolItem.setToolTipText(config.tooltip);
		newTabToolItem.setImage(Eclipse.loadIcon(config.iconFilename));

		createNewTabMenu();

		newTabToolItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (e.detail == SWT.ARROW) {
					showNewTabMenu();
				} else {
					config.action.run();
				}
			}
		});

		toolItems.add(newTabToolItem);
	}

	/**
	 * Creates the dropdown menu for the New Tab toolbar button.
	 */
	private void createNewTabMenu() {
		newTabMenu = new Menu(buttonContainer.getShell(), SWT.POP_UP);

		for (MenuItemConfig config : MENU_ITEM_CONFIGS) {
			if (config.label.isEmpty()) {
				new MenuItem(newTabMenu, SWT.SEPARATOR);
			} else {
				MenuItem item = new MenuItem(newTabMenu, SWT.PUSH);
				item.setText(config.label);
				item.setToolTipText(config.tooltip);
				item.setImage(Eclipse.loadIcon(config.iconFilename));

				item.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						config.action.run();
					}
				});
			}
		}
	}

	/**
	 * Shows the dropdown menu for the New Tab button.
	 */
	private void showNewTabMenu() {
		Rectangle bounds = newTabToolItem.getBounds();
		Point point = toolBar.toDisplay(bounds.x, bounds.y + bounds.height);
		newTabMenu.setLocation(point);
		newTabMenu.setVisible(true);
	}

	/**
	 * Creates a ButtonConfig with the specified tooltip, icon, and action.
	 *
	 * @param tooltip      The button's tooltip text.
	 * @param iconFilename The filename of the button's icon.
	 * @param action       The action to be performed when the button is clicked.
	 * @return A configured ButtonConfig instance.
	 */
	private ButtonConfig button(String tooltip, String iconFilename, Runnable action) {
		return new ButtonConfig(tooltip, iconFilename, action);
	}

	/**
	 * Creates a MenuItemConfig that calls onNewTab with the specified label.
	 *
	 * @param label   The menu item's label.
	 * @param tooltip The menu item's tooltip text.
	 * @return A configured MenuItemConfig instance.
	 */
	private MenuItemConfig menuItem(String label, String tooltip) {
		String iconFilename = label.replace(" ", "") + ".png";
		return new MenuItemConfig(label, tooltip, iconFilename, () -> onNewTab(label));
	}

	/**
	 * Handles the 'Previous' button click event by delegating to the main presenter.
	 */
	private void onPrevious() {
		mainPresenter.onPreviousTab();
	}

	/**
	 * Handles the 'Next' button click event by delegating to the main presenter.
	 */
	private void onNext() {
		mainPresenter.onNextTab();
	}

	/**
	 * Handles the 'New Tab' button click event by delegating to the main presenter.
	 */
	private void onNewTab() {
		mainPresenter.onNewTab();
	}

	/**
	 * Handles the menu item click event for creating a new tab with the specified task name.
	 *
	 * @param taskName The task name of the selected menu item.
	 */
	private void onNewTab(String taskName) {
		mainPresenter.onNewTab(taskName);
	}

	/**
	 * Handles the 'Clone Tab' button click event by delegating to the main presenter.
	 */
	private void onCloneTab() {
		mainPresenter.onCloneTab();
	}

	/**
	 * Handles the 'Close All' button click event by delegating to the main presenter.
	 */
	private void onCloseAll() {
		if (mainPresenter.onAttemptCloseAllTabs()) {
			mainPresenter.onCloseAllTabs();
		}
	}

	/**
	 * Represents a button configuration with its tooltip, icon filename, and action.
	 */
	private static class ButtonConfig {
		final String tooltip;
		final String iconFilename;
		final Runnable action;

		/**
		 * Constructs a new ButtonConfig instance with the given parameters.
		 *
		 * @param tooltip  The button's tooltip text.
		 * @param filename The filename of the button's icon.
		 * @param action   The action to be performed when the button is clicked.
		 */
		public ButtonConfig(String tooltip, String iconFilename, Runnable action) {
			this.tooltip = tooltip;
			this.iconFilename = iconFilename;
			this.action = action;
		}
	}

	/**
	 * Represents a menu item configuration with its label, tooltip, icon filename, and action.
	 */
	private static class MenuItemConfig {
		final String label;
		final String tooltip;
		final String iconFilename;
		final Runnable action;

		/**
		 * Constructs a new MenuItemConfig instance with the given parameters.
		 *
		 * @param label        The menu item's label.
		 * @param tooltip      The menu item's tooltip text.
		 * @param iconFilename The filename of the menu item's icon.
		 * @param action   The action to be performed when the button is clicked.
		 */
		public MenuItemConfig(String label, String tooltip, String iconFilename, Runnable action) {
			this.label = label;
			this.tooltip = tooltip;
			this.iconFilename = iconFilename;
			this.action = action;
		}
	}

}