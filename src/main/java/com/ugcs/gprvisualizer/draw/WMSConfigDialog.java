package com.ugcs.gprvisualizer.draw;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import com.ugcs.gprvisualizer.gpr.PrefSettings;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class WMSConfigDialog {

    private final PrefSettings prefSettings;
    private final Runnable onSaveCallback;
    private Stage stage;
    private TextField urlField;
    private ComboBox<WMSCapabilitiesParser.LayerInfo> layersComboBox;
    private ComboBox<String> formatComboBox;
    private Button testButton;
    private Button loadLayersButton;
    private Label statusLabel;
    private boolean result = false;
    private List<WMSCapabilitiesParser.LayerInfo> availableLayers;

    public WMSConfigDialog(PrefSettings prefSettings) {
        this(prefSettings, null);
    }
    
    public WMSConfigDialog(PrefSettings prefSettings, Runnable onSaveCallback) {
        this.prefSettings = prefSettings;
        this.onSaveCallback = onSaveCallback;
        createDialog();
    }

    private void createDialog() {
        stage = new Stage(StageStyle.UTILITY);
        stage.setTitle("WMS Server Configuration");
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);

        GridPane grid = new GridPane();
        grid.setAlignment(javafx.geometry.Pos.CENTER);
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 20, 20));

        // URL field
        Label urlLabel = new Label("Server URL:");
        urlField = new TextField();
        urlField.setPrefWidth(400);
        urlField.setPromptText("https://your-wms-server.com/wms");

        // Layers combo box
        Label layersLabel = new Label("Layer:");
        layersComboBox = new ComboBox<>();
        layersComboBox.setPrefWidth(300);
        layersComboBox.setPromptText("Select a layer...");
        layersComboBox.setEditable(false);
        
        // Set custom cell factory to display titles in dropdown
        layersComboBox.setCellFactory(listView -> new ListCell<WMSCapabilitiesParser.LayerInfo>() {
            @Override
            protected void updateItem(WMSCapabilitiesParser.LayerInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getTitle());
                }
            }
        });
        
        // Set button cell to show title when selected
        layersComboBox.setButtonCell(new ListCell<WMSCapabilitiesParser.LayerInfo>() {
            @Override
            protected void updateItem(WMSCapabilitiesParser.LayerInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getTitle());
                }
            }
        });
        
        // Load layers button
        loadLayersButton = new Button("Load Layers");
        loadLayersButton.setOnAction(e -> loadAvailableLayers(null));
        
        HBox layersBox = new HBox(5);
        layersBox.getChildren().addAll(layersComboBox, loadLayersButton);

        // Format combo box
        Label formatLabel = new Label("Format:");
        formatComboBox = new ComboBox<>();
        formatComboBox.getItems().addAll("image/png", "image/jpeg");
        formatComboBox.setValue("image/png");

        // Test connection button
        testButton = new Button("Test Connection");
        testButton.setOnAction(e -> testConnection());

        // Status label
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: blue;");

        // Info label
        Label infoLabel = new Label("Note: Only WGS84 (EPSG:4326) layers are supported");
        infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");

        // Buttons
        Button okButton = new Button("OK");
        Button cancelButton = new Button("Cancel");
        
        okButton.setOnAction(e -> {
            saveSettings();
            result = true;
            
            // Trigger map refresh if callback is provided
            if (onSaveCallback != null) {
                onSaveCallback.run();
            }
            
            stage.close();
        });
        
        cancelButton.setOnAction(e -> {
            result = false;
            stage.close();
        });

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        buttonBox.getChildren().addAll(testButton, okButton, cancelButton);

        // Layout
        grid.add(urlLabel, 0, 0);
        grid.add(urlField, 1, 0);
        grid.add(layersLabel, 0, 1);
        grid.add(layersBox, 1, 1);
        grid.add(formatLabel, 0, 2);
        grid.add(formatComboBox, 1, 2);
        grid.add(statusLabel, 0, 3, 2, 1);
        grid.add(infoLabel, 0, 4, 2, 1);
        grid.add(buttonBox, 0, 5, 2, 1);

        Scene scene = new Scene(grid);
        stage.setScene(scene);

        // Load current settings
        loadSettings();
    }

    private void loadSettings() {
        String url = prefSettings.getSetting("maps", "wms_url");
        String layers = prefSettings.getSetting("maps", "wms_layers");
        String format = prefSettings.getSetting("maps", "wms_format");

        if (url != null) urlField.setText(url);
        if (format != null) formatComboBox.setValue(format);
        
        // Auto-load layers if URL is available
        if (url != null && !url.trim().isEmpty()) {
            loadAvailableLayers(layers); // Pass the saved layer name to select after loading
        } else if (layers != null && !layers.trim().isEmpty()) {
            // Try to find matching LayerInfo in the combo box items (if already loaded)
            selectLayerByName(layers);
        }
    }
    
    private void selectLayerByName(String layerName) {
        for (WMSCapabilitiesParser.LayerInfo layerInfo : layersComboBox.getItems()) {
            if (layerInfo.getName().equals(layerName)) {
                layersComboBox.setValue(layerInfo);
                break;
            }
        }
    }

    private void saveSettings() {
        prefSettings.saveSetting("maps", "wms_url", urlField.getText().trim());
        
        String selectedLayer = "";
        WMSCapabilitiesParser.LayerInfo selectedLayerInfo = layersComboBox.getValue();
        if (selectedLayerInfo != null) {
            selectedLayer = selectedLayerInfo.getName();
        }
        
        prefSettings.saveSetting("maps", "wms_layers", selectedLayer);
        prefSettings.saveSetting("maps", "wms_format", formatComboBox.getValue());
    }

    private void testConnection() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            statusLabel.setText("Please enter a server URL");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        testButton.setDisable(true);
        statusLabel.setText("Testing connection...");
        statusLabel.setStyle("-fx-text-fill: blue;");

        // Run test in background thread
        Thread testThread = new Thread(() -> {
            try {
                // Build GetCapabilities request
                String testUrl = url;
                if (!testUrl.contains("?")) {
                    testUrl += "?";
                } else {
                    testUrl += "&";
                }
                testUrl += "SERVICE=WMS&VERSION=1.3.0&REQUEST=GetCapabilities";

                HttpURLConnection connection = (HttpURLConnection) new URI(testUrl).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();
                
                javafx.application.Platform.runLater(() -> {
                    if (responseCode == 200) {
                        statusLabel.setText("Connection successful!");
                        statusLabel.setStyle("-fx-text-fill: green;");
                    } else {
                        statusLabel.setText("Server responded with code: " + responseCode);
                        statusLabel.setStyle("-fx-text-fill: orange;");
                    }
                    testButton.setDisable(false);
                });

            } catch (IOException | URISyntaxException e) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Connection failed: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                    testButton.setDisable(false);
                });
            }
        });
        
        testThread.setDaemon(true);
        testThread.start();
    }
    
    private void loadAvailableLayers(String layerToSelect) {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            statusLabel.setText("Please enter a server URL first");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        
        loadLayersButton.setDisable(true);
        statusLabel.setText("Loading layers...");
        statusLabel.setStyle("-fx-text-fill: blue;");
        
        // Run in background thread
        Thread loadThread = new Thread(() -> {
            try {
                List<WMSCapabilitiesParser.LayerInfo> layers = WMSCapabilitiesParser.fetchAndParseLayers(url);
                
                javafx.application.Platform.runLater(() -> {
                    availableLayers = layers;
                    layersComboBox.getItems().clear();
                    layersComboBox.getItems().addAll(layers);
                    
                    if (layers.isEmpty()) {
                        statusLabel.setText("No layers found in capabilities");
                        statusLabel.setStyle("-fx-text-fill: orange;");
                    } else {
                        statusLabel.setText("Found " + layers.size() + " layers");
                        statusLabel.setStyle("-fx-text-fill: green;");
                        
                        // Select the specified layer if provided
                        if (layerToSelect != null && !layerToSelect.trim().isEmpty()) {
                            selectLayerByName(layerToSelect);
                        }
                    }
                    
                    loadLayersButton.setDisable(false);
                });
                
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Failed to load layers: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                    loadLayersButton.setDisable(false);
                });
            }
        });
        
        loadThread.setDaemon(true);
        loadThread.start();
    }

    public boolean showAndWait() {
        stage.showAndWait();
        return result;
    }
}