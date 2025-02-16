# python ./postprocess.py --input-dir ./output/week1/output1 --prompt ../prompts/prompt2.txt --model llama3.2 --num-ctx 25000
import numpy as np
from tqdm import tqdm
from transformers import AutoModel, AutoTokenizer
from torch.nn.functional import cosine_similarity
import torch.nn.functional as F
import torch
import csv
import pandas as pd
import re
import numpy as np
from tqdm import tqdm
import pandas as pd
import re
import os
import pandas as pd
import jsonlines
import numpy as np
from tqdm import tqdm
import json
import tiktoken
import ollama
encoding = tiktoken.encoding_for_model("gpt-4")
import os
from tqdm import tqdm
import csv 
import argparse
import os
counter = 0

checkpoint="Salesforce/codet5p-110m-embedding"
tokenizer = AutoTokenizer.from_pretrained(checkpoint, trust_remote_code=True)
model = AutoModel.from_pretrained(checkpoint, trust_remote_code=True).to("cuda")

def get_java_code(row):
# get the maximum depth
    max_depth = max([x['depth'] for x in row["depths"]])
    # get all the java code for the max_depth
    code_string = ""
    path_id = 1
    for code in row["depths"]:
        if code['depth'] == max_depth:
            code_string += f"This is path {path_id} for the API with depth {max_depth}:\n"
            code_string += code['java_code']
            path_id += 1

    return code_string

def extract_json_from_string(input_string):
    """Extract JSON from response string."""
    try:
        json_pattern = r"```(?:json)?\n(.*?)\n```"
        match = re.search(json_pattern, input_string, re.DOTALL)
        return json.loads(match.group(1)) if match else {}
    except json.JSONDecodeError:
        return {}

def get_code_embedding(code_snippet):
    """
    Generates embeddings for a given code snippet using a pre-trained model.

    Parameters:
    - code_snippet (str): The code for which embeddings are to be generated.
    - checkpoint (str): The model checkpoint to be used for embedding. Default is Salesforce/codet5p-110m-embedding.
    - device (str): Device to run the model on, either 'cuda' for GPU or 'cpu' for CPU. Default is 'cuda'.

    Returns:
    - torch.Tensor: Embedding tensor for the input code.
    """
    inputs = tokenizer.encode(code_snippet, return_tensors="pt").to("cuda")
    with torch.no_grad():
        embedding = model(inputs)[0]
    
    return embedding.cpu()

def process_json_answer(json_answer, n=2):
    global counter
    all = []
    try:
        arrays = json_answer['Sinks']
        for i, array in enumerate(arrays, 1):
            if i > n:  # Limit the number of joins to `n`
                break
            joined = '\n'.join(array)
            all.append(joined)
    except:    
        return []
    counter += 1
    return all

def calculate_embeddings(df):
    df["embeddings"] = None
    for index, row in tqdm(df.iterrows(), total=len(df), desc="Processing Rows"):
        code_snippets = row["sink_code"]
        embeddings = []
        for each in code_snippets:
            code_embedding = get_code_embedding(each)
            embeddings.append(code_embedding)
        df.at[index, "embeddings"] = embeddings

def compute_most_similar(df):
    """
    Computes the most similar method for each method in the DataFrame based on cosine similarity of embeddings.

    Parameters:
    - df (pd.DataFrame): DataFrame containing 'method', 'embeddings', and 'sink_code'.

    Returns:
    - dict: A dictionary where each method maps to its most similar method, similarity score, and matching code pair.
    """
    similarities = {}

    for index1, row1 in tqdm(df.iterrows(), total=len(df), desc="Computing Similarities"):
        ep1_id = row1["method"]  
        ep1_embeddings = row1["embeddings"]  # List of embeddings
        ep1_sink_code = row1["sink_code"]  # Corresponding code snippets
        
        closest_ep = None
        max_similarity = -1
        similar_sink_pair = None
        
        for index2, row2 in df.iterrows():
            if index1 == index2:
                continue  
            
            ep2_id = row2["method"]
            ep2_embeddings = row2["embeddings"]
            ep2_sink_code = row2["sink_code"]
            
            # Compute pairwise similarities
            for i, emb1 in enumerate(ep1_embeddings):
                for j, emb2 in enumerate(ep2_embeddings):
                    sim = torch.nn.functional.cosine_similarity(torch.tensor(emb1), torch.tensor(emb2), dim=0).item()
                    if sim > max_similarity:
                        max_similarity = sim
                        closest_ep = ep2_id
                        similar_sink_pair = (ep1_sink_code[i], ep2_sink_code[j])  
        
        similarities[ep1_id] = {
            "closest_ep": closest_ep,
            "max_similarity": max_similarity,
            "similar_sink_pair": similar_sink_pair
        }
    
    return similarities

