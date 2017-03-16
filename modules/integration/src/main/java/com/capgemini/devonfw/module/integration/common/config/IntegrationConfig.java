package com.capgemini.devonfw.module.integration.common.config;

import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.jms.ConnectionFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.integration.annotation.Gateway;
import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.dsl.jms.Jms;
import org.springframework.integration.dsl.support.GenericHandler;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.GenericMessage;

import com.capgemini.devonfw.module.integration.common.api.IntegrationHandler;

/**
 * @author pparrado
 *
 */
@Configuration
@PropertySource("classpath:integration.properties")
public class IntegrationConfig {

  private static final Logger LOG = LoggerFactory.getLogger(IntegrationConfig.class);

  @Inject
  private ConnectionFactory connectionFactory;

  @Value("${devonfw.integration.one-direction.channelname}")
  private String channel_1d;

  @Value("${devonfw.integration.one-direction.queuename}")
  private String queue_1d;

  @Value("${devonfw.integration.request-reply.channelname}")
  private String channel_rr;

  @Value("${devonfw.integration.request-reply.queuename}")
  private String queue_rr;

  @Value("${devonfw.integration.request-reply-async.channelname}")
  private String channel_async;

  @Value("${devonfw.integration.request-reply-async.queuename}")
  private String queue_async;

  @Value("${devonfw.integration.one-direction.poller.rate}")
  private int rate;

  @Value("${devonfw.integration.request-reply.receivetimeout}")
  private long rr_timeout;

  @Value("${devonfw.integration.request-reply-async.receivetimeout}")
  private long rra_timeout;

  // PRECONFIGURED GATEWAYS - - - - - - - - - - - - - - - - - - - - - -

  /**
   * Default gateway for out-of-the-box simple communication channel. One direction flow. Only sending not expecting for
   * response.
   *
   */
  @MessagingGateway
  public interface OneDirectionGateway {
    /**
     * Sends a {@link GenericMessage} with the received content.
     *
     * @param message the content to be included in the message.
     */
    @Gateway(requestChannel = "1d.Channel")
    void send(GenericMessage<?> message);
  }

  /**
   * Default gateway for out-of-the-box request-reply communication channel.
   *
   */
  @MessagingGateway
  public interface RequestReplyGateway {
    /**
     * Sends a {@link GenericMessage} with the received content.
     *
     * @param message the content to be included in the message.
     * @return the response received.
     */
    @Gateway(requestChannel = "rr.Channel")
    String echo(GenericMessage<?> message);
  }

  /**
   * Default gateway for out-out-the-box asynchronous request-reply communication channel.
   *
   */
  @MessagingGateway
  public interface AsyncGateway {
    /**
     * Sends a {@link GenericMessage} with the received content.
     *
     * @param message the content to be included in the message.
     * @return the response received.
     */
    @Gateway(requestChannel = "async.Channel")
    Future<String> sendAsync(GenericMessage<?> message);
  }

  // PRECONFIGURED FLOWS - - - - - - - - - - - - - - - - - - - - - - - -

  // out

  /**
   * If the property "integration.one-direction.emitter" has value "true" this Bean will be loaded and will create a
   * simple flow, only for sending messages, no response expected.
   *
   * @return the created {@link IntegrationFlow}
   */
  @Bean
  @ConditionalOnProperty(prefix = "devonfw.integration.one-direction", name = "emitter", havingValue = "true")
  IntegrationFlow outFlow() {

    return IntegrationFlows.from(this.channel_1d)
        .handle(Jms.outboundAdapter(this.connectionFactory).destination(this.queue_1d)).get();

  }

  /**
   * If the property "integration.request-reply.emitter" has value "true" this Bean will be loaded and will create a
   * request-reply flow.
   *
   * @return the created {@link IntegrationFlow}
   */
  @Bean
  @ConditionalOnProperty(prefix = "devonfw.integration.request-reply", name = "emitter", havingValue = "true")
  public IntegrationFlow outAndInFlow() {

    return IntegrationFlows.from(this.channel_rr).handle(
        Jms.outboundGateway(this.connectionFactory).requestDestination(this.queue_rr).receiveTimeout(this.rr_timeout))
        .get();
  }

