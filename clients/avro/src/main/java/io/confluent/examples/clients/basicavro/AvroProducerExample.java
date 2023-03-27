package io.confluent.examples.clients.basicavro;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.io.IOException;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.StringSerializer;

public class AvroProducerExample {

  private static final String TOPIC = "avro-transactions";
  private static final Properties props = new Properties();

  @SuppressWarnings("InfiniteLoopStatement")
  public static void main(final String[] args) throws IOException {

    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8081");
    props.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, false);

    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.RETRIES_CONFIG, 0);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);

    try (KafkaProducer<String, Payment> producer = new KafkaProducer<String, Payment>(props)) {

      for (long i = 0; i < 10; i++) {
        final String orderId = "id" + i;
        final Payment payment = new Payment(orderId, 1000.00d, "region-"+i*2);
        final ProducerRecord<String, Payment> record = new ProducerRecord<String, Payment>(TOPIC,
            payment.getId().toString(), payment);
        producer.send(record, (recordMetadata, exception) -> {
          System.out.println(
              "Produced message, exception:" + exception + " RecordMetaData:" + recordMetadata);
        });
        Thread.sleep(1000L);
      }

      producer.flush();
      System.out.printf("Successfully produced 10 messages to a topic called %s%n", TOPIC);

    } catch (final SerializationException | InterruptedException e) {
      e.printStackTrace();
    }

  }

}
