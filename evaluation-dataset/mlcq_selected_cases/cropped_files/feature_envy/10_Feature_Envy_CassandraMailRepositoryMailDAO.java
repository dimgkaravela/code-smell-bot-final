// MLCQ cropped selected sample
// Original local source: feature_envy/10_Feature_Envy_CassandraMailRepositoryMailDAO.java
// Original target local lines: 269-277
// Crop local lines: 239-298

            .collect(Guavate.toImmutableMap(
                pair -> pair.getLeft().asString(),
                pair -> toByteBuffer((Serializable) pair.getRight().value())));
    }

    private ImmutableMap<String, UDTValue> toHeaderMap(PerRecipientHeaders perRecipientHeaders) {
        return perRecipientHeaders.getHeadersByRecipient()
            .asMap()
            .entrySet()
            .stream()
            .flatMap(entry -> entry.getValue().stream().map(value -> Pair.of(entry.getKey(), value)))
            .map(entry -> Pair.of(entry.getKey().asString(),
                cassandraTypesProvider.getDefinedUserType(HEADER_TYPE)
                    .newValue()
                    .setString(HEADER_NAME, entry.getRight().getName())
                    .setString(HEADER_VALUE, entry.getRight().getValue())))
            .collect(Guavate.toImmutableMap(Pair::getLeft, Pair::getRight));
    }

    private PerRecipientHeaders fromHeaderMap(Map<String, UDTValue> rawMap) {
        PerRecipientHeaders result = new PerRecipientHeaders();

        rawMap.forEach((key, value) -> result.addHeaderForRecipient(PerRecipientHeaders.Header.builder()
                .name(value.getString(HEADER_NAME))
                .value(value.getString(HEADER_VALUE))
                .build(),
            toMailAddress(key)));
        return result;
    }

    private ByteBuffer toByteBuffer(Serializable serializable) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            new ObjectOutputStream(outputStream).writeObject(serializable);
            return ByteBuffer.wrap(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private AttributeValue<?> fromByteBuffer(ByteBuffer byteBuffer) {
        try {
            byte[] data = new byte[byteBuffer.remaining()];
            byteBuffer.get(data);
            ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(data));
            return AttributeValue.ofAny(objectInputStream.readObject());
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private MailAddress toMailAddress(String rawValue) {
        try {
            return new MailAddress(rawValue);
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }

}
