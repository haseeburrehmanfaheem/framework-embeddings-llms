# python /u1/hfaheem/DLAndroidArtifact/my-paths-code/postprocess.py --input-dir /u1/hfaheem/DLAndroidArtifact/my-paths-code/output6 --prompt /u1/hfaheem/DLAndroidArtifact/prompts/prompt2.txt --model llama3.3 --num-ctx 25000
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


def get_three_java_codes(row):
    """Extract 3 Java code snippets: one from max depth, one from max-1, one from max-2.
    
    If there are not enough depths, take more from the highest available.
    """
    from collections import defaultdict

    # Group by depth
    depth_dict = defaultdict(list)
    for entry in row["depths"]:
        depth_dict[entry['depth']].append(entry)

    # Sort available depths in descending order
    sorted_depths = sorted(depth_dict.keys(), reverse=True)
    
    selected_entries = []
    
    # Try to get 1 code from max, max-1, and max-2 depths
    for i in range(3):
        if i < len(sorted_depths):  # Check if that depth exists
            depth = sorted_depths[i]
            selected_entries.append(depth_dict[depth].pop(0))  # Get one entry from this depth
    
    # If we still need more codes, fill from highest available
    all_remaining_entries = sum(depth_dict.values(), [])  # Flatten remaining entries
    while len(selected_entries) < 3 and all_remaining_entries:
        selected_entries.append(all_remaining_entries.pop(0))

    # Format output with depth information
    return '\n\n'.join([
        f"This is path {i+1} for the API with depth {entry['depth']}:\n{entry['java_code']}"
        for i, entry in enumerate(selected_entries)
    ])

def remove_empty_embeddings(df):
    # Filter out rows where the embeddings column is empty or None
    df_filtered = df[df["embeddings"].apply(lambda x: x is not None and len(x) > 0)]
    return df_filtered

def remove_comments(json_string):
    """Remove single-line comments (//) from the JSON string."""
    return re.sub(r'//.*', '', json_string)

def try_extract_and_parse(pattern, input_string):
    """Extract using the given regex pattern and parse JSON after cleaning comments."""
    json_blocks = re.findall(pattern, input_string, re.DOTALL)
    for block in reversed(json_blocks):
        cleaned_block = remove_comments(block).strip()
        try:
            return json.loads(cleaned_block)
        except json.JSONDecodeError:
            continue
    return None

def try_extract_boxed_json(input_string):
    """
    Try to extract JSON from LaTeX-style boxed expressions of the form:
    $\boxed{ ... }$
    """
    boxed_blocks = re.findall(r'\$\s*\\boxed\s*\{(.*?)\}\s*\$', input_string, re.DOTALL)
    for block in boxed_blocks:
        cleaned_block = remove_comments(block).strip()
        # Unescape any escaped braces if needed
        cleaned_block = cleaned_block.replace(r'\{', '{').replace(r'\}', '}')
        try:
            return json.loads(cleaned_block)
        except json.JSONDecodeError:
            continue
    return None

def try_extract_curly_braces(input_string):
    """
    Final fallback: Look for the first substring that starts with '{' and ends with '}'.
    """
    match = re.search(r'(\{.*\})', input_string, re.DOTALL)
    if match:
        cleaned_block = remove_comments(match.group(1)).strip()
        try:
            return json.loads(cleaned_block)
        except json.JSONDecodeError:
            return None
    return None

