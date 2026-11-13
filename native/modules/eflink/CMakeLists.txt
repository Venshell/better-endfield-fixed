cmake_minimum_required(VERSION 3.22)
project(eflink_protocol_tests LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
add_executable(eflink_protocol_tests tests/eflink_tests.cpp)
target_include_directories(eflink_protocol_tests PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}"
    "${CMAKE_CURRENT_SOURCE_DIR}/../../shared/third_party")
target_link_libraries(eflink_protocol_tests PRIVATE pthread)
