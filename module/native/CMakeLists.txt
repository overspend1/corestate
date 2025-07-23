cmake_minimum_required(VERSION 3.10)

project(CoreStateModuleNative)

# Set standard to C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add subdirectories for each native component
add_subdirectory(snapshot_manager)
add_subdirectory(fs_monitor)
add_subdirectory(hw_acceleration)

# Create the main CoreState native library
add_library(corestate_native SHARED
    corestate_module.c
)

# Link all component libraries
target_link_libraries(corestate_native
    snapshot_manager
    fs_monitor
    hw_acceleration
)

# Set compiler-specific options for the main library
if(CMAKE_C_COMPILER_ID MATCHES "GNU|Clang")
    target_compile_options(corestate_native PRIVATE
        -Wall -Wextra -O2
    )
endif()

# Include directories
target_include_directories(corestate_native PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
)