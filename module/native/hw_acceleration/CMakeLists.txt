# Hardware Acceleration Component
add_library(hw_acceleration STATIC
    hsm_integration.cpp
)

target_include_directories(hw_acceleration PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Set compiler-specific options
if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
    target_compile_options(hw_acceleration PRIVATE
        -Wall -Wextra -O2
    )
endif()

# Link system libraries if needed
target_link_libraries(hw_acceleration
    # Add system libraries as needed
)