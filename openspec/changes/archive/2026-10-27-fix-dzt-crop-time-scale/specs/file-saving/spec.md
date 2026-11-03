## ADDED Requirements

### Requirement: Save to on a DZT file keeps the time scale

"Save to.." on a DZT file, to a single file or to separate lines, SHALL write each channel's recorded time range and
depth range scaled by the ratio of the saved samples per scan to the source samples per scan, so that the saved file
has the same time per sample and depth per sample as the source channel. A channel whose source header has no
positive samples per scan SHALL keep its time range and depth range. Saving SHALL NOT change the open file, so
saving it again writes the same headers.

#### Scenario: Sample crop, single file
- **WHEN** a DZT file with a range of 50 ns and 512 samples per scan has its sample range cropped to 256 samples and
  is saved with "Save to.." → "Single file"
- **AND** the saved file is opened
- **THEN** it has 257 samples per scan and its sample time is 0.09765625 ns within float precision, as in the source

#### Scenario: Depth range follows the crop
- **WHEN** a DZT file with a depth range of 2.5 m and 512 samples per scan has its sample range cropped to 256
  samples and is saved with "Save to.."
- **THEN** the saved file records a depth range of 2.5 × 257 / 512 m, about 1.255 m

#### Scenario: Top of the range cropped
- **WHEN** the sample range of a DZT file is cropped to start below the first sample and the file is saved with
  "Save to.."
- **THEN** the reopened file has the same sample time as the source

#### Scenario: Channels keep their own time scales
- **WHEN** a two-channel DZT file has channel 1 with 50 ns and channel 2 with 100 ns, both over 512 samples per
  scan, has its sample range cropped and is saved with "Save to.."
- **THEN** in the reopened file channel 1 has a sample time of 0.09765625 ns and channel 2 of 0.1953125 ns, within
  float precision

#### Scenario: Separate lines after a sample crop
- **WHEN** a DZT file has its sample range cropped and is saved with "Save to.." → "Separate lines"
- **THEN** every line file, opened in GeoHammer, has the same sample time as the source

#### Scenario: No crop
- **WHEN** a DZT file without a sample crop is saved with "Save to.."
- **THEN** the saved headers have the same samples per scan, time range and depth range as the source

#### Scenario: Saved twice
- **WHEN** a DZT file with a cropped sample range is saved with "Save to.." twice to different paths
- **THEN** both saved files have the same headers and the open file keeps its sample time
