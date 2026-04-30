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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    public List<Attachment> createScreenshotAttachments() {
        Screenshot screenshot = Screenshot.take(AppContext.stage.getScene(), "png");
        return List.of(new BytesAttachment("screenshot.png", screenshot.getBytes()));
    }

    public List<Attachment> createOpenFileAttachments() {
        List<Attachment> attachments = new ArrayList<>();
        for (SgyFile sgyFile : Nulls.toEmpty(fileManager.getFiles())) {
            if (sgyFile != null && sgyFile.getFile() != null) {
                Path path = sgyFile.getFile().toPath();
                if (Files.isRegularFile(path)) {
                    attachments.add(new FileAttachment(path));
                }
            }
        }
        return attachments;
    }

    public void submitFeedback(Feedback feedback, List<Attachment> attachments) {
        var future = executor.submit(() -> {
            try {
                String attachmentId = uploadAttachments(attachments);
                collectFeedback(feedback, attachmentId);
                status.showMessage("Feedback submitted", "Feedback");
            } catch (Exception e) {
                status.showMessage("Feedback not sent: " + e.getMessage(), "Feedback");
                throw e;
            }
        });
        taskService.registerTask(future, "Sending feedback");
    }

    private String uploadAttachments(List<Attachment> attachments) {
        if (Nulls.isNullOrEmpty(attachments)) {
            return null;
        }
        byte[] attachment = zipAttachments(attachments);
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

    private byte[] zipAttachments(List<Attachment> attachments) {
        attachments.sort(Comparator.comparingLong(Attachment::getSize));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long uncompressedSize = 0;

        try (ZipStream zip = new ZipStream(out)) {
            for (Attachment attachment : attachments) {
                long size = attachment.getSize();
                if (uncompressedSize + size > ATTACHMENT_SIZE_LIMIT) {
                    break;
                }
                try (InputStream in = attachment.getInput()) {
                    zip.addFile(attachment.getFileName(), in);
                    uncompressedSize += size;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
