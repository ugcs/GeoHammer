package com.ugcs.geohammer.settings;

import java.util.List;

import com.ugcs.geohammer.model.ActivationPolicy;
import com.ugcs.geohammer.model.ToolNode;
import com.ugcs.geohammer.model.ToolProducer;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.Listeners;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.view.UtilityWindow;
import com.ugcs.geohammer.view.Views;
import com.ugcs.geohammer.view.WindowProperties;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.StageStyle;
import org.controlsfx.validation.ValidationMessage;
import org.controlsfx.validation.ValidationResult;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class SettingsView extends UtilityWindow implements ToolProducer {

	public static final double VERTICAL_SPACING = 12;

	private final List<SettingsTab> tabs;

	@Nullable
	private Label tabTitle;

	@Nullable
	private StackPane tabContent;

	@Nullable
	private Label status;

	@Nullable
	private TabSelector tabSelector;

	private final ToggleButton settingsToggle = ResourceImageHolder.setButtonImage(
			ResourceImageHolder.SETTINGS, new ToggleButton());

	public SettingsView(GeneralTab generalTab, ScriptsTab scriptsTab, McpTab mcpTab) {
		super(getWindowProperties());

		tabs = List.of(generalTab, scriptsTab, mcpTab);

		settingsToggle.setTooltip(new Tooltip("Settings"));
		settingsToggle.setSelected(false);
		settingsToggle.setOnAction(event -> {
			if (settingsToggle.isSelected()) {
				show();
			} else {
				hide();
			}
		});
	}

	private static WindowProperties getWindowProperties() {
		return new WindowProperties("Settings")
				.withStyle(StageStyle.DECORATED)
				.withSize(640, 420)
				.withMinSize(520, 320);
	}

	@Override
	public List<ToolNode> getToolNodes() {
		return List.of(new ToolNode(settingsToggle, ActivationPolicy.always()));
	}

	@Override
	protected void onCreate() {
		super.onCreate();

		tabTitle = new Label();
		tabTitle.getStyleClass().addAll("bigger");

		tabContent = new StackPane();
		VBox.setVgrow(tabContent, Priority.ALWAYS);

		status = new Label();
		status.getStyleClass().add("error");

		HBox controlPane = new HBox(Views.DEFAULT_SPACING, status, Views.createSpacer(), createButtons());
		controlPane.setAlignment(Pos.CENTER_LEFT);

		VBox tabPane = new VBox(VERTICAL_SPACING, tabTitle, tabContent, controlPane);
		tabPane.setPadding(new Insets(16));
		tabPane.getStyleClass().add("surface");
		HBox.setHgrow(tabPane, Priority.ALWAYS);

		tabSelector = new TabSelector();
		for (SettingsTab settingsTab : tabs) {
			Tab tab = settingsTab.getTab();
			tabSelector.addTab(tab);
		}
		Listeners.onChange(tabSelector.selectedTabProperty(), this::showTab);

		List<ToggleButton> toggles = tabSelector.getToggles();
		toggles.getFirst().setSelected(true);

		VBox tabSelectorPane = new VBox(Views.DEFAULT_SPACING);
		tabSelectorPane.getChildren().addAll(toggles);

		tabSelectorPane.setPadding(new Insets(16, 8, 16, 8));
		tabSelectorPane.setPrefWidth(120);
		tabSelectorPane.setMinWidth(120);

		HBox container = new HBox(tabSelectorPane, Views.createVerticalSeparator(), tabPane);
		if (root != null) {
			root.getChildren().add(container);
		}
	}

	private HBox createButtons() {
		Button save = new Button("Save");
		save.setDefaultButton(true);
		save.setPrefWidth(60);
		save.setOnAction(event -> onSave());

		Button close = new Button("Close");
		close.setPrefWidth(60);
		close.setOnAction(event -> hide());

		HBox buttons = new HBox(Views.DEFAULT_SPACING, close, save);
		buttons.setAlignment(Pos.CENTER_RIGHT);
		return buttons;
	}

	private void showTab(Tab tab) {
		if (tab == null) {
			return;
		}
		if (tabTitle != null) {
			tabTitle.setText(tab.title());
		}
		if (tabContent != null) {
			StackPane.setAlignment(tab.content(), Pos.TOP_LEFT);
			tabContent.getChildren().setAll(tab.content());
		}
	}

	@Override
	protected void onShow() {
		super.onShow();
		load();
	}

	@Override
	protected void onHide() {
		super.onHide();
		settingsToggle.setSelected(false);
	}

	private void onSave() {
		if (!validate()) {
			return;
		}
		save();
		hide();
	}

	private boolean validate() {
		if (status != null) {
			Platform.runLater(() -> status.setText(Strings.empty()));
		}
		for (SettingsTab tab : tabs) {
			ValidationResult validation = tab.validate();
			for (ValidationMessage message : validation.getMessages()) {
				// fail on first validation message
				Control target = message.getTarget();
				if (target != null) {
					if (tabSelector != null) {
						tabSelector.select(tab.getTab());
					}
					Platform.runLater(target::requestFocus);
				}
				if (status != null) {
					Platform.runLater(() -> status.setText(message.getText()));
				}
				return false;
			}
		}
		return true;
	}

	private void save() {
		for (SettingsTab tab : tabs) {
			tab.save();
		}
	}

	private void load() {
		if (status != null) {
			Platform.runLater(() -> status.setText(Strings.empty()));
		}
		for (SettingsTab tab : tabs) {
			tab.load();
		}
	}
}
