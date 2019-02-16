package none.nintha.vtraceapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import none.nintha.vtraceapi.entity.consts.ApiGen
import none.nintha.vtraceapi.spider.Nest
import none.nintha.vtraceapi.util.HttpSender
import org.apache.logging.log4j.util.Strings
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.util.CollectionUtils
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.LongAdder
import java.util.function.Supplier

@Service
class BiliFetcher {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(BiliService::class.java)
        val ONLINE_COUNT_REGEX = Regex("""<online_count>([0-9]+)</online_count>""")
    }

    @Autowired
    lateinit var mapper: ObjectMapper

    /**
     * 获取用户总播放量
     * 返回-1代表失败
     */
    fun fetchMemberArchiveView(mid: Long): Long {
        try {
            val html = HttpSender.get(ApiGen.memberTotalView(mid))
            if (Strings.isBlank(html)) return -1

            val node = mapper.readTree(html)
            return node.get("data")?.get("archive")?.get("view")?.asLong() ?: -1
        } catch (e: Exception) {
            logger.error("解析用户总播放量异常, mid={}", mid, e)
        }
        return -1
    }

    /**
     * 批量获取用户在线人数
     * return {aid : onlineCount}
     */
    fun fetchVideoOnlineCountBatch(aids: List<Long>): Map<Long, Long> {
        if(CollectionUtils.isEmpty(aids)) return mapOf()

        return aids.map {
           Pair(it, CompletableFuture.supplyAsync(Supplier { fetchVideoOnlineCount(it) }, HttpSender.threadPool))
        }.asSequence()
                .map { Pair(it.first, it.second.get()) }
                .filter { it.second > 0 }
                .toMap()
    }

    /**
     * 获取用户在线人数
     * 返回-1代表失败
     */
    fun fetchVideoOnlineCount(aid: Long): Long {
        val cid = this.fetchVideoFirstPartId(aid)
        if (cid < 0) return -1

        try {
            var html= ""
            val longAdder = LongAdder()
            while (longAdder.sum() < 30 && Strings.isBlank(html)) {
                val spider = Nest.getSpider()
                html = spider.fetch { ip, port -> HttpSender.get(ApiGen.videoOnlineCount(aid, cid), ip, port) }.also { Nest.returnSpider(spider) }
                longAdder.increment()
            }
            if (Strings.isBlank(html)) return -1

            val rs = ONLINE_COUNT_REGEX.findAll(html)
            // -1 是去掉爬虫自己
            return rs.firstOrNull()?.groups?.get(1)?.value?.toLong() ?: 0
        } catch (e: Exception) {
            logger.error("获取用户在线人数异常，aid=$aid, cid=$cid", e)
        }
        return -1
    }

    /**
     * 获取视频第一个分P的ID（cid）
     */
    fun fetchVideoFirstPartId(aid: Long): Long {
        try {
            var html = ""
            val longAdder = LongAdder()
            while (longAdder.sum() < 20 && Strings.isBlank(html)) {
                val spider = Nest.getSpider()
                html = spider.fetch { ip, port -> HttpSender.get(ApiGen.videoPart(aid), ip, port) }.also { Nest.returnSpider(spider) }
                longAdder.increment()
            }

            if (Strings.isBlank(html)) return -1

            val node = mapper.readTree(html)
            return node.get("data").get(0).get("cid").asLong()
        } catch (e: Exception) {
            logger.error("获取视频第一个分P的ID（cid）异常，aid=$aid", e)
        }
        return -1
    }
}
