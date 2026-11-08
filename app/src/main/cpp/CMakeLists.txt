cmake_minimum_required(VERSION 3.22.1)
project("nativeapp")

add_library(nativeapp SHARED
        native-lib.cpp)

target_compile_features(nativeapp PUBLIC cxx_std_17)

find_library(log-lib log)
target_link_libraries(nativeapp ${log-lib})
