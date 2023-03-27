package io.confluent.examples.clients.basicavro;

import java.io.IOException;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.StringSerializer;

public class SimpleProducerExample {

  private static final String TOPIC = "simple-transactions";
  private static final Properties props = new Properties();

  public static void main(final String[] args) throws IOException {
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
//          props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8081");

    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.RETRIES_CONFIG, 0);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

    final int numMessages = 10;
    try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
      for (long i = 0; i < numMessages; i++) {
        final String message = "transaction " + i;
        final ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, i + "", message);
        System.out.println("Sending message : " + message);
        producer.send(record, (x, y) -> System.out.println(
            "Sent message with exception:" + y + ". RecordMetaData:" + x));
        Thread.sleep(1000L);
      }

      producer.flush();
      System.out.printf("Successfully produced " + numMessages + " messages to a topic called %s%n",
          TOPIC);

    } catch (final SerializationException | InterruptedException e) {
      e.printStackTrace();
    }
  }
}
