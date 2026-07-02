package com.ugcs.geohammer.format.gpr.segy;

import com.github.thecoldwine.sigrun.common.BinaryHeader;
import com.github.thecoldwine.sigrun.common.DataSample;
import com.github.thecoldwine.sigrun.common.SweepTypeCode;
import com.github.thecoldwine.sigrun.common.TaperType;
import com.github.thecoldwine.sigrun.common.TraceSorting;
import com.github.thecoldwine.sigrun.serialization.BinaryHeaderFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// extension of the sigrun BinaryHeaderReader
public class BinaryHeaderReader {

    private final BinaryHeaderFormat format;

    public BinaryHeaderReader(BinaryHeaderFormat format) {
        this.format = format;
    }

    public ByteOrder detectByteOrder(byte[] bytes) {
        return SegyByteOrder.detect(bytes, format);
    }

    public BinaryHeader read(byte[] bytes) {
        return read(bytes, detectByteOrder(bytes));
    }

    public BinaryHeader read(byte[] bytes, ByteOrder order) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(order);
        BinaryHeader header = new BinaryHeader();

        if (format.jobIdFormat != null)
            header.setJobId(buffer.getInt(format.jobIdFormat.posStart));
        if (format.lineNumberFormat != null)
            header.setLineNumber(buffer.getInt(format.lineNumberFormat.posStart));
        if (format.reelNumberFormat != null)
            header.setReelNumber(buffer.getInt(format.reelNumberFormat.posStart));
        if (format.dataTracesPerEnsembleFormat != null)
            header.setDataTracesPerEnsemble(buffer.getShort(format.dataTracesPerEnsembleFormat.posStart));
        if (format.auxiliaryTracesPerEnsembleFormat != null)
            header.setAuxiliaryTracesPerEnsemble(buffer.getShort(format.auxiliaryTracesPerEnsembleFormat.posStart));
        if (format.sampleIntervalFormat != null)
            header.setSampleInterval(buffer.getShort(format.sampleIntervalFormat.posStart));
        if (format.sampleIntervalOfOFRFormat != null)
            header.setSampleIntervalOfOFR(buffer.getShort(format.sampleIntervalOfOFRFormat.posStart));
        if (format.samplesPerDataTraceFormat != null)
            header.setSamplesPerDataTrace(buffer.getShort(format.samplesPerDataTraceFormat.posStart));
        if (format.samplesPerDataTraceOfOFRFormat != null)
            header.setSamplesPerDataTraceOfOFR(buffer.getShort(format.samplesPerDataTraceOfOFRFormat.posStart));
        if (format.dataSampleCodeFormat != null)
            header.setDataSampleCode(DataSample.create(buffer.getShort(format.dataSampleCodeFormat.posStart)));
        if (format.ensembleFoldFormat != null)
            header.setEnsembleFold(buffer.getShort(format.ensembleFoldFormat.posStart));
        if (format.traceSortingFormat != null)
            header.setTraceSorting(TraceSorting.create(buffer.getShort(format.traceSortingFormat.posStart)));
        if (format.verticalSumCodeFormat != null)
            header.setVerticalSumCode(buffer.getShort(format.verticalSumCodeFormat.posStart));
        if (format.sweepFrequencyAtStartFormat != null)
            header.setSweepFrequencyAtStart(buffer.getShort(format.sweepFrequencyAtStartFormat.posStart));
        if (format.sweepFrequencyAtEndFormat != null)
            header.setSweepFrequencyAtEnd(buffer.getShort(format.sweepFrequencyAtEndFormat.posStart));
        if (format.sweepLengthFormat != null)
            header.setSweepLength(buffer.getShort(format.sweepLengthFormat.posStart));
        if (format.sweepTypeCodeFormat != null)
            header.setSweepTypeCode(SweepTypeCode.create(buffer.getShort(format.sweepTypeCodeFormat.posStart)));
        if (format.traceNumberFormat != null)
            header.setTraceNumber(buffer.getShort(format.traceNumberFormat.posStart));
        if (format.taperLengthAtStartFormat != null)
            header.setTaperLengthAtStart(buffer.getShort(format.taperLengthAtStartFormat.posStart));
        if (format.taperLengthAtEndFormat != null)
            header.setTaperLengthAtEnd(buffer.getShort(format.taperLengthAtEndFormat.posStart));
        if (format.taperTypeFormat != null)
            header.setTaperType(TaperType.create(buffer.getShort(format.taperTypeFormat.posStart)));
        if (format.dataTracesCorrelatedFormat != null)
            header.setDataTracesCorrelated(buffer.getShort(format.dataTracesCorrelatedFormat.posStart));
        if (format.binaryGainRecoveredFormat != null)
            header.setBinaryGainRecovered(buffer.getShort(format.binaryGainRecoveredFormat.posStart));
        if (format.amplitudeRecoveryMethodFormat != null)
            header.setAmplitudeRecoveryMethod(buffer.getShort(format.amplitudeRecoveryMethodFormat.posStart));
        if (format.measurementSystemFormat != null)
            header.setMeasurementSystem(buffer.getShort(format.measurementSystemFormat.posStart));
        if (format.impulseSignalPolarityFormat != null)
            header.setImpulseSignalPolarity(buffer.getShort(format.impulseSignalPolarityFormat.posStart));
        if (format.vibratoryPolarityCodeFormat != null)
            header.setVibratoryPolarityCode(buffer.getShort(format.vibratoryPolarityCodeFormat.posStart));
        if (format.segyFormatRevNumberFormat != null)
            header.setSegyFormatRevNumber(buffer.getShort(format.segyFormatRevNumberFormat.posStart));
        if (format.fixedLengthTraceFlagFormat != null)
            header.setFixedLengthTraceFlag(buffer.getShort(format.fixedLengthTraceFlagFormat.posStart));
        if (format.numberOf3200ByteFormat != null)
            header.setNumberOf3200Byte(buffer.getShort(format.numberOf3200ByteFormat.posStart));

        return header;
    }
}
