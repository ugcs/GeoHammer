package com.ugcs.geohammer.format.gpr.segy;

import com.github.thecoldwine.sigrun.common.CoordinateUnitsCode;
import com.github.thecoldwine.sigrun.common.GainTypeForInstruments;
import com.github.thecoldwine.sigrun.common.TraceHeader;
import com.github.thecoldwine.sigrun.common.TraceIdentificationCode;
import com.github.thecoldwine.sigrun.serialization.TraceHeaderFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

// extension of the sigrun TraceHeaderReader
public class TraceHeaderReader {

    private final TraceHeaderFormat format;

    public TraceHeaderReader(TraceHeaderFormat format) {
        this.format = format;
    }

    public TraceHeader read(byte[] bytes, ByteOrder order) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(order);
        TraceHeader header = new TraceHeader();

        if (format.traceSequenceNumberWLFormat != null)
            header.setTraceSequenceNumberWL(buffer.getInt(format.traceSequenceNumberWLFormat.posStart));
        if (format.traceSequenceNumberWSFormat != null)
            header.setTraceSequenceNumberWS(buffer.getInt(format.traceSequenceNumberWSFormat.posStart));
        if (format.originalFieldRecordNumberFormat != null)
            header.setOriginalFieldRecordNumber(buffer.getInt(format.originalFieldRecordNumberFormat.posStart));
        if (format.traceNumberWOFRFormat != null)
            header.setTraceNumberWOFR(buffer.getInt(format.traceNumberWOFRFormat.posStart));
        if (format.energySourcePointNumberFormat != null)
            header.setEnergySourcePointNumber(buffer.getInt(format.energySourcePointNumberFormat.posStart));
        if (format.ensembleNumberFormat != null)
            header.setEnsembleNumber(buffer.getInt(format.ensembleNumberFormat.posStart));
        if (format.traceNumberWEnsembleFormat != null)
            header.setTraceNumberWEnsemble(buffer.getInt(format.traceNumberWEnsembleFormat.posStart));
        if (format.traceIdentificationCodeFormat != null)
            header.setTraceIdentificationCode(TraceIdentificationCode.create(buffer.getShort(format.traceIdentificationCodeFormat.posStart)));
        if (format.numberOfVerticallySummedTracesFormat != null)
            header.setNumberOfVerticallySummedTraces(buffer.getShort(format.numberOfVerticallySummedTracesFormat.posStart));
        if (format.numberOfHorizontallyStackedTracesFormat != null)
            header.setNumberOfHorizontallyStackedTraces(buffer.getShort(format.numberOfHorizontallyStackedTracesFormat.posStart));
        if (format.dataUseFormat != null)
            header.setDataUse(buffer.getShort(format.dataUseFormat.posStart));
        if (format.distanceFromTheCenterOfSPFormat != null)
            header.setDistanceFromTheCenterOfSP(buffer.getInt(format.distanceFromTheCenterOfSPFormat.posStart));
        if (format.receiverGroupElevationFormat != null)
            header.setReceiverGroupElevation(buffer.getInt(format.receiverGroupElevationFormat.posStart));
        if (format.surfaceElevationAtSourceFormat != null)
            header.setSurfaceElevationAtSource(buffer.getInt(format.surfaceElevationAtSourceFormat.posStart));
        if (format.sourceDepthBelowSurfaceFormat != null)
            header.setSourceDepthBelowSurface(buffer.getInt(format.sourceDepthBelowSurfaceFormat.posStart));
        if (format.datumElevationAtReceiverGroupFormat != null)
            header.setDatumElevationAtReceiverGroup(buffer.getInt(format.datumElevationAtReceiverGroupFormat.posStart));
        if (format.datumElevationAtSourceFormat != null)
            header.setDatumElevationAtSource(buffer.getInt(format.datumElevationAtSourceFormat.posStart));
        if (format.waterDepthAtSourceFormat != null)
            header.setWaterDepthAtSource(buffer.getInt(format.waterDepthAtSourceFormat.posStart));
        if (format.waterDepthAtGroupFormat != null)
            header.setWaterDepthAtGroup(buffer.getInt(format.waterDepthAtGroupFormat.posStart));
        if (format.scalarForElevationsFormat != null)
            header.setScalarForElevations(buffer.getShort(format.scalarForElevationsFormat.posStart));
        if (format.scalarForCoordinatesFormat != null)
            header.setScalarForCoordinates(buffer.getShort(format.scalarForCoordinatesFormat.posStart));
        if (format.sourceXFormat != null)
            header.setSourceX(buffer.getInt(format.sourceXFormat.posStart));
        if (format.sourceYFormat != null)
            header.setSourceY(buffer.getInt(format.sourceYFormat.posStart));
        if (format.groupXFormat != null)
            header.setGroupX(buffer.getInt(format.groupXFormat.posStart));
        if (format.groupYFormat != null)
            header.setGroupY(buffer.getInt(format.groupYFormat.posStart));
        if (format.coordinateUnitsCodeFormat != null)
            header.setCoordinateUnitsCode(CoordinateUnitsCode.create(buffer.getShort(format.coordinateUnitsCodeFormat.posStart)));
        if (format.weatheringVelocityFormat != null)
            header.setWeatheringVelocity(buffer.getShort(format.weatheringVelocityFormat.posStart));
        if (format.subweatheringVelocityFormat != null)
            header.setSubweatheringVelocity(buffer.getShort(format.subweatheringVelocityFormat.posStart));
        if (format.upholeTimeAtSourceInMsFormat != null)
            header.setUpholeTimeAtSourceInMs(buffer.getShort(format.upholeTimeAtSourceInMsFormat.posStart));
        if (format.upholeTimeAtGroupInMsFormat != null)
            header.setUpholeTimeAtGroupInMs(buffer.getShort(format.upholeTimeAtGroupInMsFormat.posStart));
        if (format.sourceStaticCorrectionInMsFormat != null)
            header.setSourceStaticCorrectionInMs(buffer.getShort(format.sourceStaticCorrectionInMsFormat.posStart));
        if (format.groupStaticCorrectionInMsFormat != null)
            header.setGroupStaticCorrectionInMs(buffer.getShort(format.groupStaticCorrectionInMsFormat.posStart));
        if (format.totalStaticAppliedInMsFormat != null)
            header.setTotalStaticAppliedInMs(buffer.getShort(format.totalStaticAppliedInMsFormat.posStart));
        if (format.lagTimeAFormat != null)
            header.setLagTimeA(buffer.getShort(format.lagTimeAFormat.posStart));
        if (format.lagTimeBFormat != null)
            header.setLagTimeB(buffer.getShort(format.lagTimeBFormat.posStart));
        if (format.delayRecordingTimeFormat != null)
            header.setDelayRecordingTime(buffer.getShort(format.delayRecordingTimeFormat.posStart));
        if (format.muteTimeStartFormat != null)
            header.setMuteTimeStart(buffer.getShort(format.muteTimeStartFormat.posStart));
        if (format.muteTimeEndFormat != null)
            header.setMuteTimeEnd(buffer.getShort(format.muteTimeEndFormat.posStart));
        if (format.numberOfSamplesFormat != null)
            header.setNumberOfSamples(buffer.getShort(format.numberOfSamplesFormat.posStart));
        if (format.sampleIntervalInMcsFormat != null)
            header.setSampleIntervalInMcs(buffer.getShort(format.sampleIntervalInMcsFormat.posStart));
        if (format.gainTypeForInstrumentsFormat != null)
            header.setGainTypeForInstruments(GainTypeForInstruments.create(buffer.getShort(format.gainTypeForInstrumentsFormat.posStart)));
        if (format.instrumentGainConstantFormat != null)
            header.setInstrumentGainConstant(buffer.getShort(format.instrumentGainConstantFormat.posStart));
        if (format.instrumentEarlyOrInitialGainFormat != null)
            header.setInstrumentEarlyOrInitialGain(buffer.getShort(format.instrumentEarlyOrInitialGainFormat.posStart));
        if (format.correlatedFormat != null)
            header.setCorrelated(buffer.getShort(format.correlatedFormat.posStart));
        if (format.sweepFrequencyAtStartFormat != null)
            header.setSweepFrequencyAtStart(buffer.getShort(format.sweepFrequencyAtStartFormat.posStart));
        if (format.sweepFrequencyAtEndFormat != null)
            header.setSweepFrequencyAtEnd(buffer.getShort(format.sweepFrequencyAtEndFormat.posStart));
        if (format.sweepLengthInMillisecondsFormat != null)
            header.setSweepLengthInMilliseconds(buffer.getShort(format.sweepLengthInMillisecondsFormat.posStart));
        if (format.sweepTypeFormat != null)
            header.setSweepType(buffer.getShort(format.sweepTypeFormat.posStart));
        if (format.sweepTraceTaperLengthAtStartInMillisecondsFormat != null)
            header.setSweepTraceTaperLengthAtStartInMilliseconds(buffer.getShort(format.sweepTraceTaperLengthAtStartInMillisecondsFormat.posStart));
        if (format.sweepTraceTaperLengthAtEndInMillisecondsFormat != null)
            header.setSweepTraceTaperLengthAtEndInMilliseconds(buffer.getShort(format.sweepTraceTaperLengthAtEndInMillisecondsFormat.posStart));
        if (format.taperTypeFormat != null)
            header.setTaperType(buffer.getShort(format.taperTypeFormat.posStart));
        if (format.aliasFilterFrequencyFormat != null)
            header.setAliasFilterFrequency(buffer.getShort(format.aliasFilterFrequencyFormat.posStart));
        if (format.aliasFilterSlopeFormat != null)
            header.setAliasFilterSlope(buffer.getShort(format.aliasFilterSlopeFormat.posStart));
        if (format.notchFilterFrequencyFormat != null)
            header.setNotchFilterFrequency(buffer.getShort(format.notchFilterFrequencyFormat.posStart));
        if (format.notchFilterSlopeFormat != null)
            header.setNotchFilterSlope(buffer.getShort(format.notchFilterSlopeFormat.posStart));
        if (format.lowCutFrequencyFormat != null)
            header.setLowCutFrequency(buffer.getShort(format.lowCutFrequencyFormat.posStart));
        if (format.highCutFrequencyFormat != null)
            header.setHighCutFrequency(buffer.getShort(format.highCutFrequencyFormat.posStart));
        if (format.lowCutSlopeFormat != null)
            header.setLowCutSlope(buffer.getShort(format.lowCutSlopeFormat.posStart));
        if (format.highCutSlopeFormat != null)
            header.setHighCutSlope(buffer.getShort(format.highCutSlopeFormat.posStart));
        if (format.yearDataRecordedFormat != null)
            header.setYearDataRecorded(buffer.getShort(format.yearDataRecordedFormat.posStart));
        if (format.dayOfYearFormat != null)
            header.setDayOfYear(buffer.getShort(format.dayOfYearFormat.posStart));
        if (format.hourOfDayFormat != null)
            header.setHourOfDay(buffer.getShort(format.hourOfDayFormat.posStart));
        if (format.minuteOfHourFormat != null)
            header.setMinuteOfHour(buffer.getShort(format.minuteOfHourFormat.posStart));
        if (format.secondOfMinuteFormat != null)
            header.setSecondOfMinute(buffer.getShort(format.secondOfMinuteFormat.posStart));
        if (format.timeBasisCodeFormat != null)
            header.setTimeBasisCode(buffer.getShort(format.timeBasisCodeFormat.posStart));
        if (format.traceWeightingFactorFormat != null)
            header.setTraceWeightingFactor(buffer.getShort(format.traceWeightingFactorFormat.posStart));
        if (format.geophoneGroupNumberOfRollSwitchPositionOneFormat != null)
            header.setGeophoneGroupNumberOfRollSwitchPositionOne(buffer.getShort(format.geophoneGroupNumberOfRollSwitchPositionOneFormat.posStart));
        if (format.geophoneGroupNumberOfTraceNumberOneWOFRFormat != null)
            header.setGeophoneGroupNumberOfTraceNumberOneWOFR(buffer.getShort(format.geophoneGroupNumberOfTraceNumberOneWOFRFormat.posStart));
        if (format.geophoneGroupNumberOfLastTraceWOFRFormat != null)
            header.setGeophoneGroupNumberOfLastTraceWOFR(buffer.getShort(format.geophoneGroupNumberOfLastTraceWOFRFormat.posStart));
        if (format.gapSizeFormat != null)
            header.setGapSize(buffer.getShort(format.gapSizeFormat.posStart));
        if (format.overTravelFormat != null)
            header.setOverTravel(buffer.getShort(format.overTravelFormat.posStart));
        if (format.xOfCDPPositionFormat != null)
            header.setxOfCDPPosition(buffer.getInt(format.xOfCDPPositionFormat.posStart));
        if (format.yOfCDPPositionFormat != null)
            header.setyOfCDPPosition(buffer.getInt(format.yOfCDPPositionFormat.posStart));
        if (format.inLineNumberFormat != null)
            header.setInLineNumber(buffer.getInt(format.inLineNumberFormat.posStart));
        if (format.crossLineNumberFormat != null)
            header.setCrossLineNumber(buffer.getInt(format.crossLineNumberFormat.posStart));
        if (format.shotpointNumberFormat != null)
            header.setShotpointNumber(buffer.getInt(format.shotpointNumberFormat.posStart));
        if (format.scalarForSPNumberFormat != null)
            header.setScalarForSPNumber(buffer.getShort(format.scalarForSPNumberFormat.posStart));
        if (format.traceValuesMUFormat != null)
            header.setTraceValuesMU(buffer.getShort(format.traceValuesMUFormat.posStart));
        if (format.transductionConstantFormat != null)
            header.setTransductionConstant(Arrays.copyOfRange(bytes, format.transductionConstantFormat.posStart, format.transductionConstantFormat.posEnd));
        if (format.transductionUnitsFormat != null)
            header.setTransductionUnits(buffer.getShort(format.transductionUnitsFormat.posStart));
        if (format.deviceTraceIdentifierFormat != null)
            header.setDeviceTraceIdentifier(buffer.getShort(format.deviceTraceIdentifierFormat.posStart));
        if (format.scalarToBeAppliedToTimesFormat != null)
            header.setScalarForTimes(buffer.getShort(format.scalarToBeAppliedToTimesFormat.posStart));
        if (format.sourceTypeOrientationFormat != null)
            header.setSourceTypeOrientation(buffer.getShort(format.sourceTypeOrientationFormat.posStart));
        if (format.sourceEnergyDirectionFormat != null)
            header.setSourceEnergyDirection(Arrays.copyOfRange(bytes, format.sourceEnergyDirectionFormat.posStart, format.sourceEnergyDirectionFormat.posEnd));
        if (format.sourceMeasurementFormat != null)
            header.setSourceMeasurement(Arrays.copyOfRange(bytes, format.sourceMeasurementFormat.posStart, format.sourceMeasurementFormat.posEnd));
        if (format.sourceMeasurementUnitFormat != null)
            header.setSourceMeasurementUnit(buffer.getShort(format.sourceMeasurementUnitFormat.posStart));

        return header;
    }
}
