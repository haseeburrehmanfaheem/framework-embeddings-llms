import pandas as pd
from sklearn.model_selection import train_test_split
import re
import sys, getopt
import jsonlines
import random
import numpy as np
import pickle
from tqdm import tqdm
import pandas as pd
import re
import numpy as np
import os
import time
from openai import OpenAI
import pandas as pd
import jsonlines
import numpy as np
from tqdm import tqdm
import json
import tiktoken
import ollama
encoding = tiktoken.encoding_for_model("gpt-4")
import os
import sys
import logging
from tqdm import tqdm

sys_prompt = ""
# CHAGNE PROMPT HEREEEEEEE
with open('./prompts/prompt1-10.txt', 'r') as file:
    sys_prompt = file.read()

# CHANGE MODEL HEREREERER
modelfile=f'''
FROM llama3.3 
system """
{sys_prompt.strip()}
"""
'''
model = 'myexample1'
num_ctx = 25000 # 15000 original,  - works with 17000 with old GPUs 
ollama.create(model='myexample1', modelfile=modelfile)




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

# trainDF.iloc[0]['code']
def generate_java_method(name, code):
    # Extract the API name and method name dynamically
    api_name, method_name = name.split("_", 1)

    # Start the Java code formatting
    java_code = []
    java_code.append(f"// API: {api_name}")
    java_code.append(f"// Service: {method_name}\n")
    java_code.append(f"public void {api_name}() {{")

    # Split and clean up the code
    lines = code.split("<|>")
    for line in lines:
        # Remove [..]: and clean up the lines
        cleaned_line = re.sub(r"\[.*?\]: ", "", line).strip()
        if cleaned_line:
            # Ensure only one semicolon is added
            if not cleaned_line.endswith(";"):
                cleaned_line += ";"
            java_code.append(f"    {cleaned_line}")

    java_code.append("}")
    return "\n".join(java_code)



def run_first_prompt_Ollama(method_code,run):
    """ runs the first prompt - extract sinks from the traces
    """
    
    global sys_prompt

    # user_prompt = get_method_traces_from_file(file_path, interface, method)
    
    user_prompt = method_code
    if(not run):
        return {
            "system_message": sys_prompt,
            "user_message": user_prompt,
            "response": "Not running"
        }
        
        
    
    response = ollama.chat(model=model, messages=[
    {
        'role': 'user',
        'content': user_prompt,
    },
    ]
    ,
     options={
        'num_ctx': num_ctx,
        # 'temperature': 0.3 # CHECK TEMPERATURE HERERERERERERERER
    }
    
    )
    
    # logging.info(f"Response for {method} = {response['message']['content']}")

    
    return {
        "system_message": sys_prompt,
        "user_message": user_prompt,
        "response": response['message']['content']
    }
    

    

# get the json from the res["response"]

def extract_json_from_string(input_string):
    """
    Extracts JSON from the given string.

    Args:
        input_string (str): The string containing embedded JSON.

    Returns:
        dict: The extracted JSON as a Python dictionary.
    """
    try:
        # Use a regex pattern to extract the JSON part
        json_pattern = r"```(?:json)?\n(.*?)\n```"
        match = re.search(json_pattern, input_string, re.DOTALL)
        if match:
            json_string = match.group(1)
            return json.loads(json_string)
        else:
            raise ValueError("No JSON found in the provided string.")
    except json.JSONDecodeError as e:
        raise ValueError(f"Error decoding JSON: {e}")




def main():
    file_name = "data/aosp.txt"
    try:
        df = getData(file_name)
    except:
        print(f"Could not read {file_name}")

    label_counts = df['label'].value_counts()

    print("################\n1 - Protection Required\n0 - No Protection\n################\n")

    print("LabelCounts:\n", label_counts)
    merged_aaAdf = decompose(df)
    merged_df = merged_aaAdf
    trainDF = merged_df
    
# s = generate_java_method(trainDF.iloc[0]['EP'],trainDF.iloc[0]['code'])
    trainDF['java_code'] = trainDF.apply(lambda row: generate_java_method(row['EP'], row['code']), axis=1)
    ## DROP THE DUPLICATES USING LENGTH
    trainDF['code_length'] = trainDF['java_code'].map(len)
    trainDF = (
        trainDF.sort_values(by='code_length', ascending=False)  
        .drop_duplicates(subset=trainDF.columns[0])             
        .reset_index(drop=True)                                 
    )
    trainDF = trainDF.drop(columns=['code_length'])
    trainDF['service_name'] = trainDF['EP'].apply(lambda word: word.split("_")[1])
    AMS_Df = trainDF
    AMS_Df['json_answer'] = None
    print(AMS_Df.shape)
    

    


    output_folder = "./output/week2/output8"
    counter = 0
    for index, row in tqdm(AMS_Df.iterrows(), total=AMS_Df.shape[0], desc="Processing rows"):
        # print(row['EP'])
        # print(row['service_name'])
        res = run_first_prompt_Ollama(row['java_code'],True)
        json_answer = None
        try:
            json_answer = extract_json_from_string(res["response"])
        except Exception as e:
            print(f"Error extracting JSON from response: {e}")
            print(row['EP'])
            print(row['service_name'])
            # continue
            # Store the json_answer in the new column
        AMS_Df.at[index, 'json_answer'] = json_answer

        # Write response details to files
        folder_path = f'{output_folder}/{row["EP"].split("_")[0]}'
        os.makedirs(folder_path, exist_ok=True)

        with open(f'{folder_path}/system_message.txt', 'w') as f:
            f.write(res["system_message"])
        with open(f'{folder_path}/user_message.txt', 'w') as f:
            f.write(res["user_message"])
        with open(f'{folder_path}/response.txt', 'w') as f:
            f.write(res["response"])

    pickle_file_path = "./serialized_data/week2/AMS_Df_8.pkl"

    # Serialize the DataFrame using pickle
    with open(pickle_file_path, 'wb') as file:
        pickle.dump(AMS_Df, file)   

    print(f"DataFrame serialized and saved to {pickle_file_path}")
    return 



main()


