// MLCQ cropped selected sample
// Original local source: feature_envy/07_Feature_Envy_Builder.java
// Original target local lines: 115-128
// Crop local lines: 85-158

        private ResetPeriod resetPeriod;

        public Builder resetPeriod(ResetPeriod resetPeriod) {
            this.resetPeriod = resetPeriod;
            this.__explicitlySet__.add("resetPeriod");
            return this;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("freeformTags")
        private java.util.Map<String, String> freeformTags;

        public Builder freeformTags(java.util.Map<String, String> freeformTags) {
            this.freeformTags = freeformTags;
            this.__explicitlySet__.add("freeformTags");
            return this;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("definedTags")
        private java.util.Map<String, java.util.Map<String, Object>> definedTags;

        public Builder definedTags(
                java.util.Map<String, java.util.Map<String, Object>> definedTags) {
            this.definedTags = definedTags;
            this.__explicitlySet__.add("definedTags");
            return this;
        }

        @com.fasterxml.jackson.annotation.JsonIgnore
        private final java.util.Set<String> __explicitlySet__ = new java.util.HashSet<String>();

        public CreateBudgetDetails build() {
            CreateBudgetDetails __instance__ =
                    new CreateBudgetDetails(
                            compartmentId,
                            targetCompartmentId,
                            displayName,
                            description,
                            amount,
                            resetPeriod,
                            freeformTags,
                            definedTags);
            __instance__.__explicitlySet__.addAll(__explicitlySet__);
            return __instance__;
        }

        @com.fasterxml.jackson.annotation.JsonIgnore
        public Builder copy(CreateBudgetDetails o) {
            Builder copiedBuilder =
                    compartmentId(o.getCompartmentId())
                            .targetCompartmentId(o.getTargetCompartmentId())
                            .displayName(o.getDisplayName())
                            .description(o.getDescription())
                            .amount(o.getAmount())
                            .resetPeriod(o.getResetPeriod())
                            .freeformTags(o.getFreeformTags())
                            .definedTags(o.getDefinedTags());

            copiedBuilder.__explicitlySet__.retainAll(o.__explicitlySet__);
            return copiedBuilder;
        }
    }

    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The OCID of the compartment
     **/
    @com.fasterxml.jackson.annotation.JsonProperty("compartmentId")
    String compartmentId;
