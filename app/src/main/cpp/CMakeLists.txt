cmake_minimum_required(VERSION 3.22.1)

project("retro_game_station_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Symbol visibility isolation
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

add_library(
    retro_native
    SHARED
    graphics/retro_surface_bridge.cpp
    audio/retro_aaudio_stream.cpp
    retro_jni.cpp
)

set(VERSION_SCRIPT "${CMAKE_CURRENT_SOURCE_DIR}/retro_native_symbols.map")

target_link_options(retro_native PRIVATE
    "-Wl,--version-script=${VERSION_SCRIPT}"
    "-Wl,--exclude-libs,ALL"
    "-Wl,-Bsymbolic"
    "-Wl,--gc-sections"
    "-Wl,--no-undefined"
)

find_library(log-lib log)
find_library(android-lib android)
find_library(aaudio-lib aaudio)

target_link_libraries(
    retro_native
    ${log-lib}
    ${android-lib}
    ${aaudio-lib}
)