def get_top_similar_methods(similarities, top_n=2, threshold=0.8):
    filtered = [entry for entry in similarities if entry['similarity'] > threshold]
    sorted_results = sorted(filtered, key=lambda x: x['similarity'], reverse=True)
    top_results = sorted_results[:top_n]
    extracted_results = [
        {
            'ep2_code': entry['ep2_code'],
            'ep2_id': entry['ep2_id'],
            'ep1_code': entry['ep1_code'],
            'similarity': entry['similarity']
        }
        for entry in top_results
    ]
    
    return extracted_results

def create_prompt2_string(top_similar, df, original_method, original_code, sink_code):
    if not top_similar:
        return "No similar APIs found"  # Return an empty string if there are no similar methods
    
    prompt = f"The method {original_method} has the following code snippet:\n\n"
    prompt += f"{original_code}\n" 
    prompt += f"and the following sink code:\n"
    prompt += f"{sink_code}\n\n"
    
    
    prompt += f"The method {original_method} has the following similar APIs:\n\n"
    for entry in top_similar:
        ep2_id = entry["ep2_id"]
        ep2_code = entry["ep2_code"]

        # Find access control level for ep2_id
        access_control = df[df["method"] == ep2_id]["access control level"].values
        access_control_str = access_control[0] if len(access_control) > 0 else "Unknown"

        prompt += f"- API Name: {ep2_id} with"
        prompt += f" Similarity Score: {entry.get('similarity', 'N/A')}\n"
        prompt += f"  - Access Control Level: {access_control_str} and the following code:\n"
        prompt += f"{ep2_code}\n\n"

    # Add final instructions for assigning access control
    # prompt += (
    #     "Your task is to assign Access Control to a new android mehtod listed below. I will provide you with the original APIs code and the other similar APIs with their ground truth Access Control."
    #     "Given this information, your task is to assign an access control to the "
    #     f"{original_method} API. After the explanation, give the final access control "
    #     "level for the API in JSON format as follows:\n"
    #     "{ \"access_control_level\": \"...\" }.\n\n"
    #     "You have 4 choices: NONE, NORMAL, DANGEROUS, SYS_OR_SIG. To make an informed decision, "
    #     "carefully review other APIs (ground truth) that I have provided above that interact "
    #     "with the same sinks, their assigned access control levels, and the semantics of those APIs. "
    # )

    return prompt
    
def run_second_prompt_Ollama(method_code, model_prompt2,run, sys_prompt2,num_ctx):
    """ runs the second prompt - extract sinks from the traces
    """

    user_prompt = method_code

    response = ollama.chat(model=model_prompt2, messages=[
    {
        'role': 'user',
        'content': user_prompt,
    },
    ]
    ,
     options={
        'num_ctx': num_ctx,
        # 'temperature': 0.3 # Todo : CHECK TEMPERATURE HERERERERERERERER
    }
    
    )
    
    return {
        "system_message": sys_prompt2,
        "user_message": user_prompt,
        "response": response['message']['content']
    }

