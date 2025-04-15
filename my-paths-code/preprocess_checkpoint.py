
# python ./preprocess.py --csv-dir ./input/Execution-Paths --output-dir ./tmpoutput/results --prompt ../prompts/prompt1-10.txt --model llama3.2 --num-ctx 25000

import os
import argparse
import json
import re
import csv
import pandas as pd
import ollama
from tqdm import tqdm

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


def process_dataframe(df, output_folder, model_name, sys_prompt, num_ctx):
    """
    Process DataFrame and save results, with checkpointing in place.
    """
    checkpoint_file = os.path.join(output_folder, "checkpoint.parquet")
    if os.path.exists(checkpoint_file):
        print(f"Checkpoint file found at {checkpoint_file}. Loading it...")
        df_checkpoint = pd.read_parquet(checkpoint_file)
        if 'id' in df.columns and 'id' in df_checkpoint.columns:
            df = df.merge(
                df_checkpoint[['id', 'json_answer', 'res1', 'prompt1']], 
                on='id', 
                how='left', 
                suffixes=('', '_chkpt')
            )
            for col in ['json_answer', 'res1', 'prompt1']:
                df[col] = df[col].combine_first(df[f"{col}_chkpt"])
            df.drop(columns=[c for c in df.columns if c.endswith('_chkpt')], inplace=True)
        else:
            print("No unique ID for merging. Using checkpoint df as is.")
            df = df_checkpoint

    else:
        print(f"No checkpoint file found at {checkpoint_file}. Starting fresh.")

    # Ensure columns exist
    if 'json_answer' not in df.columns:
        df['json_answer'] = None
    if 'res1' not in df.columns:
        df['res1'] = None
    if 'prompt1' not in df.columns:
        df['prompt1'] = None

    # Now iterate only over rows that are not processed
    # Here we consider a row "processed" if 'json_answer' is not None (or empty) 
    # Adapt if your sentinel is different
    rows_to_process = df[df['json_answer'].isnull()].index

    print(f"{len(rows_to_process)} rows left to process out of {len(df)} total rows.")

    for index in tqdm(rows_to_process, total=len(rows_to_process), desc="Processing rows"):
        row = df.loc[index]
        method_name = row['method'].split("(")[0]
        service_name = row['service_name']

        code_string_2 = get_three_java_codes(row)  # This is your custom function

        try:
            df.at[index, 'prompt1'] = code_string_2
            res = run_ollama_prompt(code_string_2, model_name, sys_prompt, num_ctx)

            df.at[index, 'res1'] = res['response']
            df.at[index, 'json_answer'] = extract_json_from_string(res["response"])
            
            # Optionally convert dict/list to JSON string
            if isinstance(df.at[index, 'json_answer'], (dict, list)):
                df.at[index, 'json_answer'] = json.dumps(df.at[index, 'json_answer'])

            folder_path = os.path.join(output_folder, service_name, method_name)
            os.makedirs(folder_path, exist_ok=True)

            for file, content in [
                ('system_message.txt', res.get("system_message", "")),
                ('user_message.txt', res.get("user_message", "")),
                ('response.txt', res.get("response", ""))
            ]:
                with open(os.path.join(folder_path, file), 'w', encoding='utf-8') as f:
                    f.write(content)

        except Exception as e:
            print(f"Error processing {service_name}/{method_name}: {e}")
            # You could optionally continue or break here. 
            # For now, let's continue to process the next row
            continue

        # --- SAVE CHECKPOINT AFTER EACH ROW (or every N rows) ---
        df.to_parquet(checkpoint_file)

    print("All done with process_dataframe. Final checkpoint saved.")
    

def main():
    parser = argparse.ArgumentParser(description='Analyze Java execution paths for security sinks')
    parser.add_argument('--csv-dir', required=True, help='Input directory containing CSV files')
    parser.add_argument('--output-dir', required=True, help='Output directory for results')
    parser.add_argument('--prompt', required=True, help='Path to prompt file')
    parser.add_argument('--model', required=True, help='Ollama model name to use')
    parser.add_argument('--num-ctx', type=int, default=25000, help='Context window size')
    # parser.add_argument('--service-name', help='Specific service name to process')
    args = parser.parse_args()
    with open(args.prompt, 'r') as f:
        sys_prompt = f.read().strip()
    model_name = "model1"

    ollama.create(
        model=model_name,
        from_=args.model.strip(),
        system=sys_prompt
    )
    df = process_csv_files(args.csv_dir)
    print(f"length of df: {len(df)}")
    process_dataframe(
        df=df,
        output_folder=args.output_dir,
        model_name=model_name,
        sys_prompt=sys_prompt,
        num_ctx=args.num_ctx
    )
    
    # write the df in a parquet
    df_output_file = os.path.join(args.output_dir, "android_services_methods.parquet")
    df.to_parquet(df_output_file)
    print(f"Dataframe saved to {df_output_file}")

if __name__ == "__main__":
    main()