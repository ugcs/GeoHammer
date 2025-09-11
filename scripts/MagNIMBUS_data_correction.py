import argparse
import pandas as pd
import statsmodels.api as sm
from scipy.fft import fft, ifft
import numpy as np
from sklearn.preprocessing import PolynomialFeatures
from scipy.ndimage import gaussian_filter1d
from scipy.signal import butter, filtfilt, iirnotch


def notch_zero_filter(data, notchh, notchl, freq): # works as highpass lowpass and notch filter
    if len(data) < 2:
        return data
    freqh = notchh * (len(data)) // (freq)
    freql = notchl * (len(data)) // (freq)
    yf = fft(data.values)
    yff = np.zeros(len(yf))
    yff[0:len(yf)] = 1
    yff[freql:freqh] = 0
    yff[len(yf) - (freqh):len(yf) - (freql)] = 0
    yf = yf * yff
    dataf = ifft(yf)
    return np.real(dataf)


### Remove any bad data points From mag data, Interpolate vector data to match frequency of scalar data
def filter_magnetic(data):
     # Shift up the X, Y, Z, and 'Good V-Data' data by one row
    data['Bx'] = data['Bx'].shift(-1)
    data['By'] = data['By'].shift(-1)
    data['Bz'] = data['Bz'].shift(-1)

    # Set consecutive duplicate values to NaN
    data['Bx'] = data['Bx'].mask(data['Bx'].shift() == data['Bx'])
    data['By'] = data['By'].mask(data['By'].shift() == data['By'])
    data['Bz'] = data['Bz'].mask(data['Bz'].shift() == data['Bz'])

    # Interpolate missing values
    data['Bx'] = data['Bx'].interpolate()
    data['By'] = data['By'].interpolate()
    data['Bz'] = data['Bz'].interpolate()

    data.dropna(subset=['Bx', 'By', 'Bz'], inplace = True)

    # Apply the lowpass filter to each vector column
    fq = 250  # frequency of data in Hz - need to change to measure real frequency (!)
    notch_high = fq // 2
    notch_low = 4

    data['Bx_F'] = notch_zero_filter(data['Bx'], notch_high, notch_low, fq)
    data['By_F'] = notch_zero_filter(data['By'], notch_high, notch_low, fq)
    data['Bz_F'] = notch_zero_filter(data['Bz'], notch_high, notch_low, fq)

    # Normalize vector data (Filtered)
    # Calculate the synthetic scalar value
    magnitude = np.sqrt(data['Bx_F']**2 + data['By_F']**2 + data['Bz_F']**2)

    # Normalize Bx, By, and Bz
    data['Bx_F_norm'] = data['Bx_F'] / magnitude
    data['By_F_norm'] = data['By_F'] / magnitude
    data['Bz_F_norm'] = data['Bz_F'] / magnitude


def filter_positional(data):
    # Set consecutive duplicate values to NaN
    data['Latitude'] = data['Latitude'].mask(data['Latitude'].shift() == data['Latitude'])
    data['Longitude'] = data['Longitude'].mask(data['Longitude'].shift() == data['Longitude'])

    # Interpolate missing values
    data['Latitude'] = data['Latitude'].interpolate()
    data['Longitude'] = data['Longitude'].interpolate()

    data.dropna(subset = ['Latitude', 'Longitude'], inplace = True)

    data['Latitude_F'] = data['Latitude']
    data['Longitude_F'] = data['Longitude']


def filter_altitude_amsl(data):
    # Set consecutive duplicate values to NaN
    data['Altitude'] = data['Altitude'].mask(data['Altitude'].shift() == data['Altitude'])

    # Interpolate missing values
    data['Altitude'] = data['Altitude'].interpolate()

    data.dropna(subset = ['Altitude'], inplace = True)

    data['Altitude_F'] = data['Altitude']


def filter_altitude_agl(data):
    # Set consecutive duplicate values to NaN
    data['Altitude AGL'] = data['Altitude AGL'].mask(data['Altitude AGL'].shift() == data['Altitude AGL'])

    # Interpolate missing values
    data['Altitude AGL'] = data['Altitude AGL'].interpolate()

    data.dropna(subset = ['Altitude AGL'], inplace = True)

    data['Altitude AGL_F'] = data['Altitude AGL']


def filter_heading(data):
    # Set consecutive duplicate values to NaN
    data['Heading'] = data['Heading'].mask(data['Heading'].shift() == data['Heading'])

    # Interpolate missing values
    data['Heading'] = data['Heading'].interpolate()

    data.dropna(subset = ['Heading'], inplace = True)

    data['Heading_F'] = data['Heading']

    # Heading terms (use sin/cos instead of raw heading)
    data['Heading_sin'] = np.sin(np.radians(data['Heading_F']))
    data['Heading_cos'] = np.cos(np.radians(data['Heading_F']))


