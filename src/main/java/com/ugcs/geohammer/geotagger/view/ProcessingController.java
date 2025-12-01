package com.ugcs.geohammer.geotagger.view;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.geotagger.Geotagger;
import javafx.application.Platform;
import org.springframework.stereotype.Component;

@Component
class ProcessingController {
	private final Geotagger geotagger;

	ProcessingController(Geotagger geotagger) {
		this.geotagger = geotagger;
	}

	void startProcessing(List<SgyFile> position, List<SgyFile> data, Consumer<Double> onProgress,
						 Consumer<String> onFinish) {
		CompletableFuture<String> future = geotagger.updateCoordinates(position, data, percentage ->
				Platform.runLater(() -> {
					double progress = Math.clamp(percentage, 0, 100) / 100.0;
					onProgress.accept(progress);
				}));

		future.thenAccept(message -> Platform.runLater(() -> onFinish.accept(message)))
				.exceptionally(e -> {
					Platform.runLater(() -> onFinish.accept(e.getMessage()));
					return null;
				});
	}
}
