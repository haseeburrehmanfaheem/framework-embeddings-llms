#!/bin/bash

# Ensure the script exits if any command fails
set -e

# Check if the required argument is provided
if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <shared_output_directory>"
    exit 1
fi

# Assign the first argument to a variable
SHARED_DIR="$1"

echo "Using shared directory: $SHARED_DIR"

# echo "Starting preprocessing..."    
# python ./preprocess_checkpoint.py --csv-dir /u1/hfaheem/DLAndroidArtifact/my-paths-code/input/v15/Execution-Paths --output-dir "$SHARED_DIR" --prompt ../prompts/prompt1-11-gemma.txt --model gemma3:27b --num-ctx 30000
# 
# echo "Preprocessing completed."

echo "Starting postprocessing..."
python ./postprocess_checkpoint.py --input-dir "$SHARED_DIR" --prompt /u1/hfaheem/DLAndroidArtifact/prompts/prompt2-6.txt --model gemma3:27b --num-ctx 30000 --similarity-threshold 0.65


# python ./postprocess_checkpoint.py --input-dir "$SHARED_DIR" --prompt /u1/hfaheem/DLAndroidArtifact/prompts/prompt2-6.txt --model gemma3:27b --num-ctx 30000 --similarity-threshold 0.70 --make-model=false
# python ./postprocess_checkpoint.py --input-dir "$SHARED_DIR" --prompt /u1/hfaheem/DLAndroidArtifact/prompts/prompt2-6.txt --model gemma3:27b --num-ctx 30000 --similarity-threshold 0.75 --make-model=false

echo "Postprocessing completed."
