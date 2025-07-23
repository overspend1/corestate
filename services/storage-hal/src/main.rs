mod erasure_coding;

use tonic::{transport::Server, Request, Response, Status};
use erasure_coding::{ErasureCoder, ErasureError};

// Import generated protobuf code
// The actual name will be based on the package defined in the .proto file
pub mod storage {
    tonic::include_proto!("corestate.v2.storage");
}
use storage::{
    storage_hal_server::{StorageHal, StorageHalServer},
    EncodeRequest, EncodeResponse, ReconstructRequest, ReconstructResponse, Shard,
};

// --- gRPC Service Implementation ---

#[derive(Default)]
pub struct StorageHalService {}

#[tonic::async_trait]
impl StorageHal for StorageHalService {
    async fn encode(
        &self,
        request: Request<EncodeRequest>,
    ) -> Result<Response<EncodeResponse>, Status> {
        let req = request.into_inner();
        
        let coder = ErasureCoder::new(req.data_shards as usize, req.parity_shards as usize)
            .map_err(|e| Status::invalid_argument(format!("Failed to create encoder: {:?}", e)))?;
        
        let encoded_shards = coder.encode_backup(&req.data)
            .map_err(|e| Status::internal(format!("Encoding failed: {:?}", e)))?;

        let proto_shards = encoded_shards.into_iter().map(|s| Shard {
            index: s.index as u32,
            data: s.data,
            checksum: s.checksum.as_bytes().to_vec(),
            r#type: match s.shard_type {
                erasure_coding::ShardType::Data => 0,
                erasure_coding::ShardType::Parity => 1,
            },
        }).collect();

        Ok(Response::new(EncodeResponse { shards: proto_shards }))
    }

    async fn reconstruct(
        &self,
        request: Request<ReconstructRequest>,
    ) -> Result<Response<ReconstructResponse>, Status> {
        let req = request.into_inner();

        let coder = ErasureCoder::new(req.data_shards as usize, req.parity_shards as usize)
            .map_err(|e| Status::invalid_argument(format!("Failed to create encoder: {:?}", e)))?;

        let shards_to_reconstruct = req.shards.into_iter().map(|s| {
            Some(erasure_coding::Shard {
                index: s.index as usize,
                data: s.data,
                checksum: blake3::Hash::from_slice(&s.checksum),
                shard_type: match s.r#type {
                    0 => erasure_coding::ShardType::Data,
                    _ => erasure_coding::ShardType::Parity,
                },
            })
        }).collect();

        let reconstructed_data = coder.reconstruct_backup(shards_to_reconstruct)
             .map_err(|e| Status::internal(format!("Reconstruction failed: {:?}", e)))?;

        Ok(Response::new(ReconstructResponse { data: reconstructed_data }))
    }
}

// --- Server Entry Point ---

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let addr = "[::1]:50051".parse()?;
    let storage_service = StorageHalService::default();

    println!("StorageHAL gRPC server listening on {}", addr);

    Server::builder()
        .add_service(StorageHalServer::new(storage_service))
        .serve(addr)
        .await?;

    Ok(())
}