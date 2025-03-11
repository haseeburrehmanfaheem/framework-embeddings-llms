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

echo "Starting preprocessing..."    
python ./preprocess.py --csv-dir ./input/Execution-Paths --output-dir "$SHARED_DIR" --prompt ../prompts/prompt1-11.txt --model llama3.3 --num-ctx 30000

echo "Preprocessing completed."

echo "Starting postprocessing..."
python ./postprocess.py --input-dir "$SHARED_DIR" --prompt ../prompts/prompt2.txt --model llama3.3 --num-ctx 30000

echo "Postprocessing completed."
