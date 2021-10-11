#!/bin/bash
set -e
set -u

echo "move image into backup"
  
mkdir -p "$1/../DELETED"
mv "$2" "$1/../DELETED/"

echo "moving image finished"

