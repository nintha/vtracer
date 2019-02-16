package none.nintha.vtraceapi.controller

import io.swagger.annotations.Api
import io.swagger.annotations.ApiParam
import none.nintha.vtraceapi.entity.Results
import none.nintha.vtraceapi.entity.TraceTaskResult
import none.nintha.vtraceapi.service.BiliService
import none.nintha.vtraceapi.service.TaskDataService
import none.nintha.vtraceapi.spider.FetchTask
import none.nintha.vtraceapi.spider.Nest
import none.nintha.vtraceapi.util.TimeUtil
import org.apache.commons.io.IOUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import javax.servlet.http.HttpServletResponse


@Api(tags = ["Nest"])
@RestController
class NestController {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)
    @Autowired
    lateinit var biliService: BiliService
    @Autowired
    lateinit var taskDataService: TaskDataService

    @GetMapping("/proxy/nest")
    fun proxySize(): Results<Map<String, Int>> {
        val map: Map<String, Int> = mapOf("total" to Nest.PROXY_MAP.size, "queue" to Nest.MAIN_QUEUE.size)
        return Results.success(map)
    }

    @GetMapping("/tasks/status")
    fun countTask(): Results<Map<String, Number>> {
        val map: Map<String, Number> = mapOf(
                "nestSize" to Nest.PROXY_MAP.size,
                "queue" to Nest.MAIN_QUEUE.size,
                "running" to BiliService.taskStatus,
                "total" to biliService.countTaskItem(),
                "finished" to biliService.countTaskResult())
        return Results.success(map)
    }


    @GetMapping("/tasks/runInMemoryTask")
    fun runInMemoryTask(): Results<Long> {
        return Results.success(biliService.runInMemoryTask())
    }

    @PostMapping("/tasks/upload")
    fun uploadTasks(file: MultipartFile): Results<Map<String, Int>> {
        if (BiliService.taskStatus != 0) {
            return Results.failed("Task is running")
        }
        val reader = file.inputStream.bufferedReader()
        biliService.resetTaskItem()
        Files.deleteIfExists(Paths.get(BiliService.zipFileName))
        logger.info("uploadTasks start")
        var duplicate = 0
        val tasks: MutableSet<FetchTask> = mutableSetOf()
        while (true) {
            val line = reader.readLine() ?: break
            try {
                if (!tasks.add(FetchTask(line.toLong()))) duplicate++
            } catch (e: Exception) {
                logger.warn("uploadTasks " + e.message)
            }
        }
        biliService.saveTaskItem(tasks)
        logger.info("uploadTasks done, unique=${tasks.size}, duplicate=$duplicate")
        return Results.success(mapOf("unique" to tasks.size, "duplicate" to duplicate))
    }

    @PostMapping("/tasks/csv/download/{taskId}")
    fun downloadCSV(response: HttpServletResponse, @PathVariable taskId: Long){
        val results = taskDataService.getTaskResultsByTaskId(taskId)
        val csv = "mid,fans,archive,archiveView,taskId,rtime\n" + results
                .map { "${it.mid},${it.fans},${it.archive},${it.archiveView},${it.taskId},${it.rtime}" }
                .joinToString("\n")
        val byteArray = csv.toByteArray()
        downloadFromInputStream(response, ByteArrayInputStream(byteArray), byteArray.size, "$taskId.csv")
    }

    private fun downloadFromInputStream(response: HttpServletResponse, input: InputStream, length: Int, filename: String) {
        //设置文件输出类型
        response.contentType = "application/octet-stream"
        response.setHeader("Content-disposition", "attachment; filename=$filename")
        //设置输出长度
        response.setHeader("Content-Length", length.toString())
        IOUtils.copy(input, response.outputStream)
    }

    @ApiParam("导出TOP粉丝数，month是需要导出的月份（可选），默认值为当月")
    @GetMapping("/tasks/export/topFansData")
    fun exportTopFansData(month: Int?): Results<*> {
        taskDataService.exportTopFansData(month ?: LocalDate.now().monthValue)
        return Results.success(null)
    }
    @ApiParam("按时间区间获取taskIds")
    @GetMapping("/tasks/taskIds/from/{from}/to/{to}")
    fun getTaskIdsByMonth(@PathVariable from: Long, @PathVariable to: Long): Results<*> {
        return Results.success(taskDataService.getTaskIdsByTimeRange(TimeUtil.ofEpochMilli(from), TimeUtil.ofEpochMilli(to)))
    }
    @ApiParam("上月的taskIds")
    @GetMapping("/tasks/lastMonthTaskIds")
    fun getLastMonthTaskIds(): Results<*> {
        return Results.success(taskDataService.getLastMonthTaskIds())
    }
    @ApiParam("当月的taskIds")
    @GetMapping("/tasks/thisMonthTaskIds")
    fun getThisMonthTaskIds(): Results<*> {
        return Results.success(taskDataService.getThisMonthTaskIds())
    }

    @GetMapping("/tasks/result/detail/{taskId}")
    fun getTaskResultByTaskIds(@PathVariable taskId: Long): Results<List<TraceTaskResult>> {
        return Results.success(taskDataService.getTaskResultsByTaskId(taskId))
    }

    @GetMapping("/tasks/result/count/{taskId}")
    fun countTaskResultByTaskIds(@PathVariable taskId: Long): Results<Long> {
        return Results.success(taskDataService.countTaskResultsByTaskId(taskId))
    }

    @GetMapping("/tasks/prev/taskId/{taskId}")
    fun getPrevTaskId(@PathVariable taskId: Long): Results<Long> {
        return Results.success(biliService.getPrevTaskId(taskId))
    }

    @ApiParam("和前一个task比较，返回数据异常的mid和fans")
    @GetMapping("/tasks/compareFans/taskId/{taskId}")
    fun compareFans(@PathVariable taskId: Long) : Results<List<Pair<Long, Long>>>{
        return Results.success(biliService.getUnexpectPairs(taskId))
    }
}
