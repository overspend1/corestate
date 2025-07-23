use criterion::{black_box, criterion_group, criterion_main, Criterion, BenchmarkId};
use compression_engine::*;
use std::hint::black_box as hint_black_box;

// Sample data for benchmarking
fn generate_test_data(size: usize) -> Vec<u8> {
    let mut data = Vec::with_capacity(size);
    for i in 0..size {
        data.push((i % 256) as u8);
    }
    data
}

fn generate_random_data(size: usize) -> Vec<u8> {
    use std::collections::hash_map::DefaultHasher;
    use std::hash::{Hash, Hasher};
    
    let mut data = Vec::with_capacity(size);
    for i in 0..size {
        let mut hasher = DefaultHasher::new();
        i.hash(&mut hasher);
        data.push((hasher.finish() % 256) as u8);
    }
    data
}

fn generate_text_data(size: usize) -> Vec<u8> {
    let text = "The quick brown fox jumps over the lazy dog. ".repeat(size / 45 + 1);
    text.into_bytes()[..size.min(text.len())].to_vec()
}

// Benchmark compression algorithms
fn bench_zstd_compression(c: &mut Criterion) {
    let mut group = c.benchmark_group("zstd_compression");
    
    for size in [1024, 10240, 102400, 1048576].iter() {
        let data = generate_test_data(*size);
        
        group.bench_with_input(BenchmarkId::new("compress", size), &data, |b, data| {
            b.iter(|| {
                let compressed = zstd::encode_all(black_box(data.as_slice()), 3).unwrap();
                hint_black_box(compressed);
            });
        });
        
        // Benchmark decompression
        let compressed = zstd::encode_all(data.as_slice(), 3).unwrap();
        group.bench_with_input(BenchmarkId::new("decompress", size), &compressed, |b, compressed| {
            b.iter(|| {
                let decompressed = zstd::decode_all(black_box(compressed.as_slice())).unwrap();
                hint_black_box(decompressed);
            });
        });
    }
    
    group.finish();
}

fn bench_lz4_compression(c: &mut Criterion) {
    let mut group = c.benchmark_group("lz4_compression");
    
    for size in [1024, 10240, 102400, 1048576].iter() {
        let data = generate_test_data(*size);
        
        group.bench_with_input(BenchmarkId::new("compress", size), &data, |b, data| {
            b.iter(|| {
                let compressed = lz4::block::compress(
                    black_box(data.as_slice()), 
                    Some(lz4::block::CompressionMode::HIGHCOMPRESSION(12)), 
                    true
                ).unwrap();
                hint_black_box(compressed);
            });
        });
        
        // Benchmark decompression
        let compressed = lz4::block::compress(
            data.as_slice(),
            Some(lz4::block::CompressionMode::HIGHCOMPRESSION(12)),
            true
        ).unwrap();
        
        group.bench_with_input(BenchmarkId::new("decompress", size), &compressed, |b, compressed| {
            b.iter(|| {
                let decompressed = lz4::block::decompress(black_box(compressed.as_slice()), None).unwrap();
                hint_black_box(decompressed);
            });
        });
    }
    
    group.finish();
}

fn bench_brotli_compression(c: &mut Criterion) {
    let mut group = c.benchmark_group("brotli_compression");
    
    for size in [1024, 10240, 102400, 1048576].iter() {
        let data = generate_test_data(*size);
        
        group.bench_with_input(BenchmarkId::new("compress", size), &data, |b, data| {
            b.iter(|| {
                let mut compressed = Vec::new();
                let mut compressor = brotli::CompressorWriter::new(&mut compressed, 4096, 6, 20);
                std::io::Write::write_all(&mut compressor, black_box(data.as_slice())).unwrap();
                drop(compressor);
                hint_black_box(compressed);
            });
        });
    }
    
    group.finish();
}

fn bench_gzip_compression(c: &mut Criterion) {
    use flate2::{Compression, write::GzEncoder};
    use std::io::Write;
    
    let mut group = c.benchmark_group("gzip_compression");
    
    for size in [1024, 10240, 102400, 1048576].iter() {
        let data = generate_test_data(*size);
        
        group.bench_with_input(BenchmarkId::new("compress", size), &data, |b, data| {
            b.iter(|| {
                let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
                encoder.write_all(black_box(data.as_slice())).unwrap();
                let compressed = encoder.finish().unwrap();
                hint_black_box(compressed);
            });
        });
    }
    
    group.finish();
}

// Benchmark different data types
fn bench_data_types(c: &mut Criterion) {
    let mut group = c.benchmark_group("data_types");
    let size = 102400; // 100KB
    
    let sequential_data = generate_test_data(size);
    let random_data = generate_random_data(size);
    let text_data = generate_text_data(size);
    
    // Test ZSTD on different data types
    group.bench_function("zstd_sequential", |b| {
        b.iter(|| {
            let compressed = zstd::encode_all(black_box(sequential_data.as_slice()), 3).unwrap();
            hint_black_box(compressed);
        });
    });
    
    group.bench_function("zstd_random", |b| {
        b.iter(|| {
            let compressed = zstd::encode_all(black_box(random_data.as_slice()), 3).unwrap();
            hint_black_box(compressed);
        });
    });
    
    group.bench_function("zstd_text", |b| {
        b.iter(|| {
            let compressed = zstd::encode_all(black_box(text_data.as_slice()), 3).unwrap();
            hint_black_box(compressed);
        });
    });
    
    group.finish();
}

// Benchmark compression levels
fn bench_compression_levels(c: &mut Criterion) {
    let mut group = c.benchmark_group("compression_levels");
    let data = generate_text_data(102400); // 100KB of text data
    
    for level in [1, 3, 6, 9, 12].iter() {
        group.bench_with_input(BenchmarkId::new("zstd_level", level), level, |b, &level| {
            b.iter(|| {
                let compressed = zstd::encode_all(black_box(data.as_slice()), level).unwrap();
                hint_black_box(compressed);
            });
        });
    }
    
    group.finish();
}

// Benchmark parallel vs sequential compression
fn bench_parallel_compression(c: &mut Criterion) {
    let mut group = c.benchmark_group("parallel_vs_sequential");
    let data = generate_test_data(1048576); // 1MB
    let chunk_size = 8192; // 8KB chunks
    
    group.bench_function("sequential", |b| {
        b.iter(|| {
            let mut compressed_chunks = Vec::new();
            for chunk in data.chunks(chunk_size) {
                let compressed = zstd::encode_all(black_box(chunk), 3).unwrap();
                compressed_chunks.push(compressed);
            }
            hint_black_box(compressed_chunks);
        });
    });
    
    group.bench_function("parallel", |b| {
        b.iter(|| {
            use rayon::prelude::*;
            let compressed_chunks: Vec<_> = data
                .par_chunks(chunk_size)
                .map(|chunk| zstd::encode_all(chunk, 3).unwrap())
                .collect();
            hint_black_box(compressed_chunks);
        });
    });
    
    group.finish();
}

criterion_group!(
    compression_benches,
    bench_zstd_compression,
    bench_lz4_compression,
    bench_brotli_compression,
    bench_gzip_compression,
    bench_data_types,
    bench_compression_levels,
    bench_parallel_compression
);

criterion_main!(compression_benches);