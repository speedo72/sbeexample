package org.example;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import schema.BondDecoder;
import schema.BondEncoder;
import schema.MessageHeaderDecoder;
import schema.MessageHeaderEncoder;
import schema.WatchlistDecoder;
import schema.WatchlistEncoder;

import java.nio.ByteBuffer;

public class Main {

    public static void main(String[] args) {

        try (final MediaDriver mediaDriver = MediaDriver.launch(new MediaDriver.Context()
                .aeronDirectoryName("/dev/shm/shared/").dirDeleteOnStart(true))) {
            final Aeron.Context aeronCtx = new Aeron.Context()
                    .aeronDirectoryName(mediaDriver.aeronDirectoryName())
                    .idleStrategy(new SleepingMillisIdleStrategy(10));

            try (final Aeron aeron = Aeron.connect(aeronCtx)) {
                final FragmentHandler fragmentHandler = new MessageHandler();
                final Subscription subscription = aeron.addSubscription("aeron:ipc", 777);

                final Publication publication = aeron.addPublication("aeron:ipc", 777);
                final Publisher publisher = new Publisher(publication);
                publisher.publishWatchlistItems();

                subscription.poll(fragmentHandler, 4);
            }
        }
    }

    public static class Publisher {
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4096);
        final MutableDirectBuffer mutableDirectBuffer = new UnsafeBuffer(byteBuffer);
        final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
        final WatchlistEncoder watchlistEncoder = new WatchlistEncoder();
        final BondEncoder bondEncoder = new BondEncoder();
        final Publication publication;

        Publisher(Publication publication) {
            this.publication = publication;
        }

        public void publishWatchlistItems() {
            messageHeaderEncoder
                .wrap(mutableDirectBuffer, 0)
                .blockLength(watchlistEncoder.sbeBlockLength())
                .templateId(watchlistEncoder.sbeTemplateId())
                .schemaId(watchlistEncoder.sbeSchemaId())
                .version(watchlistEncoder.sbeSchemaVersion());

            watchlistEncoder
                .wrap(mutableDirectBuffer, messageHeaderEncoder.encodedLength())
                .watchlistId(0)
                .watchlistItemsCount(3)
                .next()
                .cusip("123456789")
                .quantity(1)
                .level(1)
                .next()
                .cusip("987654321")
                .quantity(2)
                .level(2)
                .next()
                .cusip("ABCDEFGHI")
                .quantity(3)
                .level(3);

            publication.offer(mutableDirectBuffer, 0, messageHeaderEncoder.encodedLength() + watchlistEncoder.encodedLength());

            messageHeaderEncoder
                    .wrap(mutableDirectBuffer, 0)
                    .blockLength(watchlistEncoder.sbeBlockLength())
                    .templateId(watchlistEncoder.sbeTemplateId())
                    .schemaId(watchlistEncoder.sbeSchemaId())
                    .version(watchlistEncoder.sbeSchemaVersion());

            watchlistEncoder
                    .wrap(mutableDirectBuffer, messageHeaderEncoder.encodedLength())
                    .watchlistId(1)
                    .watchlistItemsCount(2)
                    .next()
                    .cusip("ABCDEFGHI")
                    .quantity(4)
                    .level(4)
                    .next()
                    .cusip("IHGFEDCBA")
                    .quantity(5)
                    .level(5);

            publication.offer(mutableDirectBuffer, 0, messageHeaderEncoder.encodedLength() + watchlistEncoder.encodedLength());

            messageHeaderEncoder
                    .wrap(mutableDirectBuffer, 0)
                    .blockLength(bondEncoder.sbeBlockLength())
                    .templateId(bondEncoder.sbeTemplateId())
                    .schemaId(bondEncoder.sbeSchemaId())
                    .version(bondEncoder.sbeSchemaVersion());

            bondEncoder
                    .wrap(mutableDirectBuffer, messageHeaderEncoder.encodedLength())
                    .cusip("ABCDEFGHI")
                    .isin("LONG ISIN THAT IS LONG");

            publication.offer(mutableDirectBuffer, 0, messageHeaderEncoder.encodedLength() + bondEncoder.encodedLength());

            messageHeaderEncoder
                    .wrap(mutableDirectBuffer, 0)
                    .blockLength(watchlistEncoder.sbeBlockLength())
                    .templateId(watchlistEncoder.sbeTemplateId())
                    .schemaId(watchlistEncoder.sbeSchemaId())
                    .version(watchlistEncoder.sbeSchemaVersion());

            watchlistEncoder
                    .wrap(mutableDirectBuffer, messageHeaderEncoder.encodedLength())
                    .watchlistId(2)
                    .watchlistItemsCount(1)
                    .next()
                    .cusip("TESTCUSIP")
                    .quantity(6)
                    .level(6);

            publication.offer(mutableDirectBuffer, 0, messageHeaderEncoder.encodedLength() + watchlistEncoder.encodedLength());
        }
    }



    public static class MessageHandler implements FragmentHandler {
        private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
        private final WatchlistDecoder watchlistDecoder = new WatchlistDecoder();
        private final BondDecoder bondDecoder = new BondDecoder();

        @Override
        public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
            messageHeaderDecoder.wrap(buffer, offset);
            final int blockLength = messageHeaderDecoder.blockLength();
            final int templateId = messageHeaderDecoder.templateId();
            final int schemaId = messageHeaderDecoder.schemaId();
            final int versionId = messageHeaderDecoder.version();

            if (templateId == WatchlistDecoder.TEMPLATE_ID) {
                watchlistDecoder.wrap(buffer, offset + messageHeaderDecoder.encodedLength(), blockLength, versionId);
                System.out.println("Watchlist Id: " + watchlistDecoder.watchlistId() + " has watchlist items:");

                for (WatchlistDecoder.WatchlistItemsDecoder watchlistItemsDecoder : watchlistDecoder.watchlistItems()) {
                    System.out.print("cusip: " + watchlistItemsDecoder.cusip());
                    System.out.print(" quantity: " + watchlistItemsDecoder.quantity());
                    System.out.println(" level: " + watchlistItemsDecoder.level());
                }
            }
            if (templateId == BondDecoder.TEMPLATE_ID) {
                bondDecoder.wrap(buffer, offset + messageHeaderDecoder.encodedLength(), blockLength, versionId);
                System.out.println("Bond message: " + bondDecoder.cusip() + " with isin: " + bondDecoder.isin());
            }
        }
    }
}
