python ./preprocess_claude.py --csv-dir /u1/hfaheem/DLAndroidArtifact/my-paths-code/input/Execution-Paths --output-dir ./claude-sonnet-3.7/ --prompt ../prompts/prompt1-11.txt

python ./postprocess_claude.py --input-dir ./claude-sonnet-3.7/ --prompt ../prompts/prompt2-6.txt --similarity-threshold 0.7



# /u1/hfaheem/DLAndroidArtifact/my-paths-code/all-outputs/oneplus-14/claude-sonnet-3.7