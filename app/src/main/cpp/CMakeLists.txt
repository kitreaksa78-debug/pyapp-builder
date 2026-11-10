cmake_minimum_required(VERSION 3.10.2)
project("pool-physics-engine")

add_library(
        pool-physics-engine
        SHARED
        physics-lib.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        pool-physics-engine
        ${log-lib})
