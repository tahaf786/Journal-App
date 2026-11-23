package com.devtaha.journalapp;

import org.testcontainers.kafka.KafkaContainer;

/**
 * Kafka test container with a start-up command that tolerates the copy race of the stock class.
 *
 * <p>The stock container starts {@code sh -c "while [ ! -f /tmp/testcontainers_start.sh ]; ...; /tmp/testcontainers_start.sh"}
 * while the Docker daemon is still extracting the script into the container. If the shell executes the file
 * while the daemon still has it open for writing, the kernel refuses with ETXTBSY ("Text file busy", exit 126)
 * and the whole container exits. Here the script is run through {@code sh}, which only reads it, and the
 * start is retried a few times if the shell still reports 126.
 */
final class StableKafkaContainer extends KafkaContainer {

    private static final String SCRIPT = "/tmp/testcontainers_start.sh";

    StableKafkaContainer(String image) {
        super(image);
    }

    @Override
    protected void configure() {
        super.configure();
        withCommand("sh", "-c",
                "i=0; while [ ! -s " + SCRIPT + " ]; do sleep 0.1; done; sleep 0.3; "
                        + "while true; do sh " + SCRIPT + "; rc=$?; "
                        + "if [ $rc -ne 126 ] || [ $i -ge 10 ]; then exit $rc; fi; i=$((i+1)); sleep 0.5; done");
    }
}
