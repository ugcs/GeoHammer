import argparse

def main():
    parser = argparse.ArgumentParser(description="Example script for processing data.")
    parser.add_argument('--input', type=str, required=True, help='Input file path')
    parser.add_argument('--threshold', type=float, default=0.5, help='Threshold value')
    parser.add_argument('--verbose', action='store_true', help='Enable verbose output')
    args = parser.parse_args()

    print(f"Input: {args.input}")
    print(f"Threshold: {args.threshold}")
    print(f"Verbose: {args.verbose}")

if __name__ == "__main__":
    main()