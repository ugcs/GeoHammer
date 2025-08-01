package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.ugcs.gprvisualizer.analytics.EventSender;
import com.ugcs.gprvisualizer.analytics.EventsFactory;
import com.ugcs.gprvisualizer.app.service.FileOpenEventsAnalytics;
import com.ugcs.gprvisualizer.app.scripts.PythonConfig;
import com.ugcs.gprvisualizer.app.service.PythonScriptExecutorService;
import com.ugcs.gprvisualizer.app.yaml.FileTemplates;
import com.ugcs.gprvisualizer.gpr.Model;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MenuBar;
import javafx.scene.control.Menu;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;


public class MainGeoHammer extends Application {

	private static final String TITLE_VERSION = "GeoHammer v.";

	private Model model;
	//private RootControls rootControls;
	private ApplicationContext context;

	private FileTemplates fileTemplates;

	private Loader loader;
	private SceneContent sceneContent;
	private EventSender eventSender;
	private EventsFactory eventsFactory;
	private PythonConfig pythonConfig;

	public static void main(String[] args) {
		launch(args);
	}
	
	@Override
    public void init() {
		//create all classes
		//context = new ClassPathXmlApplicationContext("spring.xml");
		context = new AnnotationConfigApplicationContext("com.ugcs");
		
		model = context.getBean(Model.class);
		  
		//rootControls = context.getBean(RootControls.class);

		appBuildInfo = context.getBean(BuildInfo.class);

		fileTemplates = context.getBean(FileTemplates.class);

		sceneContent = context.getBean(SceneContent.class);

		loader = context.getBean(Loader.class);

		eventSender = context.getBean(EventSender.class);

		context.getBean(FileOpenEventsAnalytics.class);

		eventsFactory = context.getBean(EventsFactory.class);

		context.getBean(PythonScriptExecutorService.class);

		pythonConfig = context.getBean(PythonConfig.class);
    }

	@Override
	public void start(Stage stage) throws Exception {

		AppContext.stage = stage;

        stage.getIcons().add(ResourceImageHolder.IMG_LOGO24);
	
        stage.setTitle(TITLE_VERSION + appBuildInfo.getBuildVersion());
		
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

		String os = System.getProperty("os.name").toLowerCase();
		if (Desktop.isDesktopSupported() && os.contains("mac")) {
			Desktop desktop = Desktop.getDesktop();
			desktop.setPreferencesHandler(e -> Platform.runLater(() -> {
				new SettingsView(pythonConfig);
			}));
		} else {
			MenuBar menuBar = new MenuBar();
			Menu menu = new Menu("File");
			MenuItem settingsItem = new MenuItem("Settings");
			settingsItem.setOnAction(e -> {
				new SettingsView(pythonConfig);
			});
			menu.getItems().add(settingsItem);
			menuBar.getMenus().add(menu);

			sceneContent.setTop(menuBar);
		}
        
		Scene scene = new Scene(sceneContent, screenSize.getWidth()-80, 700);
		//scene.set
		stage.setScene(scene);
		//stage.setMaximized(true);
		
		stage.setX(0);
		stage.setY(0);
		stage.show();

		stage.setOnCloseRequest(event -> {
            eventSender.shutdown();

            if (model.stopUnsaved()) {
				event.consume();
                return;
            }

            Platform.exit();
            System.exit(0);
        });

		if (fileTemplates.getTemplates().isEmpty()) {
            MessageBoxHelper.showError("There are no templates for the csv files",  
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


	private static final ProgressListener emptyListener = new ProgressListener() {
		@Override
		public void progressPercent(int percent) {}
		
		@Override
		public void progressMsg(String msg) {}

		@Override
		public void progressSubMsg(String msg) {
			// TODO Auto-generated method stub
			
		}
	};

	private BuildInfo appBuildInfo;

}
