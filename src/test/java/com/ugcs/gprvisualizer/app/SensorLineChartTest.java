package com.ugcs.gprvisualizer.app;

import com.ugcs.gprvisualizer.app.service.TemplateSettingsModel;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.gpr.PrefSettings;

import static org.junit.jupiter.api.Assertions.*;

@Disabled
@ExtendWith({MockitoExtension.class, ApplicationExtension.class})
public class SensorLineChartTest {

    @Mock
    private Model model;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TemplateSettingsModel templateSettingsModel;

    private SensorLineChart sensorLineChart;

    private Stage stage;

    @Start
    private void start(Stage stage) {
        this.stage = stage;
        sensorLineChart = new SensorLineChart(model, eventPublisher, templateSettingsModel);

        // Set up the initial scene
        VBox root = new VBox();
        root.getChildren().add(sensorLineChart.getRootNode());
        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void testProfileScrollInitialization() {
        assertNotNull(sensorLineChart.getProfileScroll(), "ProfileScroll should be initialized");
    }

    @Test
    void testProfileScrollResizing() {
        ProfileScroll profileScroll = sensorLineChart.getProfileScroll();
        double expectedWidth = 800.0;

        // Wait for JavaFX to process layout
        sleep(1000);

        assertEquals(expectedWidth, profileScroll.getWidth(), 
            "ProfileScroll width should match the stage width");
    }

    @Test
    void testGetProfileScroll() {
        ProfileScroll profileScroll = sensorLineChart.getProfileScroll();
        assertNotNull(profileScroll, "ProfileScroll should not be null");
        assertTrue(profileScroll instanceof ProfileScroll, 
            "Returned object should be instance of ProfileScroll");
    }

    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
