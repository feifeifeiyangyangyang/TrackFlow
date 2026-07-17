package com.trackflow.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.server.messaging.RabbitNames;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
  @Bean
  DirectExchange eventExchange() {
    return ExchangeBuilder.directExchange(RabbitNames.EVENT_EXCHANGE).durable(true).build();
  }

  @Bean
  DirectExchange deadLetterExchange() {
    return ExchangeBuilder.directExchange(RabbitNames.DLX).durable(true).build();
  }

  @Bean
  Queue eventProcessQueue() {
    return QueueBuilder.durable(RabbitNames.EVENT_PROCESS_QUEUE)
        .deadLetterExchange(RabbitNames.DLX)
        .deadLetterRoutingKey(RabbitNames.DLQ_ROUTING_KEY)
        .build();
  }

  @Bean
  Queue reconciliationQueue() {
    return QueueBuilder.durable(RabbitNames.RECONCILIATION_QUEUE)
        .deadLetterExchange(RabbitNames.DLX)
        .deadLetterRoutingKey(RabbitNames.DLQ_ROUTING_KEY)
        .build();
  }

  @Bean
  Queue deadLetterQueue() {
    return QueueBuilder.durable(RabbitNames.DLQ).build();
  }

  @Bean
  Binding eventProcessBinding(Queue eventProcessQueue, DirectExchange eventExchange) {
    return BindingBuilder.bind(eventProcessQueue).to(eventExchange).with(RabbitNames.EVENT_PROCESS_ROUTING_KEY);
  }

  @Bean
  Binding reconciliationBinding(Queue reconciliationQueue, DirectExchange eventExchange) {
    return BindingBuilder.bind(reconciliationQueue).to(eventExchange).with(RabbitNames.RECONCILIATION_ROUTING_KEY);
  }

  @Bean
  Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
    return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(RabbitNames.DLQ_ROUTING_KEY);
  }

  @Bean
  MessageConverter messageConverter(ObjectMapper objectMapper) {
    return new Jackson2JsonMessageConverter(objectMapper);
  }

  @Bean
  RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(messageConverter);
    template.setMandatory(true);
    return template;
  }

  @Bean
  SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory, MessageConverter messageConverter, @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(messageConverter);
    factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
    factory.setDefaultRequeueRejected(false);
    factory.setPrefetchCount(8);
    factory.setAutoStartup(autoStartup);
    return factory;
  }
}