def filter_accelerometer(data):
    # Set consecutive duplicate values to NaN
    data['AccelX'] = data['AccelX'].mask(data['AccelX'].shift() == data['AccelX'])
    data['AccelY'] = data['AccelY'].mask(data['AccelY'].shift() == data['AccelY'])
    data['AccelZ'] = data['AccelZ'].mask(data['AccelZ'].shift() == data['AccelZ'])

    # Interpolate missing values in 'X', 'Y', and 'Z' columns
    data['AccelX'] = data['AccelX'].shift(-1).interpolate()
    data['AccelY'] = data['AccelY'].shift(-1).interpolate()
    data['AccelZ'] = data['AccelZ'].shift(-1).interpolate()

    data.dropna(subset = ['AccelX', 'AccelY', 'AccelZ'], inplace = True)

    # Apply the lowpass filter to each vector column
    fq = 250  # frequency of data in Hz - need to change to measure real frequency (!)
    notch_high = fq // 2
    notch_low = 1

    # Apply the notch zero filter to each column
    data['AccelX_F'] = notch_zero_filter(data['AccelX'],  notch_high, notch_low, fq)
    data['AccelY_F'] = notch_zero_filter(data['AccelY'],  notch_high, notch_low, fq)
    data['AccelZ_F'] = notch_zero_filter(data['AccelZ'],  notch_high, notch_low, fq)

    magnitude = np.sqrt(data['AccelX_F']**2 + data['AccelY_F']**2 + data['AccelZ_F']**2)

    data['AccelX_F_norm'] = data['AccelX_F'] / magnitude
    data['AccelY_F_norm'] = data['AccelY_F'] / magnitude
    data['AccelZ_F_norm'] = data['AccelZ_F'] / magnitude


def filter_line(data):
    if (args.include_magnetic):
        filter_magnetic(data)
    if (args.include_position):
        filter_positional(data)
    if (args.include_heading):
        filter_heading(data)
    if (args.include_altitude_amsl):
        filter_altitude_amsl(data)
    if (args.include_altitude_agl):
        filter_altitude_agl(data)
    if (args.include_accelerometer):
        filter_accelerometer(data)


def select_for_training(data):
    tmi = data['TMI'].dropna()

    #### Target large mag signals and remove them from regression model

    # Calculate lower and upper percentiles for TMI
    lower_percentile = np.percentile(tmi, 10)  # May need to be adjusted
    upper_percentile = np.percentile(tmi, 90)  # May need to be adjusted

    # Exclude high and low values based on percentiles
    selected_index = data[(tmi > lower_percentile) & (tmi < upper_percentile)].index
    selected_indexer = data.index.get_indexer(selected_index)

    # Extend the valid points to include previous and next data lines around each valid point
    window_size = 1
    extended_positions = set()
    for position in selected_indexer:
        extended_positions.update(
            range(
                max(position - window_size, 0),
                min(position + window_size, len(data) - 1))
        )

    # Convert to sorted list to maintain order
    extended_indices = data.index[sorted(extended_positions)]
    selected = data.loc[extended_indices]

    dataset_percent = len(selected) / len(tmi) * 100
    print(f"Percent of dataset used for model: {dataset_percent:.1f}%")  ## target goal is >50%

    return selected


def fix_line_tmi_short(data, tmi_mean, line_index):
    residuals = data['TMI'] - data['TMI'].mean()
    residuals_median = residuals.median()
    adjusted_residuals = residuals - residuals_median + tmi_mean

    idx = adjusted_residuals.index.intersection(line_index)
    fixed = pd.DataFrame(index = idx)
    fixed['TMI_Fixed'] = adjusted_residuals.loc[idx]
    # Smooth the residuals using Gaussian filter (or any other smoother)
    fixed['TMI_Fixed_Filtered'] = gaussian_filter1d(adjusted_residuals.loc[idx], sigma = 15)
    return fixed


