package com.ugcs.gprvisualizer.app.commands;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.github.thecoldwine.sigrun.common.ext.TraceKey;
import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.app.auxcontrol.ConstPlace;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.Traces;

import java.util.Optional;

public class KmlToFlag implements Command {

    @Override
    public String getButtonText() {
        return "insert marks into SEG-Y file";
    }

    @Override
    public void execute(TraceFile file, ProgressListener listener) {

        Model model = AppContext.model;

        model.getAuxElements().stream()
            .filter(a -> a instanceof ConstPlace)
            .map(a -> (ConstPlace) a)
            .forEach(c -> {

                Optional<TraceKey> traceKey = Traces.findNearestTraceInFiles(
                        model.getFileManager().getFiles(),
                        c.getLatLon());
                if (traceKey.isPresent()) {
                    FoundPlace rect = new FoundPlace(traceKey.get(), AppContext.model);
                    SgyFile traceFile = traceKey.get().getFile();
                    traceFile.getAuxElements().add(rect);
                    traceFile.setUnsaved(true);
                }
            });

        model.getAuxElements().removeIf(p -> p instanceof ConstPlace);

        model.updateAuxElements();

        model.setKmlToFlagAvailable(false);

        model.publishEvent(new WhatChanged(this, WhatChanged.Change.updateButtons));
    }
}
