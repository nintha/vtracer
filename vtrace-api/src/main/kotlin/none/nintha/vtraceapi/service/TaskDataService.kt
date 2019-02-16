package none.nintha.vtraceapi.service

import none.nintha.vtraceapi.entity.TraceTaskResult
import none.nintha.vtraceapi.spider.TableNames
import none.nintha.vtraceapi.util.TimeUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 任务数据处理服务
 */
@Service
class TaskDataService {
    private val logger: Logger = LoggerFactory.getLogger(TaskDataService::class.java)

    companion object {
        const val LABEL_TOP_300 = "top300"
        const val LABEL_RISE_TOP_30 = "riseTop30"
        const val LABEL_FALL_TOP_30 = "fallTop30"
        const val LABEL_GREAT_THAN_500K = "gt500k"
    }

    @Autowired
    lateinit var mongoTemplate: MongoTemplate
    @Autowired
    lateinit var biliService: BiliService

    /**
     * 获取目标数据:每天top30并集, 每天大于500k并集
     */
    fun getTargetData(taskIds: List<Long>): Map<String, MutableSet<Long>> {

        val retVal: Map<String, MutableSet<Long>> = mapOf(
                LABEL_RISE_TOP_30 to mutableSetOf(),
                LABEL_FALL_TOP_30 to mutableSetOf(),
                LABEL_TOP_300 to mutableSetOf(),
                LABEL_GREAT_THAN_500K to mutableSetOf()
        )

        var lastTimeFansMap: Map<Long, Long> = mapOf()
        taskIds.forEach { taskId ->
            val taskResults = getTaskResultsByTaskId(taskId)
            taskResults.asSequence().sortedByDescending { it.fans }.take(300).forEach { retVal[LABEL_TOP_300]!!.add(it.mid) }
            taskResults.asSequence().filter { it.fans >= 500_000 }.forEach { retVal[LABEL_GREAT_THAN_500K]!!.add(it.mid) }
            // deltaTop30, exclude first loop
            if (lastTimeFansMap.isNotEmpty()) {
                val sortedList = taskResults.asSequence()
                        .filter { lastTimeFansMap.containsKey(it.mid) }
                        .map { Pair(it.mid, it.fans - lastTimeFansMap[it.mid]!!) } // <mid, fans>
                        .sortedByDescending { it.second }.toList()

                sortedList.take(30).forEach { retVal[LABEL_RISE_TOP_30]!!.add(it.first) }
                sortedList.filter { it.second < 0 }.sortedBy { it.second }.take(30).forEach { retVal[LABEL_FALL_TOP_30]!!.add(it.first) }
            }

            lastTimeFansMap = taskResults.associateBy(TraceTaskResult::mid, TraceTaskResult::fans)
        }
        return retVal
    }

    /**
     * 获取当月所有的taskId
     */
    fun getThisMonthTaskIds(): List<Long> {
        val today = LocalDate.now(TimeUtil.ZONE_SHANGHAI)
        val from = today.withDayOfMonth(1).atStartOfDay();
        val to = today.withDayOfMonth(today.lengthOfMonth()).plusDays(1).atStartOfDay()
        return getTaskIdsByTimeRange(from, to)
    }

    /**
     * 获取上个月所有的taskId
     */
    fun getLastMonthTaskIds(): List<Long> {
        val lastMonthDay = LocalDate.now(TimeUtil.ZONE_SHANGHAI).minusMonths(1)
        val from = lastMonthDay.withDayOfMonth(1).atStartOfDay();
        val to = lastMonthDay.withDayOfMonth(lastMonthDay.lengthOfMonth()).plusDays(1).atStartOfDay()
        return getTaskIdsByTimeRange(from, to)
    }

    /**
     * 根据时间区间获取taskId
     * @param from: included
     * @param to: excluded
     */
    fun getTaskIdsByTimeRange(from: LocalDateTime, to: LocalDateTime): List<Long> {
        logger.info("[getTaskIdsByTimeRange] from=$from, to=$to")
        val sort = Sort(Sort.Direction.ASC, "taskId")
        val query = Query(Criteria.where("taskId")
                .gte(from.let { Timestamp.valueOf(it) }.time)
                .lte(to.let { Timestamp.valueOf(it) }.time)).with(sort)

        return mongoTemplate.getCollection(TableNames.MONGO_TRACE_TASK_RESULT)
                .distinct("taskId", query.queryObject, java.lang.Long::class.java)
                .asSequence()
                .map { it.toLong() }
                .filter { it > 0 }
                .toList()
    }

    fun countTaskResultsByTaskId(taskId: Long): Long {
        val query = Query(Criteria.where("taskId").`is`(taskId))
        return mongoTemplate.count(query, TraceTaskResult::class.java, TableNames.MONGO_TRACE_TASK_RESULT)
    }

    fun getTaskResultsByTaskId(taskId: Long): List<TraceTaskResult> {
        val taskSize = mongoTemplate.count(Query(Criteria.where("taskId").`is`(taskId)), TraceTaskResult::class.java, TableNames.MONGO_TRACE_TASK_RESULT)
        return biliService.queryResultByTaskId(taskId, taskSize.toInt())
    }

    fun getTaskResultsByTaskIdAndMids(taskId: Long, mids: Collection<Long>): List<TraceTaskResult> {
        val sort = Sort(Sort.Direction.ASC, "mid")
        val query = Query(Criteria.where("taskId").`is`(taskId).and("mid").`in`(mids)).with(sort)
        return mongoTemplate.find(query, TraceTaskResult::class.java, TableNames.MONGO_TRACE_TASK_RESULT)
    }

    // taskId: 任务开始时间戳
    fun parseTaskId(taskId: Long): LocalDateTime {
        return Timestamp.from(Instant.ofEpochMilli(taskId)).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
    }

    fun writeFile(fileName: String, sortedMids: List<Long>, taskIds: List<Long>) {
        Files.newOutputStream(Paths.get(fileName)).bufferedWriter().use { bw ->
            bw.write("time," + sortedMids.joinToString(","))
            taskIds.sorted().forEach { taskId ->
                bw.newLine()
                val rs = getTaskResultsByTaskIdAndMids(taskId, sortedMids)
                bw.write(parseTaskId(taskId).toString())

                val rsMap = rs.associateBy { it.mid }
                sortedMids.forEach { mid ->
                    bw.write("," + (rsMap[mid]?.fans?.toString() ?: ""))
                }
                bw.flush()
            }
        }
    }

    fun exportTopFansData(month:Int) {
        logger.info("start export top fans data")
        val day = LocalDate.now(TimeUtil.ZONE_SHANGHAI).withMonth(month)
        val from = day.withDayOfMonth(1).atStartOfDay()
        val to = day.withDayOfMonth(day.lengthOfMonth()).plusDays(1).atStartOfDay()
        val taskIds = getTaskIdsByTimeRange(from,to)
        val map = getTargetData(taskIds)

        fun writeFileAndlog(label: String) {
            writeFile("${label}_${day.year}-${day.monthValue}.csv", map[label]!!.sorted(), taskIds)
            logger.info("end ${label}_${day.year}-${day.monthValue}.csv")
        }

        map.keys.forEach{ writeFileAndlog(it)}
        logger.info("end export top fans data")
    }

}

//fun main(args: Array<String>) {
//
//    println(LocalDateTime.of(2018, Month.NOVEMBER, 1, 0, 0, 0))
//
//}
