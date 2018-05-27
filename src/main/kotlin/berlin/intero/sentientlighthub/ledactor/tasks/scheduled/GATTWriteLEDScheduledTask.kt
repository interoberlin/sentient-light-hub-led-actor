package berlin.intero.sentientlighthub.ledactor.tasks.scheduled

import berlin.intero.sentientlighthub.common.SentientProperties
import berlin.intero.sentientlighthub.common.services.ConfigurationService
import berlin.intero.sentientlighthub.common.services.TinybService
import berlin.intero.sentientlighthub.common.tasks.GATTWriteAsyncTask
import berlin.intero.sentientlighthub.common.tasks.MQTTSubscribeAsyncTask
import com.google.gson.Gson
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.core.task.SyncTaskExecutor
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
    val valuesHistoric: MutableMap<String, String> = HashMap()

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

    @Scheduled(fixedDelay = SentientProperties.Frequency.SENTIENT_WRITE_DELAY)
    @SuppressWarnings("unused")
    fun write() {
        log.info("${SentientProperties.Color.TASK}-- GATT WRITE LED TASK${SentientProperties.Color.RESET}")

        val scannedDevices = TinybService.scannedDevices
        val intendedDevices = ConfigurationService.actorConfig?.actorDevices

        log.fine("Show scannedDevices ${Gson().toJson(scannedDevices.map { d -> d.address })}")
        log.fine("Show intendedDevices ${Gson().toJson(intendedDevices?.map { d -> d.address })}")

        if (scannedDevices.isEmpty()) {
            GATTScanDevicesScheduledTask().scanDevices()
        }

        values.forEach { topic, value ->

            val stripID = topic.split('/')[3]
            val ledID = topic.split('/')[4]

            val actor = ConfigurationService.getActor(stripID, ledID)

            log.info("${SentientProperties.Color.VALUE}topic $topic / val $value / strip $stripID / ledID $ledID / actor ${actor?.address} ${SentientProperties.Color.RESET}")


            if (actor != null && value != valuesHistoric[topic]) {
                val address = actor.address
                val characteristicID = SentientProperties.GATT.Characteristic.LED

                var byteValue = byteArrayOf()

                when (value) {
                    "0" -> byteValue = byteArrayOf(0x00)
                    "1" -> byteValue = byteArrayOf(0x01)
                }

                // Call GATTWriteAsyncTask
                SyncTaskExecutor().execute(GATTWriteAsyncTask(address, characteristicID, byteValue))
            }

            valuesHistoric[topic] = value
        }
    }
}