def extract_json_from_string(input_string):
    """
    Extract JSON from a response string by trying, in order:
      1. Code blocks explicitly tagged as JSON (```json).
      2. Any code blocks delimited by triple backticks (```).
      3. The entire string (if valid JSON).
      4. LaTeX-style boxed JSON (e.g. $\boxed{ ... }$).
      5. The first substring that starts with '{' and ends with '}'.
    """
    # 1. Try blocks tagged explicitly as JSON
    pattern_json = r"```json\s*\n(.*?)```"
    result = try_extract_and_parse(pattern_json, input_string)
    if result is not None:
        return result

    # 2. Fallback: try any block delimited by triple backticks
    pattern_any = r"```\s*\n(.*?)```"
    result = try_extract_and_parse(pattern_any, input_string)
    if result is not None:
        return result

    # 3. Try parsing the entire input string as JSON
    try:
        cleaned_input = remove_comments(input_string).strip()
        return json.loads(cleaned_input)
    except json.JSONDecodeError:
        pass

    # 4. Look for LaTeX-style boxed JSON (e.g. $\boxed{ ... }$)
    result = try_extract_boxed_json(input_string)
    if result is not None:
        return result

    # 5. Final fallback: search for a substring that starts with '{' and ends with '}'
    return try_extract_curly_braces(input_string)


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
    
    inputs_ids = tokenizer.encode(code_snippet.strip())  # Get tokenized IDs without truncation

    if len(inputs_ids) > 512:
        print(f"Warning: Code snippet exceeds 512 tokens ({len(inputs_ids)} tokens). It will be truncated.")
    inputs = tokenizer.encode(code_snippet, return_tensors="pt", truncation=True, max_length=512).to("cuda")
    with torch.no_grad():
        embedding = model(inputs)[0]
    
    return embedding.cpu()

def process_json_answer(json_answer, n=float("inf")):
    global counter
    all = []
    if isinstance(json_answer, str):  # If it's a string, parse it
        try:
            json_answer = json.loads(json_answer)
        except json.JSONDecodeError:
            # print("Invalid JSON format")
            return []
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
    for index, row in tqdm(df.iterrows(), total=len(df), desc="Processing Embeddings"):
        code_snippets = row["sink_code"]
        embeddings = []
        method_signature = row["depths"][0]['java_code'].split("\n")[0]
        for each in code_snippets:
            code = f'''
            {method_signature}
            {each}
            }}
            '''
            code_embedding = get_code_embedding(each)
            embeddings.append(code_embedding)
        df.at[index, "embeddings"] = embeddings

def get_top_similar_methods(similarities, top_n=2, threshold=0.7):
    # Filter based on the similarity threshold
    filtered = [entry for entry in similarities if entry['similarity'] > threshold]
    # Sort the filtered entries in descending order by similarity
    sorted_results = sorted(filtered, key=lambda x: x['similarity'], reverse=True)
    
    unique_ep2_ids = set()
    unique_ep2_codes = set()
    results = []
    
    for entry in sorted_results:
        # Skip if the ep2_code is already in the results
        if entry['ep2_code'] in unique_ep2_codes:
            continue
        
        # If the ep2_id is already included, add the entry (if its ep2_code is new)
        if entry['ep2_id'] in unique_ep2_ids:
            results.append(entry)
            unique_ep2_codes.add(entry['ep2_code'])
        else:
            # This is a new unique ep2_id
            if len(unique_ep2_ids) < top_n:
                unique_ep2_ids.add(entry['ep2_id'])
                results.append(entry)
                unique_ep2_codes.add(entry['ep2_code'])
            else:
                # Encountering a new unique ep2_id beyond our top_n limit; break out of the loop.
                break

    # Extract and return only the desired fields
    extracted_results = [
        {
            'ep2_code': entry['ep2_code'],
            'ep2_id': entry['ep2_id'],
            'ep1_code': entry['ep1_code'],
            'similarity': entry['similarity']
        }
        for entry in results
    ]
    
    return extracted_results

