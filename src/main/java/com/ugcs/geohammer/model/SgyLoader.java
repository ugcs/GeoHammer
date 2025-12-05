package com.ugcs.geohammer.model;

import java.io.File;
import java.io.FilenameFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.thecoldwine.sigrun.common.BinaryHeader;
import com.github.thecoldwine.sigrun.common.ParseProgressListener;
import com.github.thecoldwine.sigrun.serialization.BinaryHeaderFormat;
import com.github.thecoldwine.sigrun.serialization.BinaryHeaderFormatBuilder;
import com.github.thecoldwine.sigrun.serialization.FormatEntry;
import com.github.thecoldwine.sigrun.serialization.TraceHeaderFormat;
import com.github.thecoldwine.sigrun.serialization.TraceHeaderFormatBuilder;

public class SgyLoader {
    private static final Logger logger = LoggerFactory.getLogger(SgyLoader.class.getName());

    private boolean printLog = false;
    
    public SgyLoader(boolean printLog) {
    	this.printLog = printLog;
    }
    
    private static ParseProgressListener makeListener() {
        return new ParseProgressListener() {
            @Override
            public void progress(long read) {
                //System.out.println("Progress changed to: " + read);
            }
        };
    }

    public static BinaryHeaderFormat makeBinHeaderFormat() {
        return BinaryHeaderFormatBuilder.aBinaryHeaderFormat()
        		
				/*
				3201–3204 Job identification number.
				+3205–3208 Line number. 
				+3209–3212 Reel number.
				3213–3214 Number of data traces per ensemble.
				3215–3216 Number of auxiliary traces per ensemble.
				3217–3218 Sample interval. Microseconds (µs) for time data, 
					Hertz (Hz) for frequency data, 
					meters (m) or feet (ft) for depth data.
				3219–3220 Sample interval of original field recording. 
					Microseconds (µs) for time data, 
					Hertz (Hz) for frequency data, 
					meters (m) or feet (ft) for depth data.
				3221–3222 Number of samples per data trace.
				3223–3224 Number of samples per data trace for original.
				 */
                .withLineNumberFormat(FormatEntry.create(4, 8))
                .withReelNumberFormat(FormatEntry.create(8, 12))
                .withDataTracesPerEnsembleFormat(FormatEntry.create(12, 14))
                .withAuxiliaryTracesPerEnsembleFormat(FormatEntry.create(14, 16))
                .withSampleIntervalFormat(FormatEntry.create(16, 18))
                .withSampleIntervalOfOFRFormat(FormatEntry.create(18, 20))
                .withSamplesPerDataTraceFormat(FormatEntry.create(20, 22))
                .withSamplesPerDataTraceOfOFRFormat(FormatEntry.create(22, 24))
                
                .withDataSampleCodeFormat(FormatEntry.create(24, 26))
                
                //.withMeasurementSystemFormat(FormatEntry.create(24, 26)measurementSystemFormat)
                .withSweepLengthFormat(FormatEntry.create(36, 38))
                
                //36 38
                
                .withSegyFormatRevNumberFormat(FormatEntry.create(300, 302))
                .withFixedLengthTraceFlagFormat(FormatEntry.create(302, 304))
                .withNumberOf3200ByteFormat(FormatEntry.create(304, 306))
                
                
                .build();
    }

    public static TraceHeaderFormat makeTraceHeaderFormat() {
        return TraceHeaderFormatBuilder.aTraceHeaderFormat()
        		//.withTr
        		.withTraceSequenceNumberWLFormat(FormatEntry.create(0, 4))
        		.withEnsembleNumberFormat(FormatEntry.create(20, 24))
        		
        		.withScalarForElevationsFormat(FormatEntry.create(68, 70))
        		.withScalarForCoordinatesFormat(FormatEntry.create(70, 72))
        		
        		.withCoordinateUnitsCodeFormat(FormatEntry.create(70, 72))
        		.withSourceXFormat(FormatEntry.create(72, 76))
        		.withSourceYFormat(FormatEntry.create(76, 80))
        		.withGroupXFormat(FormatEntry.create(80, 84))
        		.withGroupYFormat(FormatEntry.create(84, 88))
        		.withXOfCDPPositionFormat(FormatEntry.create(180, 184))
        		.withYOfCDPPositionFormat(FormatEntry.create(184, 188))
                //115 116
                .withNumberOfSamplesFormat(FormatEntry.create(114, 116))
                //117-118     59            119 isiptr   * "Sample interval in us for this trace".
                .withSampleIntervalInMcsFormat(FormatEntry.create(116, 118))
				.withYearDataRecordedFormat(FormatEntry.create(156, 158))
				.withDayOfYearFormat(FormatEntry. create(158, 160))
				.withHourOfDayFormat(FormatEntry.create(160, 162))
				. withMinuteOfHourFormat(FormatEntry.create(162, 164))
				.withSecondOfMinuteFormat(FormatEntry.create(164, 166))
				.withDelayRecordingTimeFormat(FormatEntry.create(164, 166))
				.withTraceWeightingFactorFormat(FormatEntry.create(168, 170))
                .build();
    }


    public static FilenameFilter filter = new FilenameFilter() {
	    public boolean accept(File dir, String name) {
	        return name.toLowerCase().endsWith(".sgy");
	    }
	};

    private static void printBinHeaderInfo(BinaryHeader binaryHeader) {
        System.out.println("Binary Header info...");
        System.out.println("Data sample code:" + binaryHeader.getDataSampleCode());
        
        //binaryHeader.getTraceSequenceNumberWL();
    }

    static void printF(float[] ar) {
    	StringBuilder sb = new StringBuilder();
    	for (float f : ar) {
    		sb.append(", ");
    		sb.append(f);
    	}
    	System.out.println(sb.toString());
    }

    static String printB(byte[] ar) {
    	StringBuilder sb = new StringBuilder();
    	for (byte f : ar) {
    		sb.append(", ");
    		sb.append(f);
    	}
    	return sb.toString();
    }

} 