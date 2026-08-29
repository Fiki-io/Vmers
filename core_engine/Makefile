CXX = g++
CXX_ARM64 = aarch64-linux-gnu-g++
CXXFLAGS = -std=c++20 -O2 -Wall -pthread

SOURCES = main.cpp property_service.cpp vmlink_loader.cpp
TARGET_HOST = vmers_engine_host
TARGET_ARM64 = vmers_engine_arm64

all: host arm64

host: $(SOURCES)
	$(CXX) $(CXXFLAGS) $(SOURCES) -o $(TARGET_HOST)
	@echo "[+] Built Host Binary: $(TARGET_HOST)"

arm64: $(SOURCES)
	$(CXX_ARM64) $(CXXFLAGS) -static-libstdc++ -static-libgcc $(SOURCES) -o $(TARGET_ARM64)
	@echo "[+] Built ARM64 Binary: $(TARGET_ARM64)"

clean:
	rm -f $(TARGET_HOST) $(TARGET_ARM64)
