cmake_minimum_required(VERSION 3.18.1)

project(edgedetector)

set(OpenCV_DIR "${ANDROID_NDK}/sources/opencv/modules/core")

find_package(OpenCV REQUIRED COMPONENTS core imgproc)

add_library(edgedetector SHARED
    ImageProcessor.cpp
)

target_include_directories(edgedetector PRIVATE
    ${OpenCV_INCLUDE_DIRS}
)

target_link_libraries(edgedetector
    ${OpenCV_LIBS}
    log
)
