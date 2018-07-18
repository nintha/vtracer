package none.nintha.vtracer.controller;

import none.nintha.vtracer.entity.MemberInfo;
import none.nintha.vtracer.entity.TraceMember;
import none.nintha.vtracer.entity.TraceVideo;
import none.nintha.vtracer.entity.VideoStat;
import none.nintha.vtracer.entity.constant.TraceStatus;
import none.nintha.vtracer.service.TracerService;
import none.nintha.vtracer.util.Result;
import none.nintha.vtracer.util.TimeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class MainController {
    @Autowired
    TracerService tracerService;

    @PostMapping("/trace/member/{mid}")
    public Object addTraceMember(@PathVariable long mid) {
        int effect = tracerService.addTraceMember(mid);
        return Result.success().put("effect", effect);
    }

    @PostMapping("/trace/video/{aid}")
    public Object addTraceVideo(@PathVariable long aid) {
        int effect = tracerService.addTraceVideo(aid);
        return Result.success().put("effect", effect);
    }

    @GetMapping("/trace/member/page/{pageNum}")
    public Object listTraceMember(@PathVariable int pageNum, @RequestParam(defaultValue = "20") int pageSize) {
        long count = tracerService.countTraceMember();
        List<TraceMember> members = tracerService.getTraceMembers((pageNum - 1) * pageSize, pageSize);
        return Result.success().put("list", members).put("total", count).put("pageSize", pageSize);
    }

    @GetMapping("/trace/video/page/{pageNum}")
    public Object listTraceVideo(@PathVariable int pageNum, @RequestParam(defaultValue = "20") int pageSize) {
        long count = tracerService.countTraceVideo();
        List<TraceVideo> list = tracerService.getTraceVideos((pageNum - 1) * pageSize, pageSize);
        return Result.success().put("list", list).put("total", count).put("pageSize", pageSize);
    }

    @GetMapping("/trace/video/mid/{mid}/page/{pageNum}")
    public Object listTraceVideoByMid(
            @PathVariable long mid,
            @PathVariable int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {

        long count = tracerService.countTraceVideoByMid(mid);
        List<TraceVideo> list = tracerService.getTraceVideosByMid(mid, (pageNum - 1) * pageSize, pageSize);
        return Result.success().put("list", list).put("total", count).put("pageSize", pageSize).put("mid", mid);
    }

    @GetMapping("/trace/videoStat/{aid}")
    public Object listVideoStatForChart(@PathVariable long aid, String st, String et) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Date sTime = TimeUtil.toDate(LocalDateTime.parse(st, df));
        Date eTime = TimeUtil.toDate(LocalDateTime.parse(et, df));
        List<VideoStat> list = tracerService.getVideoStatForChart(aid, sTime, eTime);
        return Result.success().put("list", list).put("total", list.size());
    }

    @GetMapping("/trace/memberInfo/{mid}")
    public Object listMemberInfoForChart(@PathVariable long mid, String st, String et) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Date sTime = TimeUtil.toDate(LocalDateTime.parse(st, df));
        Date eTime = TimeUtil.toDate(LocalDateTime.parse(et, df));
        List<MemberInfo> list = tracerService.getMemberInfoForChart(mid, sTime, eTime);
        return Result.success().put("list", list).put("total", list.size());
    }


//    @PutMapping("/trace/video/{aid}/endTime/{endTime}")
//    public Object updateTraceStatus(@PathVariable long aid, @PathVariable long endTime) {
//        long effect = tracerService.updateEndTime(aid, Date.from(Instant.ofEpochMilli(endTime)));
//
//        return Result.success().put("effect", effect);
//    }

    @PutMapping("/trace/video/{aid}/status/{status}")
    public Object updateTraceStatus(@PathVariable long aid, @PathVariable int status) {
        long effect = tracerService.updateTraceStatus(aid, status);
        // 开启后自动续期一周
        if (effect > 0 && status == TraceStatus.RUNNING.code) {
            LocalDateTime weekAfter = LocalDateTime.now().plusDays(7);
            tracerService.updateEndTime(aid, TimeUtil.toDate(weekAfter));
        }
        return Result.success().put("effect", effect);
    }

    @DeleteMapping("/trace/member/{mid}")
    public Object removeTraceMember(@PathVariable long mid) {
        long effect = tracerService.removeTraceMember(mid);
        return Result.success().put("effect", effect);
    }
}
