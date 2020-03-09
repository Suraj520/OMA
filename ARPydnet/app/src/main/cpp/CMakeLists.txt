# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.4.1)

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add_library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.

add_library( # Specifies the name of the library.
             native-norm

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             native-norm.c)

add_library( # Specifies the name of the library.
        native-color

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        native-color.c)

#add_library( # Specifies the name of the library.
#        native-tflite-model
#
#        # Sets the library as a shared library.
#        SHARED
#
#        # Provides a relative path to your source file(s).
#        native-tflite-model.c)


#https://stackoverflow.com/questions/49834875/problems-with-using-tensorflow-lite-c-api-in-android-studio-project

# This will create a new "variable" holding the path to a directory
# where we will put our library and header files.
# Change this to your needs
set(distribution_DIR ${CMAKE_SOURCE_DIR}/dist)

# This states that there exists a shared library called libtensorflowLite
# which will be imported (means it is not built with the rest of the project!)
#add_library(libtensorflowlite_c SHARED IMPORTED)
#add_library(libtensorflowlite_gpu_delegate SHARED IMPORTED)

# This indicates where the libtensorflowLite.so for each architecture is found relative to our distribution directory
#set_target_properties(libtensorflowlite_c PROPERTIES IMPORTED_LOCATION
#        ${distribution_DIR}/libs/${ANDROID_ABI}/libtensorflowlite_c.so)
#
#set_target_properties(libtensorflowlite_gpu_delegate PROPERTIES IMPORTED_LOCATION
#        ${distribution_DIR}/libs/${ANDROID_ABI}/libtensorflowlite_gpu_delegate.so)

# This indicates where the header files are found relative to our distribution dir
#target_include_directories(native-tflite-model PRIVATE
#        ${distribution_DIR}/include)

# Finally, we make sure our libtensorflowLite.so is linked to our native-lib and loaded during runtime
#target_link_libraries( # Specifies the target library.
#        native-tflite-model
#        libtensorflowlite_c
#        libtensorflowlite_gpu_delegate
#        # Links the target library to the log library
#        # included in the NDK.
#        ${log-lib} )