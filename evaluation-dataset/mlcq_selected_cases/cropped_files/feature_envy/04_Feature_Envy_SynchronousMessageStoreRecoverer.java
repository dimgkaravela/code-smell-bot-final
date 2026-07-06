// MLCQ cropped selected sample
// Original local source: feature_envy/04_Feature_Envy_SynchronousMessageStoreRecoverer.java
// Original target local lines: 75-163
// Crop local lines: 45-193

import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.messages.MessageStoreMessages;
import org.apache.qpid.server.logging.messages.TransactionLogMessages;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.plugin.MessageMetaDataType;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.store.MessageEnqueueRecord;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.store.StoredMessage;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.store.Transaction.EnqueueRecord;
import org.apache.qpid.server.store.handler.DistributedTransactionHandler;
import org.apache.qpid.server.store.handler.MessageHandler;
import org.apache.qpid.server.store.handler.MessageInstanceHandler;
import org.apache.qpid.server.transport.util.Functions;
import org.apache.qpid.server.txn.DtxBranch;
import org.apache.qpid.server.txn.DtxRegistry;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.txn.Xid;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class SynchronousMessageStoreRecoverer implements MessageStoreRecoverer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SynchronousMessageStoreRecoverer.class);

    @Override
    public ListenableFuture<Void> recover(QueueManagingVirtualHost<?> virtualHost)
    {
        EventLogger eventLogger = virtualHost.getEventLogger();
        MessageStore store = virtualHost.getMessageStore();
        MessageStore.MessageStoreReader storeReader = store.newMessageStoreReader();
        MessageStoreLogSubject logSubject = new MessageStoreLogSubject(virtualHost.getName(), store.getClass().getSimpleName());

        Map<Queue<?>, Integer> queueRecoveries = new TreeMap<>();
        Map<Long, ServerMessage<?>> recoveredMessages = new HashMap<>();
        Map<Long, StoredMessage<?>> unusedMessages = new TreeMap<>();
        Map<UUID, Integer> unknownQueuesWithMessages = new HashMap<>();
        Map<Queue<?>, Integer> queuesWithUnknownMessages = new HashMap<>();

        eventLogger.message(logSubject, MessageStoreMessages.RECOVERY_START());

        storeReader.visitMessages(new MessageVisitor(recoveredMessages, unusedMessages));

        eventLogger.message(logSubject, TransactionLogMessages.RECOVERY_START(null, false));
        try
        {
            storeReader.visitMessageInstances(new MessageInstanceVisitor(virtualHost,
                                                                         store,
                                                                         queueRecoveries,
                                                                         recoveredMessages,
                                                                         unusedMessages,
                                                                         unknownQueuesWithMessages,
                                                                         queuesWithUnknownMessages));
        }
        finally
        {
            if (!unknownQueuesWithMessages.isEmpty())
            {
                unknownQueuesWithMessages.forEach((queueId, count) -> {
                    LOGGER.info("Discarded {} entry(s) associated with queue id '{}' as a queue with this "
                                 + "id does not appear in the configuration.",
                                 count, queueId);
                });
            }
            if (!queuesWithUnknownMessages.isEmpty())
            {
                queuesWithUnknownMessages.forEach((queue, count) -> {
                    LOGGER.info("Discarded {} entry(s) associated with queue '{}' as the referenced message "
                                 + "does not exist.",
                                 count, queue.getName());
                });
            }
        }

        for(Map.Entry<Queue<?>, Integer> entry : queueRecoveries.entrySet())
        {
            Queue<?> queue = entry.getKey();
            Integer deliveredCount = entry.getValue();
            eventLogger.message(logSubject, TransactionLogMessages.RECOVERED(deliveredCount, queue.getName()));
            eventLogger.message(logSubject, TransactionLogMessages.RECOVERY_COMPLETE(queue.getName(), true));
            queue.completeRecovery();
        }

        for (Queue<?> q : virtualHost.getChildren(Queue.class))
        {
            if (!queueRecoveries.containsKey(q))
            {
                q.completeRecovery();
            }
        }

        storeReader.visitDistributedTransactions(new DistributedTransactionVisitor(virtualHost,
                                                                                   eventLogger,
                                                                                   logSubject, recoveredMessages, unusedMessages));

        for(StoredMessage<?> m : unusedMessages.values())
        {
            LOGGER.debug("Message id '{}' is orphaned, removing", m.getMessageNumber());
            m.remove();
        }

        if (unusedMessages.size() > 0)
        {
            LOGGER.info("Discarded {} orphaned message(s).", unusedMessages.size());
        }

        eventLogger.message(logSubject, TransactionLogMessages.RECOVERY_COMPLETE(null, false));

        eventLogger.message(logSubject,
                             MessageStoreMessages.RECOVERED(recoveredMessages.size() - unusedMessages.size()));
        eventLogger.message(logSubject, MessageStoreMessages.RECOVERY_COMPLETE());

        return Futures.immediateFuture(null);
    }

    @Override
    public void cancel()
    {
        // No-op
    }

    private static class MessageVisitor implements MessageHandler
    {

        private final Map<Long, ServerMessage<?>> _recoveredMessages;
        private final Map<Long, StoredMessage<?>> _unusedMessages;

        MessageVisitor(final Map<Long, ServerMessage<?>> recoveredMessages,
                       final Map<Long, StoredMessage<?>> unusedMessages)
        {
            _recoveredMessages = recoveredMessages;
            _unusedMessages = unusedMessages;
        }

        @Override
        public boolean handle(StoredMessage<?> message)
        {
            StorableMessageMetaData metaData = message.getMetaData();

            @SuppressWarnings("rawtypes")
            MessageMetaDataType type = metaData.getType();

            @SuppressWarnings("unchecked")
            ServerMessage<?> serverMessage = type.createMessage(message);
