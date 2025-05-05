# python ./preprocess.py --csv-dir ./input/Execution-Paths --output-dir ./tmpoutput/results --prompt ../prompts/prompt1-10.txt --model llama3.2 --num-ctx 25000

import os
import argparse
import json
import re
import csv
import pandas as pd
import ollama
from tqdm import tqdm
import anthropic
from anthropic.types.message_create_params import MessageCreateParamsNonStreaming
from anthropic.types.messages.batch_create_params import Request
import time 
import tiktoken
encoding = tiktoken.encoding_for_model("gpt-4")
client = anthropic.Anthropic(api_key="")
MODEL_NAME = "claude-3-7-sonnet-20250219"  
MAX_TOKENS = 4096
TEMPERATURE = 0.3

def process_csv_files(csv_directory):
    """Process CSV files and structure data into DataFrame."""
    data_dict = {}
    
    for filename in os.listdir(csv_directory):
        if filename.endswith('.csv'):
            service_name = os.path.splitext(filename)[0]
            filepath = os.path.join(csv_directory, filename)
            
            with open(filepath, 'r', newline='', encoding='utf-8') as csvfile:
                reader = csv.DictReader(csvfile)
                for row in reader:
                    try:
                        class_name = row['Class'].strip('"')
                        method = row['Method'].strip('"')
                        depth = row['Depth'].strip('"')
                        trace = row['Trace Instruction(s) ...'].strip('"')
                        java_code = row['Code Merged'].strip('"')
                        access_control_level = row['Access Control Level'].strip('"')
                        key = (service_name, class_name, method, access_control_level)
                        
                        depth_entry = {
                            'depth': int(depth),
                            'trace': trace,
                            'java_code': java_code
                        }
                        
                        if key not in data_dict:
                            data_dict[key] = []
                        data_dict[key].append(depth_entry)
                        
                    except KeyError as e:
                        print(f"Missing column in {filename}: {e}")
                        continue

    rows = []
    for key in data_dict:
        service, cls, method, acl = key
        depths = sorted(data_dict[key], key=lambda x: x['depth'])
        rows.append({
            'service_name': service,
            'class': cls,
            'method': method,
            'depths': depths,
            'access control level': acl
        })
    
    return pd.DataFrame(rows)

def get_java_code(row):
    """Extract Java code from max depth entries."""
    max_depth = max([x['depth'] for x in row["depths"]])
    return '\n'.join([f"This is path {i+1} for the API with depth {max_depth}:\n{code['java_code']}"
                     for i, code in enumerate([d for d in row["depths"] if d['depth'] == max_depth])])

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

def run_ollama_prompt(method_code, model_name, sys_prompt, num_ctx):
    """Execute the analysis prompt with Ollama."""
    response = ollama.chat(
        model=model_name,
        messages=[{'role': 'user', 'content': method_code}],
        options={'num_ctx': num_ctx, 'temperature': 0.3} # todo: change temperature
    )
    return {
        "system_message": sys_prompt,
        "user_message": method_code,
        "response": response['message']['content']
    }

def try_extract_and_parse(pattern, input_string, remove_comments_first=False):
    """Extract using the given regex pattern and parse JSON."""
    json_blocks = re.findall(pattern, input_string, re.DOTALL)
    for block in reversed(json_blocks):
        block = block.strip()
        # First attempt without removing comments
        try:
            return json.loads(block)
        except json.JSONDecodeError:
            pass

        # If that fails and remove_comments_first is True, try after removing comments
        if remove_comments_first:
            cleaned_block = remove_comments(block)
            try:
                return json.loads(cleaned_block)
            except json.JSONDecodeError:
                continue
    return None

def try_extract_boxed_json(input_string, remove_comments_first=False):
    """Try to extract JSON from LaTeX-style boxed expressions of the form: $\boxed{ ... }$."""
    boxed_blocks = re.findall(r'\$\s*\\boxed\s*\{(.*?)\}\s*\$', input_string, re.DOTALL)
    for block in boxed_blocks:
        block = block.strip()

        # First attempt without removing comments
        try:
            return json.loads(block)
        except json.JSONDecodeError:
            pass

        # If that fails and remove_comments_first is True, try after removing comments
        if remove_comments_first:
            cleaned_block = remove_comments(block)
            try:
                return json.loads(cleaned_block)
            except json.JSONDecodeError:
                continue
    return None

def try_extract_curly_braces(input_string, remove_comments_first=False):
    """Final fallback: Look for the first substring that starts with '{' and ends with '}'."""
    match = re.search(r'(\{.*\})', input_string, re.DOTALL)
    if match:
        block = match.group(1).strip()

        # First attempt without removing comments
        try:
            return json.loads(block)
        except json.JSONDecodeError:
            pass

        # If that fails and remove_comments_first is True, try after removing comments
        if remove_comments_first:
            cleaned_block = remove_comments(block)
            try:
                return json.loads(cleaned_block)
            except json.JSONDecodeError:
                return None
    return None

