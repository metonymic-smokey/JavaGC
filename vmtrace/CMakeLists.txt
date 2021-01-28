cmake_minimum_required(VERSION 3.0)
project (vmtrace)

# exports compile_commands.json to be used by your IDE for completion
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# ensure JAVA_HOME is set
if(NOT DEFINED ENV{JAVA_HOME})
    MESSAGE(FATAL_ERROR "JAVA_HOME not set")
endif()

set(CMAKE_CXX_FLAGS "-O2")

# include java stuff
if(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
  message("apple system.... using darwin")
  include_directories($ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/darwin)
endif()

if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
  message("linux system..... using linux")
  include_directories($ENV{JAVA_HOME}/include $ENV{JAVA_HOME}/include/linux)
endif()


# create shared library - will be saved as libvmtrace.so
add_library(vmtrace SHARED vmtrace.cpp)
