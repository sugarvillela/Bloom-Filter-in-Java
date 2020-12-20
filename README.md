# Bloom-Filter-in-Java
Keep exists/noExists status for a large amount of data in a small space.   

## Three files
* HashFactory
* BloomStore
* BloomCalculations

## HashFactory
* Uses Jenkins, Murmur and FNV hashes
## BloomStore
* 64-bit binary storage implementation
## BloomCalculations
* Calculates sizes, number of hashes etc. using the standard math
* Generates unique random sets of strings for false-positive trending
* Calculates best size from the random tests

## Additional Code
* This repo comes from SearchGen, which generates Java code for word searches
