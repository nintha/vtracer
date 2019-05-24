package none.nintha.vtraceapi.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.WriteConcern
import none.nintha.vtraceapi.entity.PartCard
import none.nintha.vtraceapi.entity.TraceTaskResult
import none.nintha.vtraceapi.spider.FetchTask
import none.nintha.vtraceapi.spider.Nest
import none.nintha.vtraceapi.spider.Spider
import none.nintha.vtraceapi.spider.TableNames
import none.nintha.vtraceapi.util.HttpSender
import none.nintha.vtraceapi.util.TimeUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.BeanUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import org.springframework.util.CollectionUtils
import org.springframework.util.StringUtils
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.management.Query


@Service
class BiliService {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(BiliService::class.java)
        private const val QUERY_LIMIT = 1000
        const val POST_MEMBER_API = "http://space.bilibili.com/ajax/member/GetInfo"
        const val GET_CARD_API = "http://api.bilibili.com/x/web-interface/card" // ?mid={mid}
        val sexMap: Map<String, Int> = mapOf("男" to 0, "女" to 1, "保密" to 2)
        var taskStatus: Int = 0 // 0-stopped, 1-running, 2-exporting, 3-stopping, 4-scheduled

        const val zipFileName = "result.zip"
        const val ABNORMAL_FANS_VALUE = 400 // top用户爬取时，fans低于这个值作为异常数据
        var defaultTaskId: Long = 0
    }

    @Autowired
    lateinit var mongoTemplate: MongoTemplate
    @Autowired
    lateinit var biliFetcher: BiliFetcher
    @Autowired
    lateinit var mailService: MailService
    @Autowired
    lateinit var mapper: ObjectMapper

    @Value("@{vtarce.task-watcher.emails}")
    lateinit var taskWatcherEmails: String


    /**
     * 执行一次任务(任务内容一次性读到内存中),并发送结果邮件
     * @return 本次任务ID
     */
    fun runInMemoryTask(): Long {
        // 取整到小时
        defaultTaskId = TimeUtil.toDate(LocalDateTime.now().withSecond(0).withNano(0)).time
        logger.info("[Run task In Memory] start, taskId=$defaultTaskId.")

        val fetchFunc = this::fetchTraceTaskResult
        val idMapper = TraceTaskResult::mid

        val tasks = mongoTemplate.findAll(FetchTask::class.java, TableNames.MONGO_TRACE_TASK_TIEM)
        var remainMids: MutableSet<Long> = tasks.asSequence().map { it.mid }.toMutableSet()
        while (true) {
            val finishedMids = handleTask(remainMids.map { FetchTask(it) }, fetchFunc, idMapper)

            logger.info("[Run task In Memory] ${finishedMids.size}/${remainMids.size}")
            remainMids.removeAll(finishedMids)
            if (remainMids.isEmpty()) break
        }

        // 等待mongo数据缓存同步
        Thread.sleep(10_000)

        // 异常错误处理
        for (times in (1..4)) {
            val pairs = getUnexpectPairs(defaultTaskId)
            remainMids = pairs.map { it.first }.toMutableSet()
            logger.info("[Run task In Memory] #$times handle abnormal data, taskId=$defaultTaskId, size=${pairs.size}, detail=$pairs")
            if(remainMids.isEmpty()) break

            // remove abnormal results
            val removeQuery = Query(Criteria.where("taskId").`is`(defaultTaskId).and("mid").`in`(remainMids))
            mongoTemplate.remove(removeQuery, TableNames.MONGO_TRACE_TASK_RESULT)
            while (true) {
                if (remainMids.isEmpty()) break
                val finishedMids = handleTask(remainMids.map { FetchTask(it) }, fetchFunc, idMapper)

                logger.info("[Run task In Memory] #$times retry , ${finishedMids.size}/${remainMids.size}")
                remainMids.removeAll(finishedMids)
            }
            Thread.sleep(2_000)
        }

        CompletableFuture.runAsync {
            // 等待mongo数据缓存同步
            Thread.sleep(20_000)
            val results = queryResultByTaskId(defaultTaskId, tasks.size)
            logger.info("[Run task In Memory] done, result/task=${results.size}/${tasks.size}.")
            sendTaskResultEmail(results, tasks.size.toLong())
        }
        return defaultTaskId
    }

    fun queryResultByTaskId(taskId: Long, taskSize: Int): List<TraceTaskResult> {
        val rslist: MutableList<TraceTaskResult> = mutableListOf();
        val maxPage = taskSize / QUERY_LIMIT + if (taskSize % QUERY_LIMIT == 0) 0 else 1
        logger.info("[queryResultByTaskId] Args: maxPage=$maxPage, queryLimit=$QUERY_LIMIT, taskId=$taskId, taskSize=$taskSize")

        (1..maxPage).map { pageNum ->
            Pair(pageNum, CompletableFuture.supplyAsync {
                val resultQuery = Query(Criteria.where("taskId").`is`(taskId)).skip((pageNum - 1) * QUERY_LIMIT.toLong()).limit(QUERY_LIMIT)
                mongoTemplate.find(resultQuery, TraceTaskResult::class.java, TableNames.MONGO_TRACE_TASK_RESULT)
            })
        }.forEach {
            val list = it.second.get()
            rslist.addAll(list)
            logger.info("[queryResultByTaskId] page=${it.first}, skip=${(it.first - 1) * QUERY_LIMIT.toLong()} > get=${list.size}")
        }

        logger.info("[queryResultByTaskId] query is done > rslist=${rslist.size}")
        return rslist
    }

    fun getTaskResultsByTaskId(taskId: Long): List<TraceTaskResult> {
        val taskSize = mongoTemplate.count(Query(Criteria.where("taskId").`is`(taskId)), TraceTaskResult::class.java, TableNames.MONGO_TRACE_TASK_RESULT)
        return queryResultByTaskId(taskId, taskSize.toInt())
    }


    /**
     * 和前一个任务进行比较，一些异常的数据的mid
     */
    fun getUnexpectPairs(taskId: Long): List<Pair<Long, Long>> {
        val checkQuery = Query(Criteria.where("taskId").`is`(defaultTaskId).and("fans").lte(ABNORMAL_FANS_VALUE))
        val abnormalResults: MutableList<TraceTaskResult> = mongoTemplate.find(checkQuery, TraceTaskResult::class.java, TableNames.MONGO_TRACE_TASK_RESULT)
        val abnormalMids = abnormalResults.map { it.mid }.toMutableSet()
        // 两个数据相差过大
        fun isBigDiff(a: Long, b: Long): Boolean {
            if (b == 0L || a == 0L) return true
            if (a > (b+3000)) return true
            if (a < (b-200)) return true
            return Math.abs(a * 1.0 / b - 1) > 0.25
        }

        val prevTaskId = getPrevTaskId(taskId)
        if (prevTaskId == 0L) {
            return abnormalResults.map { Pair(it.mid, it.fans) }
        }

        val thisResults = getTaskResultsByTaskId(taskId)
        val thisMap = thisResults.associateBy { it.mid }

        val prevResults = getTaskResultsByTaskId(prevTaskId)
        val prevMap = prevResults.associateBy { it.mid }

        val intersectMids = thisMap.keys.intersect(prevMap.keys)
        val unexpectResultMids = intersectMids.filter { mid ->
            isBigDiff(thisMap[mid]!!.fans, prevMap[mid]!!.fans)
        }.toSet()

        val dayAgoTaskId = getPrevTaskId(prevTaskId)

        val dayAgoResults = getTaskResultsByTaskId(dayAgoTaskId)
        val dayAgoMap = dayAgoResults.filter { unexpectResultMids.contains(it.mid) }.associateBy { it.mid }

        // 获取前一任务中异常的MIDs，把这一部分从unexpectResultMids中移除
        val unexpectMidsInPrev = dayAgoMap.keys.filter { mid ->
            isBigDiff(dayAgoMap[mid]!!.fans, prevMap[mid]!!.fans)
        }

        abnormalMids.addAll(unexpectResultMids.filter { !unexpectMidsInPrev.contains(it) })
        return abnormalMids.map { Pair(it, thisMap[it]!!.fans) }
    }

    /**
     * 获取前一个taskId
     */
    fun getPrevTaskId(taskId: Long): Long {
        val someDayAgo = taskId - 3600 * 24 * 1000 * 7 //一周前
        val query = Query(Criteria.where("taskId").lt(taskId).gte(someDayAgo))

        val rslist = mongoTemplate.getCollection(TableNames.MONGO_TRACE_TASK_RESULT)
                .distinct("taskId", query.queryObject, java.lang.Long::class.java)
                .asSequence()
                .map { it.toLong() }
                .filter { it > 0 }
                .sortedDescending()
                .toList()
        return rslist.firstOrNull() ?: 0
    }

    /**
     * 任务代理，根据传入的参数执行任务
     * @param tasks 任务内容
     * @param fetchFunc 爬取方法
     * @return 成功爬取的mid
     */
    fun <T : Any> handleTask(tasks: List<FetchTask>, fetchFunc: (Long) -> T, midGetter: (T) -> Long): List<Long> {
        return tasks.map { task ->
            CompletableFuture.supplyAsync(Supplier { fetchFunc(task.mid) }, HttpSender.threadPool)
        }.asSequence()
                .map { it.get() }
                .filter { midGetter.invoke(it) > 0 }
                .toList()
                .apply { saveTaskResult(this, midGetter) }
                .map(midGetter)
    }

    fun fetchTraceTaskResult(mid: Long): TraceTaskResult {
        val partCard = fetchCard(mid)
        val archiveView = biliFetcher.fetchMemberArchiveView(mid)
        return if (archiveView < 0L) {
            TraceTaskResult()
        } else {
            TraceTaskResult().apply { BeanUtils.copyProperties(partCard, this) }.apply { this.archiveView = archiveView }
        }
    }

    fun fetchCardLocally(mid: Long): PartCard {
        return fetchCardCommon(mid, Spider(HttpSender.LOCALHOST, 0))
    }

    /**
     * 使用代理池IP爬取数据
     */
    fun fetchCard(mid: Long): PartCard {
        return fetchCardCommon(mid, null, 3)
    }

    /**
     * 当返回值的code!=0时进行重试;
     * code!=0有两种情况: A. 服务器抽风了，重新访问下又正常, B. 这个mid被屏蔽无法查询
     * 一般情况下无法区分，所以需要重试处理
     */
    fun fetchCardCommon(mid: Long, theSpider: Spider? = null, retry: Int = 0): PartCard {
        val spider = theSpider ?: Nest.getSpider()
        val html = spider.fetchCard(mid).also { Nest.returnSpider(spider) }
        if (html.isBlank()) return PartCard(0)

        var retVal = PartCard(0)
        try {
            val res = mapper.readTree(html)
            val code = res.get("code")?.asInt() ?: -1
            retVal = if (code == 0) {
                val partCard = PartCard(mid)
                res.get("data").get("card").apply {
                    partCard.name = this.get("name").asText()
                    partCard.fans = this.get("fans").asLong()
                    partCard.attention = this.get("attention").asLong()
                    partCard.face = this.get("face").asText().run {
                        if (this.contains("face/")) this.split("face/")[1] else this
                    }
                    partCard.sex = sexMap[this.get("sex").asText()] ?: 2
                    partCard.sign = this.get("sign").asText().trim()
                    partCard.rtime = Date().time
                    partCard.taskId = defaultTaskId
                }
                partCard.archive = res.get("data").get("archive_count").asLong()
                partCard
            } else {
                PartCard(mid)
            }
        } catch (e: Exception) {
            logger.debug("[Fetch Card] error mid=$mid, none.nintha.vtraceapi.spider=${spider.getProxyAddress()}(FT=${spider.failTimes}), html=$html", e)
        }

        // 对taskId = 0的异常情况进行重试
        if (retVal.taskId == 0L && retry > 0) {
            return fetchCardCommon(mid, theSpider, retry - 1)
        }

        if (retVal.taskId == 0L) retVal = PartCard(0)
        return retVal
    }


    fun <T : Any> saveTaskResult(results: Collection<T>, midGetter: (T) -> Long) {
        if (CollectionUtils.isEmpty(results)) return

        val docs = when {
            // PartCard => TraceTaskResult
            results.iterator().next() is PartCard -> {
                results.map { rs -> TraceTaskResult().apply { BeanUtils.copyProperties(rs, this) } }.filter { it.taskId > 0 }
            }
            else -> results
        }

        mongoTemplate.setWriteConcern(WriteConcern.UNACKNOWLEDGED)
        mongoTemplate.insert(docs, TableNames.MONGO_TRACE_TASK_RESULT)
    }

    fun resetTaskItem() {
        mongoTemplate.dropCollection(TableNames.MONGO_TRACE_TASK_TIEM)
        mongoTemplate.indexOps(TableNames.MONGO_TRACE_TASK_TIEM).ensureIndex(Index("mid", Sort.Direction.ASC).unique())
        mongoTemplate.indexOps(TableNames.MONGO_TRACE_TASK_TIEM).ensureIndex(Index("status", Sort.Direction.ASC))
    }

    fun saveTaskItem(items: Collection<FetchTask>) {
        mongoTemplate.setWriteConcern(WriteConcern.UNACKNOWLEDGED)
        mongoTemplate.insert(items, TableNames.MONGO_TRACE_TASK_TIEM)
    }


    fun countTaskItem(): Long {
        return mongoTemplate.count(Query(), TableNames.MONGO_TRACE_TASK_TIEM)
    }

    fun countTaskResult(): Long {
        return mongoTemplate.count(Query(), TableNames.MONGO_TRACE_TASK_RESULT)
    }


    /**
     * 发送任务报告邮件
     */
    fun sendTaskResultEmail(results: List<TraceTaskResult>, taskSize: Long) {
        if (StringUtils.isEmpty(taskWatcherEmails)) return
        if (CollectionUtils.isEmpty(results)) return

        val taskId: Long = results.first().taskId
        val subject = "Vtracer Task Report ${TimeUtil.ofEpochSec(taskId / 1000)}"
        val text = """
            |${TimeUtil.ofEpochSec(taskId / 1000)}
            |taskId=$taskId
            |result/taskSize=${results.size}/$taskSize""".trimMargin()

        val csv = "mid,fans,archive,archiveView,taskId,rtime\n" + results
                .map { "${it.mid},${it.fans},${it.archive},${it.archiveView},${it.taskId},${it.rtime}" }
                .joinToString("\n")

        if (Files.notExists(Paths.get("csv"))) {
            Files.createDirectory(Paths.get("csv"))
        }
        val zipFileName = "csv/$taskId.zip"
        val fos = Files.newOutputStream(Paths.get(zipFileName))
        ZipOutputStream(fos).apply { this.setLevel(9) }.use { zip ->
            zip.putNextEntry(ZipEntry("output.csv"))
            zip.write(csv.toByteArray())
            zip.closeEntry()
        }

        taskWatcherEmails.split(",").map { it.trim() }.forEach { email ->
            mailService.sendAttachmentsMail(email, text, subject, mapOf("$taskId.zip" to zipFileName))
        }

        logger.info("sendTaskResultEmail, emails=$taskWatcherEmails")

    }
}
