cmake_minimum_required(VERSION 3.22)
project(BetterEndfieldNextThirdPartyHostTest LANGUAGES CXX)

add_executable(third_party_host_test
    main.cpp
    ../../shared/third_party_modules/third_party_host.cpp)
target_compile_features(third_party_host_test PRIVATE cxx_std_20)
target_include_directories(third_party_host_test PRIVATE ../../shared/include)
if(WIN32)
    target_link_libraries(third_party_host_test PRIVATE ws2_32)
    add_library(third_party_fixture SHARED fixture_module.cpp)
    target_include_directories(third_party_fixture PRIVATE ../../shared/include)
else()
    target_link_libraries(third_party_host_test PRIVATE c++_static c++abi dl)
endif()
