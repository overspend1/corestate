# Snapshot Manager Component
add_library(snapshot_manager STATIC
    cow_snapshot.cpp
)

target_include_directories(snapshot_manager PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Set compiler-specific options
if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
    target_compile_options(snapshot_manager PRIVATE
        -Wall -Wextra -O2
    )
endif()

# Link system libraries if needed
target_link_libraries(snapshot_manager
    # Add system libraries as needed
)