def compute_80_top2(df):
    similarities = {}

    for index1, row1 in tqdm(df.iterrows(), total=len(df), desc="Computing Similarities"):
        ep1_id = row1["method"] # name of the EP1
        ep1_embeddings = row1["embeddings"] # list of embeddings for the EP1
        ep1_sink_code = row1["sink_code"] # list of code snippets for the EP1
        
        similar_sink_pairs = []  # List to store all similar sink code pairs with similarity > 0.8
        
        for index2, row2 in df.iterrows():
            if index1 == index2:
                continue  
            
            ep2_id = row2["method"] # name of the EP2
            ep2_embeddings = row2["embeddings"] # list of embeddings for the EP2
            ep2_sink_code = row2["sink_code"] # list of code snippets for the EP2  
            
            # Compute similarities
            for i, emb1 in enumerate(ep1_embeddings):
                for j, emb2 in enumerate(ep2_embeddings):
                    sim = torch.nn.functional.cosine_similarity(torch.tensor(emb1), torch.tensor(emb2), dim=0).item()
                    if sim > 0.8:  # Only consider pairs with similarity > 0.8
                        similar_sink_pairs.append({
                            "similarity": sim,
                            "ep1_code": ep1_sink_code[i],
                            "ep2_id": ep2_id,
                            "ep2_code": ep2_sink_code[j]
                        })
        
        # Store all similar pairs for the current EP
        similarities[ep1_id] = similar_sink_pairs
    return similarities

def write_csvs(similarities, CSV_FILE):
    first_path = os.path.join(CSV_FILE, "_allcode.csv")
    with open(first_path, mode='w', newline='', encoding='utf-8') as file:
        writer = csv.writer(file)

        # Write header
        writer.writerow(["EP1_ID", "EP2_ID", "EP1_Code", "EP2_Code", "Similarity"])

        # Iterate through the data and write each entry
        for ep, similar_pairs in similarities.items():
            if similar_pairs:
                for pair in similar_pairs:
                    writer.writerow([
                        ep,
                        pair['ep2_id'],
                        pair['ep1_code'].replace("\n", " "),  # Replace newlines for better CSV readability
                        pair['ep2_code'].replace("\n", " "),  # Replace newlines for better CSV readability
                        pair['similarity']
                    ])
            else:
                writer.writerow([ep, "No similar EPs with similarity > 0.8", "", "", ""])

    # Writing the data to a CSV file
    second_path = os.path.join(CSV_FILE, "_score.csv")
    with open(second_path, mode='w', newline='', encoding='utf-8') as file:
        writer = csv.writer(file)
        # Write the header
        writer.writerow(["EP", "EP2_ID", "Max_Similarity"])
        
        for ep, similar_pairs in similarities.items():
            if not similar_pairs:
                writer.writerow([ep, "No similar EPs", ""])
            else:
                max_similarity_per_ep2 = {}
                for pair in similar_pairs:
                    ep2_id = pair['ep2_id']
                    similarity = pair['similarity']
                    if ep2_id not in max_similarity_per_ep2:
                        max_similarity_per_ep2[ep2_id] = similarity
                    else:
                        max_similarity_per_ep2[ep2_id] = max(max_similarity_per_ep2[ep2_id], similarity)
                
                # Sorting the EP2 IDs by similarity in descending order
                sorted_ep2_ids = sorted(max_similarity_per_ep2.items(), key=lambda x: x[1], reverse=True)
                
                # Writing sorted EP2 IDs and their max similarity
                for ep2_id, max_similarity in sorted_ep2_ids:
                    writer.writerow([ep, ep2_id, f"{max_similarity:.4f}"])



    third_path = os.path.join(CSV_FILE, "_top2code" + ".csv")
    with open(third_path, mode='w', newline='', encoding='utf-8') as file:
        writer = csv.writer(file)

        # Write header
        writer.writerow(["EP1_ID", "EP2_ID", "EP1_Code", "EP2_Code", "Similarity"])

        # Iterate through the data and write only the top 2 most similar pairs
        for ep, similar_pairs in similarities.items():
            
            if similar_pairs:
                # Sort the pairs by similarity in descending order
                top_pairs = sorted(similar_pairs, key=lambda x: x["similarity"], reverse=True)[:2]
                for pair in top_pairs:
                    writer.writerow([
                        ep,
                        pair['ep2_id'],
                        pair['ep1_code'].replace("\n", " "),  # Replace newlines for better CSV readability
                        pair['ep2_code'].replace("\n", " "),  # Replace newlines for better CSV readability
                        pair['similarity']
                    ])
            else:
                writer.writerow([ep, "No similar EPs with similarity > 0.8", "", "", ""])

    print(f"Data has been written to {CSV_FILE}")

