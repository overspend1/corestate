cmake_minimum_required(VERSION 3.10)

project(CoreStateModuleNative)

# Set standard to C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add subdirectories for each native component
add_subdirectory(snapshot_manager)
add_subdirectory(fs_monitor)
add_subdirectory(hw_acceleration)

# Example of creating a shared library (will be expanded later)
# add_library(corestate_native SHARED ...)