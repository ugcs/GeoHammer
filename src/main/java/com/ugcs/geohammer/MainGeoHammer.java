package com.ugcs.geohammer;

import com.ugcs.geohammer.release.Version;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.analytics.EventSender;
import com.ugcs.geohammer.analytics.EventsFactory;
import com.ugcs.geohammer.view.Dialogs;
import com.ugcs.geohammer.analytics.FileOpenEventsAnalytics;

import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.view.style.ThemeService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;

public class MainGeoHammer extends Application {

	private static final String TITLE_VERSION = "GeoHammer ";

    private ConfigurableApplicationContext context;

	private Model model;

    private BuildInfo appBuildInfo;

	private FileTemplates fileTemplates;

	private Loader loader;

	private SceneContent sceneContent;

	private EventSender eventSender;

	private EventsFactory eventsFactory;

	private ThemeService themeService;

	public static void main(String[] args) {
		launch(args);
	}
	
	@Override
    public void init() {
		//create all classes
		context = new AnnotationConfigApplicationContext("com.ugcs");
		context.registerShutdownHook();
		
		model = context.getBean(Model.class);
		  
		appBuildInfo = context.getBean(BuildInfo.class);

		fileTemplates = context.getBean(FileTemplates.class);

		sceneContent = context.getBean(SceneContent.class);

		loader = context.getBean(Loader.class);

		eventSender = context.getBean(EventSender.class);

		context.getBean(FileOpenEventsAnalytics.class);

		eventsFactory = context.getBean(EventsFactory.class);

		themeService = context.getBean(ThemeService.class);
    }

	@Override
	public void start(Stage stage) {
		AppContext.stage = stage;
        stage.getIcons().add(ResourceImageHolder.IMG_LOGO24);
        stage.setTitle(TITLE_VERSION + Version.toString(appBuildInfo.getBuildVersion()));
		
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        
		Scene scene = new Scene(sceneContent, screenSize.getWidth()-80, 700);
		stage.setScene(scene);
		themeService.registerScene(scene);

		stage.setX(0);
		stage.setY(0);
		stage.show();

		// JavaFX windows do not trigger the JVM splash auto-close (that hook is AWT-only),
		// so close the -splash: image manually once the main stage is shown.
		SplashScreen splash = SplashScreen.getSplashScreen();
		if (splash != null) {
			splash.close();
		}

		stage.setOnCloseRequest(event -> {
            eventSender.shutdown();

            if (model.stopUnsaved()) {
				event.consume();
                return;
            }

			context.close();

            Platform.exit();
            System.exit(0);
        });

		if (fileTemplates.getTemplates().isEmpty()) {
            Dialogs.showError("There are no templates for the csv files",
			"There are no templates for the csv files loaded, so you could not open any csv");
		}

		//load files if they were given in parameters 
		if (!getParameters().getRaw().isEmpty()) {
			String name = getParameters().getRaw().get(0);
			List<File> f = Arrays.asList(new File(name));			
			loader.load(f);
		}

		eventSender.send(eventsFactory.createAppStartedEvent(appBuildInfo.getBuildVersion()));
	}
}
