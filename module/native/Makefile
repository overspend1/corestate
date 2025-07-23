# CoreState Kernel Module Makefile

MODULE_NAME := corestate
obj-m := $(MODULE_NAME).o
$(MODULE_NAME)-objs := corestate_module.o

# Kernel build directory (adapt for different Android versions)
KERNEL_DIR ?= /lib/modules/$(shell uname -r)/build
ANDROID_KERNEL_DIR ?= /android/kernel

# Architecture specific settings
ARCH ?= arm64
CROSS_COMPILE ?= aarch64-linux-android-

# Android specific paths
ANDROID_NDK_PATH ?= /opt/android-ndk
ANDROID_PLATFORM ?= 29

# Compiler flags
ccflags-y := -Wall -Wextra -std=gnu99
ccflags-y += -DDEBUG
ccflags-y += -I$(src)/include
ccflags-y += -DCORESTATE_VERSION=\"2.0.0\"

# Build for current kernel (development)
all: modules

modules:
	$(MAKE) -C $(KERNEL_DIR) M=$(PWD) modules

clean:
	$(MAKE) -C $(KERNEL_DIR) M=$(PWD) clean
	rm -f *.ko *.o *.mod.c *.mod *.order *.symvers

# Build for Android kernel
android: KERNEL_DIR := $(ANDROID_KERNEL_DIR)
android: ARCH := arm64
android: CROSS_COMPILE := aarch64-linux-android-
android: ccflags-y += -DANDROID_BUILD
android: modules

# Build for Android x86_64 (emulator)
android-x86: ARCH := x86_64
android-x86: CROSS_COMPILE := x86_64-linux-android-
android-x86: ccflags-y += -DANDROID_BUILD -DANDROID_X86
android-x86: modules

# Install module (requires root)
install: modules
	sudo cp $(MODULE_NAME).ko /lib/modules/$(shell uname -r)/extra/
	sudo depmod -a
	@echo "Module installed. Load with: sudo modprobe $(MODULE_NAME)"

# Uninstall module
uninstall:
	sudo rm -f /lib/modules/$(shell uname -r)/extra/$(MODULE_NAME).ko
	sudo depmod -a
	@echo "Module uninstalled"

# Load module for development
load: modules
	sudo insmod $(MODULE_NAME).ko
	@echo "Module loaded. Check dmesg for output."

# Unload module
unload:
	sudo rmmod $(MODULE_NAME)
	@echo "Module unloaded"

# Build module for Android using NDK
android-ndk:
	$(ANDROID_NDK_PATH)/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android$(ANDROID_PLATFORM)-clang \
		-I$(ANDROID_KERNEL_DIR)/include \
		-I$(ANDROID_KERNEL_DIR)/arch/arm64/include \
		-D__KERNEL__ \
		-DMODULE \
		-DCORESTATE_VERSION=\"2.0.0\" \
		-DANDROID_BUILD \
		-Wall -Wextra \
		-nostdlib \
		-c corestate_module.c -o corestate_module.o

# Package for Android deployment
android-package: android
	mkdir -p android-package/system/lib/modules
	cp $(MODULE_NAME).ko android-package/system/lib/modules/
	echo "#!/system/bin/sh" > android-package/install.sh
	echo "mount -o remount,rw /system" >> android-package/install.sh
	echo "cp /sdcard/$(MODULE_NAME).ko /system/lib/modules/" >> android-package/install.sh
	echo "chmod 644 /system/lib/modules/$(MODULE_NAME).ko" >> android-package/install.sh
	echo "echo '$(MODULE_NAME)' >> /system/etc/modules.load" >> android-package/install.sh
	echo "mount -o remount,ro /system" >> android-package/install.sh
	echo "echo 'Module installed. Reboot required.'" >> android-package/install.sh
	chmod +x android-package/install.sh
	cd android-package && tar czf ../corestate-module-android.tar.gz *

# KernelSU integration
kernelsu: ccflags-y += -DKERNELSU_INTEGRATION
kernelsu: ccflags-y += -I$(KERNELSU_DIR)/kernel
kernelsu: modules

# Debug build
debug: ccflags-y += -DDEBUG_VERBOSE -g
debug: modules

# Test the module
test: load
	@echo "Testing CoreState module..."
	@echo "activate" | sudo tee /proc/corestate > /dev/null
	@echo "enable_cow" | sudo tee /proc/corestate > /dev/null
	@echo "enable_snapshots" | sudo tee /proc/corestate > /dev/null
	@echo "create_snapshot /data" | sudo tee /proc/corestate > /dev/null
	@echo "Module status:"
	@cat /proc/corestate
	@echo "Test completed. Check output above."

# Help
help:
	@echo "CoreState Kernel Module Build System"
	@echo ""
	@echo "Targets:"
	@echo "  all               - Build module for current kernel"
	@echo "  modules           - Same as 'all'"
	@echo "  android           - Build for Android ARM64"
	@echo "  android-x86       - Build for Android x86_64 (emulator)"
	@echo "  android-ndk       - Build using Android NDK"
	@echo "  android-package   - Create Android deployment package"
	@echo "  kernelsu          - Build with KernelSU integration"
	@echo "  debug             - Build with debug symbols"
	@echo "  clean             - Clean build files"
	@echo "  install           - Install module (requires root)"
	@echo "  uninstall         - Remove installed module"
	@echo "  load              - Load module for testing"
	@echo "  unload            - Unload module"
	@echo "  test              - Load and test module functionality"
	@echo "  help              - Show this help"
	@echo ""
	@echo "Variables:"
	@echo "  KERNEL_DIR        - Kernel build directory"
	@echo "  ANDROID_KERNEL_DIR- Android kernel directory"
	@echo "  ARCH              - Target architecture (arm64, x86_64)"
	@echo "  CROSS_COMPILE     - Cross compiler prefix"
	@echo "  ANDROID_NDK_PATH  - Android NDK installation path"
	@echo "  KERNELSU_DIR      - KernelSU source directory"

.PHONY: all modules clean android android-x86 android-ndk android-package kernelsu debug install uninstall load unload test help