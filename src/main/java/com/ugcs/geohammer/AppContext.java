package com.ugcs.geohammer;

import com.ugcs.geohammer.view.status.Status;
import com.ugcs.geohammer.model.Model;

import com.ugcs.geohammer.util.Check;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public final class AppContext implements ApplicationContextAware {
	
	public static Stage stage;

	public static Scene scene;

	public static Model model;

	public static Status status;

	private static ApplicationContext context;

	private AppContext() {
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		context = applicationContext;
	}

	public static <T> T getInstance(Class<T> type) {
		Check.notNull(context, "Context not initialized");
		Check.notNull(type);

		return context.getBean(type);
	}
}