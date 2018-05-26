package berlin.intero.sentientlighthub.ledactor.tasks.scheduled

import berlin.intero.sentientlighthub.common.SentientProperties
import berlin.intero.sentientlighthub.common.services.ConfigurationService
import berlin.intero.sentientlighthub.common.tasks.GATTWriteAsyncTask
import berlin.intero.sentientlighthub.common.tasks.MQTTSubscribeAsyncTask
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.logging.Logger

/**
 * This scheduled task
 * <li> calls {@link MQTTSubscribeAsyncTask} to subscribe mapping values from MQTT broker
 * <li> calls {@link GATTWriteAsyncTask} to write values to a GATT device
 */
@Component
class GATTWriteLEDScheduledTask {
    val values: MutableMap<String, String> = HashMap()

    companion object {
        private val log: Logger = Logger.getLogger(GATTWriteLEDScheduledTask::class.simpleName)
    }

    init {
        val topic = "${SentientProperties.MQTT.Topic.LED}/#"
        val callback = object : MqttCallback {
            override fun messageArrived(topic: String, message: MqttMessage) {
                log.fine("MQTT value receiced")
                values[topic] = String(message.payload)
            }

            override fun connectionLost(cause: Throwable?) {
                log.fine("MQTT connection lost")
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                log.fine("MQTT delivery complete")
            }
        }

        // Call MQTTSubscribeAsyncTask
        SimpleAsyncTaskExecutor().execute(MQTTSubscribeAsyncTask(topic, callback))
    }

    @Scheduled(fixedDelay = SentientProperties.Frequency.SENTIENT_MAPPING_DELAY)
    fun map() {
        log.info("${SentientProperties.Color.TASK}-- GATT WRITE LED TASK${SentientProperties.Color.RESET}")

        values.forEach { topic, value ->

            val stripID = topic.split('/')[3]
            val ledID = topic.split('/')[4]

            val actor = ConfigurationService.getActor(stripID, ledID)

            log.info("${SentientProperties.Color.VALUE}topic $topic / val $value / strip $stripID / ledID $ledID / actor ${actor?.address} ${SentientProperties.Color.RESET}")

            if (actor != null) {
                val address = actor.address
                val characteristicID = SentientProperties.GATT.Characteristic.LED

                var byteValue = byteArrayOf()

                when (value) {
                    "0" -> byteValue = byteArrayOf(0x00)
                    "1" -> byteValue = byteArrayOf(0x01)
                }

                // Call GATTWriteAsyncTask
                SimpleAsyncTaskExecutor().execute(GATTWriteAsyncTask(address, characteristicID, byteValue))
            }
        }
    }
}
