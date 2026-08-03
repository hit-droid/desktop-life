cmake_minimum_required(VERSION 3.10)

# Set app name.
set(APP_NAME Live2DEngine)

# ============================================================
# SDK Paths (absolute paths to the Cubism SDK)
# ============================================================
set(CUBISM_SDK_ROOT /tmp/cubism_sdk/CubismSdkForNative-5-r.5)
set(CORE_PATH ${CUBISM_SDK_ROOT}/Core)
set(FRAMEWORK_PATH ${CUBISM_SDK_ROOT}/Framework)
set(SAMPLES_COMMON_PATH ${CUBISM_SDK_ROOT}/Samples/Common)
set(STB_PATH ${CUBISM_SDK_ROOT}/Samples/OpenGL/thirdParty/stb)

# ============================================================
# C++ Standard
# ============================================================
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ============================================================
# Add Cubism Core as static library
# ============================================================
add_library(Live2DCubismCore STATIC IMPORTED)
set_target_properties(Live2DCubismCore
  PROPERTIES
    IMPORTED_LOCATION
      ${CORE_PATH}/lib/android/${ANDROID_ABI}/libLive2DCubismCore.a
    INTERFACE_INCLUDE_DIRECTORIES ${CORE_PATH}/include
)

# ============================================================
# Add Cubism Native Framework
# ============================================================
set(FRAMEWORK_SOURCE OpenGL)

# Framework source is already in our project tree, but we need
# to reference the original SDK Framework for the CMake build.
# We use the local copy under framework/ for the build.
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/framework ${CMAKE_CURRENT_BINARY_DIR}/Framework)
target_compile_definitions(Framework PUBLIC CSM_TARGET_ANDROID_ES2)

# ============================================================
# Main library target
# ============================================================
add_library(${APP_NAME} SHARED)

# ============================================================
# Common source files (from Samples/Common)
# ============================================================
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/Common ${CMAKE_CURRENT_BINARY_DIR}/src/Common)

# ============================================================
# App-specific source files
# ============================================================
target_sources(${APP_NAME}
  PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/JniBridgeC.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/JniBridgeC.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppDefine.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppDefine.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppMinimumDelegate.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppMinimumDelegate.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppMinimumLive2DManager.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppMinimumLive2DManager.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppMinimumModel.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppMinimumModel.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppPal.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppPal.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppSprite.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppSprite.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppSpriteShader.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppSpriteShader.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppTextureManager.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppTextureManager.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppMinimumView.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/LAppMinimumView.hpp
)

# ============================================================
# Include directories
# ============================================================
target_include_directories(${APP_NAME} PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_include_directories(${APP_NAME} PRIVATE ${STB_PATH})

# ============================================================
# Link libraries
# ============================================================
target_link_libraries(${APP_NAME}
  Framework
  Live2DCubismCore
  GLESv2
  log
  android
)

# ============================================================
# Android 16KB page-size option
# ============================================================
target_link_options(${APP_NAME} PRIVATE "-Wl,-z,max-page-size=16384")