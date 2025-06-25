package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.TraceHeader;
import com.github.thecoldwine.sigrun.common.ext.GprFile;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.github.thecoldwine.sigrun.common.ext.TraceKey;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.auxcontrol.ClickPlace;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.Check;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class SampleCutter {

    private final Model model;

    private final ApplicationEventPublisher eventPublisher;

    public SampleCutter(Model model, ApplicationEventPublisher eventPublisher) {
        this.model = model;
        this.eventPublisher = eventPublisher;
    }

    public void cropGprSamples(GPRChart gprChart, int offset, int length) {
        Check.notNull(gprChart);

        List<SgyFile> obsolete = new ArrayList<>();
        List<SgyFile> cropped = new ArrayList<>();

        TraceFile chartFile = gprChart.getFile();
        if (chartFile instanceof GprFile gprFile) {
            cropped.add(cropGprSamples(gprFile, offset, length));
            obsolete.add(gprFile);
        }

        // clear selection
        model.clearSelectedTrace(gprChart);

        // refresh chart
        for (SgyFile file : obsolete) {
            model.getFileManager().removeFile(file);
            model.publishEvent(new FileClosedEvent(this, file));
        }
        for (SgyFile file : cropped) {
            model.getFileManager().addFile(file);
        }
        model.publishEvent(new FileOpenedEvent(this,
                cropped.stream().map(SgyFile::getFile).toList()));

        // update model
        model.init();
        eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.traceCut));
    }

    private GprFile cropGprSamples(GprFile gprFile, int offset, int length) {
        Check.notNull(gprFile);

        GprFile copy = new GprFile();
        copy.setFile(gprFile.getFile());
        copy.setBinHdr(gprFile.getBinHdr());
        copy.setTxtHdr(gprFile.getTxtHdr());
        copy.setBinaryHeader(gprFile.getBinaryHeader());
        copy.setUnsaved(true);

        List<Trace> traces = gprFile.getTraces();
        List<Trace> cropTraces = new ArrayList<>(traces.size());
        for (Trace trace : traces) {
            //float[] samples = trace.getOriginalValues();

            // fit crop range to fit sample array
            int cropOffset = Math.min(Math.max(0, offset), trace.numValues());
            int cropLength = Math.min(length, trace.numValues() - offset);

            float[] cropValues = new float[cropLength];
            for (int i = 0; i < cropLength; i++) {
                cropValues[i] = trace.getOriginalValue(cropOffset + i);
            }

            // update trace header
            byte[] binHeader = trace.getBinHeader();
            byte[] cropBinHeader = Arrays.copyOf(binHeader, binHeader.length);
            setNumSamlpes(cropBinHeader, cropValues.length);

            // re-read updated header to keep consistent parsed version
            TraceHeader header = GprFile.traceHeaderReader.read(cropBinHeader);

            Trace cropTrace = new Trace(cropBinHeader, header, cropValues, trace.getLatLonOrigin());
            cropTrace.setLatLon(trace.getLatLon());
            cropTrace.setMarked(trace.isMarked());

            cropTraces.add(cropTrace);
        }

        copy.setTraces(cropTraces);
        copy.getSampleNormalizer().normalize(cropTraces);

        copy.updateTraces();
        copy.updateTraceDistances();

        // copy aux elements
        if (!traces.isEmpty()) {
            copy.setAuxElements(copyAuxElements(gprFile, copy));
        }

        return copy;
    }

    private List<BaseObject> copyAuxElements(GprFile file, GprFile target) {
        List<BaseObject> auxElements = new ArrayList<>();
        for (BaseObject auxElement : file.getAuxElements()) {
            BaseObject copy;
            if (auxElement instanceof ClickPlace clickPlace) {
                TraceKey trace = clickPlace.getTrace();
                TraceKey targetTrace = new TraceKey(target, trace.getIndex());
                copy = new ClickPlace(targetTrace);
            } else if (auxElement instanceof FoundPlace foundPlace) {
                TraceKey trace = foundPlace.getTrace();
                TraceKey targetTrace = new TraceKey(target, trace.getIndex());
                copy = new FoundPlace(targetTrace, model);
            } else {
                copy = auxElement.copy(0);
            }
            if (copy != null) {
                auxElements.add(copy);
            }
        }
        return auxElements;
    }

    private void setNumSamlpes(byte[] binTraceHeader, int numSamples) {
        ByteBuffer buffer = ByteBuffer.wrap(binTraceHeader);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.putShort(114, (short)numSamples);
    }
}
