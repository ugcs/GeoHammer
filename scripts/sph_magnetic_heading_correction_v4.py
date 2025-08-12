import argparse
import pandas as pd
import statsmodels.api as sm
from scipy.fft import fft, ifft
import numpy as np
from sklearn.preprocessing import PolynomialFeatures
from scipy.ndimage import gaussian_filter1d


def main():
    parser = argparse.ArgumentParser(description="Corrects and filters magnetic and IMU survey data, applies regression models, and exports the processed results to a CSV file.")
    parser.add_argument("file_path", help='File path')
    parser.add_argument("--start_latitude", type=float, help="Start point latitude", default=None)
    parser.add_argument("--start_longitude", type=float, help="Start point longitude", default=None)
    parser.add_argument("--end_latitude", type=float, help="End point latitude", default=None)
    parser.add_argument("--end_longitude", type=float, help="End point longitude", default=None)
    args = parser.parse_args()

    input_file = args.file_path
    output_file = args.file_path

    # Read the CSV file
    data_cleaned = pd.read_csv(input_file)

    # Set default start/end points if not provided
    default_start = [data_cleaned['Longitude'].iloc[0], data_cleaned['Latitude'].iloc[0]]
    default_end = [data_cleaned['Longitude'].iloc[-1], data_cleaned['Latitude'].iloc[-1]]

    start_point = [
         args.start_longitude if args.start_longitude is not None else default_start[0],
         args.start_latitude if args.start_latitude is not None else default_start[1]
    ]
    end_point = [
         args.end_longitude if args.end_longitude is not None else default_end[0],
         args.end_latitude if args.end_latitude is not None else default_end[1]
    ]

    # List column names
    print("Column names:", data_cleaned.columns)


    ### Remove any bad data points From mag data, Interpolate vector data to match frequency of scalar data

    data_cleaned['Time Stamp [ms]'] = data_cleaned['Counter']

    # Shift up the X, Y, Z, and 'Good V-Data' data by one row
    data_cleaned['Bx'] = data_cleaned['Bx'].shift(-1)
    data_cleaned['By'] = data_cleaned['By'].shift(-1)
    data_cleaned['Bz'] = data_cleaned['Bz'].shift(-1)

    # For 'Bx(nT)' column, set consecutive duplicate values to NaN
    data_cleaned['Bx'] = data_cleaned['Bx'].mask(data_cleaned['Bx'].shift() == data_cleaned['Bx'])

    # For 'BBy(nT)' column, set consecutive duplicate values to NaN
    data_cleaned['By'] = data_cleaned['By'].mask(data_cleaned['By'].shift() == data_cleaned['By'])

    # For 'Bz(nT)' column, set consecutive duplicate values to NaN
    data_cleaned['Bz'] = data_cleaned['Bz'].mask(data_cleaned['Bz'].shift() == data_cleaned['Bz'])

    # First, replace specific bad values with NaN
    #data_cleaned.loc[data_cleaned['Good S-Data'] != 1, ['Mag Data [nT]']] = np.nan

    # Remove rows based on sensitivity condition adjust if needed
    data_cleaned.loc[data_cleaned['ScSensitivity'] <= 25, ['TMI']] = np.nan
    data_cleaned.loc[data_cleaned['ScSensitivity'] <= 25, ['ScSensitivity']] = np.nan

    # Interpolate missing values in 'Bx', 'By', and 'Z' columns
    data_cleaned['Bx'] = data_cleaned['Bx'].interpolate()
    data_cleaned['By'] = data_cleaned['By'].interpolate()
    data_cleaned['Bz'] = data_cleaned['Bz'].interpolate()
    data_cleaned['Mag Data [nT]'] = data_cleaned['TMI'].interpolate()
    data_cleaned['ScSensitivity'] = data_cleaned['ScSensitivity'].interpolate()

    # Optional: Remove any rows that still contain NaN if necessary
    data_cleaned = data_cleaned.dropna(subset=['Bx', 'By', 'Bz', 'TMI'])


    ####Clean Interpolate IMU Data


    # For 'AX' column, set consecutive duplicate values to NaN
    data_cleaned['AccelX'] = data_cleaned['AccelX'].mask(data_cleaned['AccelX'].shift() == data_cleaned['AccelX'])

    # For 'AY)' column, set consecutive duplicate values to NaN
    data_cleaned['AccelY'] = data_cleaned['AccelY'].mask(data_cleaned['AccelY'].shift() == data_cleaned['AccelY'])

    # For 'AZ)' column, set consecutive duplicate values to NaN
    data_cleaned['AccelZ'] = data_cleaned['AccelZ'].mask(data_cleaned['AccelZ'].shift() == data_cleaned['AccelZ'])


    # Interpolate missing values in 'X', 'Y', and 'Z' columns
    data_cleaned['AccelX'] = data_cleaned['AccelX'].shift(-1).interpolate()
    data_cleaned['AccelY'] = data_cleaned['AccelY'].shift(-1).interpolate()
    data_cleaned['AccelZ'] = data_cleaned['AccelZ'].shift(-1).interpolate()

    ## Interpolate Altitude , Altitude AGL, Heading, lat,long,alt,SPH  (New V4)###
    # For Altitude , Altitude AGL, Heading, Lat, Long column, set consecutive duplicate values to NaN
    data_cleaned['Altitude'] = data_cleaned['Altitude'].mask(data_cleaned['Altitude'].shift() == data_cleaned['Altitude'])
    data_cleaned['Altitude AGL'] = data_cleaned['Altitude AGL'].mask(data_cleaned['Altitude AGL'].shift() == data_cleaned['Altitude AGL'])
    data_cleaned['Latitude'] = data_cleaned['Latitude'].mask(data_cleaned['Latitude'].shift() == data_cleaned['Latitude'])
    data_cleaned['Longitude'] = data_cleaned['Longitude'].mask(data_cleaned['Longitude'].shift() == data_cleaned['Longitude'])
    data_cleaned['Heading'] = data_cleaned['Heading'].mask(data_cleaned['Heading'].shift() == data_cleaned['Heading'])

    # Interpolate missing values
    data_cleaned['Altitude'] = data_cleaned['Altitude'].interpolate()
    data_cleaned['Altitude AGL'] = data_cleaned['Altitude AGL'].interpolate()
    data_cleaned['Latitude'] = data_cleaned['Latitude'].interpolate()
    data_cleaned['Longitude'] = data_cleaned['Longitude'].interpolate()
    data_cleaned['Heading'] = data_cleaned['Heading'].interpolate()

    data_cleaned = data_cleaned.dropna(subset=['Altitude', 'Altitude AGL', 'Latitude', 'Longitude','Heading'])

    ####Apply Low pass Filter to vector data

    def notch_zero_filter(data, notchh, notchl, freq): # works as highpass lowpass and notch filter
        freqh = notchh*(len(data))//(freq)
        freql = notchl*(len(data))//(freq)
        yf = fft(data.values)
        yff = np.zeros(len(yf))
        yff[0:len(yf)] = 1
        yff[freql:freqh] = 0
        yff[len(yf)-(freqh):len(yf)-(freql)] = 0
        yf = yf*yff
        dataf = ifft(yf)
        return np.real(dataf)


    fs = 250  # frequency of data in Hz (rounded up from 83.33 to 84hz
    N = len(data_cleaned['Time Stamp [ms]'])
    t = 1/fs

    notch_high = fs//2
    notch_low = 4

    # Apply the lowpass filter to each vector column
    data_cleaned['Bx_F'] = notch_zero_filter(data_cleaned['Bx'],  notch_high, notch_low, fs)
    data_cleaned['By_F'] = notch_zero_filter(data_cleaned['By'],  notch_high, notch_low, fs)
    data_cleaned['Bz_F'] = notch_zero_filter(data_cleaned['Bz'],  notch_high, notch_low, fs)

    notch_high = fs//2
    notch_low =4

    data_cleaned['TMI_F'] = notch_zero_filter(data_cleaned['TMI'],  notch_high, notch_low, fs)

    ##normalize and filter Accel/GPS data
    notch_high = fs//2
    notch_low =1

    data_cleaned['ScSensitivity_F'] = notch_zero_filter(data_cleaned['ScSensitivity'],  notch_high, notch_low, fs)

    # Apply the notch zero filter to each column
    data_cleaned['AccelX_F'] = notch_zero_filter(data_cleaned['AccelX'],  notch_high, notch_low, fs)
    data_cleaned['AccelY_F'] = notch_zero_filter(data_cleaned['AccelY'],  notch_high, notch_low, fs)
    data_cleaned['AccelZ_F'] = notch_zero_filter(data_cleaned['AccelZ'],  notch_high, notch_low, fs)




    Acc_mag_F = np.sqrt(data_cleaned['AccelX_F']**2 + data_cleaned['AccelY_F']**2 + data_cleaned['AccelZ_F']**2)


    data_cleaned.loc[:, 'normalized_Ax_F'] = data_cleaned['AccelX_F']/Acc_mag_F
    data_cleaned.loc[:, 'normalized_Ay_F'] = data_cleaned['AccelY_F']/Acc_mag_F
    data_cleaned.loc[:, 'normalized_Az_F'] = data_cleaned['AccelZ_F']/Acc_mag_F

    notch_high = fs//2
    notch_low =10

    #data_cleaned['Altitude AGL_F'] = notch_zero_filter(data_cleaned['Altitude AGL'],  notch_high, notch_low, fs)
    data_cleaned['Altitude AGL_F'] = notch_zero_filter(data_cleaned['Altitude AGL'],  notch_high, notch_low, fs)
    data_cleaned['Altitude_F'] = notch_zero_filter(data_cleaned['Altitude'],  notch_high, notch_low, fs)
    data_cleaned['Latitude_F'] = notch_zero_filter(data_cleaned['Latitude'],  notch_high, notch_low, fs)
    data_cleaned['Longitude_F'] = notch_zero_filter(data_cleaned['Longitude'],  notch_high, notch_low, fs)
    data_cleaned['Heading_F'] = notch_zero_filter(data_cleaned['Heading'],  notch_high, notch_low, fs)



    ###### normalize vector data (Filtered)
    #
    # Calculate the synthetic scalar value
    synthetic_scalar_value_F = np.sqrt(data_cleaned['Bx_F']**2 + data_cleaned['By_F']**2 + data_cleaned['Bz_F']**2)


    # Normalize Bx, By, and Bz
    normalized_Bx_F = data_cleaned['Bx_F'] / synthetic_scalar_value_F
    normalized_By_F = data_cleaned['By_F'] / synthetic_scalar_value_F
    normalized_Bz_F = data_cleaned['Bz_F'] / synthetic_scalar_value_F

    data_cleaned.loc[:, 'normalized_Bx_F'] = normalized_Bx_F
    data_cleaned.loc[:, 'normalized_By_F'] = normalized_By_F
    data_cleaned.loc[:, 'normalized_Bz_F'] = normalized_Bz_F




    # Projected values
    projected_X_F = normalized_Bx_F * data_cleaned['TMI_F']
    projected_Y_F = normalized_By_F * data_cleaned['TMI_F']
    projected_Z_F = normalized_Bz_F * data_cleaned['TMI_F']

    data_cleaned.loc[:, 'projected_X_F'] = projected_X_F
    data_cleaned.loc[:, 'projected_Y_F'] = projected_Y_F
    data_cleaned.loc[:, 'projected_Z_F'] = projected_Z_F

    data_cleaned.loc[:, 'synthetic_scalar_value'] = synthetic_scalar_value_F

    data_cleaned = data_cleaned.dropna(subset=['projected_Y_F', 'synthetic_scalar_value'])



    ## Interactive selection of start and end points (Cut data set to include survey lines only)
    print("Select the start and end points on the plot (left-click on the start point, right-click on the end point).")

    ###Stop

    # Find the closest points in the dataset
    def find_nearest_point(df, point):
        distances = np.sqrt((df['Longitude'] - point[0])**2 + (df['Latitude'] - point[1])**2)
        return distances.idxmin()

    start_idx = find_nearest_point(data_cleaned, start_point)
    end_idx = find_nearest_point(data_cleaned, end_point)

    # Slice the data_cleaned DataFrame
    selected_data = data_cleaned.loc[start_idx:end_idx].reset_index(drop=True)

    ####Stop check if path is correct, If so move to next section, if no rerun Interactive selection are reselect points
    # Remove rows not within the selected start and end points
    data_cleaned = data_cleaned.loc[start_idx:end_idx].reset_index(drop=True)

    ##Determine the channel with the largest median value
    medians = {
        'normalized_Bx_F': np.median(np.abs(data_cleaned['normalized_Bx_F'])),
        'normalized_By_F': np.median(np.abs(data_cleaned['normalized_By_F'])),
        'normalized_Bz_F': np.median(np.abs(data_cleaned['normalized_Bz_F']))
    }
    largest_median_channel = max(medians, key=medians.get)
    largest_median_value = medians[largest_median_channel]*100

    print(f"The channel with the largest median value is {largest_median_channel} with a median of {largest_median_value:.1f}%. Do not include this channel in the corrections")


    ###Recommandation, do not use the component with the largest magnetude in model.

    # Specify which components to include
    # Example: first_order_components = ['normalized_Bx_F', 'normalized_By_F']

    components_to_include = ['normalized_Bx_F', 'normalized_Bz_F']
    # Adjust this line to include the desired components


    #### Target large mag signals and remove them from regression model

    # Specify which components to include


    # Calculate lower and upper percentiles for TMI
    lower_percentile = np.percentile(data_cleaned['TMI'].dropna(), 10)  # May need to be adjusted
    upper_percentile = np.percentile(data_cleaned['TMI'].dropna(), 90)  # May need to be adjusted

    # Exclude high and low values based on percentiles
    valid_points = data_cleaned[(data_cleaned['TMI'] > lower_percentile) & (data_cleaned['TMI'] < upper_percentile)].index

    # Extend the valid points to include 150 previous and 150 next data lines around each valid point
    window_size = 1
    extended_indices = set()
    for index in valid_points:
        extended_indices.update(range(max(0, index - window_size), min(len(data_cleaned), index + window_size + 1)))

    # Convert to sorted list to maintain order
    extended_indices = sorted(extended_indices)
    extended_changes = data_cleaned.loc[extended_indices]

    # Convert to sorted list to maintain order
    extended_indices = sorted(extended_indices)
    extended_changes = data_cleaned.loc[extended_indices]
    dataset_percent = len(extended_changes)/len(data_cleaned['TMI'])*100
    print(f"Percent of dataset used for model: {dataset_percent:.1f}%")  ## target goal is >50%

    # Define signal indices as points not in extended_indices
    signal_indices = set(data_cleaned.index) - set(extended_indices)
    signal_indices = sorted(signal_indices)


    ##New V4 Section added Start#####################################################################################New Section added Start

    ##Select Higher Order terms to Include

    # Define which components should include both first-order and higher-order terms
    #Options:Altitude_F', 'Altitude AGL_F','Latitude_F', 'Longitude_F','normalized_Ax_F' ,'normalized_Ay_F','normalized_Az_F'
    higher_order_components = ['Altitude_F', 'Latitude_F', 'Longitude_F']  # Both first and higher order terms

    first_order_components = components_to_include
    # You want to include first-order terms for higher-order components as well
    first_order_components += higher_order_components


    # Select the features and target for the regression model
    X_end = extended_changes[components_to_include]
    y_end = extended_changes['TMI']

    # Handle missing values by dropping rows with any NaN values
    X_end = X_end.dropna()
    y_end = y_end.loc[X_end.index]


    X_end = sm.add_constant(X_end)
    model = sm.OLS(y_end, X_end, missing='drop').fit()

    #Apply the model to the entire dataset
    X_full = data_cleaned[components_to_include]
    X_full = sm.add_constant(X_full)  # Add a constant (intercept) to the model

    #Predict the magnetic data based on the full dataset
    y_pred = model.predict(X_full)

    #Calculate the residuals for the entire dataset
    residuals = data_cleaned['TMI'] - y_pred

    #Re-center the residuals by adding the mean of the original magnetic data
    mean_mag_data = data_cleaned['TMI'].mean()
    adjusted_residuals = residuals + mean_mag_data


    # Select the features and target for the regression model
    X_end_high_order = extended_changes[higher_order_components]
    X_end_first_order = extended_changes[first_order_components]
    y_end = extended_changes['TMI']

    # Handle missing values by dropping rows with any NaN values
    X_end_high_order = X_end_high_order.dropna()
    X_end_first_order = X_end_first_order.loc[X_end_high_order.index]
    y_end = y_end.loc[X_end_high_order.index]

    # Generate polynomial features for the higher-order terms
    poly_degree = 2  # Set the degree for higher-order terms
    poly = PolynomialFeatures(degree=poly_degree, include_bias=False)
    X_end_high_order_poly = poly.fit_transform(X_end_high_order)

    # Concatenate the first-order components and higher-order components
    X_end_combined = np.hstack([X_end_first_order.values, X_end_high_order_poly])

    # Add a constant (intercept) to the model
    X_end_combined = sm.add_constant(X_end_combined)

    # Fit the model with mixed higher-order and first-order terms
    model_combined = sm.OLS(y_end, X_end_combined, missing='drop').fit()

    # Apply the model to the entire dataset
    X_full_high_order = data_cleaned[higher_order_components]
    X_full_first_order = data_cleaned[first_order_components]

    # Transform the higher-order components
    X_full_high_order_poly = poly.transform(X_full_high_order)

    # Concatenate the first-order and higher-order components for the full dataset
    X_full_combined = np.hstack([X_full_first_order.values, X_full_high_order_poly])

    # Add a constant (intercept) to the combined model
    X_full_combined = sm.add_constant(X_full_combined)

    # Predict the magnetic data based on the full dataset using the mixed model
    y_pred_combined = model_combined.predict(X_full_combined)

    # Calculate the residuals for the entire dataset
    residuals_combined = data_cleaned['TMI'] - y_pred_combined

    # Re-center the residuals by adding the mean of the original magnetic data
    adjusted_residuals_combined = residuals_combined + mean_mag_data

    # Smooth the residuals using Gaussian filter (or any other smoother)
    adjusted_residuals_F_combined = gaussian_filter1d(adjusted_residuals_combined, sigma=15)

    # Apply filters to the original and adjusted residuals
    data_cleaned['TMI_F'] = notch_zero_filter(data_cleaned['TMI'], notch_high, notch_low, fs)
    #adjusted_residuals_F_combined = notch_zero_filter(adjusted_residuals_combined, notch_high, notch_low, fs)


    ##### Export {data_cleaned} corrected data to File (Pick Column name for corrected data)
    data_cleaned.loc[:, 'TMI_Higher_Order_Correction_Applied_Accel'] = adjusted_residuals_combined
    data_cleaned.loc[:, 'TMI_Higher_Order_Correction_Applied_Accel_Filtered'] = adjusted_residuals_F_combined


    ##New Section V4 ####################################################################################New Section End


    print("Column names:", data_cleaned.columns)

    ## Remove unwanted columns (Optinal)
    # Replace 'unwanted_column1', 'unwanted_column2' with the actual column names you want to remove

    columns_to_remove = [ 'Time Stamp [ms]', 'Mag Data [nT]', 'Bx_F', 'By_F', 'Bz_F',
        'TMI_F', 'ScSensitivity_F', 'AccelX_F', 'AccelY_F', 'AccelZ_F',
        'normalized_Bx_F', 'normalized_By_F', 'normalized_Bz_F',
        'projected_X_F', 'projected_Y_F', 'projected_Z_F',
        'synthetic_scalar_value',
        'Altitude_F', 'Latitude_F',
        'Altitude AGL_F', 'Longitude_F', 'Heading_F', 'normalized_Ax_F', 'normalized_Ay_F','normalized_Az_F']
    data_cleaned = data_cleaned.drop(columns=columns_to_remove)

    # Verify the remaining columns
    print("Remaining columns:", data_cleaned.columns)

    # Write the DataFrame to a CSV file
    data_cleaned.to_csv( output_file, index=False, sep=',')
    print(f"data_cleaned exported to {output_file}")

if __name__ == "__main__":
    main()
