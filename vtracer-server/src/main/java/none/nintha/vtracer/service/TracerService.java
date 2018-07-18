package none.nintha.vtracer.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.mongodb.client.result.UpdateResult;
import none.nintha.vtracer.entity.*;
import none.nintha.vtracer.entity.constant.TableNames;
import none.nintha.vtracer.entity.constant.TraceStatus;
import none.nintha.vtracer.util.HttpSender;
import none.nintha.vtracer.util.TimeUtil;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
public class TracerService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


    private final static String API_UP_SUBMIT = "http://space.bilibili.com/ajax/member/getSubmitVideos?mid=${mid}&pagesize=1&page=1&order=pubdate";
    private final static String API_VIDEO_STAT_BATCH = "http://api.bilibili.com/x/article/archives?ids=${aids}";
    private final static String API_VIDEO_STAT = "http://api.bilibili.com/x/web-interface/archive/stat?aid=${aid}";
    private final static String API_VIDEO_HTML = "http://www.bilibili.com/video/av${aid}";

    // 默认视频追踪时长(天)
    private final static int TRACING_PERIOD_DAYS = 7;

    private final static int TRACE_MEMBER_MAX = 150;
    private final static int TRACE_VIDEO_MAX = 300;

    // {mid: aid}
    private final static Map<Long, Long> traceVideoMap = Maps.newConcurrentMap();

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    BiliFetcher biliFetcher;

    /**
     * 发现UP新上传视频
     *
     * @param mid
     */
    public void traceMemberArchive(long mid) {
        try {
            JsonNode node = mapper.readTree(new URL(API_UP_SUBMIT.replace("${mid}", mid + "")));
            JsonNode item = node.get("data").get("vlist").get(0);
            if (item == null) return;
            long aid = item.get("aid").asLong();

            Long lastAid = traceVideoMap.put(mid, aid);
            // 第一次运行或没有新视频结束任务
            if (lastAid == null || aid == lastAid) return;

            this.addTraceVideo(aid);
        } catch (Exception e) {
            logger.error("solve member least video error.", e);
        }
    }

    /**
     * 追踪UP数据变化，如fans
     *
     * @param mid
     */
    public void traceMemberInfo(long mid) {
        BiliMember member = biliFetcher.fetchBiliMember(mid);
        if (member == null) return;

        long view = biliFetcher.fetchMemberArchiveView(mid);
        MemberInfo info = MemberInfo.ofBiliMember(member);
        info.setArchiveView(view);
        MemberInfo lastMemberInfo = getLastMemberInfo(mid);
        // 重复数据不再插入
        if (Objects.equals(info, lastMemberInfo) == false) {
            mongoTemplate.insert(info, TableNames.MONGO_TRACE_MEMBER_INFO);
        }
    }

    /**
     * 获取最后一次用户信息追踪记录
     */
    private MemberInfo getLastMemberInfo(long mid) {
        Query query = new Query(Criteria.where("mid").is(mid)).with(new Sort(Sort.Direction.DESC, "ctime"));
        return mongoTemplate.findOne(query, MemberInfo.class, TableNames.MONGO_TRACE_MEMBER_INFO);
    }

    /**
     * 追踪视频数据
     *
     * @param aid
     */
    public void traceVideo(long aid) {
        try {
            JsonNode node = mapper.readTree(new URL(API_VIDEO_STAT.replace("${aid}", aid + "")));
            String dataJson = node.get("data").toString();
            VideoStat videoStat = mapper.readValue(dataJson, VideoStat.class);
            if (videoStat == null) return;

            VideoStat last = getLastVideoStat(aid);
            // 重复数据不再插入
            if (Objects.equals(videoStat, last) == false) {
                videoStat.setCtime(new Date());
                mongoTemplate.insert(videoStat, TableNames.MONGO_TRACE_VIDEO_STAT);
            }
        } catch (Exception e) {
            logger.error("solve video stat error, aid={}.", aid, e);
        }
    }

    /**
     * 批量追踪视频数据， 一次最多追踪100个
     */
    public void traceVideoBatch(List<Long> aidList) {
        if (CollectionUtils.isEmpty(aidList)) return;
        if (aidList.size() > 100) {
            aidList = aidList.subList(0, 100);
        }
        JsonNode node = null;
        try {
            String aids = aidList.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
            node = mapper.readTree(new URL(API_VIDEO_STAT_BATCH.replace("${aids}", aids)));
        } catch (Exception e) {
            logger.error("batch trace video stat, fetch error, aids={}.", aidList, e);
        }
        for (long aid : aidList) {
            try {
                if(node.get("data").get(aid + "") == null){
                    logger.warn("batch trace video stat, aid:{} is not exist.", aid);
                    continue;
                }
                JsonNode jsonNode = node.get("data").get(aid + "").get("stat");
                if (jsonNode.isNull()) {
                    continue;
                }
                VideoStat videoStat = mapper.readValue(jsonNode.toString(), VideoStat.class);
                if (videoStat == null) return;

//                System.out.println(videoStat);
                VideoStat last = getLastVideoStat(aid);
                // 重复数据不再插入
                if (Objects.equals(videoStat, last) == false) {
                    videoStat.setCtime(new Date());
                    mongoTemplate.insert(videoStat, TableNames.MONGO_TRACE_VIDEO_STAT);
                }
            } catch (Exception e) {
                logger.error("batch trace video stat, loop error, aid={}.", aid, e);
            }
        }

    }

    private VideoStat getLastVideoStat(long aid) {
        Query query = new Query(Criteria.where("aid").is(aid)).with(new Sort(Sort.Direction.DESC, "ctime"));
        return mongoTemplate.findOne(query, VideoStat.class, TableNames.MONGO_TRACE_VIDEO_STAT);
    }

    public long countTraceVideoByMid(long mid) {
        return mongoTemplate.count(new Query(Criteria.where("mid").is(mid)), TableNames.MONGO_TRACE_VIDEO);
    }

    public List<TraceVideo> getTraceVideosByMid(long mid, long skip, int limit) {
        Sort sort2 = new Sort(Sort.Direction.DESC, "status").and(new Sort(Sort.Direction.DESC, "ctime"));
        Query query = new Query(Criteria.where("mid").is(mid)).with(sort2).limit(limit).skip(skip);
        return mongoTemplate.find(query, TraceVideo.class, TableNames.MONGO_TRACE_VIDEO);
    }

    public long countEnableTraceVideo() {
        return mongoTemplate.count(new Query(Criteria.where("status").is(TraceStatus.RUNNING)), TableNames.MONGO_TRACE_VIDEO);
    }

    public long countTraceVideo() {
        return mongoTemplate.count(new Query(), TableNames.MONGO_TRACE_VIDEO);
    }

    public List<TraceVideo> getTraceVideos(long skip, int limit) {
        Sort sort2 = new Sort(Sort.Direction.DESC, "status").and(new Sort(Sort.Direction.DESC, "ctime"));
        return mongoTemplate.find(new Query().with(sort2).limit(limit).skip(skip), TraceVideo.class, TableNames.MONGO_TRACE_VIDEO);
    }

    public long countTraceMember() {
        return mongoTemplate.count(new Query(), TableNames.MONGO_TRACE_MEMBER);
    }

    public List<TraceMember> getTraceMembers(long skip, int limit) {
        Sort sort = new Sort(Sort.Direction.DESC, "ctime");
        return mongoTemplate.find(new Query().with(sort).skip(skip).limit(limit), TraceMember.class, TableNames.MONGO_TRACE_MEMBER);
    }

    public int addTraceMember(long mid) {
        TraceMember tm = mongoTemplate.findOne(new Query(Criteria.where("mid").is(mid)), TraceMember.class, TableNames.MONGO_TRACE_MEMBER);
        if (tm != null) return 0;
        if (this.countTraceMember() >= TRACE_MEMBER_MAX) return 0;

        BiliMember biliMember = biliFetcher.fetchBiliMember(mid);
        TraceMember member = new TraceMember();
        member.setMid(mid);
        member.setName(biliMember.getName());
        member.setFace(biliMember.getFace());
        member.setCtime(new Date());
        mongoTemplate.insert(member, TableNames.MONGO_TRACE_MEMBER);
        return 1;
    }

    public int addTraceVideo(long aid) {
        boolean exists = mongoTemplate.exists(new Query(Criteria.where("aid").is(aid)), TableNames.MONGO_TRACE_VIDEO);
        if (exists) return 0;
        if (this.countEnableTraceVideo() >= TRACE_VIDEO_MAX) return 0;

        JsonNode videoInfo = fetchVideoInfo(aid);

        TraceVideo tv = new TraceVideo();
        tv.setAid(aid);
        tv.setMid(videoInfo == null ? 0 : videoInfo.get("owner").get("mid").asLong(0));
        tv.setStatus(TraceStatus.RUNNING.code);
        tv.setTitle(videoInfo == null ? Strings.EMPTY : videoInfo.get("title").asText());
        tv.setPic(videoInfo == null ? Strings.EMPTY : videoInfo.get("pic").asText());
        tv.setCtime(new Date());
        // 默认追踪7天
        tv.setEndTime(TimeUtil.toDate(LocalDateTime.now().plusDays(TRACING_PERIOD_DAYS)));
        // 把新视频加入追踪表里
        mongoTemplate.insert(tv, TableNames.MONGO_TRACE_VIDEO);
        return 1;
    }

    private JsonNode fetchVideoInfo(long aid) {
        String html = HttpSender.get(API_VIDEO_HTML.replace("${aid}", aid + ""));
        String json = null;
        try {
            Pattern regex = Pattern.compile("window.__INITIAL_STATE__=(.+?);\\(function");
            Matcher regexMatcher = regex.matcher(html);
            if (regexMatcher.find()) {
                json = regexMatcher.group(1);
            }
            return mapper.readTree(json).get("videoData");
        } catch (Exception ex) {
            logger.error("fetch video info error.\n json:{}", json, ex);
        }
        return null;
    }

    public long updateEndTime(long aid, Date endTime) {
        Query query = new Query(Criteria.where("aid").is(aid));
        Update update = Update.update("endTime", endTime);
        UpdateResult updateResult = mongoTemplate.updateFirst(query, update, TableNames.MONGO_TRACE_VIDEO);
        return updateResult.getModifiedCount();
    }

    public long updateTraceStatus(long aid, int status) {
        Query query = new Query(Criteria.where("aid").is(aid));
        Update update = Update.update("status", status);
        UpdateResult updateResult = mongoTemplate.updateFirst(query, update, TableNames.MONGO_TRACE_VIDEO);
        return updateResult.getModifiedCount();
    }

    /**
     * 删除追踪member，同时删除对应的追踪数据
     */
    public long removeTraceMember(long mid) {
        Query query = new Query(Criteria.where("mid").is(mid));
        long effect = mongoTemplate.remove(query, TableNames.MONGO_TRACE_MEMBER).getDeletedCount();
        if (effect > 0) mongoTemplate.remove(query, TableNames.MONGO_TRACE_MEMBER_INFO);
        return effect;
    }

    /**
     * 删除追踪video，同时删除对应的追踪数据
     */
    public long removeTraceVideo(long aid) {
        Query query = new Query(Criteria.where("aid").is(aid));
        long effect = mongoTemplate.remove(query, TableNames.MONGO_TRACE_VIDEO).getDeletedCount();
        if (effect > 0) mongoTemplate.remove(query, TableNames.MONGO_TRACE_VIDEO_STAT);
        return effect;
    }

    /**
     */
    public List<VideoStat> getVideoStatForChart(long aid, Date startTime, Date endTime) {
        long interval = (endTime.getTime() - startTime.getTime()) / 300;
        TypedAggregation<VideoStat> agg = Aggregation.newAggregation(
                VideoStat.class,
                match(Criteria.where("aid").is(aid).and("ctime").gte(startTime).lte(endTime)),
                project("ctime", "coin", "share", "view", "danmaku", "favorite", "reply", "like", "dislike")
                        .andExpression("ceil((ctime - [0]) / [1])", new Date(0), interval)
                        .as("cdate"),
                group("cdate")
                        .max("view").as("view")
                        .max("coin").as("coin")
                        .max("danmaku").as("danmaku")
                        .max("favorite").as("favorite")
                        .max("reply").as("reply")
                        .max("share").as("share")
                        .max("like").as("like")
                        .max("dislike").as("dislike")
                        .max("ctime").as("ctime"),
                sort(Sort.Direction.ASC, "ctime"));
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, TableNames.MONGO_TRACE_VIDEO_STAT, Map.class);
        List<Map> mappedResults = results.getMappedResults();
        return mappedResults.stream().map(v -> mapper.convertValue(v, VideoStat.class)).collect(Collectors.toList());
    }

    /**
     */
    public List<MemberInfo> getMemberInfoForChart(long mid, Date startTime, Date endTime) {
        long interval = (endTime.getTime() - startTime.getTime()) / 300;
        TypedAggregation<MemberInfo> agg = Aggregation.newAggregation(
                MemberInfo.class,
                match(Criteria.where("mid").is(mid).and("ctime").gte(startTime).lte(endTime)),
                project("ctime", "fans", "archiveView")
                        .andExpression("ceil((ctime - [0]) / [1])", new Date(0), interval)
                        .as("cdate"),
                group("cdate")
                        .max("fans").as("fans")
                        .max("archiveView").as("archiveView")
                        .max("ctime").as("ctime"),
                sort(Sort.Direction.ASC, "ctime"));
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, TableNames.MONGO_TRACE_MEMBER_INFO, Map.class);
        List<Map> mappedResults = results.getMappedResults();
        // 先用map接收数据可以过滤NULL值，防止NPE异常
        return mappedResults.stream().map(v -> mapper.convertValue(v, MemberInfo.class)).collect(Collectors.toList());
    }

    public static void main(String[] args) {
        new TracerService().traceVideoBatch(Arrays.asList(24852251L, 24834131L, 24833129L, 24823510L, 24788948L, 24750208L, 24729595L, 24706532L, 24702428L, 24700430L, 24669669L, 24664229L, 24633291L, 2580484L, 24618353L, 24586349L, 24577001L, 24575061L, 24561131L, 24525126L, 24511060L, 24507374L, 24503002L, 24500861L, 24500598L, 24165605L, 23937952L, 23867003L));
    }

}
