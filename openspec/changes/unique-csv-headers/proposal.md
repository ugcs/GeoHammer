## Why

A CSV header line with a repeated or empty column name makes every later column read the data of its left
neighbour (#1029). The file opens without an error, the track and series show wrong values, and saving writes the
shifted values back and drops a column from the source file.

Examples: in `Time,Value,Value,Lat,Lon` latitude reads the second `Value` column; in `Lat,Lon,,,Mag` the `Mag` series
reads an empty column.

## What Changes

- Every column of a CSV or fixed-width data file keeps its own data, whatever the names in the header are.
- Column names are made unique when the file is opened:
  - the first column with a name keeps it; a later column with the same name gets a suffix `_1`, `_2`, …;
  - a column with an empty name is named `column_<index>` by its zero-based position, the naming already used for
    files without a header line;
  - a generated name never takes a name that another column has in the file.
- Saving writes the unique names into the header line.
- Opening a file whose columns were renamed shows a warning that lists the new names and says they will be saved.
- **BREAKING**: a file with repeated column names that opened before now shows the first of them under the original
  name (it was the last one), and a single empty column name is replaced with `column_<index>`.

## Non-goals

- Keeping the original header line on save.
- Template editor: its preview keeps showing the names as written in the file.
- Quoted column names and separators inside quotes.
- Rows with more or fewer cells than the header line.
- Templates that map two columns to the same name.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `csv-loading`: new requirement that every column is loaded from its own position under a unique name, that the
  unique names are saved, and that the user is warned about renamed columns.

## Impact

- `format/csv/parser/Parser` — header names are made unique before the column schema is built.
- `model/template/DataMapping` — the `column_` prefix becomes a shared constant.
- `CsvWriter`, `FixedWidthWriter` — no change: they already write the names of the loaded columns.
- Tests: `CsvParserTest`, `CsvFileTest`.
