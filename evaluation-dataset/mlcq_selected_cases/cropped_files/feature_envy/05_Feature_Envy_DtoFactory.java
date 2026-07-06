// MLCQ cropped selected sample
// Original local source: feature_envy/05_Feature_Envy_DtoFactory.java
// Original target local lines: 3122-3197
// Crop local lines: 3092-3227

                        nodeDtos.add(createProvenanceEventNodeDTO((ProvenanceEventLineageNode) node));
                        break;
                }
            }
        }
        resultsDto.setNodes(nodeDtos);

        // include any errors
        if (results.getError() != null) {
            final Set<String> errors = new HashSet<>();
            errors.add(results.getError());
            resultsDto.setErrors(errors);
        }

        // create the link dto's
        final List<ProvenanceLinkDTO> linkDtos = new ArrayList<>();
        for (final LineageEdge edge : edges) {
            linkDtos.add(createProvenanceLinkDTO(edge));
        }
        resultsDto.setLinks(linkDtos);

        return dto;
    }

    /**
     * Creates a SystemDiagnosticsDTO for the specified SystemDiagnostics.
     *
     * @param sysDiagnostics diags
     * @return dto
     */
    public SystemDiagnosticsDTO createSystemDiagnosticsDto(final SystemDiagnostics sysDiagnostics) {

        final SystemDiagnosticsDTO dto = new SystemDiagnosticsDTO();
        final SystemDiagnosticsSnapshotDTO snapshot = new SystemDiagnosticsSnapshotDTO();
        dto.setAggregateSnapshot(snapshot);

        snapshot.setStatsLastRefreshed(new Date(sysDiagnostics.getCreationTimestamp()));

        // processors
        snapshot.setAvailableProcessors(sysDiagnostics.getAvailableProcessors());
        snapshot.setProcessorLoadAverage(sysDiagnostics.getProcessorLoadAverage());

        // threads
        snapshot.setDaemonThreads(sysDiagnostics.getDaemonThreads());
        snapshot.setTotalThreads(sysDiagnostics.getTotalThreads());

        // heap
        snapshot.setMaxHeap(FormatUtils.formatDataSize(sysDiagnostics.getMaxHeap()));
        snapshot.setMaxHeapBytes(sysDiagnostics.getMaxHeap());
        snapshot.setTotalHeap(FormatUtils.formatDataSize(sysDiagnostics.getTotalHeap()));
        snapshot.setTotalHeapBytes(sysDiagnostics.getTotalHeap());
        snapshot.setUsedHeap(FormatUtils.formatDataSize(sysDiagnostics.getUsedHeap()));
        snapshot.setUsedHeapBytes(sysDiagnostics.getUsedHeap());
        snapshot.setFreeHeap(FormatUtils.formatDataSize(sysDiagnostics.getFreeHeap()));
        snapshot.setFreeHeapBytes(sysDiagnostics.getFreeHeap());
        if (sysDiagnostics.getHeapUtilization() != -1) {
            snapshot.setHeapUtilization(FormatUtils.formatUtilization(sysDiagnostics.getHeapUtilization()));
        }

        // non heap
        snapshot.setMaxNonHeap(FormatUtils.formatDataSize(sysDiagnostics.getMaxNonHeap()));
        snapshot.setMaxNonHeapBytes(sysDiagnostics.getMaxNonHeap());
        snapshot.setTotalNonHeap(FormatUtils.formatDataSize(sysDiagnostics.getTotalNonHeap()));
        snapshot.setTotalNonHeapBytes(sysDiagnostics.getTotalNonHeap());
        snapshot.setUsedNonHeap(FormatUtils.formatDataSize(sysDiagnostics.getUsedNonHeap()));
        snapshot.setUsedNonHeapBytes(sysDiagnostics.getUsedNonHeap());
        snapshot.setFreeNonHeap(FormatUtils.formatDataSize(sysDiagnostics.getFreeNonHeap()));
        snapshot.setFreeNonHeapBytes(sysDiagnostics.getFreeNonHeap());
        if (sysDiagnostics.getNonHeapUtilization() != -1) {
            snapshot.setNonHeapUtilization(FormatUtils.formatUtilization(sysDiagnostics.getNonHeapUtilization()));
        }

        // flow file disk usage
        final SystemDiagnosticsSnapshotDTO.StorageUsageDTO flowFileRepositoryStorageUsageDto = createStorageUsageDTO(null, sysDiagnostics.getFlowFileRepositoryStorageUsage());
        snapshot.setFlowFileRepositoryStorageUsage(flowFileRepositoryStorageUsageDto);

        // content disk usage
        final Set<SystemDiagnosticsSnapshotDTO.StorageUsageDTO> contentRepositoryStorageUsageDtos = new LinkedHashSet<>();
        snapshot.setContentRepositoryStorageUsage(contentRepositoryStorageUsageDtos);
        for (final Map.Entry<String, StorageUsage> entry : sysDiagnostics.getContentRepositoryStorageUsage().entrySet()) {
            contentRepositoryStorageUsageDtos.add(createStorageUsageDTO(entry.getKey(), entry.getValue()));
        }

        // provenance disk usage
        final Set<SystemDiagnosticsSnapshotDTO.StorageUsageDTO> provenanceRepositoryStorageUsageDtos = new LinkedHashSet<>();
        snapshot.setProvenanceRepositoryStorageUsage(provenanceRepositoryStorageUsageDtos);
        for (final Map.Entry<String, StorageUsage> entry : sysDiagnostics.getProvenanceRepositoryStorageUsage().entrySet()) {
            provenanceRepositoryStorageUsageDtos.add(createStorageUsageDTO(entry.getKey(), entry.getValue()));
        }

        // garbage collection
        final Set<SystemDiagnosticsSnapshotDTO.GarbageCollectionDTO> garbageCollectionDtos = new LinkedHashSet<>();
        snapshot.setGarbageCollection(garbageCollectionDtos);
        for (final Map.Entry<String, GarbageCollection> entry : sysDiagnostics.getGarbageCollection().entrySet()) {
            garbageCollectionDtos.add(createGarbageCollectionDTO(entry.getKey(), entry.getValue()));
        }

        // version info
        final SystemDiagnosticsSnapshotDTO.VersionInfoDTO versionInfoDto = createVersionInfoDTO();
        snapshot.setVersionInfo(versionInfoDto);

        // uptime
        snapshot.setUptime(FormatUtils.formatHoursMinutesSeconds(sysDiagnostics.getUptime(), TimeUnit.MILLISECONDS));

        return dto;
    }

    /**
     * Creates a StorageUsageDTO from the specified StorageUsage.
     *
     * @param identifier id
     * @param storageUsage usage
     * @return dto
     */
    public SystemDiagnosticsSnapshotDTO.StorageUsageDTO createStorageUsageDTO(final String identifier, final StorageUsage storageUsage) {
        final SystemDiagnosticsSnapshotDTO.StorageUsageDTO dto = new SystemDiagnosticsSnapshotDTO.StorageUsageDTO();
        dto.setIdentifier(identifier);
        dto.setFreeSpace(FormatUtils.formatDataSize(storageUsage.getFreeSpace()));
        dto.setTotalSpace(FormatUtils.formatDataSize(storageUsage.getTotalSpace()));
        dto.setUsedSpace(FormatUtils.formatDataSize(storageUsage.getUsedSpace()));
        dto.setFreeSpaceBytes(storageUsage.getFreeSpace());
        dto.setTotalSpaceBytes(storageUsage.getTotalSpace());
        dto.setUsedSpaceBytes(storageUsage.getUsedSpace());
        dto.setUtilization(FormatUtils.formatUtilization(storageUsage.getDiskUtilization()));
        return dto;
    }

    /**
     * Creates a GarbageCollectionDTO from the specified GarbageCollection.
     *
     * @param name name
     * @param garbageCollection gc
     * @return dto
     */
    public SystemDiagnosticsSnapshotDTO.GarbageCollectionDTO createGarbageCollectionDTO(final String name, final GarbageCollection garbageCollection) {
        final SystemDiagnosticsSnapshotDTO.GarbageCollectionDTO dto = new SystemDiagnosticsSnapshotDTO.GarbageCollectionDTO();
