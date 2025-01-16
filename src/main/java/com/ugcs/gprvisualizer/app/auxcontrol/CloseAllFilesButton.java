package com.ugcs.gprvisualizer.app.auxcontrol;

import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.GPRChart;
import com.ugcs.gprvisualizer.app.ScrollableData;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.gpr.Model;
import javafx.geometry.Point2D;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.awt.*;
import java.util.List;
import java.util.Optional;

public class CloseAllFilesButton extends RemoveFileButton {

    private final Model model;

    public CloseAllFilesButton(Model model) {
        super(0, null, null, model);
        this.model = model;
    }

    @Override
    public boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {

        if (isPointInside(localPoint, profField) && profField instanceof GPRChart gprChart) {

            List<SgyFile> sgyFiles = gprChart.getField().getSgyFiles();

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Close files");
            alert.setContentText(
                    (sgyFiles.stream().anyMatch(SgyFile::isUnsaved) ? "Files is not saved!\n" : "")
                            + "Confirm to close files");

            Optional<ButtonType> result = alert.showAndWait();

            if ((result.isPresent()) && (result.get() == ButtonType.OK)) {
                for (SgyFile sgyFile : sgyFiles) {
                    model.publishEvent(new FileClosedEvent(this, sgyFile));
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void drawOnCut(Graphics2D g2, ScrollableData scrollableData) {
        Rectangle rect = getRect(scrollableData);
        g2.translate(rect.x, rect.y);
        g2.drawImage(ResourceImageHolder.IMG_CLOSE, 0, 0, null);
        g2.translate(-rect.x, -rect.y);
    }

    private Rectangle getRect(ScrollableData scrollableData) {
        if (scrollableData instanceof GPRChart gprChart) {
            return new Rectangle(gprChart.getField().getVisibleStart(), 0,
                    R_HOR, R_VER);
        } else {
            return null;
        }
    }

    @Override
    public boolean isPointInside(Point2D localPoint, ScrollableData profField) {
        Rectangle rect = getRect(profField);
        return rect.contains(localPoint.getX(), localPoint.getY());
    }
}
