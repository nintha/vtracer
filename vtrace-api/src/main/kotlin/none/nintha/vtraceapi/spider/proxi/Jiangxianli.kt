package none.nintha.vtraceapi.spider.proxi

import com.fasterxml.jackson.databind.ObjectMapper
import none.nintha.vtraceapi.util.HttpSender
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.StringUtils

class Jiangxianli : Proci {
    override val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val objectMapper: ObjectMapper = ObjectMapper()
    private val urls = (1..6).map { "http://ip.jiangxianli.com/api/proxy_ips?page=$it" }

    override fun fetch(): Set<String> {
        try {
            return urls.flatMap {
                val html = HttpSender.get(it)
                if(StringUtils.isEmpty(html)) return@flatMap setOf<String>()

                return@flatMap objectMapper.readTree(html).get("data")?.get("data")?.asIterable()
                        ?.filter { it.get("ip") != null }
                        ?.map { node -> "${node.get("ip").asText()}:${node.get("port").asText()}" }
                        ?.toSet() ?: setOf()
            }.toSet()
        } catch (e: Exception) {
            logger.error("", e)
            return setOf()
        }
    }
}
//
//fun main(args: Array<String>) {
//    val proxi = Jiangxianli()
//    val proxys = proxi.fetchAndCheck()
//    println("proxies size=${proxys.size}")
//    LoggerFactory.getLogger("Xicidaili").info(proxys.toString())
//}
