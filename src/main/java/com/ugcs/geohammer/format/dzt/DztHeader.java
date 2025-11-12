package com.ugcs.geohammer.format.dzt;

public class DztHeader {
	// constants
	private static final int MINHEADSIZE = 1024;
	private static final int PARAREASIZE = 128;
	private static final int GPSAREASIZE = 2 * 4; //sizeof(RGPS); !!!!!!!
	private static final int INFOAREASIZE = (MINHEADSIZE - PARAREASIZE- GPSAREASIZE) ;

	
	@BaseField(position = 0)
	short rh_tag; // 0x00ff if header, 0xfnff for old file 00
	
	@BaseField(position = 2)
	short rh_data; // Offset to Data from beginning of file 02
	
	@BaseField(position = 4)
	short rh_nsamp; // samples per scan 04
	
	@BaseField(position = 6)
	short rh_bits; // bits per data word (8,16, 32) * 06
	
	@BaseField(position = 8)
	short rh_zero; // if rh_system is SIR-30 or UtilityScan DF 08
	
	// then equals repeats/sample  otherwise is 0x80 for 8 bit data and   0x8000 for 16 bit data
	@BaseField(position = 10)
	float rhf_sps; // scans per second 10
	
	@BaseField(position = 14)
	float rhf_spm; // scans per meter 14
	
	@BaseField(position = 18)
	float rhf_mpm; // meters per mark 18
	
	@BaseField(position = 22)
	float rhf_position; // position (ns) 22
	
	@BaseField(position = 26)
	float rhf_range; // range (ns) 26
	
	@BaseField(position = 30)
	short rh_npass; // num of passes for 2-D files 30
	
	@BaseField(position = 32)
	byte[] rhb_cdt = new byte[4]; // Creation date & time 32
	
	@BaseField(position = 36)
	byte[] rhb_mdt = new byte[4]; // Last modification date & time 36
	
	@BaseField(position = 40)
	short rh_mapOffset; // For internal use 40 
	
	@BaseField(position = 42)
	short rh_mapSize; // For internal use	42
	
	@BaseField(position = 44)
	short rh_text; // offset to text 44
	
	@BaseField(position = 46)
	short rh_ntext; // size of text 46
	
	@BaseField(position = 48)
	short rh_proc; // offset to processing history 48
	
	@BaseField(position = 50)
	short rh_nproc; // size of processing history 50
	
	@BaseField(position = 52)
	short rh_nchan; // number of channels 52
	
	@BaseField(position = 54)
	float rhf_epsr; // average dielectric constant 54
	
	@BaseField(position = 58)
	float rhf_top; // position in meters 58
	
	@BaseField(position = 62)
	float rhf_depth; // range in meters 62
		
	@BaseField(position = 66)
	byte[] rh_coordX = new byte[8]; // X coordinates 66
	
	@BaseField(position = 74)
	float rhf_servo_level; // gain servo level 74
	
	@BaseField(position = 78)
	byte[] reserved = new byte[3]; // reserved 78
	
	@BaseField(position = 81)
	byte rh_accomp; // Ant Conf component 81
	
	@BaseField(position = 82)
	short rh_sconfig; // setup config number 82
	
	@BaseField(position = 84)
	short rh_spp; // scans per pass 84
	
	@BaseField(position = 86)
	short rh_linenum; // line number 86
	
	@BaseField(position = 88)
	byte[] rh_coordY = new byte[8]; // Y coordinates 88
	
	@BaseField(position = 96)
	byte rh_lineorder;//:4; // 96
	
	@BaseField(position = 96)
	byte rh_slicetype;//:4; // 96
	
	@BaseField(position = 97)
	char rh_dtype; // 97
	
	@BaseField(position = 98, size=14)
	String rh_antname; // Antenna name 98
	
	@BaseField(position = 112)
	byte rh_pass0TX;//:4; // Activ Transmit mask 112
	
	@BaseField(position = 112)
	byte rh_pass1TX;//:4; // Activ Transmit mask 112
	
	@BaseField(position = 113)
	byte rh_version;//:3; // 1 – no GPS; 2 - GPS 113
	
	@BaseField(position = 113)
	byte rh_system;//:5; // (see below for description)** 113
	
	@BaseField(position = 114, size=12)
	String rh_name; // Initial File Name 114
	
	@BaseField(position = 126)
	short rh_chksum; // checksum for header 126
	
	@BaseField(position = 128, size=INFOAREASIZE)
	byte[] variable = new byte[INFOAREASIZE]; // Variable data 128
	
	@BaseField(position = 944)
	byte[] rh_RGPS = new byte[24]; // GPS info 944

}