def create_prompt2_string(top_similar, df, original_method, original_code, sink_code, method_name_unprocessed):
    if not top_similar:
        return "No similar APIs found"  # Return an empty string if there are no similar methods

    original_class = df[df["method"] == method_name_unprocessed]["service_name"].values[0]
    prompt = f"The method {original_method} in the following class {original_class} has the following code snippet:\n\n"
    prompt += f"{original_code}\n"
    prompt += f"and the following sink code:\n"
    prompt += f"{sink_code}\n\n"
    
    prompt += f"The method {original_method} has the following similar APIs:\n\n"
    
    # Group entries by unique API method (ep2_id)
    grouped = {}
    for entry in top_similar:
        ep2_id = entry["ep2_id"]
        ep2_code = entry["ep2_code"]
        similarity = entry.get("similarity", "N/A")
        if ep2_id not in grouped:
            grouped[ep2_id] = []
        grouped[ep2_id].append((ep2_code, similarity))
    
    # Now iterate over each unique API and include all corresponding sink codes with their similarity scores
    for ep2_id, code_entries in grouped.items():
        # Get access control and class information from the dataframe
        access_control = df[df["method"] == ep2_id]["access control level"].values
        class_name = df[df["method"] == ep2_id]["service_name"].values[0]
        access_control_str = access_control[0] if len(access_control) > 0 else "Unknown"
        
        prompt += f"- API Name: {ep2_id} in the following Class: {class_name} with the following sink code entries:\n"
        for code, similarity in code_entries:
            prompt += f"  - Similarity: {similarity}, Code:\n{code}\n"
        prompt += f"  - Access Control Level: {access_control_str}\n\n"
    
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
        'temperature': 0.3 # Todo : Change temperature here
    }
    )
    
    return {
        "system_message": sys_prompt2,
        "user_message": user_prompt,
        "response": response['message']['content']
    }

def compute_80_top2(df, threshold=0.7):
    # Preprocess all embeddings and metadata
    all_embeddings = []
    method_info = []  # List of tuples (method_id, code_snippet)
    method_to_indices = {}  # Maps method to its embedding indices

    for idx, row in df.iterrows():
        method = row["method"]
        embeddings = row["embeddings"]
        codes = row["sink_code"]
        indices = []
        for emb, code in zip(embeddings, codes):
            all_embeddings.append(emb.clone().detach())
            method_info.append((method, code))
            indices.append(len(all_embeddings) - 1)  # Current index
        method_to_indices[method] = indices

    # Move all embeddings to GPU and normalize
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    all_embeddings = torch.stack(all_embeddings).to(device)
    all_embeddings = torch.nn.functional.normalize(all_embeddings, p=2, dim=1)

    similarities = {method: [] for method in method_to_indices.keys()}

    # Process each method's embeddings against all others
    for method in tqdm(method_to_indices, desc="Processing methods"):
        ep1_indices = method_to_indices[method]
        if not ep1_indices:
            continue

        # Get other embeddings not belonging to this method
        other_indices = [i for i in range(len(all_embeddings)) if method_info[i][0] != method]
        if not other_indices:
            continue

        # Compute similarities in batches to manage memory
        batch_size = 100000  # Adjust based on GPU memory
        ep1_embs = all_embeddings[ep1_indices]
        num_other = len(other_indices)
        num_batches = (num_other + batch_size - 1) // batch_size

        for batch_idx in range(num_batches):
            start = batch_idx * batch_size
            end = min((batch_idx + 1) * batch_size, num_other)
            batch_other_indices = other_indices[start:end]
            other_embs = all_embeddings[batch_other_indices]

            # Compute cosine similarity matrix
            sim_batch = torch.mm(ep1_embs, other_embs.T)
            sim_batch = sim_batch.cpu().numpy()  # Move to CPU to save GPU memory

            # Find pairs above threshold
            rows, cols = (sim_batch > threshold).nonzero()
            for i, j in zip(rows, cols):
                ep1_code = method_info[ep1_indices[i]][1]
                other_idx = batch_other_indices[j]
                other_method, other_code = method_info[other_idx]
                similarities[method].append({
                    "similarity": sim_batch[i, j],
                    "ep1_code": ep1_code,
                    "ep2_id": other_method,
                    "ep2_code": other_code
                })

    # Count methods with no similar pairs
    no_similar_count = sum(1 for entries in similarities.values() if not entries)
    print(f"Total methods with no similar sink pairs: {no_similar_count}")

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



    third_path = os.path.join(CSV_FILE, "_topncode" + ".csv")
    with open(third_path, mode='w', newline='', encoding='utf-8') as file:
        writer = csv.writer(file)

        # Write header
        writer.writerow(["EP1_ID", "EP2_ID", "EP1_Code", "EP2_Code", "Similarity"])

        # Iterate through the data and write only the top 2 most similar pairs
        for ep, similar_pairs in similarities.items():
            
            if similar_pairs:
                # Sort the pairs by similarity in descending order
                top_pairs = sorted(similar_pairs, key=lambda x: x["similarity"], reverse=True)
                for pair in top_pairs:
                    writer.writerow([
                        ep,
                        pair['ep2_id'],
                        pair['ep1_code'].replace("\n", " "),  # Replace newlines for better CSV readability
                        pair['ep2_code'].replace("\n", " "),  # Replace newlines for better CSV readability
                        pair['similarity']
                    ])
            else:
                writer.writerow([ep, "No similar EPs with similarity > 0.5", "", "", ""])

    print(f"Data has been written to {CSV_FILE}")


