package poc.activemq.queue.producer;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import poc.activemq.queue.util.ActiveMQBrokerFailover;

import java.io.IOException;

/**
 * Base class for a REST controller to push messages to active MQ
 * The controller is based on the REST API of active MQ to push messages into a queue
 * <p>
 * To send messages to Active MQ call the method: sendToQueue(message, contentType)
 * <p>
 * <br>
 * The following properties are required:
 * broker.user Active MQ user name
 * broker.password Active MQ user's password as plaintext
 * broker.hosts Comma separated list of Active MQ brokers, each broker must consist the host and port, e.g.
 * broker1:8161, broker2:8161
 * <p>
 * User: sigals
 * Date: 29/07/2016
 */
@Service
@Slf4j
public class HttpActiveMQProducer implements InitializingBean {

    private static final String BROKER_URL_FORMAT = "http://%s:%s@%s/api/message?destination=queue://test&jms.closeTimeout=5000";
//    private static final String BROKER_URL_FORMAT = "http://%s:%s@%s/api/message?destination=queue://%s";
    private static final int RETRIES = 1;

    private String brokerUrl ="http://admin:admin@localhost:8161/api/message?destination=queue://%s&jms.closeTimeout=5000";

    @Value("${broker.hosts}")
    private String[] brokerHosts = new String[]{"localhost:8161"};
    @Value("${broker.user}")
    private String user = "admin";
    @Value("${broker.password}")
    private String password = "admin";

    int sendToQueue(final String message, @SuppressWarnings("SameParameterValue") final String queueName) {

        HttpClient httpClient = HttpClientBuilder.create().build();
        int status = Integer.MIN_VALUE;
        for (int i = 0; status != HttpStatus.SC_OK && i < RETRIES; i++) {
            // Send the message to the API
            HttpPost post = new HttpPost(String.format(brokerUrl, queueName));
            HttpEntity messageEntity = new ByteArrayEntity(message.getBytes());
            post.setHeader("Content-Type", "application/json");
            post.setEntity(messageEntity);
            try {
                HttpResponse queueResponse = httpClient.execute(post);
                status = queueResponse.getStatusLine().getStatusCode();
            } catch (IOException e) {
                log.error("Failed to send request to queue", e);
                status = HttpStatus.SC_INTERNAL_SERVER_ERROR;

                // If the response failed try to identify the active broker again as a failover
                // If there's only one broker hosts, there's no failover
                if (brokerHosts.length != 1) {
                    try {
                        brokerUrl = ActiveMQBrokerFailover.getMasterBrokerUrl(BROKER_URL_FORMAT, user, password, brokerHosts);
                    } catch (Exception e1) {
                        log.error("Failed to get an active message queue broker", e1);
                        return HttpStatus.SC_INTERNAL_SERVER_ERROR;
                    }
                }
            }
        }

        return status;
    }

    /**
     * Find the Active MQ master broker before the controller starts accepting requests
     *
     * @throws Exception If an active broker is not found for any reason
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        brokerUrl = ActiveMQBrokerFailover.getMasterBrokerUrl(BROKER_URL_FORMAT, user, password, brokerHosts);
    }

}
