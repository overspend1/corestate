use reed_solomon_erasure::ReedSolomon;
use blake3::hash as calculate_blake3;

// --- Custom Error and Data Structures ---

#[derive(Debug)]
pub enum ErasureError {
    InsufficientShards,
    EncodingError(reed_solomon_erasure::Error),
}

impl From<reed_solomon_erasure::Error> for ErasureError {
    fn from(e: reed_solomon_erasure::Error) -> Self {
        ErasureError::EncodingError(e)
    }
}

#[derive(PartialEq, Debug)]
pub enum ShardType {
    Data,
    Parity,
}

#[derive(Debug)]
pub struct Shard {
    pub index: usize,
    pub data: Vec<u8>,
    pub checksum: blake3::Hash,
    pub shard_type: ShardType,
}

// --- ErasureCoder Implementation ---

pub struct ErasureCoder {
    data_shards: usize,
    parity_shards: usize,
    encoder: ReedSolomon,
}

impl ErasureCoder {
    pub fn new(data_shards: usize, parity_shards: usize) -> Result<Self, ErasureError> {
        let encoder = ReedSolomon::new(data_shards, parity_shards)?;
        Ok(Self {
            data_shards,
            parity_shards,
            encoder,
        })
    }
    
    pub fn encode_backup(&self, data: &[u8]) -> Result<Vec<Shard>, ErasureError> {
        let shard_size = (data.len() + self.data_shards - 1) / self.data_shards;
        let mut shards_data: Vec<Vec<u8>> = vec![vec![0u8; shard_size]; self.data_shards + self.parity_shards];
        
        for (i, chunk) in data.chunks(shard_size).enumerate() {
            shards_data[i][..chunk.len()].copy_from_slice(chunk);
        }
        
        self.encoder.encode(&mut shards_data)?;
        
        Ok(shards_data.into_iter().enumerate().map(|(index, data)| {
            Shard {
                index,
                checksum: calculate_blake3(&data),
                shard_type: if index < self.data_shards {
                    ShardType::Data
                } else {
                    ShardType::Parity
                },
                data,
            }
        }).collect())
    }
    
    pub fn reconstruct_backup(&self, available_shards: Vec<Option<Shard>>) -> Result<Vec<u8>, ErasureError> {
        if available_shards.iter().filter(|s| s.is_some()).count() < self.data_shards {
            return Err(ErasureError::InsufficientShards);
        }
        
        let mut shards_data: Vec<Option<Vec<u8>>> = available_shards
            .into_iter()
            .map(|shard| shard.map(|s| s.data))
            .collect();
        
        self.encoder.reconstruct(&mut shards_data)?;
        
        let mut result = Vec::new();
        for shard_opt in shards_data.iter().take(self.data_shards) {
            if let Some(data) = shard_opt {
                result.extend_from_slice(data);
            } else {
                // This should not happen if reconstruction was successful
                return Err(ErasureError::InsufficientShards);
            }
        }
        
        Ok(result)
    }
}