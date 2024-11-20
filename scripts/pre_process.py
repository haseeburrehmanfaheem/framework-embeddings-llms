##############
# IMPORTS
#############

import pandas as pd
from sklearn.model_selection import train_test_split
import re
import sys, getopt
import jsonlines
import random

###################
# Utility Functions
###################

def getWithoutAC(s):
    s = s[1:-1]
    s = s.split(", ")
    withoutAccess = []
    for k in s:
        if "[AC]" not in k:
            withoutAccess.append(k)
    return "<|>".join(withoutAccess)

def extractAC(s):
    s = s[1:-1]
    s = s.split(", ")
    ans = -1
    
    for k in s:
        if "[AC]" in k:
            match = re.search(r'\((\d+)\)', k)
            if match:
                number = int(match.group(1))
                ans = max(ans, number)
    m = {-1: 0, 0: 0, 1: 0, 2: 1, 3: 1}
    return m[ans]

def parse_data_string(dict_str):
    newDict = {}
    dict_str = dict_str[1:-1]
    dict_str = dict_str.split(',')
    for d in dict_str:
        newDict[d.split('=')[0].strip()] = d.split('=')[1].strip()
    return newDict

def getData(fileName):
# Read the file
    with open(fileName, "r") as file:
        lines = file.readlines()

    # Initialize variables
    current_ep = None
    all_entries = []

    # Iterate over the lines in the file
    for line in lines:
        # If the line starts with 'EP:', it's the start of a new EP block
        if line.startswith('EP:'):
            current_ep = line.strip().split('EP: ')[1]
        # If the line starts with 'ControlFlow:', it's the start of a new data block
        elif line.startswith('ControlFlow:'):
            current_block = {}
            current_block['EP'] = current_ep
            current_block['code'] = line.strip().split('ControlFlow: ')[1]
            all_entries.append(current_block)
        # elif line.startswith('Features:'):
        #     newDict = parse_data_string(line.strip().split('Features: ')[1])
        #     for k in newDict.keys():
        #         current_block[k] = newDict[k]
        #     all_entries.append(current_block)

    # Convert the list of blocks to a dataframe
    df = pd.DataFrame(all_entries)
    df["label"] = df["code"].apply(extractAC)
    df["code"] = df["code"].apply(getWithoutAC)
    return df

import numpy as np

def slidingWindow(path):
    window = []
    curPath = []
    for p in path:
        curPath.append(p)
        window.append(list(curPath))
    return window


def decompose(df):
    # Create a helper column for chunk grouping
    df['group'] = np.where(df['label'] == 0, np.arange(len(df)), df.groupby('EP').cumcount() // 4)

    # Group by 'EP', 'label' and 'group', and then merge 'subsequences'
    df_agg = df.groupby(['EP', 'label', 'group']).agg({
        'code': lambda x: '<PATH_SEP>'.join(x)
    })

    # Reset the index to get 'EP' and 'label' as columns
    df_agg.reset_index(inplace=True)

    # Drop the 'group' column
    df_agg.drop(columns=['group'], inplace=True)

    # If you still want to aggregate 'label' by taking the max value
    df_agg['label'] = df_agg.groupby('EP')['label'].transform('max')

    return df_agg



def shuffle_dataframes(df):
    # Concatenate the dataframes
    
    # Separate the labels from the features
    X = df.drop('label', axis=1)
    y = df['label']

    # Split the data into train and remaining data (test + validation)
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)

    # Create train, test, and validation dataframes
    train_df = pd.concat([X_train, y_train], axis=1)
    test_df = pd.concat([X_test, y_test], axis=1)
    
    return train_df, test_df


# Creating the Training Data for the model
def createJsonL(df, fileName):
    fileName = fileName.replace(".txt", "")
    json_objects = []

    # Group the DataFrame by unique values in the 'EP' column
    grouped = df.groupby('EP')

    # Iterate over each group
    for _, group in grouped:
        # Shuffle the group randomly
        shuffled_group = group.sample(frac=1, random_state=42)  # Set a random_state for reproducibility

        # Keep at most 10 rows in the shuffled group
        # shuffled_group = shuffled_group.head(10)

        # Iterate over each row in the shuffled group
        for _, row in shuffled_group.iterrows():
            prompt = []

            # Iterate over each column (excluding 'label')
            for column in shuffled_group.columns:
                if column != 'label':
                    prompt.append(str(column) + ' = ' + str(row[column]))

            # Create the JSON object
            json_object = {
                'code': " ".join(prompt),
                'label': int(row['label'])
            }

            # Append the JSON object to the list
            json_objects.append(json_object)

    with jsonlines.open(fileName, 'w') as writer:
        writer.write_all(json_objects)

# df = getAggregate(df)
def run(argv):
    try:
        opts, args = getopt.getopt(argv, "h", ["file="])
    except getopt.GetoptError:
        sys.exit(2)

    file_name = None

    for opt, arg in opts:
        if opt == '-h':
            print('Example: python pre_process.py --file=<file_path>')
            sys.exit()
        elif opt == "--file":
            file_name = arg
        else:
            print('Example: python pre_process.py --file=<file_path>')
            sys.exit()
    
    if not file_name:
        print('Example Usage: python pre_process.py --file=<file_path>')
        sys.exit()
    
    try:
        df = getData(file_name)
    except:
        print(f"Could not read {file_name}")
    
    label_counts = df['label'].value_counts()

    print("################\n1 - Protection Required\n0 - No Protection\n################\n")

    print("LabelCounts:\n", label_counts)
    merged_aaAdf = decompose(df)
    trainDF, testDF = shuffle_dataframes(merged_df)

    label_counts = merged_df['label'].value_counts()
    print("LabelCounts:\n", label_counts)

    print(merged_df.head())

    createJsonL(trainDF, f'{file_name}_train.jsonl')
    createJsonL(testDF, f'{file_name}_test.jsonl')


if __name__ == "__main__":
    run(sys.argv[1:])
