#!/bin/bash

# Function to get the top-level directories in a given directory
getTopLevelDirectories() {
  local directory="$1"
  find "$directory" -maxdepth 1 -type d ! -path "$directory" -exec basename {} \;
}

# Check if the directory argument is provided
if [ -z "$1" ]; then
  echo "Directory argument is missing."
  echo "Usage: ./script.sh <directory>"
  exit 1
fi

# Store the directory path from command-line argument
directoryPath="$1"

# Call the function to get the top-level directories
directories=$(getTopLevelDirectories "$directoryPath")

# Edit the startAnalysis command for each directory
for directory in $directories; do
  # Replace {path} with the current directory
  command="./gradlew startAnalysis -Dpath='InputNew/Roms/$directory/' -Dname='$directory' -Dtype='DATA' -Dout='Output/'"
  
  # Execute the edited command
  echo "Executing command for $directory:"
  echo "$command"
  # Uncomment the following line to execute the command
  # $command
  echo
done