def remove_comments(json_string):
    """Remove single-line comments (//) from the JSON string."""
    return re.sub(r'//.*', '', json_string)

def extract_json_from_string(input_string):
    """
    Extract JSON from a response string by trying, in order:
      1. Code blocks explicitly tagged as JSON (```json).
      2. Any code blocks delimited by triple backticks (```).
      3. The entire string (if valid JSON).
      4. LaTeX-style boxed JSON (e.g. $\boxed{ ... }$).
      5. The first substring that starts with '{' and ends with '}'.
    Tries first without removing comments, then retries with removing comments.
    """

    # 1. Try blocks tagged explicitly as JSON
    pattern_json = r"```json\s*\n(.*?)```"
    result = try_extract_and_parse(pattern_json, input_string, remove_comments_first=False)
    if result is not None:
        return result

    # Retry with comment removal
    result = try_extract_and_parse(pattern_json, input_string, remove_comments_first=True)
    if result is not None:
        return result

    # 2. Fallback: try any block delimited by triple backticks
    pattern_any = r"```\s*\n(.*?)```"
    result = try_extract_and_parse(pattern_any, input_string, remove_comments_first=False)
    if result is not None:
        return result

    # Retry with comment removal
    result = try_extract_and_parse(pattern_any, input_string, remove_comments_first=True)
    if result is not None:
        return result

    # 3. Try parsing the entire input string as JSON
    try:
        return json.loads(input_string.strip())
    except json.JSONDecodeError:
        pass

    # Retry with comment removal
    try:
        return json.loads(remove_comments(input_string).strip())
    except json.JSONDecodeError:
        pass

    # 4. Look for LaTeX-style boxed JSON (e.g. $\boxed{ ... }$)
    result = try_extract_boxed_json(input_string, remove_comments_first=False)
    if result is not None:
        return result

    # Retry with comment removal
    result = try_extract_boxed_json(input_string, remove_comments_first=True)
    if result is not None:
        return result

    # 5. Final fallback: search for a substring that starts with '{' and ends with '}'
    result = try_extract_curly_braces(input_string, remove_comments_first=False)
    if result is not None:
        return result

    # Retry with comment removal
    return try_extract_curly_braces(input_string, remove_comments_first=True)

def wait_for_batch(batch_id, client, poll_interval=5):
    """Poll the batch status indefinitely until it's no longer 'in_progress'."""
    while True:
        batch_status = client.messages.batches.retrieve(batch_id)
        if batch_status.processing_status != "in_progress":
            return batch_status
        time.sleep(poll_interval)

