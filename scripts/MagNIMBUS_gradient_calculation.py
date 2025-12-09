import sys
import argparse
import pandas as pd


def check_column_exists(data, column):
    if column not in data.columns:
        print(f"Error: Column '{column}' not found in input data")
        sys.exit(1)


def check_column_numeric(data, column):
    if not pd.api.types.is_numeric_dtype(data[column]):
        print(f"Error: Column '{column}' must be numeric")
        sys.exit(1)


def calculate_gradient(data, lower_column, upper_column):
    check_column_exists(data, lower_column)
    check_column_numeric(data, lower_column)
    check_column_exists(data, upper_column)
    check_column_numeric(data, upper_column)

    # target column type is string with empty values for n/a
    data["Gradient"] = (data[lower_column] - data[upper_column]).fillna("")


def main():
    parser = argparse.ArgumentParser(
        description="Calculates gradient as a difference in TMI values from lower and upper sensors."
    )

    parser.add_argument("file_path", help='File path')
    parser.add_argument("--lower-sensor-column", default="TMI_LPF", help="Lower sensor column (default: TMI_LPF)")
    parser.add_argument("--upper-sensor-column", default="TMI_S_LPF", help="Upper sensor column (default: TMI_S_LPF)")
    args = parser.parse_args()

    input_path = args.file_path
    output_path = args.file_path

    print(f"Reading file {input_path}")
    data = pd.read_csv(input_path)

    print(f"Calculating gradient {args.lower_sensor_column} - {args.upper_sensor_column}")
    calculate_gradient(data, args.lower_sensor_column, args.upper_sensor_column)

    print(f"Writing result to {output_path}")
    data.to_csv(output_path, index = False, sep = ',')

    print(f"Done")


if __name__ == "__main__":
    main()
