## ADDED Requirements

### Requirement: 16-bit GPR samples are clamped on save
When a GPR file whose samples are stored as 16-bit integers is written, each sample outside the range the format can
hold SHALL be written as the nearest value of that range, keeping its sign. For a SEG-Y file with 2-byte integer
samples the range SHALL be -32768 to 32767. For a 16-bit DZT file the range SHALL be -32767 to 32768, the values a
16-bit DZT sample reads back as in GeoHammer. Samples within the range SHALL be written as they are written today.
The trace number stored at the start of each DZT trace SHALL be written as it is written today.

#### Scenario: SEG-Y sample above the range
- **WHEN** a SEG-Y file with 2-byte integer samples has a sample of 40000 after gain and is saved with "Save to.."
- **AND** the saved file is opened
- **THEN** that sample is 32767

#### Scenario: SEG-Y sample below the range
- **WHEN** a SEG-Y file with 2-byte integer samples has a sample of -40000 and is saved with "Save to.."
- **AND** the saved file is opened
- **THEN** that sample is -32768

#### Scenario: DZT sample above the range
- **WHEN** a 16-bit DZT file has a sample of 40000 and is saved with "Save to.."
- **AND** the saved file is opened
- **THEN** that sample is 32768

#### Scenario: DZT sample below the range
- **WHEN** a 16-bit DZT file has a sample of -40000 and is saved with "Save to.."
- **AND** the saved file is opened
- **THEN** that sample is -32767

#### Scenario: Samples at the ends of the range
- **WHEN** a 16-bit GPR file has samples equal to the lowest and highest values of its range and is saved with
  "Save to.."
- **THEN** the reopened file has the same samples

#### Scenario: DZT trace numbers past 32768 traces
- **WHEN** a 16-bit DZT file with more than 32768 traces is saved with "Save to.."
- **THEN** the trace number at the start of each trace has the same stored value as before this change
