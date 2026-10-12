cmake_minimum_required(VERSION 3.22.1)

project("retrostation_ppsspp" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Symbol visibility isolation: hide all symbols by default
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/include
)

# Core bridge source files
add_library(retrostation_ppsspp SHARED
    src/main/cpp/ppsspp_bridge.cpp
)

# Strict symbol isolation: prevent collision with FFmpeg/zlib/SDL2 symbols in other engines
set(VERSION_SCRIPT "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/ppsspp_symbols.map")

target_link_options(retrostation_ppsspp PRIVATE
    "-Wl,--version-script=${VERSION_SCRIPT}"
    "-Wl,--exclude-libs,ALL"
    "-Wl,-Bsymbolic"
    "-Wl,--gc-sections"
    "-Wl,--no-undefined"
    "-Wl,-z,max-page-size=16384"
)

target_compile_definitions(retrostation_ppsspp PRIVATE
    ANDROID_NDK_BUILD
)

find_library(LOG_LIB log)
find_library(ANDROID_LIB android)

target_link_libraries(retrostation_ppsspp PRIVATE
    ${LOG_LIB}
    ${ANDROID_LIB}
)
