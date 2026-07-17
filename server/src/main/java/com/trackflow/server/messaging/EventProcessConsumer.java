package com.trackflow.server.messaging;

import com.rabbitmq.client.Channel;
import com.trackflow.server.service.CoreService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class EventProcessConsumer {
  private final CoreService service;
  private final String workerId = "event-consumer-" + UUID.randomUUID();

  public EventProcessConsumer(CoreService service) {
    this.service = service;
  }

  @RabbitListener(queues = RabbitNames.EVENT_PROCESS_QUEUE, containerFactory = "rabbitListenerContainerFactory")
  public void onMessage(EventProcessMessage payload, Message message, Channel channel) throws IOException {
    long deliveryTag = message.getMessageProperties().getDeliveryTag();
    try {
      String result = service.processEventTask(payload.taskId(), workerId);
      if ("FAILED".equals(result)) {
        channel.basicReject(deliveryTag, false);
      } else {
        channel.basicAck(deliveryTag, false);
      }
    } catch (Exception e) {
      channel.basicReject(deliveryTag, false);
    }
  }
}
