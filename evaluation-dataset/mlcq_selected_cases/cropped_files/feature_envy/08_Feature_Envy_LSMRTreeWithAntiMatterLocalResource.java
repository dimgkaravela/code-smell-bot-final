// MLCQ cropped selected sample
// Original local source: feature_envy/08_Feature_Envy_LSMRTreeWithAntiMatterLocalResource.java
// Original target local lines: 108-121
// Crop local lines: 78-151

            ILSMOperationTrackerFactory opTrackerProvider, ILSMIOOperationCallbackFactory ioOpCallbackFactory,
            IMetadataPageManagerFactory metadataPageManagerFactory, IVirtualBufferCacheProvider vbcProvider,
            ILSMIOOperationSchedulerProvider ioSchedulerProvider, ILSMMergePolicyFactory mergePolicyFactory,
            Map<String, String> mergePolicyProperties, IBinaryComparatorFactory[] btreeCmpFactories,
            IPrimitiveValueProviderFactory[] valueProviderFactories, RTreePolicyType rtreePolicyType,
            ILinearizeComparatorFactory linearizeCmpFactory, int[] rtreeFields, boolean isPointMBR, boolean durable) {
        super(path, storageManager, typeTraits, rtreeCmpFactories, filterTypeTraits, filterCmpFactories, filterFields,
                opTrackerProvider, ioOpCallbackFactory, metadataPageManagerFactory, vbcProvider, ioSchedulerProvider,
                mergePolicyFactory, mergePolicyProperties, durable);
        this.btreeCmpFactories = btreeCmpFactories;
        this.valueProviderFactories = valueProviderFactories;
        this.rtreePolicyType = rtreePolicyType;
        this.linearizeCmpFactory = linearizeCmpFactory;
        this.rtreeFields = rtreeFields;
        this.isPointMBR = isPointMBR;
    }

    private LSMRTreeWithAntiMatterLocalResource(IPersistedResourceRegistry registry, JsonNode json,
            IBinaryComparatorFactory[] btreeCmpFactories, IPrimitiveValueProviderFactory[] valueProviderFactories,
            RTreePolicyType rtreePolicyType, ILinearizeComparatorFactory linearizeCmpFactory, int[] rtreeFields,
            boolean isPointMBR) throws HyracksDataException {
        super(registry, json);
        this.btreeCmpFactories = btreeCmpFactories;
        this.valueProviderFactories = valueProviderFactories;
        this.rtreePolicyType = rtreePolicyType;
        this.linearizeCmpFactory = linearizeCmpFactory;
        this.rtreeFields = rtreeFields;
        this.isPointMBR = isPointMBR;
    }

    @Override
    public ILSMIndex createInstance(INCServiceContext serviceCtx) throws HyracksDataException {
        IIOManager ioManager = serviceCtx.getIoManager();
        FileReference file = ioManager.resolve(path);
        List<IVirtualBufferCache> virtualBufferCaches = vbcProvider.getVirtualBufferCaches(serviceCtx, file);
        ioOpCallbackFactory.initialize(serviceCtx, this);
        return LSMRTreeUtils.createLSMTreeWithAntiMatterTuples(ioManager, virtualBufferCaches, file,
                storageManager.getBufferCache(serviceCtx), typeTraits, cmpFactories, btreeCmpFactories,
                valueProviderFactories, rtreePolicyType,
                mergePolicyFactory.createMergePolicy(mergePolicyProperties, serviceCtx),
                opTrackerProvider.getOperationTracker(serviceCtx, this), ioSchedulerProvider.getIoScheduler(serviceCtx),
                ioOpCallbackFactory, linearizeCmpFactory, rtreeFields, filterTypeTraits, filterCmpFactories,
                filterFields, durable, isPointMBR, metadataPageManagerFactory);
    }

    @Override
    public JsonNode toJson(IPersistedResourceRegistry registry) throws HyracksDataException {
        ObjectNode jsonObject = registry.getClassIdentifier(getClass(), serialVersionUID);
        super.appendToJson(jsonObject, registry);
        ArrayNode btreeCmpFactoriesArray = OBJECT_MAPPER.createArrayNode();
        for (IBinaryComparatorFactory factory : btreeCmpFactories) {
            btreeCmpFactoriesArray.add(factory.toJson(registry));
        }
        jsonObject.set("btreeCmpFactories", btreeCmpFactoriesArray);
        jsonObject.set("linearizeCmpFactory", linearizeCmpFactory.toJson(registry));

        final ArrayNode valueProviderFactoriesArray = OBJECT_MAPPER.createArrayNode();
        for (IPrimitiveValueProviderFactory factory : valueProviderFactories) {
            valueProviderFactoriesArray.add(factory.toJson(registry));
        }
        jsonObject.set("valueProviderFactories", valueProviderFactoriesArray);
        jsonObject.set("rtreePolicyType", rtreePolicyType.toJson(registry));
        jsonObject.putPOJO("rtreeFields", rtreeFields);
        jsonObject.put("isPointMBR", isPointMBR);
        return jsonObject;
    }

    public static IJsonSerializable fromJson(IPersistedResourceRegistry registry, JsonNode json)
            throws HyracksDataException {
        final int[] rtreeFields = OBJECT_MAPPER.convertValue(json.get("rtreeFields"), int[].class);
        final boolean isPointMBR = json.get("isPointMBR").asBoolean();
        final RTreePolicyType rtreePolicyType = (RTreePolicyType) registry.deserialize(json.get("rtreePolicyType"));
        final ILinearizeComparatorFactory linearizeCmpFactory =
                (ILinearizeComparatorFactory) registry.deserialize(json.get("linearizeCmpFactory"));
