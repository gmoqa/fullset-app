cmake_minimum_required(VERSION 3.10)
project(whisper-android C CXX)

set(CMAKE_CXX_STANDARD 17)
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper)

find_library(LOG_LIB log)

# El código nativo va SIEMPRE optimizado, también en builds debug: sin -O3 la inferencia es
# ~10x más lenta (lo que depuramos es el Kotlin, no whisper.cpp). Se setea antes de traer ggml
# para que su subproyecto herede los mismos flags, que es donde está el cómputo pesado.
set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG} -O3")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -O3")

# ggml (solo backend CPU: los demás backends ni se copiaron al proyecto).
include(FetchContent)
FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

add_library(
    whisper_jni SHARED
    ${WHISPER_DIR}/src/whisper.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper_jni.c
)

target_compile_definitions(whisper_jni PUBLIC GGML_USE_CPU)
target_compile_definitions(whisper_jni PRIVATE WHISPER_VERSION="1.9.1")

target_include_directories(
    whisper_jni PRIVATE
    ${WHISPER_DIR}
    ${WHISPER_DIR}/src
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
)

# En release achicamos el .so todo lo posible (la app entera pesa ~22 MB).
if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(whisper_jni PRIVATE -O3)
    target_compile_options(whisper_jni PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
    target_compile_options(whisper_jni PRIVATE -ffunction-sections -fdata-sections)
    target_link_options(whisper_jni PRIVATE -Wl,--gc-sections)
    target_link_options(whisper_jni PRIVATE -Wl,--exclude-libs,ALL)
endif ()

target_link_libraries(whisper_jni ${LOG_LIB} android ggml)
