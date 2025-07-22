#include <iostream>
#include <vector>
#include <string>
#include <unordered_map>
#include <memory>
#include <shared_mutex>
#include <functional>
#include <chrono>

// --- Placeholder Implementations and Stubs ---

// A mock B+ Tree implementation for compilation
template<typename K, typename V>
class BPlusTree {
public:
    void insert(const K& key, const V& value) {}
    std::vector<V> range_query(std::function<bool(const V&)> predicate) {
        return {};
    }
};

// Mock system/utility functions
uint64_t get_current_timestamp() {
    return std::chrono::duration_cast<std::chrono::seconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();
}

uint32_t calculate_crc32(const void* data, size_t size) {
    return 0; // Placeholder
}

void trigger_incremental_backup() {
    std::cout << "Incremental backup triggered!" << std::endl;
}

// --- Main BlockLevelTracker Class ---

class BlockLevelTracker {
private:
    struct BlockInfo {
        uint64_t block_number;
        uint64_t last_modified;
        uint32_t checksum;
        bool is_dirty;
    };
    
    mutable std::shared_mutex tracker_mutex;
    std::unordered_map<uint64_t, BlockInfo> block_map;
    std::unique_ptr<BPlusTree<uint64_t, BlockInfo>> block_index;
    
    size_t dirty_block_count = 0;
    const size_t incremental_threshold = 1000; // Trigger after 1000 dirty blocks

public:
    BlockLevelTracker() : block_index(std::make_unique<BPlusTree<uint64_t, BlockInfo>>()) {}

    void track_write(uint64_t block_num, const void* data, size_t size) {
        std::unique_lock lock(tracker_mutex);
        
        BlockInfo& info = block_map[block_num];
        if (!info.is_dirty) {
            dirty_block_count++;
        }
        
        info.block_number = block_num;
        info.last_modified = get_current_timestamp();
        info.checksum = calculate_crc32(data, size);
        info.is_dirty = true;
        
        block_index->insert(block_num, info);
        
        if (dirty_block_count > incremental_threshold) {
            trigger_incremental_backup();
            dirty_block_count = 0; // Reset counter
        }
    }
    
    std::vector<BlockInfo> get_dirty_blocks(uint64_t since_timestamp) {
        std::shared_lock lock(tracker_mutex);
        return block_index->range_query(
            [since_timestamp](const BlockInfo& info) {
                return info.last_modified > since_timestamp && info.is_dirty;
            }
        );
    }
};