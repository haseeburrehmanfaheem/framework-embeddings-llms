
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

def run_ollama_prompt(method_code, model_name, sys_prompt, num_ctx):
    """Execute the analysis prompt with Ollama."""
    response = ollama.chat(
        model=model_name,
        messages=[{'role': 'user', 'content': method_code}],
        options={'num_ctx': num_ctx}
    )
    return {
        "system_message": sys_prompt,
        "user_message": method_code,
        "response": response['message']['content']
    }

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


def process_dataframe(df, output_folder, model_name, sys_prompt, num_ctx):
    """Process DataFrame and save results."""
    df['json_answer'] = None
    df['res1'] = None
    
    for index, row in tqdm(df.iterrows(), total=df.shape[0], desc="Processing rows"):
        method_name = row['method'].split("(")[0]
        service_name = row['service_name']
        code_string = get_java_code(row)

        try:
            res = run_ollama_prompt(code_string, model_name, sys_prompt, num_ctx)
            df.at[index, 'res1'] = res['response']
            df.at[index, 'json_answer'] = extract_json_from_string(res["response"])
            # print(f"json_answer at this index is {df.at[index, 'json_answer']}")
            df["json_answer"] = df["json_answer"].apply(lambda x: json.dumps(x) if isinstance(x, (dict, list)) else str(x))
            print(f"json_answer at this index is {df.at[index, 'json_answer']}")
            folder_path = os.path.join(output_folder, service_name, method_name)
            os.makedirs(folder_path, exist_ok=True)
            
            for file, content in [('system_message.txt', res["system_message"]),
                                 ('user_message.txt', res["user_message"]),
                                 ('response.txt', res["response"])]:
                with open(os.path.join(folder_path, file), 'w') as f:
                    f.write(content)
        
                
        except Exception as e:
            print(f"Error processing {service_name}/{method_name}: {e}")
    

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
    # ollama.create(model=model_name,
    #           from_=args.model,
    #           system=sys_prompt.strip())
    
    modelfile=f'''
    FROM llama3.3
    system """
    {sys_prompt.strip()}
    """
    '''
    
    ollama.create(model=model_name, modelfile=modelfile)
    

    df = process_csv_files(args.csv_dir)
    
    # if args.service_name:
    # df = df[df["service_name"] == "Lcom.android.server.pm.UserManagerService"]
    df = df[:500]
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