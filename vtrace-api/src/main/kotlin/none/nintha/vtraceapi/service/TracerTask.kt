package none.nintha.vtraceapi.service

import none.nintha.vtraceapi.entity.QueryReq
import none.nintha.vtraceapi.entity.consts.TraceStatus
import none.nintha.vtraceapi.spider.Nest
import none.nintha.vtraceapi.spider.proxi.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.util.CollectionUtils
import java.util.*
import java.util.stream.Collectors

@Component
class TracerTask {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(TracerTask::class.java)
        val procis: Array<Proci> = arrayOf(Ip98daili(), Cnproxy(), Kuaidaili(), Xicidaili(), Jiangxianli())
        const val QUERY_LIMIT: Int = 100
        const val PROXY_MAX: Int = 1500
    }

    @Autowired
    lateinit var biliService: BiliService
    @Autowired
    lateinit var tracerService: TracerService
    // 更新代理池
    @Scheduled(fixedDelay = 1000 * 60 * 6)
    fun proxyLoop() {
        // 减少空闲时间的消耗
        val proxySize = Nest.PROXY_MAP.size
        if (proxySize >= PROXY_MAX) return

        logger.info("[Task] Start a proxy loop.")
//        Nest.addProxys(setOf("${HttpSender.LOCALHOST}:0")) //补充本地地址
        for (proci: Proci in procis) {
            val proxys: Set<String> = proci.fetchAndCheck()
            if (CollectionUtils.isEmpty(proxys)) continue
            Nest.addProxys(proxys)
        }

        Nest.saveData()
        logger.info("[Task] End a proxy loop.")
    }
    // 爬虫任务
    @Scheduled(cron = "0 0 11,23 * * ? ")
    fun inMemoryLoop(){
        biliService.runInMemoryTask()
    }
    // 用户数据追踪
    @Scheduled(cron = "40 0/6 * * * ? ")
    fun memberLoop() {
        val memberCount = tracerService.countTraceMember(QueryReq()).toInt()
        val maxPage: Int = memberCount / QUERY_LIMIT + if (memberCount % QUERY_LIMIT == 0) 0 else 1
        for (pageNum in 1..maxPage) {
            val members = tracerService.getTraceMembers(QueryReq(pageNum, QUERY_LIMIT))
            for (m in members) {
                tracerService.traceMemberInfo(m.mid)
                // 检测用户新视频
                if(m.mini == 0) tracerService.traceMemberArchive(m.mid)
            }

        }
        logger.info("trace member, size={}", memberCount)
    }
    // 视频数据追踪
    @Scheduled(cron = "20 0/3 * * * ? ")
    fun videoLoop() {
        val now = Date()
        val keepOnMids = tracerService.allKeepTraceMembers.map { it.mid }
        val videoCount = tracerService.countTraceVideo(QueryReq()).toInt()
        var traceCount: Long = 0
        val maxPage = videoCount / QUERY_LIMIT + if (videoCount % QUERY_LIMIT == 0) 0 else 1
        for (pageNum in 1..maxPage) {
            val videos = tracerService.getTraceVideos(QueryReq(pageNum, QUERY_LIMIT))
            val matchAids = videos.stream()
                    .filter { v -> v.status == TraceStatus.RUNNING.code }
                    .filter { v -> v.endTime.after(now) || keepOnMids.contains(v.mid) }
                    .map { it.aid }
                    .collect(Collectors.toList<Long>())
            tracerService.traceVideoBatch(matchAids)
            // 把已经过期，为running状态，不在 keep 列表中的任务 更新为 stopped
            videos.stream().filter { v -> v.endTime.before(now) }
                    .filter { v -> v.status == TraceStatus.RUNNING.code }
                    .filter { v -> !keepOnMids.contains(v.mid) }
                    .forEach { v -> tracerService.updateTraceStatus(v.aid, TraceStatus.STOPPED.code) }

            traceCount += matchAids.size.toLong()
        }
        logger.info("trace video, size={}, keeps={}", traceCount, keepOnMids.size)
    }
}