# Platform-neutral package decoding and resource selection, shared by PC/Android.
# Engine calls, process memory access and native layout discovery stay in adapters.
file(GLOB BEM_ZSTD_COMMON CONFIGURE_DEPENDS ../../shared/third_party/zstd/lib/common/*.c)
file(GLOB BEM_ZSTD_DECODE CONFIGURE_DEPENDS ../../shared/third_party/zstd/lib/decompress/*.c)
file(GLOB BEM_ZSTD_COMPRESS CONFIGURE_DEPENDS ../../shared/third_party/zstd/lib/compress/*.c)
add_library(BetterEndfield.BemZstd STATIC ${BEM_ZSTD_COMMON} ${BEM_ZSTD_DECODE} ${BEM_ZSTD_COMPRESS})
target_compile_definitions(BetterEndfield.BemZstd PRIVATE ZSTD_DISABLE_ASM)
set_target_properties(BetterEndfield.BemZstd PROPERTIES POSITION_INDEPENDENT_CODE ON)

# ZIP container support (format specification section 14). miniz is vendored as the
# amalgamated upstream single-file pair.
add_library(BetterEndfield.BemMiniz STATIC ../../shared/third_party/miniz/miniz.c)
target_include_directories(BetterEndfield.BemMiniz PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/../../shared/third_party/miniz")
if(NOT MSVC)
    target_compile_options(BetterEndfield.BemMiniz PRIVATE -w)
endif()
set_target_properties(BetterEndfield.BemMiniz PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(BetterEndfield.CustomModelCore STATIC bem.cpp mod_registry.cpp)
target_compile_features(BetterEndfield.CustomModelCore PUBLIC cxx_std_20)
target_include_directories(BetterEndfield.CustomModelCore PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}")
target_link_libraries(BetterEndfield.CustomModelCore PUBLIC BetterEndfield.BemZstd BetterEndfield.BemMiniz)
set_target_properties(BetterEndfield.CustomModelCore PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_executable(BetterEndfield.BemValidate EXCLUDE_FROM_ALL ../../tests/bem_v1_validate.cpp)
target_link_libraries(BetterEndfield.BemValidate PRIVATE BetterEndfield.CustomModelCore)
add_executable(BetterEndfield.BemV11CapacityTests EXCLUDE_FROM_ALL ../../tests/bem_v11_capacity_tests.cpp)
add_executable(BetterEndfield.BemV13MorphTests EXCLUDE_FROM_ALL ../../tests/bem_v13_morph_tests.cpp)
add_executable(BetterEndfield.AnimationDriverTests EXCLUDE_FROM_ALL
    ../../tests/animation_driver_tests.cpp animation_driver.cpp)
target_include_directories(BetterEndfield.AnimationDriverTests PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")
target_compile_features(BetterEndfield.AnimationDriverTests PRIVATE cxx_std_20)
add_executable(BetterEndfield.BemV14ResourceTests EXCLUDE_FROM_ALL ../../tests/bem_v14_resource_tests.cpp)
target_link_libraries(BetterEndfield.BemV14ResourceTests PRIVATE BetterEndfield.CustomModelCore)
target_link_libraries(BetterEndfield.BemV13MorphTests PRIVATE BetterEndfield.CustomModelCore)
add_executable(BetterEndfield.BemKeepTextureValidate EXCLUDE_FROM_ALL ../../tests/bem_keep_texture_validate.cpp)
target_link_libraries(BetterEndfield.BemKeepTextureValidate PRIVATE BetterEndfield.CustomModelCore)
target_link_libraries(BetterEndfield.BemV11CapacityTests PRIVATE BetterEndfield.CustomModelCore)
if(ANDROID)
    target_link_libraries(BetterEndfield.BemValidate PRIVATE c++_static c++abi)
    target_link_libraries(BetterEndfield.BemV11CapacityTests PRIVATE c++_static c++abi)
endif()
