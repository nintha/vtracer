package none.nintha.vtraceapi.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import none.nintha.vtraceapi.util.TimeUtil
import java.time.LocalDateTime

fun <T> JsonNode.asBean(valueType: Class<T>, mapper: ObjectMapper): T {
    return mapper.readValue(this.toString(), valueType)
}

fun LocalDateTime.toEpochMilli(): Long{
    return TimeUtil.toDate(this).time
}
