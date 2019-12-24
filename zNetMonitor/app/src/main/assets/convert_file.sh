#!/bin/bash

echo Converting to ${1} to plains SVG
inkscape --vacuum-defs --export-plain-svg=${1} ${1}

