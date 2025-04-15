#!/bin/bash

# Define model names
models=("llama3.3" "gemma3:27b" "deepseek-r1:70b")

# Base directories
csv_dir="./input/Execution-Paths"
prompt_file="../prompts/prompt1-11.txt"
output_base="p1-results"
num_ctx=40000

# Loop through models
for model in "${models[@]}"; do
  # Replace ':' with '-' for safe folder names
  safe_model_name=${model//:/-}
  
  # Output directory
  output_dir="${output_base}/${safe_model_name}"

  # Run the command
  echo "Running for model: $model"
  python ./preprocess_checkpoint.py \
    --csv-dir "$csv_dir" \
    --output-dir "$output_dir" \
    --prompt "$prompt_file" \
    --model "$model" \
    --num-ctx "$num_ctx"
done
