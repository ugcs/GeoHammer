package com.ugcs.geohammer.feedback;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.BuildInfo;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.FileManager;
import com.ugcs.geohammer.service.TaskService;
import com.ugcs.geohammer.service.jira.JiraCollector;
import com.ugcs.geohammer.service.jira.JiraTempFile;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.RetrofitCalls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.ZipStream;
import com.ugcs.geohammer.view.status.Status;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

@Service
public class FeedbackService {

    // uncompressed attachment size limit
    private static final long ATTACHMENT_SIZE_LIMIT = 200 * 1024 * 1024;

    @Value("${jira.collectorId}")
    private String jiraCollectorId;

    private final JiraCollector jiraCollector;

    private final BuildInfo buildInfo;

    private final FileManager fileManager;

    private final Status status;

    private final TaskService taskService;

    private final ExecutorService executor;

    public FeedbackService(
            JiraCollector jiraCollector,
            BuildInfo buildInfo,
            FileManager fileManager,
            Status status,
            TaskService taskService,
            ExecutorService executor) {
        this.jiraCollector = jiraCollector;
        this.buildInfo = buildInfo;
        this.status = status;
        this.fileManager = fileManager;
        this.taskService = taskService;
        this.executor = executor;
    }

    public void submitFeedback(Feedback feedback, boolean attachScreenshot, boolean attachFiles) {
        // screenshot should be taken on the fx application thread
        Screenshot screenshot = attachScreenshot
                ? Screenshot.take(AppContext.stage.getScene(), "screenshot.png", "png")
                : null;

        var future = executor.submit(() -> {
            try {
                List<Path> paths = attachFiles ? getOpenPaths() : List.of();
                String attachmentId = uploadAttachment(screenshot, paths);
                collectFeedback(feedback, attachmentId);
                status.showMessage("Feedback submitted", "Feedback");
            } catch (Exception e) {
                status.showMessage("Feedback not sent: " + e.getMessage(), "Feedback");
                throw e;
            }
        });
        taskService.registerTask(future, "Sending feedback");
    }

    private String uploadAttachment(Screenshot screenshot, List<Path> paths) {
        if (screenshot == null && Nulls.isNullOrEmpty(paths)) {
            return null;
        }
        byte[] attachment = buildAttachment(screenshot, paths);
        if (attachment.length == 0) {
            return null;
        }
        status.showMessage("Uploading attachment: " + attachment.length + " bytes", "Feedback");
        RequestBody attachmentBody = RequestBody.create(
                attachment,
                MediaType.parse("application/zip"));
        JiraTempFile tempFile = RetrofitCalls.call(
                jiraCollector.uploadTempFile(
                        jiraCollectorId,
                        "attachment.zip",
                        attachment.length,
                        attachmentBody
                )
        );
        return tempFile != null
                ? Strings.emptyToNull(tempFile.getId())
                : null;
    }

    private void collectFeedback(Feedback feedback, String tempFileId) {
        Check.notNull(feedback);
        Check.notEmpty(feedback.subject());

        String product = "GeoHammer " + buildInfo.getBuildVersion();
        RetrofitCalls.call(
                jiraCollector.submitFeedback(
                        jiraCollectorId,
                        product,
                        feedback.subject(),
                        Strings.emptyToNull(feedback.message()),
                        Strings.emptyToNull(feedback.name()),
                        Strings.emptyToNull(feedback.email()),
                        Strings.emptyToNull(tempFileId)
                )
        );
    }

    private byte[] buildAttachment(Screenshot screenshot, List<Path> paths) {
        record Entry(Path path, long size) {}
        List<Entry> entries = Nulls.toEmpty(paths).stream()
                .map(path -> {
                    try {
                        if (!Files.isRegularFile(path)) {
                            return null;
                        }
                        return new Entry(path, Files.size(path));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(Entry::size))
                .toList();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long uncompressedSize = 0;

        try (ZipStream zip = new ZipStream(out)) {
            if (screenshot != null) {
                byte[] image = screenshot.getBytes();
                if (image.length <= ATTACHMENT_SIZE_LIMIT) {
                    zip.addFile(screenshot.getFileName(), image);
                    uncompressedSize += image.length;
                }
            }
            for (Entry entry : entries) {
                if (uncompressedSize + entry.size > ATTACHMENT_SIZE_LIMIT) {
                    break;
                }
                zip.addFile(entry.path);
                uncompressedSize += entry.size;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private List<Path> getOpenPaths() {
        List<Path> paths = new ArrayList<>();
        for (SgyFile sgyFile : Nulls.toEmpty(fileManager.getFiles())) {
            if (sgyFile != null && sgyFile.getFile() != null) {
                paths.add(sgyFile.getFile().toPath());
            }
        }
        return paths;
    }
}
