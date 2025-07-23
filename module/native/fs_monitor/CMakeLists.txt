# File System Monitor Component
add_library(fs_monitor STATIC
    block_tracker.cpp
)

target_include_directories(fs_monitor PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Set compiler-specific options
if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
    target_compile_options(fs_monitor PRIVATE
        -Wall -Wextra -O2
    )
endif()

# Link system libraries if needed
target_link_libraries(fs_monitor
    # Add system libraries as needed
)