def process_dataframe2(df, similarities, output_folder_preprocess, model_prompt2, sys_prompt2, num_ctx):
    df["json_answer2"] = None
    df["access control level predicted"] = "invalid"

    for index, row in tqdm(df.iterrows(), total=df.shape[0], desc="Processing rows"):
        full_method_name = row['method']
        method_name = row['method'].split("(")[0]
        service_name = row['service_name']
        top_similar = get_top_similar_methods(similarities.get(full_method_name, []))

        prompt = ""
        json_answer = {"access_control_level": "invalid"}
        res = {"user_message": "invalid", "response": "invalid"}

        if top_similar:
            prompt = create_prompt2_string(
                top_similar, df, method_name, get_java_code(row), row["sink_code"]
            )
            res = run_second_prompt_Ollama(prompt, model_prompt2,True, sys_prompt2, num_ctx)

        access_control = "invalid"
        try:
            json_answer = extract_json_from_string(res["response"])
            access_control = json_answer.get("access_control_level", "invalid")
            # check if access_control is string, if not, convert it to string
            if not isinstance(access_control, str):
                access_control = str(access_control)
            json_str = json.dumps(json_answer) if json_answer else "{}"
        except Exception as e:
            print(f"Error extracting JSON from response: {e}")
            print(f"Method: {method_name}, Service: {service_name}")

        df.at[index, 'json_answer2'] = json_str
        df.at[index, 'access control level predicted'] = access_control

        print(f"Method: {method_name}, Service: {service_name}, Access Control: {access_control}")
        
        
        # todo: uncomment later
        # Define folder path and create directories 
        # folder_path = os.path.join(output_folder_preprocess, service_name, method_name)
        # os.makedirs(folder_path, exist_ok=True)

        # with open(os.path.join(folder_path, 'user_message2.txt'), 'w') as f:
        #     f.write(res["user_message"])
        # with open(os.path.join(folder_path, 'response2.txt'), 'w') as f:
        #     f.write(res["response"])

    # Save the updated DataFrame to a Parquet file
    df_to_save = df.drop(columns=['embeddings'], errors='ignore')
    df_output_file = os.path.join(output_folder_preprocess, "android_services_methods_postprocess.parquet")
    df_to_save.to_parquet(df_output_file)
    print(f"DataFrame serialized and saved to {df_output_file}")

    return df  

def main():
    parser = argparse.ArgumentParser(description='Analyze Java execution paths for security sinks')
    parser.add_argument('--prompt', required=True, help='Path to prompt file')
    parser.add_argument('--model', required=True, help='Ollama model name to use')
    parser.add_argument('--num-ctx', type=int, default=25000, help='Context window size')
    parser.add_argument('--input-dir', required=True, help='Input directory for results')
    
    args = parser.parse_args()
    with open(args.prompt, 'r') as file:
        sys_prompt2 = file.read()
    model_prompt2 = "myexample2"
    ollama.create(model=model_prompt2,
    from_=args.model,
    system=sys_prompt2.strip())
    
    file_path = os.path.join(args.input_dir, "android_services_methods.parquet")  
    
    df = pd.read_parquet(file_path)
    df['sink_code'] = df['json_answer'].apply(process_json_answer)
    print(f"total rows = {len(df)}")
    print(f"row with valid json  = {counter}")

    calculate_embeddings(df)
    similarities = compute_80_top2(df)
    write_csvs(similarities, args.input_dir)
    
    df = process_dataframe2(df, similarities, args.input_dir, model_prompt2,sys_prompt2.strip(),int(args.num_ctx))
    
    
    df_filtered = df[df['access control level predicted'] != 'invalid']
    accuracy = (df_filtered['access control level'] == df_filtered['access control level predicted']).mean() * 100
    print(f'Overall Accuracy: {accuracy:.2f}% total rows = {len(df_filtered)}')
    access_levels = ['NONE', 'NORMAL', 'DANGEROUS', 'SYS_OR_SIG']
    # Generate statistics for each actual access control level
    for acl in access_levels:
        subset = df_filtered[df_filtered['access control level'] == acl]
        stats = subset['access control level predicted'].value_counts().to_frame(name='Count')
        stats['Percentage'] = (stats['Count'] / stats['Count'].sum()) * 100
        print(f"\nPredicted AC Stats for '{acl}':")
        print(stats.to_string())
    print("Done")
    

if __name__ == "__main__":
    main()
    