cmake_minimum_required(VERSION 3.22.1)
project(edge_detection_vr)

# Create native library
add_library(edgedetector SHARED
    ImageProcessor.cpp
)

# Link libraries
target_link_libraries(edgedetector
    log
)