def fix_line_tmi(data, tmi_mean, line_index):
    print(f"Line length {len(data)}")

    if (len(data['TMI'].dropna()) < 50):
        print("Line range is too small")
        return fix_line_tmi_short(data, tmi_mean, line_index)

    # ----------------------------------------
    # fit model

    selected = select_for_training(data)

    ## Select Higher Order terms to Include
    # Define which components should include both first-order and higher-order terms
    # Options:Altitude_F', 'Altitude AGL_F','Latitude_F', 'Longitude_F','normalized_Ax_F' ,'normalized_Ay_F','normalized_Az_F'

    first_order_components = []
    if (args.include_magnetic):
        first_order_components += ['Bx_F_norm', 'By_F_norm', 'Bz_F_norm']
    if (args.include_heading):
        first_order_components += ['Heading_sin', 'Heading_cos']

    high_order_components = []
    if (args.include_position):
        high_order_components += ['Latitude_F', 'Longitude_F']
    if (args.include_altitude_agl):
        high_order_components += ['Altitude AGL_F']
    if (args.include_altitude_amsl):
        high_order_components += ['Altitude_F']
    if (args.include_accelerometer):
        high_order_components += ['AccelX_F_norm', 'AccelY_F_norm', 'AccelZ_F_norm']

    # You want to include first-order terms for higher-order components as well
    first_order_components += high_order_components

    poly = PolynomialFeatures(degree = 2, include_bias = False)

    # Select the features and target for the regression model
    x_high_order = selected[high_order_components]
    # Handle missing values by dropping rows with any NaN values
    x_high_order = x_high_order.dropna()
    # Generate polynomial features for the higher-order terms
    if len(x_high_order) == 0:
        print("Warning: High order components are empty")
        return fix_line_tmi_short(data, tmi_mean, line_index)
    x_high_order_poly = poly.fit_transform(x_high_order)

    x_first_order = selected[first_order_components]
    x_first_order = x_first_order.loc[x_high_order.index]

    # Concatenate the first-order components and higher-order components
    x_combined = np.hstack([x_first_order.values, x_high_order_poly])
    # Add a constant (intercept) to the model
    x_combined = sm.add_constant(x_combined)

    y = selected['TMI']
    y = y.loc[x_high_order.index]

    # Fit the model with mixed higher-order and first-order terms
    if (args.quantile_regression):
        model = sm.QuantReg(y, x_combined, missing='drop').fit(q = 0.5)
    else:
        model = sm.OLS(y, x_combined, missing='drop').fit()
    #print(model.summary())

    # ----------------------------------------
    # predict

    # Apply the model to the entire dataset
    x_full_high_order = data[high_order_components]
    # Transform the higher-order components
    x_full_high_order_poly = poly.transform(x_full_high_order)

    x_full_first_order = data[first_order_components]
    # Concatenate the first-order and higher-order components for the full dataset
    x_full_combined = np.hstack([x_full_first_order.values, x_full_high_order_poly])
    # Add a constant (intercept) to the combined model
    x_full_combined = sm.add_constant(x_full_combined)

    # Predict the magnetic data based on the full dataset using the mixed model
    y_pred = model.predict(x_full_combined)

    # Calculate the residuals for the entire dataset
    residuals = data['TMI'] - y_pred
    #residuals = residuals.rolling(301, center = True, min_periods = 1).median()

    # Re-center the residuals by adding the mean of the original magnetic data
    residuals_median = residuals.median()
    adjusted_residuals = residuals - residuals_median + tmi_mean

    idx = adjusted_residuals.index.intersection(line_index)
    fixed = pd.DataFrame(index = idx)
    fixed['TMI_Fixed'] = adjusted_residuals.loc[idx]
    # Smooth the residuals using Gaussian filter (or any other smoother)
    fixed['TMI_Fixed_Filtered'] = gaussian_filter1d(adjusted_residuals.loc[idx], sigma = 15)

    return fixed


def fix_tmi(data):
    origin = data

    # Fill missing line indices
    num_empty_next_wp = len(data['Next WP']) - len(data['Next WP'].dropna())
    print(f"Empty Next WP: {num_empty_next_wp}")
    if num_empty_next_wp > 0:
        data = data.copy()
        data['Next WP'] = data['Next WP'].fillna(method='ffill').fillna(0)

    # Get global TMI mean for compensation adjustments
    tmi_mean = data['TMI'].dropna().mean()

    results = []

    line_window = 30
    for key, line_data in data.groupby('Next WP'):
        line_index = line_data.index
        start = max(line_index.min() - line_window, 0)
        end = min(line_index.max() + line_window, len(data) - 1)
        line_data_windowed = data.loc[start:end].copy()

        filter_line(line_data_windowed)
        line_fixed = fix_line_tmi(line_data_windowed, tmi_mean, line_index)

        results.append(line_fixed)

    result = pd.concat(results, axis = 0)
    origin['TMI_Fixed'] = result['TMI_Fixed']
    origin['TMI_Fixed_Filtered'] = result['TMI_Fixed_Filtered']


def main():
    global args

    parser = argparse.ArgumentParser(description="Corrects and filters magnetic and IMU survey data, applies regression models, and exports the processed results to a CSV file.")
    parser.add_argument("file_path", help='File path')
    parser.add_argument("--include-position", action="store_true", help="Include position (latitude/longitude)")
    parser.add_argument("--include-magnetic", action="store_true", help="Include magnetic (Bx/By/Bz)")
    parser.add_argument("--include-heading", action="store_true", help="Include heading")
    parser.add_argument("--include-altitude-agl", action="store_true", help="Include altitude AGL")
    parser.add_argument("--include-altitude-amsl", action="store_true", help="Include altitude AMSL")
    parser.add_argument("--include-accelerometer", action="store_true", help="Include accelerometer")
    parser.add_argument("--quantile-regression", action="store_true", help="Quantile regression")
    args = parser.parse_args()

    input_file = args.file_path
    output_file = args.file_path

    # Read the CSV file
    data = pd.read_csv(input_file)

    # List column names
    print("Column names: ", data.columns)

    # Run correction
    fix_tmi(data)

    # Write the DataFrame to a CSV file
    data.to_csv(output_file, index = False, sep = ',')
    print(f"Fixed data saved to {output_file}")

if __name__ == "__main__":
    main()
