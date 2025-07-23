fn main() -> Result<(), Box<dyn std::error::Error>> {
    tonic_build::configure()
        .build_server(true)
        .compile(
            &[
                "../../shared/proto/storage.proto",
                "../../shared/proto/backup.proto", // Compile other protos for potential future use
            ],
            &["../../shared/proto"], // Specify proto include path
        )?;
    Ok(())
}