#!/bin/bash

# Define variables
BASE_DIR="/u1/hfaheem/DLAndroidArtifact/my-paths-code"
PROMPT_FILE="/u1/hfaheem/DLAndroidArtifact/prompts/prompt2-6.txt"
MODEL="deepseek-r1:70b"
NUM_CTX=25000
INPUT_FILE="android_services_methods.parquet"
OUTPUTS=("output16" "output17" "output18")
THRESHOLDS=(0.65 0.70 0.75)

# Loop over the configurations
for i in "${!OUTPUTS[@]}"; do
    OUT_DIR="${BASE_DIR}/${OUTPUTS[$i]}"
    THRESHOLD="${THRESHOLDS[$i]}"

    echo "Creating directory $OUT_DIR"
    mkdir -p "$OUT_DIR" || { echo "Failed to create directory $OUT_DIR"; exit 1; }

    echo "Copying input file to $OUT_DIR"
    cp "${BASE_DIR}/output13/${INPUT_FILE}" "$OUT_DIR/" || { echo "Failed to copy input file"; exit 1; }

    echo "Running postprocessing with threshold $THRESHOLD"
    python "${BASE_DIR}/postprocess.py" \
        --input-dir "$OUT_DIR" \
        --prompt "$PROMPT_FILE" \
        --model "$MODEL" \
        --num-ctx "$NUM_CTX" \
        --similarity-threshold "$THRESHOLD" || { echo "Postprocessing failed for $OUT_DIR"; exit 1; }

    echo "Finished processing for $OUT_DIR"
    echo "--------------------------------"
done
