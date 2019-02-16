package none.nintha.vtraceapi.controller

import io.swagger.annotations.Api
import io.swagger.annotations.ApiParam
import none.nintha.vtraceapi.entity.Results
import none.nintha.vtraceapi.service.BiliFetcher
import none.nintha.vtraceapi.service.BiliService
import none.nintha.vtraceapi.service.MailService
import none.nintha.vtraceapi.service.TaskDataService
import none.nintha.vtraceapi.spider.TableNames
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.Null


@Api(tags = ["Test"])
@RestController
class TestController {
    val logger: Logger = LoggerFactory.getLogger(TestController::class.java)
    @Autowired
    lateinit var mongoTemplate: MongoTemplate
    @Autowired
    lateinit var biliService: BiliService
    @Autowired
    lateinit var biliFetcher: BiliFetcher
    @Autowired
    lateinit var taskDataService: TaskDataService

    @Autowired
    lateinit var mailService: MailService

    @ApiParam("视频实时在线人数")
    @GetMapping("/test/onlineCount")
    fun proxySize(aid: Long): Results<Long> {
        val onlineCount = biliFetcher.fetchVideoOnlineCount(aid)
        return Results.success(onlineCount)
    }

    @GetMapping("/test/sendMail")
    fun sendMail(target: String, text: String): Results<Long> {
        mailService.sendAttachmentsMail(target, text, attachments = mapOf("data.csv" to "csv/1.csv"))
        return Results.success(null)
    }

    @GetMapping("/test/sendLastTaskReport")
    fun sendLastTaskReport(): Results<Long> {
        val taskId = taskDataService.getLastMonthTaskIds().last()
        val results = taskDataService.getTaskResultsByTaskId(taskId)
        biliService.sendTaskResultEmail(results, results.size.toLong())
        return Results.success(null)
    }

    @ApiParam("测试：执行一次任务")
    @GetMapping("/test/runInMemoryTask")
    fun runInMemoryTask(): Results<Long> {
        if (BiliService.taskStatus >= 1) return Results.failed("Task is running")
        // run
        val taskId = biliService.runInMemoryTask()
        return Results.success(taskId)
    }

    @ApiParam("测试：执行一次任务并进行清理")
    @GetMapping("/test/runInMemoryTaskAndCleanIt")
    fun runInMemoryTaskAndCleanIt(): Results<Null> {
        if (BiliService.taskStatus >= 1) return Results.failed("Task is running")
        // run
        val taskId = biliService.runInMemoryTask()

        // clean
        val removeQuery = Query(Criteria.where("taskId").`is`(taskId))
        mongoTemplate.remove(removeQuery, TableNames.MONGO_TRACE_TASK_RESULT)
        logger.info("clean task=$taskId")
        // check
        val count = mongoTemplate.count(removeQuery, TableNames.MONGO_TRACE_TASK_RESULT)
        if(count > 0){
            logger.warn("测试后清理出现异常, taskId=$taskId")
        }
        logger.info("clean over")
        return Results.success()
    }
}