def process_dataframe2(df, similarities, output_folder_preprocess, model_prompt2, sys_prompt2, num_ctx):
    df["json_answer2"] = None
    df["access control level predicted"] = "invalid"
    df["res2"] = None  # New column to store the raw response

    for index, row in tqdm(df.iterrows(), total=df.shape[0], desc="Processing rows"):
        full_method_name = row['method']
        method_name = row['method'].split("(")[0]
        service_name = row['service_name']
        top_similar = get_top_similar_methods(similarities.get(full_method_name, []))

        prompt = ""
        json_answer = {"access_control_level": "invalid"}
        res = {"user_message": "invalid", "response": "no top_similar found"}

        if top_similar:
            prompt = create_prompt2_string(
                top_similar, df, method_name, get_three_java_codes(row), row["sink_code"], full_method_name
            )
            res = run_second_prompt_Ollama(prompt, model_prompt2, True, sys_prompt2, num_ctx)
        else:
            res["response"] = "no top_similar found"

        # Store the raw response in df["res2"]
        df.at[index, 'res2'] = res["response"]

        access_control = "invalid"
        json_str = "no top_similar found"

        try:
            json_answer = extract_json_from_string(res["response"])
            access_control = json_answer.get("access_control_level", "invalid")
            if not isinstance(access_control, str):
                access_control = str(access_control)
            json_str = json.dumps(json_answer) if json_answer else "{}"
        except Exception as e:
            print(f"Error extracting JSON from response: {e}")
            print(f"Method: {method_name}, Service: {service_name}")
            json_str = "error extracting json"

        df.at[index, 'json_answer2'] = json_str
        df.at[index, 'access control level predicted'] = access_control

        print(f"Method: {method_name}, Service: {service_name}, Access Control: {access_control}")

        # Define folder path and create directories 
        folder_path = os.path.join(output_folder_preprocess, service_name, method_name)
        os.makedirs(folder_path, exist_ok=True)

        with open(os.path.join(folder_path, 'user_message2.txt'), 'w') as f:
            f.write(res["user_message"])
        with open(os.path.join(folder_path, 'response2.txt'), 'w') as f:
            f.write(res["response"])

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
    
    modelfile=f'''
    FROM llama3.3
    system """
    {sys_prompt2.strip()}
    """
    '''
    model_prompt2 = "myexample2"
    
    ollama.create(model=model_prompt2, modelfile=modelfile)
    # ollama.create(model=model_prompt2,
    # from_=args.model,
    # system=sys_prompt2.strip())
    
    file_path = os.path.join(args.input_dir, "android_services_methods.parquet")  
    
    df = pd.read_parquet(file_path)
    df['sink_code'] = df['json_answer'].apply(process_json_answer)
    print(f"total rows = {len(df)}")
    print(f"row with valid json  = {counter}")

    calculate_embeddings(df)
    df = remove_empty_embeddings(df)
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
    