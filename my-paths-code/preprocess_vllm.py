# python ./preprocess.py --csv-dir ./input/Execution-Paths --output-dir ./tmpoutput/results --prompt ../prompts/prompt1-10.txt --model llama3.2 --num-ctx 25000


# NCCL_P2P_LEVEL=NVL vllm serve Qwen/Qwen2.5-Coder-32B-Instruct  --tensor-parallel-size 2   --port 8000   --host 0.0.0.0   --gpu-memory-utilization 0.95   --max-model-len 5000 --api-key token123


import os
import argparse
import json
import re
import csv
import pandas as pd
from tqdm import tqdm
import threading
from openai import OpenAI
import time
import argparse
import os
import json
import threading
from tqdm import tqdm
from openai import OpenAI
import os, json, argparse, threading
from concurrent.futures import ThreadPoolExecutor, as_completed
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


BASE_URL    = "http://localhost:8000/v1"
API_KEY     = "token123"
MODEL_NAME  = "Valdemardi/DeepSeek-R1-Distill-Qwen-32B-AWQ"
MAX_TOKENS  = 7000
TEMPERATURE = 0.3
TIMEOUT     = 3 * 24 * 3600  # 3 days
CONCURRENCY = 270            # ≤ --max-num-seqs on the vLLM server

# Initialize client (will be re-assigned in main if you choose)
client = OpenAI(api_key=API_KEY, base_url=BASE_URL)

def run_vllm_prompt(user_prompt, system_prompt, idx, results):
    """
    Sends one chat completion to vLLM using the OpenAI client.
    Stores a dict with system_message, user_message, and response in results[idx].
    """
    resp = client.chat.completions.create(
        model=MODEL_NAME,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user",   "content": user_prompt}
        ],
        max_tokens=MAX_TOKENS,
        temperature=TEMPERATURE,
        stream=False,
        timeout=TIMEOUT
    )
    results[idx] = {
        "system_message": system_prompt,
        "user_message":   user_prompt,
        "response":       resp.choices[0].message.content
    }

# ------------------------------------------------------------
def call_llm(prompt: str, system_prompt: str) -> str:
    """Single synchronous call to the vLLM server; returns the response text."""
    resp = client.chat.completions.create(
        model       = MODEL_NAME,
        messages    = [
            {"role": "system", "content": system_prompt},
            {"role": "user",   "content": prompt}
        ],
        max_tokens  = MAX_TOKENS,
        temperature = TEMPERATURE,
        timeout     = TIMEOUT,
        stream      = False
    )
    return resp.choices[0].message.content

# ------------------------------------------------------------
def process_dataframe(df, output_dir, system_prompt):
    df["prompt1"]      = df.apply(lambda r: get_three_java_codes(r), axis=1)
    df["res1"]         = None
    df["json_answer"]  = None

    def worker(row_idx: int) -> int:
        """Callable for the pool – returns row_idx so we know which row to update."""
        prompt   = df.at[row_idx, "prompt1"]
        response = call_llm(prompt, system_prompt)
        df.at[row_idx, "res1"] = response
        parsed = extract_json_from_string(response)
        df.at[row_idx, "json_answer"] = (
            json.dumps(parsed) if isinstance(parsed, (dict, list)) else str(parsed)
        )

        # save per‑row artefacts
        method_name  = df.at[row_idx, "method"].split("(")[0]
        service_name = df.at[row_idx, "service_name"]
        folder = os.path.join(output_dir, service_name, method_name)
        os.makedirs(folder, exist_ok=True)
        for name, content in [
            ("system_message.txt", system_prompt),
            ("user_message.txt",   prompt),
            ("response.txt",       response)
        ]:
            with open(os.path.join(folder, name), "w") as f:
                f.write(content)
        return row_idx  # not strictly needed, but handy for progress

    # Thread‑pool with a hard cap on concurrent requests
    with ThreadPoolExecutor(max_workers=CONCURRENCY) as pool:
        futures = [pool.submit(worker, i) for i in range(len(df))]
        for _ in tqdm(as_completed(futures), total=len(futures), desc="Rows"):
            pass   # progress bar ticks each time a row finishes

# ------------------------------------------------------------
def main():
    args = {
        "csv_dir":   "/u1/hfaheem/DLAndroidArtifact/my-paths-code/input/Execution-Paths",
        "output_dir": f"./output_results_{MODEL_NAME}",
        "prompt":    "/u1/hfaheem/DLAndroidArtifact/prompts/prompt1-11-qwen.txt",
    }

    with open(args["prompt"], "r") as f:
        system_prompt = f.read().strip()

    global client
    client = OpenAI(api_key=API_KEY, base_url=BASE_URL)

    df = process_csv_files(args["csv_dir"])
    print(f"Loaded {len(df)} rows to analyse.")

    process_dataframe(df, output_dir=args["output_dir"], system_prompt=system_prompt)

    out_file = os.path.join(args["output_dir"], "android_services_methods.parquet")
    df.to_parquet(out_file)
    print(f"Saved results to {out_file}")

if __name__ == "__main__":
    main()


