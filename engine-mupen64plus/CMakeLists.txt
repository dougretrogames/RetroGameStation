cmake_minimum_required(VERSION 3.22.1)

project("retrostation_mupen64plus" LANGUAGES C CXX)

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
add_library(retrostation_mupen64plus SHARED
    src/main/cpp/mupen64plus_bridge.cpp
)

# Strict symbol isolation: prevent collision with SDL2/libpng/zlib in other engines
set(VERSION_SCRIPT "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/mupen64plus_symbols.map")

target_link_options(retrostation_mupen64plus PRIVATE
    "-Wl,--version-script=${VERSION_SCRIPT}"
    "-Wl,--exclude-libs,ALL"
    "-Wl,-Bsymbolic"
    "-Wl,--gc-sections"
    "-Wl,--no-undefined"
    "-Wl,-z,max-page-size=16384"
)

target_compile_definitions(retrostation_mupen64plus PRIVATE
    ANDROID_NDK_BUILD
)

find_library(LOG_LIB log)
find_library(ANDROID_LIB android)

target_link_libraries(retrostation_mupen64plus PRIVATE
    ${LOG_LIB}
    ${ANDROID_LIB}
)
