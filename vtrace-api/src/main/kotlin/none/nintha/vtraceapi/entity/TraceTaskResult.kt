package none.nintha.vtraceapi.entity

data class TraceTaskResult(var taskId: Long = 0,
                           var mid: Long = 0,
                           var fans: Long = 0,
                           var archive: Long = 0,
                           var archiveView: Long = 0,
                           var rtime: Long = 0)
