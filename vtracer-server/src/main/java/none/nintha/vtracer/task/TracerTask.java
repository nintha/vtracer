package none.nintha.vtracer.task;

import none.nintha.vtracer.entity.TraceMember;
import none.nintha.vtracer.entity.TraceVideo;
import none.nintha.vtracer.entity.constant.TraceStatus;
import none.nintha.vtracer.service.TracerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
@Component

public class TracerTask{
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    // 查询步进
    private final static int QUERY_LIMIT = 100;

    @Autowired
    TracerService tracerService;

    @Scheduled(cron = "40 0/5 * * * ? ")
    void memberLoop() {
        long memberCount = tracerService.countTraceMember();
        for (long skip = 0; skip < memberCount; skip += QUERY_LIMIT) {
            List<TraceMember> members = tracerService.getTraceMembers(skip, QUERY_LIMIT);
            members.forEach(m -> {
                tracerService.traceMemberArchive(m.getMid());
                tracerService.traceMemberInfo(m.getMid());
            });
        }
        logger.info("trace member, size={}", memberCount);
    }

    @Scheduled(cron = "20 0/2 * * * ? ")
    void videoLoop() {
        Date now = new Date();
        long videoCount = tracerService.countTraceVideo();
        long traceCount = 0;
        for (long skip = 0; skip < videoCount; skip += QUERY_LIMIT) {
            List<TraceVideo> videos = tracerService.getTraceVideos(skip, QUERY_LIMIT);
            List<Long> matchAids = videos.stream()
                    .filter(v -> v.getStatus() == TraceStatus.RUNNING.code)
                    .filter(v -> v.getEndTime().after(now))
                    .map(TraceVideo::getAid)
                    .collect(Collectors.toList());
            tracerService.traceVideoBatch(matchAids);
            // 更新已经过期但为running状态的任务
            videos.stream().filter(v -> v.getEndTime().before(now))
                    .filter(v -> v.getStatus() == TraceStatus.RUNNING.code)
                    .forEach(v -> tracerService.updateTraceStatus(v.getAid(), TraceStatus.STOPPED.code));

            traceCount += matchAids.size();
        }
        logger.info("trace video, size={}", traceCount);
    }


}
