use anyhow::{anyhow, Result};
use serde::{Deserialize, Serialize};
use std::io::{Read, Write};

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
pub enum CompressionType {
    Zstd,
    Lz4,
    Brotli,
    Gzip,
    Xz,
}

impl CompressionType {
    pub fn from_str(s: &str) -> Result<Self> {
        match s.to_lowercase().as_str() {
            "zstd" => Ok(CompressionType::Zstd),
            "lz4" => Ok(CompressionType::Lz4),
            "brotli" => Ok(CompressionType::Brotli),
            "gzip" => Ok(CompressionType::Gzip),
            "xz" => Ok(CompressionType::Xz),
            _ => Err(anyhow!("Unknown compression type: {}", s)),
        }
    }

    pub fn as_str(&self) -> &'static str {
        match self {
            CompressionType::Zstd => "zstd",
            CompressionType::Lz4 => "lz4",
            CompressionType::Brotli => "brotli",
            CompressionType::Gzip => "gzip",
            CompressionType::Xz => "xz",
        }
    }
}

pub fn compress_data(data: &[u8], compression_type: CompressionType) -> Result<Vec<u8>> {
    match compression_type {
        CompressionType::Zstd => compress_zstd(data),
        CompressionType::Lz4 => compress_lz4(data),
        CompressionType::Brotli => compress_brotli(data),
        CompressionType::Gzip => compress_gzip(data),
        CompressionType::Xz => compress_xz(data),
    }
}

pub fn decompress_data(data: &[u8], compression_type: CompressionType) -> Result<Vec<u8>> {
    match compression_type {
        CompressionType::Zstd => decompress_zstd(data),
        CompressionType::Lz4 => decompress_lz4(data),
        CompressionType::Brotli => decompress_brotli(data),
        CompressionType::Gzip => decompress_gzip(data),
        CompressionType::Xz => decompress_xz(data),
    }
}

fn compress_zstd(data: &[u8]) -> Result<Vec<u8>> {
    let compressed = zstd::encode_all(data, 3)?;
    Ok(compressed)
}

fn decompress_zstd(data: &[u8]) -> Result<Vec<u8>> {
    let decompressed = zstd::decode_all(data)?;
    Ok(decompressed)
}

fn compress_lz4(data: &[u8]) -> Result<Vec<u8>> {
    let compressed = lz4::block::compress(data, Some(lz4::block::CompressionMode::HIGHCOMPRESSION(9)), true)?;
    Ok(compressed)
}

fn decompress_lz4(data: &[u8]) -> Result<Vec<u8>> {
    let decompressed = lz4::block::decompress(data, None)?;
    Ok(decompressed)
}

fn compress_brotli(data: &[u8]) -> Result<Vec<u8>> {
    let mut compressed = Vec::new();
    let mut encoder = brotli::CompressorWriter::new(&mut compressed, 4096, 6, 22);
    encoder.write_all(data)?;
    drop(encoder);
    Ok(compressed)
}

fn decompress_brotli(data: &[u8]) -> Result<Vec<u8>> {
    let mut decompressed = Vec::new();
    let mut decoder = brotli::Decompressor::new(data, 4096);
    decoder.read_to_end(&mut decompressed)?;
    Ok(decompressed)
}

fn compress_gzip(data: &[u8]) -> Result<Vec<u8>> {
    use flate2::{write::GzEncoder, Compression};
    let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
    encoder.write_all(data)?;
    let compressed = encoder.finish()?;
    Ok(compressed)
}

fn decompress_gzip(data: &[u8]) -> Result<Vec<u8>> {
    use flate2::read::GzDecoder;
    let mut decoder = GzDecoder::new(data);
    let mut decompressed = Vec::new();
    decoder.read_to_end(&mut decompressed)?;
    Ok(decompressed)
}

fn compress_xz(data: &[u8]) -> Result<Vec<u8>> {
    let mut compressed = Vec::new();
    let mut encoder = xz2::write::XzEncoder::new(&mut compressed, 6);
    encoder.write_all(data)?;
    encoder.finish()?;
    Ok(compressed)
}

fn decompress_xz(data: &[u8]) -> Result<Vec<u8>> {
    let mut decoder = xz2::read::XzDecoder::new(data);
    let mut decompressed = Vec::new();
    decoder.read_to_end(&mut decompressed)?;
    Ok(decompressed)
}

pub fn get_compression_ratio(original_size: usize, compressed_size: usize) -> f64 {
    if original_size == 0 {
        return 0.0;
    }
    (original_size as f64 - compressed_size as f64) / original_size as f64
}

pub fn choose_best_compression(data: &[u8]) -> Result<(CompressionType, Vec<u8>)> {
    let types = [
        CompressionType::Zstd,
        CompressionType::Lz4,
        CompressionType::Brotli,
        CompressionType::Gzip,
    ];

    let mut best_type = CompressionType::Zstd;
    let mut best_compressed = compress_data(data, best_type)?;
    let mut best_ratio = get_compression_ratio(data.len(), best_compressed.len());

    for &compression_type in &types[1..] {
        let compressed = compress_data(data, compression_type)?;
        let ratio = get_compression_ratio(data.len(), compressed.len());
        
        if ratio > best_ratio {
            best_type = compression_type;
            best_compressed = compressed;
            best_ratio = ratio;
        }
    }

    Ok((best_type, best_compressed))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_zstd_compression() {
        let data = b"Hello, World! This is a test string for compression.";
        let compressed = compress_zstd(data).unwrap();
        let decompressed = decompress_zstd(&compressed).unwrap();
        assert_eq!(data, decompressed.as_slice());
    }

    #[test]
    fn test_all_compression_types() {
        let data = b"The quick brown fox jumps over the lazy dog. ".repeat(100);
        
        for compression_type in [
            CompressionType::Zstd,
            CompressionType::Lz4,
            CompressionType::Brotli,
            CompressionType::Gzip,
            CompressionType::Xz,
        ] {
            let compressed = compress_data(&data, compression_type).unwrap();
            let decompressed = decompress_data(&compressed, compression_type).unwrap();
            assert_eq!(data, decompressed);
            assert!(compressed.len() < data.len()); // Should compress well
        }
    }

    #[test]
    fn test_best_compression_choice() {
        let data = b"This is a repetitive string. ".repeat(50);
        let (best_type, compressed) = choose_best_compression(&data).unwrap();
        let decompressed = decompress_data(&compressed, best_type).unwrap();
        assert_eq!(data, decompressed.as_slice());
    }
}