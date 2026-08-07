cmake_minimum_required(VERSION 3.14)
project(whisper_ios C CXX)
set(CMAKE_CXX_STANDARD 17)

# Fuentes vendoreadas de whisper.cpp (las mismas que usa Android).
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../app/src/main/cpp/whisper)

# CPU-only y estático (como Android): sin Metal/BLAS para no complicar el link.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_ACCELERATE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)

add_subdirectory(${WHISPER_DIR}/ggml ggml-build)

# whisper.cpp + el shim en una sola estática (`libwhisper.a`).
add_library(whisper STATIC
    ${WHISPER_DIR}/src/whisper.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper_shim.c
)
target_compile_definitions(whisper PUBLIC GGML_USE_CPU)
target_compile_definitions(whisper PRIVATE WHISPER_VERSION="1.9.1")
target_include_directories(whisper PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${WHISPER_DIR} ${WHISPER_DIR}/src ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include ${WHISPER_DIR}/ggml/src)
target_link_libraries(whisper PUBLIC ggml)
