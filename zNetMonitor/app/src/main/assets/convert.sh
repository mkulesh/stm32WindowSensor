#!/bin/sh

find . -name "*.svg" -exec ./convert_file.sh {} \;