def batch_process_dataframe(df, output_folder, SYSTEM_PROMPT):
    """
    Create a batch request from all DataFrame rows, submit it,
    wait for processing to complete, then update the DataFrame with the results.
    Tracks token usage for input and output.
    """
    # Initialize token counting columns
    df["input_tokens_1"] = 0
    df["output_tokens_1"] = 0
    
    # Initialize the tokenizer
    enc = tiktoken.encoding_for_model("gpt-4")  # Using gpt-4 encoding as an approximation
    
    # Count system prompt tokens once
    sys_prompt_tokens = len(enc.encode(SYSTEM_PROMPT))
    
    # Dictionary to map custom_id to additional row info
    custom_id_to_info = {}

    # Build the list of batch requests
    requests = []
    for index, row in df.iterrows():
        prompt_code = get_three_java_codes(row)
        
        # Count input tokens (system prompt + user message)
        user_tokens = len(enc.encode(prompt_code))
        total_input_tokens = sys_prompt_tokens + user_tokens
        df.at[index, "input_tokens_1"] = total_input_tokens
        
        custom_id = str(index)  # use the row index as a unique identifier
        custom_id_to_info[custom_id] = {
            "row_index": index,
            "prompt_code": prompt_code,
            "service_name": row.get("service_name", "default_service"),
            "method": row.get("method", "default_method"),
        }
        # Create the messages list: optionally include a system prompt and the user prompt.
        messages = []
        messages.append({"role": "user", "content": prompt_code})
        
        requests.append(
            Request(
                custom_id=custom_id,
                params=MessageCreateParamsNonStreaming(
                    model=MODEL_NAME,
                    max_tokens=MAX_TOKENS,
                    temperature=TEMPERATURE,
                    system=SYSTEM_PROMPT,
                    messages=messages
                )
            )
        )

    # Submit the batch request
    batch_response = client.messages.batches.create(requests=requests)
    batch_id = batch_response.id
    print(f"Created batch with ID: {batch_id}")

    # Wait for the batch to finish processing by polling
    wait_for_batch(batch_id, client)
    print("Batch processing completed, retrieving results...")

    df["json_answer"] = None
    df["res1"] = None
    
    # Iterate over results and update the DataFrame.
    # We're using a non-streaming approach here by simply iterating over the results iterator.
    for result in client.messages.batches.results(batch_id):
        custom_id = result.custom_id
        res_type = result.result.type
        
        if res_type == "succeeded":
            # Get the text response from the result
            text_response = result.result.message.content[0].text
            
            # Count output tokens
            output_tokens = len(enc.encode(text_response))
            row_index = custom_id_to_info[custom_id]["row_index"]
            df.at[row_index, "output_tokens_1"] = output_tokens
            
            # print(f"Success! Custom ID {custom_id}: {text_response}")
            
            # Retrieve row information using custom_id and update the DataFrame.
            df.at[row_index, "res1"] = text_response
            df.at[row_index, "json_answer"] = extract_json_from_string(text_response)
            
            # Determine output folder path (e.g., using service name and method)
            service_name = custom_id_to_info[custom_id]["service_name"]
            # Split method to get its base name (customize this if needed)
            method_name = custom_id_to_info[custom_id]["method"].split("(")[0]
            folder_path = os.path.join(output_folder, service_name, method_name)
            os.makedirs(folder_path, exist_ok=True)
            
            # Write out system, user, and response messages as text files
            for file_name, content in [
                ("system_message.txt", SYSTEM_PROMPT),
                ("user_message.txt", custom_id_to_info[custom_id]["prompt_code"]),
                ("response.txt", text_response)
            ]:
                file_path = os.path.join(folder_path, file_name)
                with open(file_path, "w") as f:
                    f.write(content)
                    
        elif res_type == "errored":
            print(f"Error for custom ID {custom_id}: {result.result.error}")
            # Set output tokens to 0 for errors
            row_index = custom_id_to_info[custom_id]["row_index"]
            df.at[row_index, "output_tokens_1"] = 0
        elif res_type == "expired":
            print(f"Request expired for custom ID {custom_id}")
            # Set output tokens to 0 for expired requests
            row_index = custom_id_to_info[custom_id]["row_index"]
            df.at[row_index, "output_tokens_1"] = 0
        else:
            print(f"Unhandled result type for custom ID {custom_id}: {res_type}")
            # Set output tokens to 0 for unhandled result types
            row_index = custom_id_to_info[custom_id]["row_index"]
            df.at[row_index, "output_tokens_1"] = 0

    # Optionally, serialize any JSON answers for storage
    df["json_answer"] = df["json_answer"].apply(lambda x: json.dumps(x) if isinstance(x, (dict, list)) else str(x))
    
    # Calculate and print token usage statistics
    total_input_tokens = df['input_tokens_1'].sum()
    total_output_tokens = df['output_tokens_1'].sum()
    total_tokens = total_input_tokens + total_output_tokens
    avg_input_tokens = df['input_tokens_1'].mean() 
    avg_output_tokens = df['output_tokens_1'].mean()
    
    print(f"\nToken Usage Statistics:")
    print(f"Total input tokens: {total_input_tokens}")
    print(f"Total output tokens: {total_output_tokens}")
    print(f"Total tokens (input + output): {total_tokens}")
    print(f"Average input tokens per request: {avg_input_tokens:.2f}")
    print(f"Average output tokens per request: {avg_output_tokens:.2f}")
    return df


def main():
    parser = argparse.ArgumentParser(description='Analyze Java execution paths for security sinks')
    parser.add_argument('--csv-dir', required=True, help='Input directory containing CSV files')
    parser.add_argument('--output-dir', required=True, help='Output directory for results')
    parser.add_argument('--prompt', required=True, help='Path to prompt file')
    # parser.add_argument('--service-name', help='Specific service name to process')
    args = parser.parse_args()
    with open(args.prompt, 'r') as f:
        sys_prompt = f.read().strip()

    
    df = process_csv_files(args.csv_dir)
    print(f"length of df: {len(df)}")
    df = batch_process_dataframe(
        df=df,
        output_folder=args.output_dir,
        SYSTEM_PROMPT=sys_prompt
    )
    
    # Calculate token usage statistics
    total_input_tokens = df['input_tokens_1'].sum()
    total_output_tokens = df['output_tokens_1'].sum()
    total_tokens = total_input_tokens + total_output_tokens
    
    # Write token usage statistics to a file
    stats_path = os.path.join(args.output_dir, "token_usage_stats.txt")
    with open(stats_path, 'w') as f:
        f.write(f"Token Usage Statistics:\n")
        f.write(f"Total input tokens: {total_input_tokens}\n")
        f.write(f"Total output tokens: {total_output_tokens}\n")
        f.write(f"Total tokens (input + output): {total_tokens}\n")
        f.write(f"Average input tokens per request: {df['input_tokens_1'].mean():.2f}\n")
        f.write(f"Average output tokens per request: {df['output_tokens_1'].mean():.2f}\n")
    
    print(f"Token usage statistics saved to {stats_path}")
    
    # write the df in a parquet
    df_output_file = os.path.join(args.output_dir, "android_services_methods.parquet")
    df.to_parquet(df_output_file)
    print(f"Dataframe saved to {df_output_file}")

if __name__ == "__main__":
    main()