  /**
   * If the property "integration.request-reply-async.emitter" has value "true" this Bean will be loaded and will create
   * a request-reply asynchronous flow for sending messages and receiving responses.
   *
   * @return the created {@link IntegrationFlow}
   */
  @Bean
  @ConditionalOnProperty(prefix = "devonfw.integration.request-reply-async", name = "emitter", havingValue = "true")
  public IntegrationFlow asyncOutboundFlow() {

    return IntegrationFlows.from(this.channel_async).handle(Jms.outboundGateway(this.connectionFactory)
        .requestDestination(this.queue_async).receiveTimeout(this.rra_timeout)).get();
  }

  // in

  /**
   * If the property "integration.one-direction.listener" has value "true" this Bean will be loaded and will create a
   * simple flow for receiving messages.
   *
   * @param handler the {@link MessageHandler} that will manage each message received.
   * @return the created {@link IntegrationFlow}
   */
  @Bean
  @ConditionalOnProperty(prefix = "devonfw.integration.one-direction", name = "listener", havingValue = "true")
  public IntegrationFlow inFlow(MessageHandler handler) {

    return IntegrationFlows.from(Jms.inboundAdapter(this.connectionFactory).destination(this.queue_1d),
        c -> c.poller(Pollers.fixedRate(this.rate, TimeUnit.MILLISECONDS))).handle(m -> {
          try {
            handler.handleMessage(m);
          } catch (Exception e) {
            LOG.error(String.format("MessageHandler threw an error: %s", e.getMessage()), e);
          }
        }).get();
  }

  /**
   * If the property "integration.request-reply.listener" has value "true" this Bean will be loaded and will create a
   * request-reply flow for receiving and replying to messages.
   *
   * @param handler the {@link IntegrationHandler} to manage the messages and send back the response
   * @return the created {@link IntegrationFlow}
   */
  @Bean
  @ConditionalOnProperty(prefix = "devonfw.integration.request-reply", name = "listener", havingValue = "true")
  public IntegrationFlow inAndOutFlow(IntegrationHandler handler) {

    return IntegrationFlows.from(Jms.inboundGateway(this.connectionFactory).destination(this.queue_rr))
        .wireTap(flow -> flow.handle(System.out::println)).handle(new GenericHandler<String>() {

          @Override
          public Object handle(String payload, Map<String, Object> headers) {

            try {
              return handler.handleMessage(new GenericMessage<>(payload, headers));
            } catch (Exception e) {
              LOG.error(String.format("IntegrationHandler threw an error: %s", e.getMessage()), e);
              return null;
            }
          }
        }).get();
  }

  /**
   * If the property "integration.request-reply-async.listener" has value "true" this Bean will be loaded and will
   * create a request-reply asynchronous flow for sending messages and receiving responses.
   *
   * @param handler the {@link IntegrationHandler} to manage the messages and send back the response
   * @return the created {@link IntegrationFlow}
   */
  @Bean
  @ConditionalOnProperty(prefix = "devonfw.integration.request-reply-async", name = "listener", havingValue = "true")
  public IntegrationFlow asyncInAndOutFlow(IntegrationHandler handler) {

    return IntegrationFlows.from(Jms.inboundGateway(this.connectionFactory).destination(this.queue_async))
        .wireTap(flow -> flow.handle(System.out::println)).handle(new GenericHandler<String>() {
          @Override
          public Object handle(String payload, Map<String, Object> headers) {

            try {
              return handler.handleMessage(new GenericMessage<>(payload, headers));
            } catch (Exception e) {
              LOG.error(String.format("IntegrationHandler threw an error: %s", e.getMessage()), e);
              return null;
            }
          }
        }).get();
  }

}
